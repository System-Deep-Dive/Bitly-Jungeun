package com.example.bitly.service;

import com.example.bitly.entity.UrlBaseline;
import com.example.bitly.entity.UrlIndexed;
import com.example.bitly.repository.UrlBaselineRepository;
import com.example.bitly.repository.UrlIndexedRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class UrlService {

    private final UrlBaselineRepository urlBaselineRepository;
    private final UrlIndexedRepository urlIndexedRepository;
    private final CacheService cacheService;
    private static final String BASE62_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final long ID_OFFSET = 1000000000L;
    private static final AtomicLong COUNTER = new AtomicLong(System.currentTimeMillis());

    @Transactional
    public String createShortUrl(String originalUrl) {
        // 이미 등록된 URL인지 확인 (Baseline 테이블에서 체크)
        return urlBaselineRepository.findByOriginalUrl(originalUrl)
                .map(UrlBaseline::getShortUrl)
                .orElseGet(() -> {
                    // 새로운 shortUrl 생성
                    String shortUrl = generateUniqueShortUrl();

                    // 1. Baseline 테이블에 저장 (인덱스 없음)
                    UrlBaseline baselineUrl = UrlBaseline.builder()
                            .shortUrl(shortUrl)
                            .originalUrl(originalUrl)
                            .build();
                    urlBaselineRepository.save(baselineUrl);

                    // 2. Indexed 테이블에 저장 (인덱스 있음)
                    UrlIndexed indexedUrl = UrlIndexed.builder()
                            .shortUrl(shortUrl)
                            .originalUrl(originalUrl)
                            .build();
                    urlIndexedRepository.save(indexedUrl);

                    log.info("URL 생성 완료: {} -> {} (2개 테이블에 저장)", shortUrl, originalUrl);
                    return shortUrl;
                });
    }

    // 고유한 shortUrl 생성 (메인 테이블 없이)
    private String generateUniqueShortUrl() {
        long id = COUNTER.incrementAndGet();
        return encode(id);
    }

    // Phase별 준비 메서드들 (이제 단순히 로그만 출력)
    @Transactional
    public void prepareBaselinePhase() {
        // Phase 1: Baseline 테이블 준비 완료 (이미 데이터 있음)
        log.info("Phase 1 준비 완료: Baseline 테이블 사용 (Full Table Scan) - {} 개 데이터", urlBaselineRepository.count());
    }

    @Transactional
    public void prepareIndexedPhase() {
        // Phase 2: Indexed 테이블 준비 완료 (이미 데이터 있음)
        log.info("Phase 2 준비 완료: Indexed 테이블 사용 (Index Scan) - {} 개 데이터", urlIndexedRepository.count());
    }

    // Phase 1: Baseline - 인덱스 없는 테이블에서 Full Table Scan
    @Transactional(readOnly = true)
    public String getOriginalUrlBaseline(String shortUrl) {
        return urlBaselineRepository.findByShortUrl(shortUrl)
                .map(UrlBaseline::getOriginalUrl)
                .orElseThrow(() -> new EntityNotFoundException("URL not found for short URL: " + shortUrl));
    }

    // Phase 2: Indexed - 인덱스 있는 테이블에서 Index Scan
    @Transactional(readOnly = true)
    public String getOriginalUrlIndexed(String shortUrl) {
        return urlIndexedRepository.findByShortUrl(shortUrl)
                .map(UrlIndexed::getOriginalUrl)
                .orElseThrow(() -> new EntityNotFoundException("URL not found for short URL: " + shortUrl));
    }

    // Phase 3: Cached - 캐시 우선 조회 (Redis 캐시 구현)
    @Transactional(readOnly = true)
    public String getOriginalUrlCached(String shortUrl) {
        // 1. 캐시에서 먼저 조회
        Optional<String> cachedUrl = cacheService.get(shortUrl);
        if (cachedUrl.isPresent()) {
            log.debug("Cache HIT for shortUrl: {}", shortUrl);
            return cachedUrl.get();
        }

        // 2. 캐시 미스 시 DB에서 조회
        log.debug("Cache MISS for shortUrl: {}, querying database", shortUrl);
        String originalUrl = urlBaselineRepository.findByShortUrl(shortUrl)
                .map(UrlBaseline::getOriginalUrl)
                .orElseThrow(() -> new EntityNotFoundException("URL not found for short URL: " + shortUrl));

        // 3. DB에서 조회한 결과를 캐시에 저장
        cacheService.put(shortUrl, originalUrl);
        log.debug("Cached result for shortUrl: {}", shortUrl);

        return originalUrl;
    }

    private String encode(long id) {
        long targetId = id + ID_OFFSET;
        StringBuilder sb = new StringBuilder();
        while (targetId > 0) {
            sb.append(BASE62_CHARS.charAt((int) (targetId % 62)));
            targetId /= 62;
        }
        return sb.reverse().toString();
    }
}
