package com.example.bitly.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "url_mapping_indexed", indexes = @Index(name = "idx_short_url_indexed", columnList = "short_url"))
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UrlIndexed {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "short_url", nullable = false, unique = true)
    private String shortUrl;

    @Column(name = "original_url", nullable = false, length = 2000)
    private String originalUrl;
}
