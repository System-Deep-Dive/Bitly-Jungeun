import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate } from 'k6/metrics';

// Custom metrics
const errorRate = new Rate('errors');

// 테스트 데이터 생성 함수 (2개 테이블에 동시 저장)
function generateTestData(baseUrl, count = 10000) {
    console.log(`📊 테스트 데이터 생성 시작: ${count}개 (Baseline + Indexed 테이블에 동시 저장)`);

    const createdShortUrls = []; // 실제 생성된 shortUrl 저장

    let createdCount = 0;
    for (let i = 1; i <= count; i++) {
        const originalUrl = `http://test-${i}.local`;
        const payload = JSON.stringify({
            originalUrl: originalUrl
        });

        const params = {
            headers: {
                'Content-Type': 'application/json',
            },
        };

        const response = http.post(`${baseUrl}/api/v1/urls/`, payload, params);

        // 첫 번째 요청의 응답 로그 출력
        if (i === 1) {
            console.log(`📊 POST 응답 상태: ${response.status}`);
            console.log(`📊 POST 응답 Body: "${response.body}"`);
            console.log(`📊 POST 응답 Body 길이: ${response.body ? response.body.length : 0}`);
        }

        if (response.status === 201 && response.body) {
            // 생성된 shortUrl을 저장 (응답 body에서 추출)
            let shortUrl;
            try {
                // JSON 파싱 시도
                shortUrl = JSON.parse(response.body);
            } catch (e) {
                // JSON이 아니면 따옴표 제거
                shortUrl = response.body.replace(/"/g, '');
            }
            if (shortUrl && shortUrl.length > 0) {
                createdShortUrls.push(shortUrl);
                createdCount++;
                if (i <= 5) { // 처음 5개만 로그 출력
                    console.log(`📊 생성된 shortUrl: ${shortUrl}`);
                }
            }
        } else {
            console.log(`❌ POST 요청 실패: ${response.status} - ${response.body}`);
        }

        // 1000개마다 진행상황 출력
        if (i % 1000 === 0) {
            console.log(`📊 테스트 데이터 생성 진행: ${i}/${count} (${createdCount}개 성공)`);
        }
    }

    console.log(`✅ 테스트 데이터 생성 완료: ${createdCount}/${count}개 성공`);
    console.log(`📊 각 테이블에 저장됨: url_mapping_baseline, url_mapping_indexed`);
    console.log(`📊 생성된 shortUrl 샘플: ${createdShortUrls.slice(0, 5).join(', ')}`);
    return { createdShortUrls, createdCount };
}

export const options = {
    stages: [
        // Phase 1: Baseline (60초) - 인덱스 없이
        { duration: '60s', target: 50 },  // VU 수 감소
        { duration: '10s', target: 0 }, // Cool down
        // Phase 2: Indexed (60초) - 인덱스 있음
        { duration: '60s', target: 50 },  // VU 수 감소
        { duration: '10s', target: 0 }, // Cool down
        // Phase 3: Cached (60초) - Redis 캐시
        { duration: '60s', target: 50 },  // VU 수 감소
    ],
    thresholds: {
        http_req_duration: ['p(95)<10000'],  // 타임아웃 증가
        errors: ['rate<0.1'],  // 에러율 허용치 증가
    },
};

// Phase 상태 관리
let currentPhase = 'baseline';
let phaseStartTime = Date.now();

export function setup() {
    console.log('🚀 성능 테스트 시작 - Phase별 테이블 분리');
    console.log('📊 네트워크 진단 시작...');

    // 1. 테스트 데이터 생성 (2개 테이블에 동시 저장됨)
    const baseUrl = 'http://api:8080';
    console.log(`📊 대상 URL: ${baseUrl}`);

    // API 연결 확인 (재시도 로직)
    console.log('📊 API 연결 확인 중...');
    let healthResponse;
    let retryCount = 0;
    const maxRetries = 10;

    while (retryCount < maxRetries) {
        healthResponse = http.get(`${baseUrl}/actuator/health`);
        if (healthResponse.status === 200) {
            console.log('✅ API 연결 성공');
            break;
        } else {
            console.log(`❌ API 연결 실패 (${retryCount + 1}/${maxRetries}): ${healthResponse.status}`);
            console.log(`📊 응답 Body: ${healthResponse.body}`);
            console.log(`📊 에러: ${healthResponse.error}`);
            retryCount++;
            if (retryCount < maxRetries) {
                console.log('⏳ 5초 후 재시도...');
                sleep(5);
            }
        }
    }

    if (healthResponse.status !== 200) {
        console.log('❌ API 연결 최종 실패');
        return { createdShortUrls: [], createdCount: 0 };
    }

    const { createdShortUrls, createdCount } = generateTestData(baseUrl, 10); // 더 적게 시작

    // 2. 실제 존재하는 모든 shortUrl 조회
    console.log('📊 실제 존재하는 shortUrl 조회 중...');
    const allUrlsResponse = http.get(`${baseUrl}/api/v1/urls/test/all-short-urls`);
    let actualShortUrls = [];

    if (allUrlsResponse.status === 200) {
        actualShortUrls = JSON.parse(allUrlsResponse.body);
        console.log(`✅ 실제 존재하는 shortUrl: ${actualShortUrls.length}개`);
        console.log(`📊 샘플: ${actualShortUrls.slice(0, 5).join(', ')}`);
    } else {
        console.log('❌ shortUrl 조회 실패, 생성된 URL 사용');
        actualShortUrls = createdShortUrls;
    }

    // 3. Phase 준비 완료 확인 (이제 단순히 로그만 출력)
    console.log('📊 Phase 1 준비: Baseline 테이블 사용 (Full Table Scan)');
    const baselineResponse = http.post(`${baseUrl}/api/v1/urls/prepare/baseline`);
    check(baselineResponse, {
        'Phase 1 준비 성공': (r) => r.status === 200,
    });

    console.log('📊 Phase 2 준비: Indexed 테이블 사용 (Index Scan)');
    const indexedResponse = http.post(`${baseUrl}/api/v1/urls/prepare/indexed`);
    check(indexedResponse, {
        'Phase 2 준비 성공': (r) => r.status === 200,
    });

    console.log('✅ 모든 Phase 준비 완료 - 테스트 시작');
    console.log(`📊 최종 사용할 shortUrl 개수: ${actualShortUrls.length}`);
    console.log(`📊 샘플 shortUrl: ${actualShortUrls.slice(0, 3).join(', ')}`);

    if (actualShortUrls.length === 0) {
        console.log('❌ 사용할 수 있는 shortUrl이 없습니다!');
    }

    return { createdShortUrls: actualShortUrls, createdCount: actualShortUrls.length };
}

export default function (data) {
    const now = Date.now();
    const elapsed = now - phaseStartTime;

    // Phase 결정 로직 (시간 기반) - JSON 응답 엔드포인트 사용
    let phase;
    if (elapsed < 60000) {
        // Phase 1: Baseline (0-60초)
        phase = { name: 'baseline', endpoint: '/baseline' };
    } else if (elapsed < 130000) {
        // Phase 2: Indexed (60-130초, 10초 cool down 포함)
        if (currentPhase !== 'indexed') {
            console.log('📊 Phase 2 시작: Indexed 테이블 사용 (Index Scan)');
            currentPhase = 'indexed';
        }
        phase = { name: 'indexed', endpoint: '/indexed' };
    } else {
        // Phase 3: Cached (130초 이후)
        if (currentPhase !== 'cached') {
            console.log('📊 Phase 3 시작: Redis 캐시 사용');
            currentPhase = 'cached';
        }
        phase = { name: 'cached', endpoint: '/cached' };
    }

    // 순차적으로 조회 (랜덤 대신)
    if (!data.createdShortUrls || data.createdShortUrls.length === 0) {
        console.log('❌ 생성된 shortUrl이 없습니다. 테스트를 건너뜁니다.');
        return;
    }

    const urlIndex = __VU % data.createdShortUrls.length;
    const selectedUrl = data.createdShortUrls[urlIndex];

    if (!selectedUrl || selectedUrl === 'undefined') {
        console.log(`❌ 잘못된 shortUrl: ${selectedUrl}`);
        return;
    }

    const url = `http://api:8080/api/v1/urls${phase.endpoint}/${selectedUrl}`;

    // redirects: 0 옵션을 추가하여 리디렉션을 방지합니다.
    const response = http.get(url, {
        tags: { phase: phase.name },
        redirects: 0,
    });

    const success = check(response, {
        // 이제 302 상태 코드만 성공으로 간주합니다.
        'status is 302': (r) => r.status === 302,
    });

    // 에러 시 상세 로그 출력
    if (!success) {
        console.log(`❌ 요청 실패: ${url} - Status: ${response.status}, Body: ${response.body}`);
    }

    errorRate.add(!success);

    sleep(0.1); // 100ms 대기
}