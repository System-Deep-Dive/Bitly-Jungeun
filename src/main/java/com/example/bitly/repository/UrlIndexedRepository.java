package com.example.bitly.repository;

import com.example.bitly.entity.UrlIndexed;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UrlIndexedRepository extends JpaRepository<UrlIndexed, Long> {

    Optional<UrlIndexed> findByShortUrl(String shortUrl);
}
