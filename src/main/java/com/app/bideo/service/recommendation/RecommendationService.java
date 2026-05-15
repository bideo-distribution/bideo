package com.app.bideo.service.recommendation;

import com.app.bideo.dto.recommendation.CreatorRecommendationResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * [모델 분류] 추천 (Recommendation) 서비스 — 작가 추천.
 * cf. PredictionService(분류) / FollowerGrowthService(회귀).
 */
@Service
@RequiredArgsConstructor
public class RecommendationService {

    private final RecommendationApiClient apiClient;

    public CreatorRecommendationResponseDTO forUser(Long userId, int k, double growthWeight) {
        return apiClient.forUser(userId, k, growthWeight);
    }

    public CreatorRecommendationResponseDTO similar(Long creatorId, int k) {
        return apiClient.similar(creatorId, k);
    }
}
