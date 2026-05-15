package com.app.bideo.dto.prediction;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * [모델 분류] 분류 (Classification) DTO — 경매 낙찰 여부 예측 응답.
 * cf. FollowerGrowthResponseDTO 는 회귀 응답.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AuctionPredictionResponseDTO {

    private Double score;

    @JsonProperty("is_won_predicted")
    private Boolean isWonPredicted;

    private Double threshold;

    private String confidence;

    private String warning;
}
