package com.example.bitly.repository;

import com.example.bitly.entity.UrlBaseline;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UrlBaselineRepository extends JpaRepository<UrlBaseline, Long> {

    Optional<UrlBaseline> findByShortUrl(String shortUrl);

    Optional<UrlBaseline> findByOriginalUrl(String originalUrl);
}
