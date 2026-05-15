package com.app.bideo.dto.prediction;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * [모델 분류] 회귀 (Regression) DTO — 작가 팔로워 성장 예측 입력.
 * cf. AuctionPredictionRequestDTO 는 분류 — 경매 낙찰 여부 예측.
 *
 * FastAPI Pydantic FollowerGrowthRequest 와 1:1 매핑.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FollowerGrowthRequestDTO {

    /** 가입 후 경과 주(週) 수. */
    @JsonProperty("current_week_offset")
    private Integer currentWeekOffset;

    /** 현재 팔로워 수. */
    @JsonProperty("current_followers")
    private Integer currentFollowers;

    /** 몇 주 후를 예측할지 (1~52). */
    @JsonProperty("weeks_ahead")
    private Integer weeksAhead;
}
