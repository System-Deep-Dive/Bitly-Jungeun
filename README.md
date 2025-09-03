## Design Doc: 빠른 리다이렉트가 가능한 URL 단축기

본 문서는 구글식 디자인 문서 구조를 참고하여(URL 단축기의) 리다이렉트 경로를 초저지연으로 만드는 설계를 요약합니다.

### 1. Context & Scope
- 문제 배경: URL 단축기는 극단적인 읽기 중심 워크로드를 갖습니다. 수백만~수억 건의 리다이렉트 트래픽에서 p95/p99 지연을 낮추는 것이 핵심입니다.
- 현재 가정 트래픽: 1억 DAU × 5회/일 ≈ 5억/일 → 평균 ~5,787 RPS, 피크 100× 가정 시 ~60만 RPS.
- 스코프: 단축 코드 → 원본 URL 매핑 조회 경로 최적화(데이터 레이어 및 엣지)
- 비스코프: 단축 URL 생성 알고리즘의 상세(충돌 방지, 키 공간 설계 등)는 별도 문서에서 다룸.

### 2. Goals / Non-Goals
- Goals
  - p95 리다이렉트 지연 단자리 ms(지역/엣지 히트 시)를 목표
  - 피크 시 ~60만 RPS 리다이렉트 처리 가능
  - 고가용성: 단일 장애 지점 제거, 캐시/DB/엣지 이중화
  - 캐시 히트율 90%+ (핫 코드 기준), 오리진 도달율 최소화
- Non-Goals
  - 단축 URL 생성 파이프라인 최적화
  - 광고/과금 로직 최적화
  - 정교한 AB 테스트/퍼스널라이제이션

### 3. System Context Diagram (고수준)
- 사용자는 `https://sho.rt/{code}` 로 접속
- CDN(전세계 PoP) → 엣지 함수(코드 조회) →
  - 엣지 캐시 hit 시: 301/302 즉시 응답
  - miss 시: 오리진 API → Redis →(miss)→ DB 조회 후 응답 및 상위 캐시 적재
  - 관측(로그/메트릭/트레이싱)은 엣지/오리진 모두에서 수집

### 4. APIs (스케치)
- GET `/{code}`: 코드로 리다이렉트 수행
  - 301/302 Location: `<original_url>`
  - 캐시 제어 헤더: 엣지 캐시 가능, 단 TTL/무효화 정책 고려
- POST `/shorten` (참고): 원본 URL → 단축 코드 생성(본 문서 비스코프)

### 5. Data Storage
- 테이블: `url_mapping`
  - `code` (PK, 고정 길이 문자열) — 단축 코드, 기본 키 및 인덱스
  - `original_url` (text)
  - `created_at`, `expires_at`(선택)
- 인덱싱
  - B-트리 인덱스(기본) 또는 해시 인덱스(정확 일치 최적화, PostgreSQL 등)
  - 샤딩/파티셔닝: 코드 해시 기반 범용 샤딩, 리전별 리드 레플리카

### 6. Design
- 6.1 읽기 경로 레이어링
  - 엣지 캐시(및 엣지 키-밸류 저장) → 오리진 Redis/Memcached → RDBMS
  - 캐시 키: `url:{code}` → value: `<original_url>`
  - TTL: 인기 코드 장기 캐시, 비인기 코드 짧은 TTL 또는 캐시 미적재

  - 엣지(Cloudflare Workers, Lambda@Edge)
    - 인기 코드 즉시 리다이렉트, 오리진 경유 차단
    - 캐시 무효화: 관리용 API 또는 tag 기반 purge

  - 오리진(애플리케이션)
    - 캐시 미스 시 Redis 조회, 다시 미스면 DB 조회 후 301/302
    - 결과를 Redis 및 엣지에 업서트(upsert)

- 6.2 캐시 정책
  - 에비션: LRU 우선, 크기 제한 엄수
  - TTL: 트래픽 기반 가변 TTL(핫 키는 길게)
  - 프리워밍: 롤아웃 전 상위 N개 인기 코드 프리로드

- 6.3 일관성/무효화
  - 대부분 read-only. 삭제/만료/수정 이벤트 시
    - 오리진에서 Redis/엣지에 무효화 브로드캐스트
    - 지연 허용 시 TTL 자연 만료

### 7. Alternatives Considered
- 단일 DB만으로 버티기
  - 장점: 단순. 단일 신뢰 경로
  - 단점: 초고 RPS 피크 처리 어려움, 스케일/비용 한계
- CDN만 사용(오리진 캐시 미활용)
  - 장점: 사용자는 빠름(히트 시)
  - 단점: 글로벌 무효화/미스 처리 비용 증가, 오리진 병목 발생
- NoSQL(예: DynamoDB) 단독
  - 장점: 키-밸류 조회에 적합, 수평 확장
  - 단점: 기존 RDB 스키마/관계 활용 어려움, 마이그레이션 비용

### 8. Cross-Cutting Concerns
- 보안: 개방 리다이렉트 방지(허용 도메인 검증), 악용 방지 레이트 리밋
- 프라이버시: 쿼리 파라미터/PII 로깅 최소화, 지역 규제 준수
- 관측성: RPS, p50/95/99, 히트율, 오리진 도달율, 4xx/5xx, 엣지/오리진 트레이싱
- 신뢰성: 멀티 리전, 다중 캐시 노드, 캐시 장애 시 페일오버 경로 확인
- 비용: CDN/엣지/캐시/DB 별 단가 모니터링, 인기 키 집중 최적화

### 9. Rollout Plan
- 단계 1: DB 인덱싱/샤딩 정비, 읽기용 레플리카 확충
- 단계 2: 오리진 Redis 도입, 캐시 키/TTL 설계, 프리워밍
- 단계 3: CDN/엣지 함수 배포, 인기 코드 엣지 캐시
- 단계 4: 글로벌 무효화/태그 purge 자동화, 히트율/지연 최적화 반복

### 10. Metrics & SLO
- SLO: p95 리다이렉트 < 50ms(리전 내), 가용성 ≥ 99.95%
- 핵심 지표: 캐시 히트율, 오리진 도달율, 엣지/오리진 p95/99, 에러율, 비용/요청

### 11. Risks & Mitigations
- 글로벌 캐시 불일치 → TTL/태그 purge, 변경 이벤트 브로드캐스트
- 엣지 제한(메모리/런타임) → 경량 로직, 키 압축, 외부 의존 최소화
- 핫 키 쏠림 → 레이트 리밋/스티키 캐시, 다중 리전 분산

### 12. Open Questions
- 만료 정책: per-code TTL vs 글로벌 TTL 최적 조합?
- 멀티 테넌트/도메인 지원 시 키 스키마 확장 방안?
- 퍼스널라이즈드 리다이렉트(기기/지역별) 요구가 생길 경우 엣지 로직 분기 전략?

### 13. References
- Google 스타일 디자인 문서 개요 정리: GN 기사 요약 참고 (https://news.hada.io/topic?id=14704)


## Local Dev Setup (Docker Compose)

### Prerequisites
- Docker Desktop (Compose v2)

### Services
- PostgreSQL 16, Redis 7(alpine). Spring Boot 앱은 추후 `api` 서비스로 추가 예정.

### Memory Limits in Compose
- `deploy.resources.limits.memory`는 Swarm 용 필드입니다. 로컬 Compose에서 강제하려면 `docker compose --compatibility up` 또는 서비스별 `mem_limit`(레거시) 사용을 권장합니다.
- JVM(스프링) 컨테이너는 `-XX:+UseContainerSupport -XX:MaxRAMPercentage=<N>`로 컨테이너 메모리 한도를 인지시켜야 OOM을 피할 수 있습니다.

### How to Run
```bash
docker compose --compatibility up -d
```

### Connection Info
- Postgres: `localhost:5432` (user: bitly, password: bitly, db: bitly)
- Redis: `localhost:6379`

### Spring Boot (예시 설정)
- `application.yml` 예시
```yaml
spring:
  datasource:
    url: jdbc:postgresql://postgres:5432/bitly
    username: bitly
    password: bitly
  redis:
    host: redis
    port: 6379
server:
  port: 8080
```

### Notes
- Redis는 `--maxmemory 1gb --maxmemory-policy allkeys-lru`로 설정되어 LRU 에비션 동작.
- Postgres/Redis 모두 healthcheck 포함.
- 추후 엣지/CDN 적용 시, 인기 코드 Top-N 프리워밍과 태그 기반 purge 전략을 고려하세요.
