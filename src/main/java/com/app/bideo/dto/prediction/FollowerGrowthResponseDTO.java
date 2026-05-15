package com.app.bideo.dto.prediction;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * [모델 분류] 회귀 (Regression) DTO — 작가 팔로워 성장 예측 응답.
 * cf. AuctionPredictionResponseDTO 는 분류 응답.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FollowerGrowthResponseDTO {

    /** 추론된 등급 (NEWCOMER/RISING/ESTABLISHED). */
    private String grade;

    @JsonProperty("current_week")
    private Integer currentWeek;

    @JsonProperty("target_week")
    private Integer targetWeek;

    @JsonProperty("weeks_ahead")
    private Integer weeksAhead;

    @JsonProperty("current_followers")
    private Integer currentFollowers;

    /** target_week 의 예상 팔로워 수. */
    private Integer predicted;

    /** 95% 신뢰구간 하한. */
    @JsonProperty("ci_low")
    private Integer ciLow;

    /** 95% 신뢰구간 상한. */
    @JsonProperty("ci_high")
    private Integer ciHigh;

    /** 증가 예상치 (predicted - currentFollowers). */
    private Integer growth;

    /** 증가율 (0.0~). null 가능 (currentFollowers=0). */
    @JsonProperty("growth_rate")
    private Double growthRate;

    /** 학습 잔차 표준편차. */
    @JsonProperty("resid_std")
    private Double residStd;
}
