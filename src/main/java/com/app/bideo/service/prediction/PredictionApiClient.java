package com.app.bideo.service.prediction;

import com.app.bideo.dto.prediction.AuctionPredictionRequestDTO;
import com.app.bideo.dto.prediction.AuctionPredictionResponseDTO;
import com.app.bideo.dto.prediction.CurationResponseDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * [모델 분류] 분류 (Classification) 호출 클라이언트 — 경매 낙찰 예측.
 * cf. FollowerGrowthApiClient 는 회귀 — 작가 팔로워 성장 예측.
 *
 * FastAPI 의 ML 추론 엔드포인트 호출 클라이언트.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PredictionApiClient {

    @Qualifier("mlApiRestTemplate")
    private final RestTemplate restTemplate;

    @Value("${ml.api.base-url}")
    private String baseUrl;

    /** 단일 경매 예측. */
    public AuctionPredictionResponseDTO predict(AuctionPredictionRequestDTO request) {
        String url = baseUrl + "/api/predictions/predict";
        try {
            return restTemplate.postForObject(url, request, AuctionPredictionResponseDTO.class);
        } catch (RestClientException e) {
            log.error("[ML] predict 호출 실패: {}", e.getMessage());
            throw e;
        }
    }

    /** 메인 큐레이션 — 진행 중 경매 Top-K. scan_limit 100 으로 제한해 추론 부하 줄임. */
    public CurationResponseDTO getCuration(int k) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/api/predictions/curation")
                .queryParam("k", k)
                .queryParam("scan_limit", 100)
                .toUriString();
        try {
            return restTemplate.getForObject(url, CurationResponseDTO.class);
        } catch (RestClientException e) {
            log.error("[ML] curation 호출 실패: {}", e.getMessage());
            throw e;
        }
    }
}
