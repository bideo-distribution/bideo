package com.app.bideo.service.prediction;

import com.app.bideo.dto.prediction.FollowerGrowthRequestDTO;
import com.app.bideo.dto.prediction.FollowerGrowthResponseDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * [모델 분류] 회귀 (Regression) 호출 클라이언트 — 작가 팔로워 성장 예측.
 * cf. PredictionApiClient 는 분류 — 경매 낙찰 예측.
 *
 * FastAPI 의 /api/growth/* 엔드포인트 호출.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FollowerGrowthApiClient {

    @Qualifier("mlApiRestTemplate")
    private final RestTemplate restTemplate;

    @Value("${ml.api.base-url}")
    private String baseUrl;

    /** 단일 시점 예측 (weeks_ahead 주 후). */
    public FollowerGrowthResponseDTO forecast(FollowerGrowthRequestDTO request) {
        String url = baseUrl + "/api/growth/forecast";
        try {
            return restTemplate.postForObject(url, request, FollowerGrowthResponseDTO.class);
        } catch (RestClientException e) {
            log.error("[ML/Growth] forecast 호출 실패: {}", e.getMessage());
            throw e;
        }
    }
}
