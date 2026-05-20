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

    /** 첫 팔로워 이후 경과 주(週) 수. (실데이터 모델은 "데뷔" 기준) */
    @JsonProperty("current_week_offset")
    private Integer currentWeekOffset;

    /** 현재 팔로워 수. */
    @JsonProperty("current_followers")
    private Integer currentFollowers;

    /** 몇 주 후를 예측할지 (1~52). */
    @JsonProperty("weeks_ahead")
    private Integer weeksAhead;

    /** 업로드한 작품 수 (활동량 핵심 피처). */
    @JsonProperty("n_works")
    private Integer nWorks;

    /** 최근 4주간 팔로워 증감 (음수 가능). */
    @JsonProperty("recent_velocity_4w")
    private Integer recentVelocity4w;

    /** 인증 작가 여부 (0/1). */
    @JsonProperty("creator_verified")
    private Integer creatorVerified;
}
