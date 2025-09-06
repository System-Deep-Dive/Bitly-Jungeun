package com.example.bitly.repository;

import com.example.bitly.entity.Url;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UrlRepository extends JpaRepository<Url, Long> {

    Optional<Url> findByOriginalUrl(String originalUrl);

    Optional<Url> findByShortUrl(String shortUrl);
}