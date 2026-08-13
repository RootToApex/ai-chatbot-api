#!/bin/bash
# 공통 시크릿 스캐너 — pre-commit / pre-push 가 함께 쓴다.
# 사용법: secret-scan.sh <라벨>   (검사할 텍스트는 stdin으로)
# 반환: 발견 시 1, 깨끗하면 0
#
# 08-09 보강: ①플레이스홀더 판정을 "줄 전체"가 아니라 "값 부분"으로 (줄 끝 # TODO로 우회되던 구멍)
#            ②비인용 값(password: Real123) 탐지  ③출력 마스킹(키 전문을 터미널에 재노출하지 않음)

set -uo pipefail

LABEL="${1:-검사}"
RED=$'\033[31m'; YEL=$'\033[33m'; BOLD=$'\033[1m'; OFF=$'\033[0m'
FAIL=0
INPUT=$(cat)

banner() {
  echo ""
  echo "${RED}${BOLD}╔══════════════════════════════════════════════════════════╗${OFF}"
  printf "${RED}${BOLD}║  🚨 %-52s║${OFF}\n" "$LABEL 차단 — 시크릿 의심"
  echo "${RED}${BOLD}╚══════════════════════════════════════════════════════════╝${OFF}"
}

# 값 부분이 플레이스홀더인가 (LOOSE 패턴에만 적용)
# 줄 전체가 아니라 마지막 : 또는 = 뒤의 "값"만 본다 — 뒤에 주석이 붙어도 값 판정은 안 바뀐다
is_placeholder() {
  local val
  val=$(printf '%s' "$1" | sed -E 's/.*[:=][[:space:]]*//')
  printf '%s' "$val" | grep -Eiq \
    '^["'"'"']?(your[-_a-z]*(key|secret|token|password|pass)|xxx+|changeme|change_me|placeholder|dummy[a-z0-9_-]*|fake[a-z0-9_-]*|sample[a-z0-9_-]*|example[a-z0-9_-]*|<[^>]+>|\$\{[^}]+\}|\.\.\.|todo$|replace[-_]?me)'
}

# check <STRICT|LOOSE> <이름> <정규식>
#   STRICT = 실제 키 형태. 플레이스홀더 예외 없음.
#            (AWS 공식 예제 키가 AKIAIOSFODNN7EXAMPLE인 것처럼 진짜 키에도 example이 들어간다)
#   LOOSE  = 일반 할당문. "값"이 플레이스홀더면 통과시킨다.
check() {
  local mode="$1" label="$2" re="$3" hits real=""
  hits=$(echo "$INPUT" | grep -En -- "$re" || true)   # '--' 없으면 '-----BEGIN'을 옵션으로 오인한다
  [ -z "$hits" ] && return 0
  if [ "$mode" = "STRICT" ]; then
    real="$hits"
  else
    while IFS= read -r line; do
      [ -z "$line" ] && continue
      is_placeholder "$line" || real="${real}${line}"$'\n'
    done <<< "$hits"
  fi
  [ -z "$real" ] && return 0
  [ "$FAIL" -eq 0 ] && banner
  echo "${RED}▶ ${label}${OFF}"
  # 🔒 마스킹: 10자 이상 토큰의 몸통을 가려서 보여준다 — 키 전문을 터미널·로그에 재노출하지 않기 위함
  echo "$real" | sed -E 's/[A-Za-z0-9+/_-]{10,}/████/g' | cut -c1-90 | sed 's/^/    /'
  FAIL=1
}

check STRICT "개인키 블록(PRIVATE KEY)"        '-----BEGIN [A-Z ]*PRIVATE KEY-----'
check STRICT "OpenAI/Anthropic/OpenRouter 키"  'sk-(ant-|or-v1-|proj-)?[A-Za-z0-9_-]{20,}'
check STRICT "Google API 키"                   'AIza[0-9A-Za-z_-]{35}'
check STRICT "AWS Access Key ID"               '(AKIA|ASIA)[0-9A-Z]{16}'
check STRICT "GitHub 토큰"                     'gh[pousr]_[A-Za-z0-9]{30,}'
check STRICT "Slack 토큰/웹훅"                 'xox[baprs]-[0-9A-Za-z-]{10,}|hooks\.slack\.com/services/'
check STRICT "Groq 키"                         'gsk_[A-Za-z0-9]{20,}'
check STRICT "HuggingFace 토큰"                'hf_[A-Za-z0-9]{20,}'
check STRICT "JWT (서명된 토큰)"               'eyJ[A-Za-z0-9_-]{10,}\.eyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]+'
# LOOSE — 값은 따옴표 유무 무관 8자+ (단 $로 시작하는 환경변수 참조는 제외)
check LOOSE  "AWS Secret Access Key 형태"      'aws_secret_access_key[[:space:]]*[:=]'
check LOOSE  "JWT 서명 시크릿 하드코딩"        '(jwt|token)[._-]?(secret|signing[-_]?key|private[-_]?key)[[:space:]]*[:=][[:space:]]*["'"'"']?[^"'"'"'[:space:]$]{8,}'
check LOOSE  "비밀번호/키 하드코딩"            '(api[-_]?key|secret[-_]?key|client[-_]?secret|access[-_]?token|refresh[-_]?token|password|passwd|credential)[[:space:]]*[:=][[:space:]]*["'"'"']?[^"'"'"'[:space:]$]{8,}'
check LOOSE  "DB URL에 자격증명 포함"          '(jdbc:|postgresql://|mysql://|mongodb(\+srv)?://)[^[:space:]"'"'"']*:[^[:space:]"'"'"'@/]{6,}@'

exit $FAIL
