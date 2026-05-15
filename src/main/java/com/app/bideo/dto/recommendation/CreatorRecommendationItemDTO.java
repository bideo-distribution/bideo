package com.app.bideo.dto.recommendation;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreatorRecommendationItemDTO {

    @JsonProperty("creator_id")
    private Long creatorId;

    private String nickname;

    @JsonProperty("profile_image")
    private String profileImage;

    private String bio;

    @JsonProperty("follower_count")
    private Integer followerCount;

    @JsonProperty("creator_tier")
    private String creatorTier;

    @JsonProperty("creator_verified")
    private Boolean creatorVerified;

    private Double score;

    /** CF / POPULAR / SIMILAR */
    private String reason;
}
