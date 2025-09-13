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


### 5. Data Storage

#### 5.1 테이블 분리 구조 (Phase별 성능 측정용)
- **`url_mapping_baseline`**: Phase 1용 (인덱스 없음)
  - `id` (PK, Auto Increment)
  - `short_url` (varchar, unique) — 인덱스 없음 (Full Table Scan)
  - `original_url` (varchar, 2000자)

- **`url_mapping_indexed`**: Phase 2용 (인덱스 있음)  
  - `id` (PK, Auto Increment)
  - `short_url` (varchar, unique) — B-tree 인덱스 있음 (Index Scan)
  - `original_url` (varchar, 2000자)
  - 인덱스: `idx_short_url_indexed` on `short_url`

#### 5.2 데이터 동기화 전략
- **URL 생성 시**: 2개 테이블에 동시 저장 (동일한 shortUrl, originalUrl)
- **Phase별 조회**: 각각 다른 테이블에서 조회하여 성능 차이 측정
- **캐시 레이어**: Phase 3에서 Redis 캐시 추가 (테이블은 indexed 사용)

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
  - TTL: 15분 (단일 정책으로 단순화)
  - 에비션: LRU (Redis 기본)
- **특징**: O(1) 캐시 조회, DB 폴백
- **예상 성능**: 1-5ms per request (100x 개선)
- **캐시 히트율 목표**: 90%+

#### 6.4 캐시 정책 및 일관성
- **에비션 정책**: Redis LRU (최근 사용 우선)
- **TTL 전략**: 15분 고정 (단순화된 정책)
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


### 8. Metrics & Success Criteria

#### 8.1 Performance Targets
- **Phase 1**: Baseline 측정 (현재 성능)
- **Phase 2**: Phase 1 대비 5x 이상 성능 개선
- **Phase 3**: Phase 1 대비 20x 이상 성능 개선, 캐시 히트율 90%+

#### 8.2 Monitoring Metrics
- **응답 시간**: p95, p99, 평균
- **처리량**: RPS, 총 요청 수
- **캐시 효율**: 히트율, 미스율
- **에러율**: HTTP 4xx/5xx 비율


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
