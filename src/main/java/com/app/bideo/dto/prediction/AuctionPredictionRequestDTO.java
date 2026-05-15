package com.app.bideo.dto.prediction;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * [모델 분류] 분류 (Classification) DTO — 경매 낙찰 여부 예측 입력.
 * cf. FollowerGrowthRequestDTO 는 회귀 — 작가 팔로워 성장 예측.
 *
 * FastAPI 단일 예측 입력 — Pydantic AuctionInput 과 1:1 매핑.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuctionPredictionRequestDTO {

    @JsonProperty("starting_price")
    private Integer startingPrice;

    @JsonProperty("duration_hours")
    private Double durationHours;

    @JsonProperty("view_count")
    private Integer viewCount;

    @JsonProperty("like_count")
    private Integer likeCount;

    @JsonProperty("bookmark_count")
    private Integer bookmarkCount;

    @JsonProperty("creator_followers")
    private Integer creatorFollowers;

    @JsonProperty("creator_verified")
    private Integer creatorVerified;

    @JsonProperty("work_category")
    private String workCategory;

    @JsonProperty("started_hour")
    private Integer startedHour;

    @JsonProperty("started_dow")
    private Integer startedDow;
}
