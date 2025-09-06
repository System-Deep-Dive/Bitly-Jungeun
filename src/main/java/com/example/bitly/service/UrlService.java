package com.example.bitly.service;

import com.example.bitly.entity.Url;
import com.example.bitly.repository.UrlRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UrlService {

    private final UrlRepository urlRepository;
    private static final String BASE62_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final long ID_OFFSET = 1000000000L;

    @Transactional
    public String createShortUrl(String originalUrl) {
        // 이미 등록된 URL인지 확인
        return urlRepository.findByOriginalUrl(originalUrl)
            .map(Url::getShortUrl)
            .orElseGet(() -> {
                Url newUrl = new Url(originalUrl);
                urlRepository.save(newUrl);
                String shortUrl = encode(newUrl.getId());
                newUrl.setShortUrl(shortUrl);
                return shortUrl;
            });
    }

    @Transactional(readOnly = true)
    public String getOriginalUrl(String shortUrl) {
        return urlRepository.findByShortUrl(shortUrl)
            .map(Url::getOriginalUrl)
            .orElseThrow(() -> new EntityNotFoundException("URL not found for short URL: " + shortUrl));
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
