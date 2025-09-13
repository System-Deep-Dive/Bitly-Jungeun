package com.example.bitly.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "url_mapping_baseline")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UrlBaseline {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "short_url", nullable = false, unique = true)
    // 인덱스 없음 - Full Table Scan용
    private String shortUrl;

    @Column(name = "original_url", nullable = false, length = 2000)
    private String originalUrl;
}
