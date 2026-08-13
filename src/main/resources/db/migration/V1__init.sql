-- 식별자는 UUID로 둔다. 순번이 노출되지 않고, 분산 환경에서 미리 생성할 수 있다.
CREATE TABLE users (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email      VARCHAR(255) NOT NULL,
    password   VARCHAR(255) NOT NULL,
    name       VARCHAR(100) NOT NULL,
    role       VARCHAR(20)  NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uk_users_email UNIQUE (email)
);

CREATE INDEX idx_users_created_at ON users (created_at);

CREATE TABLE threads (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id          UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_question_at TIMESTAMPTZ NOT NULL
);

-- 30분 경계 판정: 유저의 가장 최근 스레드를 last_question_at 역순으로 1건 조회
CREATE INDEX idx_threads_user_last_question ON threads (user_id, last_question_at DESC);
-- 목록 조회 정렬: 스레드 생성일시 + id tie-breaker
CREATE INDEX idx_threads_user_created ON threads (user_id, created_at, id);

CREATE TABLE chats (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    thread_id  UUID        NOT NULL REFERENCES threads (id) ON DELETE CASCADE,
    question   TEXT        NOT NULL,
    answer     TEXT        NOT NULL,
    model      VARCHAR(100),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_chats_thread_created ON chats (thread_id, created_at, id);
-- 최근 24시간 집계·보고서
CREATE INDEX idx_chats_created_at ON chats (created_at);

CREATE TABLE feedbacks (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    chat_id     UUID        NOT NULL REFERENCES chats (id) ON DELETE CASCADE,
    is_positive BOOLEAN     NOT NULL,
    status      VARCHAR(20) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_feedbacks_user_chat UNIQUE (user_id, chat_id)
);

CREATE INDEX idx_feedbacks_user_created ON feedbacks (user_id, created_at, id);

CREATE TABLE login_events (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_login_events_created_at ON login_events (created_at);
