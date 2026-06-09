package com.app.bideo.service.prediction;

import com.app.bideo.dto.prediction.AuctionPredictionRequestDTO;
import com.app.bideo.dto.prediction.AuctionPredictionResponseDTO;
import com.app.bideo.dto.prediction.CurationItemDTO;
import com.app.bideo.dto.prediction.CurationResponseDTO;
import com.app.bideo.service.work.WorkService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * [모델 분류] 분류 (Classification) 서비스 — 경매 낙찰 예측.
 * cf. FollowerGrowthService 는 회귀 — 작가 팔로워 성장 예측.
 */
@Service
@RequiredArgsConstructor
public class PredictionService {

    private final PredictionApiClient predictionApiClient;
    private final WorkService workService;

    public AuctionPredictionResponseDTO predict(AuctionPredictionRequestDTO request) {
        return predictionApiClient.predict(request);
    }

    public CurationResponseDTO getCuration(int k) {
        CurationResponseDTO response = predictionApiClient.getCuration(k);
        enrichWithThumbnails(response);
        return response;
    }

    /**
     * FastAPI 큐레이션 응답에는 작품 이미지가 없어 화면이 더미(picsum)를 띄웠다.
     * workId 로 실제 작품 썸네일(S3 presigned URL)을 조회해 채워 상세 화면과 일치시킨다.
     */
    private void enrichWithThumbnails(CurationResponseDTO response) {
        if (response == null || response.getItems() == null || response.getItems().isEmpty()) {
            return;
        }
        List<Long> workIds = response.getItems().stream()
                .map(CurationItemDTO::getWorkId)
                .filter(Objects::nonNull)
                .toList();
        Map<Long, String> thumbnails = workService.getThumbnailUrls(workIds);
        response.getItems().forEach(item ->
                item.setThumbnailUrl(thumbnails.get(item.getWorkId())));
    }
}
