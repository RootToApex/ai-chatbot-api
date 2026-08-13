# AI 챗봇 API

Kotlin + Spring Boot 기반 챗봇 API 서버. 회원 인증(JWT), OpenAI 연동 대화(스트리밍 포함), 피드백, 관리자 분석·보고 기능을 제공한다.

## 실행 방법

```bash
docker compose up -d --wait   # PostgreSQL 15.8
./gradlew bootRun             # 애플리케이션 실행
./gradlew test                # 테스트 (OpenAI 키 불필요 — 결정론적 fake 사용)
```

- OpenAI 실 호출에는 `OPENAI_API_KEY` 환경변수가 필요하다 (`.env.example` 참고). 키가 없으면 대화 생성 API는 명시적 503을 반환한다 — 그 외 전 기능은 키 없이 동작한다.

## 과제 분석

(제출 전 작성 — 요구사항을 어떻게 읽었고 무엇을 우선했는지)

## 설계 계획

### 요구사항 매트릭스

| ID | 기능 | 우선순위 | 완료 판정 |
|---|---|---|---|
| A1 | 회원가입 | 필수 | 이메일 중복 409, 성공 201 + 사용자 정보(비밀번호 제외) |
| A2 | 로그인 | 필수 | 일치 시 200 + JWT, 불일치 401 |
| A3 | JWT 인증 | 필수 | 가입/로그인 제외 전 요청에 유효 토큰 없으면 401 |
| C1 | 스레드 자동 생성/재사용 | 필수 | 첫 질문 또는 마지막 질문 후 30분 경과 시 신규, 이내면 기존 유지 |
| C2 | 대화 생성 | 필수 | 질문→답변 응답, isStreaming/model 옵션 동작 |
| C3 | 대화 목록 조회 | 필수 | 스레드 단위 그룹화, 본인만(admin 전체), 정렬+페이지네이션 |
| C4 | 스레드 삭제 | 필수 | 본인 스레드만 204, 타인 접근 403/404 |
| F1 | 피드백 생성 | 필수 | 본인 대화만(admin 전체), 동일 (user,chat) 중복 409 |
| F2 | 피드백 목록 조회 | 필수 | 본인만(admin 전체), 정렬+페이지네이션+긍정/부정 필터 |
| F3 | 피드백 상태 변경 | 필수 | admin만, pending/resolved |
| R1 | 활동 기록 | 필수 | 요청 시점 기준 최근 24h 가입/로그인/대화 수 (admin) |
| R2 | 보고서 CSV | 필수 | 최근 24h 전체 대화+작성자 CSV (admin) |

구현 순서: A → C → F → R (git log가 곧 우선순위 기록)

### API

모든 경로 `/api/v1` 접두. 시각은 UTC(timestamptz) 기준.

| 메서드 | 경로 | 권한 | 요청 | 응답·상태코드 |
|---|---|---|---|---|
| POST | /auth/signup | 공개 | email, password, name | 201 사용자 정보 / 400 / 409 |
| POST | /auth/login | 공개 | email, password | 200 accessToken / 401 |
| POST | /chats | 인증 | question, isStreaming?, model? | 201 chat / isStreaming=true면 200 text/event-stream / 400 / 503(LLM 불가) |
| GET | /chats | 인증 | page, size, sort=asc\|desc | 200 스레드 그룹 페이지 (admin은 전체 유저) / 400 |
| DELETE | /threads/{id} | 인증 | - | 204 / 403 / 404 |
| POST | /feedbacks | 인증 | chatId, isPositive | 201 / 403 / 404 / 409 |
| GET | /feedbacks | 인증 | page, size, sort, isPositive? | 200 페이지 / 400 |
| PATCH | /feedbacks/{id}/status | admin | status=pending\|resolved | 200 / 400 / 403 / 404 |
| GET | /admin/activity | admin | - | 200 {signupCount, loginCount, chatCount} |
| GET | /admin/report | admin | - | 200 CSV 다운로드 |

인증: `Authorization: Bearer <JWT>`. 401(미인증)/403(권한 부족)은 전 엔드포인트 공통.

### 데이터 모델

- **users** — id PK, email unique, password(해시), name, role(member|admin), created_at
- **threads** — id PK, user_id FK, created_at, last_question_at(30분 경계 판단)
- **chats** — id PK, thread_id FK, question, answer, created_at
- **feedbacks** — id PK, user_id FK, chat_id FK, unique(user_id, chat_id), is_positive, status, created_at
- **login_events** — id PK, user_id FK, created_at (활동 기록의 로그인 수 집계용)

관계: users 1:N threads 1:N chats, users·chats 1:N feedbacks. 스레드 삭제 시 chats·feedbacks ON DELETE CASCADE.

### 주요 가정 (요구사항이 정하지 않아 직접 결정한 것)

| # | 가정 | 결정 |
|---|---|---|
| 1 | 30분 경계 기준 | 스레드의 마지막 질문 시각(last_question_at)을 질문마다 갱신, 경과 시 새 스레드 |
| 2 | LLM에 보내는 이력 | 현재 스레드의 최근 20개 대화만 전송 (DB에는 전부 저장, 조회는 전체) |
| 3 | OpenAI 키 부재 시 | 대화 생성만 명시적 503. 테스트는 결정론적 fake로 키 없이 재현 |
| 4 | LLM 호출 실패 시 | chat 행을 남기지 않고 5xx 응답 (질문만 저장된 반쪽 데이터를 만들지 않음) |
| 5 | 스트리밍 | SSE(text/event-stream), 스트림 완료 후 DB 저장, 중간 실패 시 error 이벤트 |
| 6 | admin 생성 | 시드 마이그레이션(패스워드는 환경변수). 회원가입 API는 항상 member — role을 입력으로 받지 않음 |
| 7 | 대화 목록 페이지네이션 | 스레드 단위 페이징(0-base, 생성일시+id 정렬), 스레드 내부 대화는 오름차순 고정, size 상한 100 |
| 8 | "하루 동안" | 요청 시점부터 rolling 24시간(UTC). 로그인 수는 login_events 테이블로 집계 |
| 9 | 스레드 삭제 | 하위 chats·feedbacks까지 물리 삭제(CASCADE). soft delete는 요구에 없어 배제 |

## 구현 범위와 우선순위

(제출 전 작성 — 매트릭스 대비 충족 여부)

## AI 활용 방식과 어려움

(제출 전 작성)

## 가장 어려웠던 기능

(제출 전 작성)

## 설계 판단과 트레이드오프

(제출 전 작성 — 무엇을 왜 버렸는지)
