package com.app.bideo.service.prediction;

import com.app.bideo.dto.prediction.AuctionPredictionRequestDTO;
import com.app.bideo.dto.prediction.AuctionPredictionResponseDTO;
import com.app.bideo.dto.prediction.CurationResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * [모델 분류] 분류 (Classification) 서비스 — 경매 낙찰 예측.
 * cf. FollowerGrowthService 는 회귀 — 작가 팔로워 성장 예측.
 */
@Service
@RequiredArgsConstructor
public class PredictionService {

    private final PredictionApiClient predictionApiClient;

    public AuctionPredictionResponseDTO predict(AuctionPredictionRequestDTO request) {
        return predictionApiClient.predict(request);
    }

    public CurationResponseDTO getCuration(int k) {
        return predictionApiClient.getCuration(k);
    }
}
