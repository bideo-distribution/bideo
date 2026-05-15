package com.app.bideo.controller.prediction;

import com.app.bideo.dto.prediction.AuctionPredictionRequestDTO;
import com.app.bideo.dto.prediction.AuctionPredictionResponseDTO;
import com.app.bideo.dto.prediction.CurationResponseDTO;
import com.app.bideo.service.prediction.PredictionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * [모델 분류] 분류 (Classification) 컨트롤러 — 경매 낙찰 예측.
 * cf. FollowerGrowthController 는 회귀 — 작가 팔로워 성장 예측.
 *
 * BIDEO 경매 예측 — FastAPI(ML 서버) 를 호출해 점수를 반환.
 *
 * - GET  /api/prediction/curation?k=10 → 진행 중 경매 Top-K
 * - POST /api/prediction/predict       → 단일 경매 예측
 */
@RestController
@RequestMapping("/api/prediction")
@RequiredArgsConstructor
public class PredictionController {

    private final PredictionService predictionService;

    @GetMapping("/curation")
    public ResponseEntity<CurationResponseDTO> getCuration(
            @RequestParam(defaultValue = "10") int k
    ) {
        return ResponseEntity.ok(predictionService.getCuration(k));
    }

    @PostMapping("/predict")
    public ResponseEntity<AuctionPredictionResponseDTO> predict(
            @RequestBody AuctionPredictionRequestDTO request
    ) {
        return ResponseEntity.ok(predictionService.predict(request));
    }
}
