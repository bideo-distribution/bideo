package com.app.bideo.service.recommendation;

import com.app.bideo.dto.recommendation.CreatorRecommendationResponseDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * [모델 분류] 추천 (Recommendation) 호출 클라이언트 — 작가 추천 (Item-CF).
 * cf. PredictionApiClient(분류) / FollowerGrowthApiClient(회귀).
 *
 * FastAPI 의 /api/recommend/* 엔드포인트 호출.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RecommendationApiClient {

    @Qualifier("mlApiRestTemplate")
    private final RestTemplate restTemplate;

    @Value("${ml.api.base-url}")
    private String baseUrl;

    /** 사용자 추천 — follow 한 작가 기반 Item-CF, cold start 시 인기 작가 fallback. */
    public CreatorRecommendationResponseDTO forUser(Long userId, int k, double growthWeight) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/api/recommend/creators/me")
                .queryParam("user_id", userId)
                .queryParam("k", k)
                .queryParam("growth_weight", growthWeight)
                .toUriString();
        try {
            return restTemplate.getForObject(url, CreatorRecommendationResponseDTO.class);
        } catch (RestClientException e) {
            log.error("[ML/Reco] forUser 호출 실패: {}", e.getMessage());
            throw e;
        }
    }

    /** 비슷한 작가 — 작가 상세의 '비슷한 작가' 영역. */
    public CreatorRecommendationResponseDTO similar(Long creatorId, int k) {
        String url = UriComponentsBuilder.fromHttpUrl(
                        baseUrl + "/api/recommend/creators/similar/" + creatorId)
                .queryParam("k", k)
                .toUriString();
        try {
            return restTemplate.getForObject(url, CreatorRecommendationResponseDTO.class);
        } catch (RestClientException e) {
            log.error("[ML/Reco] similar 호출 실패: {}", e.getMessage());
            throw e;
        }
    }
}
