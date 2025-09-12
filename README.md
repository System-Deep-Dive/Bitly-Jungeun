## Design Doc: 단계별 성능 최적화 실험 - URL 단축기

본 문서는 실제 성능 측정을 통해 데이터베이스 최적화와 캐싱 전략의 효과를 검증하는 실험을 위한 디자인 문서입니다.

### 1. Context & Scope
- 문제 배경: URL 단축기의 핵심 병목은 단축 코드 → 원본 URL 조회 성능입니다.
- 실험 목표: 동일한 워크로드에서 인덱스와 캐싱의 성능 개선 효과를 k6로 측정
- 현재 가정 트래픽: 1,000 DAU × 10회/일 ≈ 10,000/일 → 평균 ~0.12 RPS (실험용 소규모)
- 스코프: 단축 코드 조회 경로 최적화 (DB 인덱스 + Redis 캐시)
- 비스코프: 단축 URL 생성 알고리즘, CDN/엣지 최적화

### 2. Goals / Non-Goals
- Goals
  - 각 최적화 단계별 응답 시간 측정 (p95, p99)
  - 캐시 히트율 90%+ 달성 (캐시 단계)
  - 단계별 성능 개선율 정량적 측정
- Non-Goals
  - 프로덕션 레벨 트래픽 처리 (60만 RPS)
  - CDN/엣지 컴퓨팅 구현
  - 고가용성/장애 복구

### 3. System Context Diagram (실험용)
- 사용자는 `http://localhost:8080/api/v1/urls/{shortUrl}` 로 접속
- **Phase 1**: Spring Boot → PostgreSQL (인덱스 없음)
- **Phase 2**: Spring Boot → PostgreSQL (인덱스 있음)
- **Phase 3**: Spring Boot → Redis → PostgreSQL (캐시 + 인덱스)

### 4. APIs

#### 4.1 기본 API
- **GET** `/api/v1/urls/{shortUrl}`: 코드로 리다이렉트 수행 (302)
- **POST** `/api/v1/urls`: 원본 URL → 단축 코드 생성

#### 4.2 Phase별 테스트 API들
- **GET** `/api/v1/urls/baseline/{shortUrl}`: **Phase 1** - DB 직접 조회 (인덱스 없음)
- **GET** `/api/v1/urls/indexed/{shortUrl}`: **Phase 2** - 인덱스 활용 조회
- **GET** `/api/v1/urls/cached/{shortUrl}`: **Phase 3** - Redis 캐시 우선 조회

#### 4.3 모니터링 API (Phase 3)
- **GET** `/api/v1/test/stats`: 캐시 통계 조회
- **POST** `/api/v1/test/load-test`: 간단한 로드 테스트

### 5. Data Storage
- 테이블: `url_mapping`
  - `id` (PK, 고정 길이 문자열) — 단축 코드, 기본 키 및 인덱스
  - `original_url` (text)
  - `shortUrl`
  - `created_at`, `expires_at`(선택)
- 인덱싱
  - B-트리 인덱스(기본) 또는 해시 인덱스(정확 일치 최적화, PostgreSQL 등)
  - 샤딩/파티셔닝: 코드 해시 기반 범용 샤딩, 리전별 리드 레플리카

### 6. Performance Optimization Phases

#### 6.1 Phase 1: Baseline (인덱스 없음)
- **아키텍처**: Spring Boot → PostgreSQL
- **특징**: 가장 단순한 구현, DB 직접 조회
- **예상 성능**: 50-200ms per request (Full table scan)
- **목적**: 기준 성능 측정

#### 6.2 Phase 2: Database Indexing (인덱스 있음)
- **아키텍처**: Spring Boot → PostgreSQL (with index)
- **최적화**: `short_url` 컬럼에 B-tree 인덱스 추가
- **특징**: O(log n) 조회 시간
- **예상 성능**: 10-50ms per request (5-10x 개선)
- **목적**: 인덱싱 효과 검증

#### 6.3 Phase 3: Redis Caching (캐시 + 인덱스)
- **아키텍처**: Spring Boot → Redis → PostgreSQL
- **캐시 전략**:
  - 키 포맷: `url:{shortCode}`
  - TTL: 일반 URL 1시간, 인기 URL 24시간
  - 에비션: LRU (Redis 기본)
- **특징**: O(1) 캐시 조회, DB 폴백
- **예상 성능**: 1-5ms per request (100x 개선)
- **캐시 히트율 목표**: 90%+

#### 6.4 캐시 정책 및 일관성
- **에비션 정책**: Redis LRU (최근 사용 우선)
- **TTL 전략**: 길이 기반 간단 판별 (3자리 이하 = 핫)
- **일관성**: Read-only 워크로드로 캐시 무효화 최소화
- **모니터링**: 히트율, 미스율 실시간 추적

### 7. Test Strategy & Tools

#### 7.1 k6 Load Testing
- **테스트 시나리오**: 동일한 1,000개 단축 URL에 대한 반복 조회
- **부하 패턴**: 10 VU × 30초 (총 300회 요청)
- **측정 지표**:
  - Response time (avg, p95, p99)
  - Requests per second
  - Error rate
  - Throughput

#### 7.2 Performance Comparison Matrix
| Phase | Architecture | Expected p95 | Expected RPS | Cache Hit Rate |
|-------|-------------|--------------|--------------|----------------|
| 1 | DB Only | 100-200ms | ~10-20 | N/A |
| 2 | DB + Index | 20-50ms | ~50-100 | N/A |
| 3 | Redis + DB | 5-20ms | ~200-500 | 90%+ |

### 8. Implementation Plan

#### 8.1 Phase 1: Baseline Setup (1-2일)
- 현재 코드 상태로 성능 측정
- k6 스크립트 작성 및 실행
- 기준 성능 데이터 수집

#### 8.2 Phase 2: Database Optimization (1-2일)
- PostgreSQL에 `short_url` 인덱스 추가
- 동일 k6 테스트 실행
- 인덱스 효과 정량적 측정

#### 8.3 Phase 3: Caching Implementation (2-3일)
- Redis 캐시 서비스 구현
- 캐시 통계 모니터링 추가
- 캐시 워밍업 및 히트율 최적화

### 9. Metrics & Success Criteria

#### 9.1 Performance Targets
- **Phase 1**: Baseline 측정 (현재 성능)
- **Phase 2**: Phase 1 대비 5x 이상 성능 개선
- **Phase 3**: Phase 1 대비 20x 이상 성능 개선, 캐시 히트율 90%+

#### 9.2 Monitoring Metrics
- **응답 시간**: p95, p99, 평균
- **처리량**: RPS, 총 요청 수
- **캐시 효율**: 히트율, 미스율
- **에러율**: HTTP 4xx/5xx 비율

### 10. Risks & Mitigations

#### 10.1 Technical Risks
- **캐시 스탬피드**: 동일 키 동시 요청 → Redis 싱글 스레드 특성으로 자연 해결
- **메모리 부족**: Redis 캐시 크기 제한 → LRU 에비션 정책으로 자동 관리
- **캐시 일관성**: DB 변경 시 캐시 무효화 → Read-only 워크로드로 최소화

#### 10.2 Measurement Risks
- **테스트 환경 편향**: 로컬 환경 한계 → Docker Compose로 일관된 환경 유지
- **부하 패턴 부정확**: 실제 트래픽과 다름 → 동일 URL 반복 조회로 Hot key 시뮬레이션
- **측정 오차**: 네트워크 지연 등 → 동일 조건 반복 테스트로 평균화

### 11. k6 Test Scripts

#### 11.1 Phase별 Load Test 스크립트들

**k6-baseline.js** (Phase 1: 인덱스 없음)
```javascript
import http from 'k6/http';
import { check, sleep } from 'k6';

export let options = {
  vus: 10,
  duration: '30s',
  thresholds: {
    http_req_duration: ['p(95)<500'], // Phase 1에서는 느릴 수 있음
  },
};

const shortUrls = ['abc123', 'def456', 'ghi789']; // 테스트용 URL들

export default function () {
  const randomUrl = shortUrls[Math.floor(Math.random() * shortUrls.length)];
  let response = http.get(`http://localhost:8080/api/v1/urls/baseline/${randomUrl}`);

  check(response, {
    'status is 302': (r) => r.status === 302,
    'has location header': (r) => r.headers['Location'] !== undefined,
  });

  sleep(0.1);
}
```

**k6-indexed.js** (Phase 2: 인덱스 있음)
```javascript
import http from 'k6/http';
import { check, sleep } from 'k6';

export let options = {
  vus: 10,
  duration: '30s',
  thresholds: {
    http_req_duration: ['p(95)<100'], // 인덱스로 빨라질 것
  },
};

const shortUrls = ['abc123', 'def456', 'ghi789'];

export default function () {
  const randomUrl = shortUrls[Math.floor(Math.random() * shortUrls.length)];
  let response = http.get(`http://localhost:8080/api/v1/urls/indexed/${randomUrl}`);

  check(response, {
    'status is 302': (r) => r.status === 302,
    'response time improved': (r) => r.timings.duration < 200, // Phase 1보다 개선
  });

  sleep(0.1);
}
```

**k6-cached.js** (Phase 3: 캐시 있음)
```javascript
import http from 'k6/http';
import { check, sleep } from 'k6';

export let options = {
  vus: 10,
  duration: '30s',
  thresholds: {
    http_req_duration: ['p(95)<50'], // 캐시로 매우 빨라질 것
  },
};

const shortUrls = ['abc123', 'def456', 'ghi789'];

export default function () {
  const randomUrl = shortUrls[Math.floor(Math.random() * shortUrls.length)];
  let response = http.get(`http://localhost:8080/api/v1/urls/cached/${randomUrl}`);

  check(response, {
    'status is 302': (r) => r.status === 302,
    'cache hit or fast response': (r) => r.timings.duration < 20, // 캐시 히트
  });

  sleep(0.05); // 더 빠른 요청 간격
}
```


### 12. Success Criteria & Validation

#### 12.1 Phase별 검증 포인트
- **Phase 1**: 안정적인 베이스라인 측정
- **Phase 2**: 인덱스 추가 후 5x 성능 개선 확인
- **Phase 3**: 캐시 적용 후 20x 성능 개선 + 90% 히트율 달성

#### 12.2 결과 해석
- 실제 성능 수치는 하드웨어/데이터 크기에 따라 달라질 수 있음
- 상대적 개선율이 더 중요 (Phase 1 → Phase 2 → Phase 3)
- 캐시 히트율은 워크로드 패턴에 따라 조정 필요

### 13. References
- k6 Documentation: https://k6.io/docs/
- Redis Caching Patterns: https://redis.io/documentation
- PostgreSQL Indexing: https://www.postgresql.org/docs/current/indexes.html

## Local Dev Setup (Docker Compose)

### Prerequisites
- Docker Desktop (Compose v2)

### Services
- PostgreSQL 16, Redis 7(alpine). Spring Boot 앱은 추후 `api` 서비스로 추가 예정.

### Memory Limits in Compose
- `deploy.resources.limits.memory`는 Swarm 용 필드입니다. 로컬 Compose에서 강제하려면 `docker compose --compatibility up` 또는 서비스별 `mem_limit`(레거시) 사용을 권장합니다.
- JVM(스프링) 컨테이너는 `-XX:+UseContainerSupport -XX:MaxRAMPercentage=<N>`로 컨테이너 메모리 한도를 인지시켜야 OOM을 피할 수 있습니다.

### How to Run

#### 전체 모니터링 스택 실행
```bash
# 모든 서비스 실행 (Spring Boot + 모니터링)
docker compose --compatibility up -d

# 또는 모니터링만 실행
docker compose --compatibility up -d prometheus grafana node-exporter postgres-exporter cadvisor
```

#### Spring Boot 앱 실행
```bash
./gradlew bootRun
```

#### 모니터링 대시보드 접속
- **Grafana**: http://localhost:3000 (admin/admin)
- **Prometheus**: http://localhost:9090
- **cAdvisor**: http://localhost:8081
- **Node Exporter**: http://localhost:9100
- **PostgreSQL Exporter**: http://localhost:9187
- **Redis Exporter**: http://localhost:9121

### Grafana Dashboard

#### 1. Spring Boot 애플리케이션 모니터링
- **Dashboard ID**: `4701` (JVM Micrometer) - Spring Boot 3.x 호환

#### 2. 시스템 모니터링
- **Dashboard ID**: `11074` (Node Exporter for Prometheus Dashboard)

#### 3. PostgreSQL 모니터링
- **Dashboard ID**: `9628` (PostgreSQL Database)
- **Dashboard ID**: `455` (PostgreSQL Overview)

#### 4. Redis 모니터링
- **Dashboard ID**: `763` (Redis Dashboard)
- **Dashboard ID**: `11835` (Redis Exporter)

#### k6 성능 테스트 대시보드
- **Dashboard ID**: `2587` (k6 Performance Testing)
- **용도**: k6 성능 테스트 결과 시각화
- **데이터소스**: InfluxDB (k6 메트릭)

#### 대시보드 Import 방법
1. Grafana 접속: http://localhost:3000
2. 좌측 메뉴 → **Dashboards** → **+ New** → **Import**
3. **Import via grafana.com** 탭 선택
4. **Dashboard ID** 입력 (예: `4701`, `2587`)
5. **Load** → **Prometheus** 또는 **InfluxDB** 데이터소스 선택 → **Import**

### Connection Info
- Postgres: `localhost:5432` (user: bitly, password: bitly, db: bitly)
- Redis: `localhost:6379`

### Spring Boot 설정
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

### Performance Testing Flow
1. **Phase 1**: `k6 run k6-baseline.js` (현재 상태로 테스트)
2. **Phase 2**: 인덱스 추가 후 `k6 run k6-indexed.js` 실행
3. **Phase 3**: 캐시 구현 후 `k6 run k6-cached.js` 실행

### Troubleshooting
- **포트 충돌**: `docker compose down`으로 정리 후 재시작
- **메모리 부족**: Docker Desktop 메모리 할당량 증가
- **Redis 연결 실패**: `docker compose logs redis`로 확인
