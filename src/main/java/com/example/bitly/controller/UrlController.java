package com.example.bitly.controller;

import com.example.bitly.controller.dto.UrlCreateRequest;
import com.example.bitly.service.CacheService;
import com.example.bitly.service.UrlService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "URL 단축 API", description = "URL을 단축하고, 단축된 URL을 원래 URL로 리디렉션하는 API입니다.")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/urls/")
public class UrlController {

    private final UrlService urlService;
    private final CacheService cacheService;

    @Operation(summary = "URL 단축 생성", description = "원본 URL을 받아 단축된 URL을 생성합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "단축 URL 생성 성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 URL 형식")
    })
    @PostMapping()
    public ResponseEntity<String> createShortUrl(@RequestBody UrlCreateRequest request) {
        String shortUrl = urlService.createShortUrl(request.getOriginalUrl());
        // JSON 문자열로 명확하게 반환
        return ResponseEntity.status(HttpStatus.CREATED)
                .header("Content-Type", "application/json")
                .body("\"" + shortUrl + "\"");
    }

    @Operation(summary = "원본 URL로 리디렉션", description = "단축 URL을 통해 원본 URL로 리디렉션합니다. (Phase 3: 캐시 우선)")
    @ApiResponses({
            @ApiResponse(responseCode = "302", description = "리디렉션 성공"),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 단축 URL")
    })
    @GetMapping("/{shortUrl}")
    public void redirect(
            @Parameter(description = "단축 URL", required = true) @PathVariable String shortUrl,
            HttpServletResponse response) {
        // 기본 API는 Phase 3 (캐시 우선) 사용
        String originalUrl = urlService.getOriginalUrlCached(shortUrl);
        try {
            response.sendRedirect(originalUrl);
        } catch (IOException e) {
            throw new RuntimeException(e); // Checked → Unchecked 변환
        }
    }

    // Phase별 성능 테스트용 API들
    @GetMapping("/baseline/{shortUrl}")
    public void redirectBaseline(
            @Parameter(description = "단축 URL", required = true) @PathVariable String shortUrl,
            HttpServletResponse response) {
        // Phase 1: DB 직접 조회 (인덱스 없이)
        String originalUrl = urlService.getOriginalUrlBaseline(shortUrl);
        try {
            response.sendRedirect(originalUrl);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @GetMapping("/indexed/{shortUrl}")
    public void redirectIndexed(
            @Parameter(description = "단축 URL", required = true) @PathVariable String shortUrl,
            HttpServletResponse response) {
        // Phase 2: 인덱스 활용 조회
        String originalUrl = urlService.getOriginalUrlIndexed(shortUrl);
        try {
            response.sendRedirect(originalUrl);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @GetMapping("/cached/{shortUrl}")
    public void redirectCached(
            @Parameter(description = "단축 URL", required = true) @PathVariable String shortUrl,
            HttpServletResponse response) {
        // Phase 3: 캐시 우선 조회
        String originalUrl = urlService.getOriginalUrlCached(shortUrl);
        try {
            response.sendRedirect(originalUrl);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    // 캐시 통계 조회 API
    @GetMapping("/stats")
    public ResponseEntity<CacheService.CacheStats> getCacheStats() {
        CacheService.CacheStats stats = cacheService.getStats();
        return ResponseEntity.ok(stats);
    }

    // Phase 준비 API들
    @PostMapping("/prepare/baseline")
    public ResponseEntity<String> prepareBaselinePhase() {
        urlService.prepareBaselinePhase();
        return ResponseEntity.ok("Phase 1 준비 완료: 인덱스 삭제 (Full Table Scan)");
    }

    @PostMapping("/prepare/indexed")
    public ResponseEntity<String> prepareIndexedPhase() {
        urlService.prepareIndexedPhase();
        return ResponseEntity.ok("Phase 2 준비 완료: 인덱스 생성 (Index Scan)");
    }
}
