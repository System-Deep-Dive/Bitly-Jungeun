package com.example.bitly.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class CacheService {

    private final RedisTemplate<String, String> redisTemplate;

    private static final String CACHE_PREFIX = "url:";
    private static final Duration DEFAULT_TTL = Duration.ofMinutes(15); // 15분

    /**
     * 캐시에서 URL 조회
     */
    public Optional<String> get(String shortUrl) {
        try {
            String key = CACHE_PREFIX + shortUrl;
            String originalUrl = redisTemplate.opsForValue().get(key);

            if (originalUrl != null) {
                log.debug("Cache HIT for key: {}", key);
                return Optional.of(originalUrl);
            } else {
                log.debug("Cache MISS for key: {}", key);
                return Optional.empty();
            }
        } catch (Exception e) {
            log.error("Redis cache error for key: {}", shortUrl, e);
            return Optional.empty();
        }
    }

    /**
     * 캐시에 URL 저장
     */
    public void put(String shortUrl, String originalUrl) {
        try {
            String key = CACHE_PREFIX + shortUrl;

            redisTemplate.opsForValue().set(key, originalUrl, DEFAULT_TTL);
            log.debug("Cached URL: {} -> {} (TTL: {})", shortUrl, originalUrl, DEFAULT_TTL);
        } catch (Exception e) {
            log.error("Redis cache put error for key: {}", shortUrl, e);
        }
    }

    /**
     * 캐시에서 URL 삭제
     */
    public void evict(String shortUrl) {
        try {
            String key = CACHE_PREFIX + shortUrl;
            redisTemplate.delete(key);
            log.debug("Evicted cache for key: {}", key);
        } catch (Exception e) {
            log.error("Redis cache evict error for key: {}", shortUrl, e);
        }
    }

    /**
     * 캐시 통계 조회
     */
    public CacheStats getStats() {
        try {
            // Redis INFO 명령으로 통계 조회 (간단한 구현)
            // 실제 운영에서는 Redis INFO 명령을 직접 사용하거나 RedisTemplate의 info() 메서드 사용
            return new CacheStats(0, 0, 0.0); // 임시 구현
        } catch (Exception e) {
            log.error("Error getting cache stats", e);
            return new CacheStats(0, 0, 0.0);
        }
    }

    /**
     * 캐시 통계 DTO
     */
    public record CacheStats(long hits, long misses, double hitRate) {
        @Override
        public String toString() {
            return String.format("CacheStats{hits=%d, misses=%d, hitRate=%.2f%%}", hits, misses, hitRate);
        }
    }
}
