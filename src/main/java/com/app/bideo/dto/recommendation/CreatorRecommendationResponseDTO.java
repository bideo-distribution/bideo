package com.app.bideo.dto.recommendation;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class CreatorRecommendationResponseDTO {

    @JsonProperty("user_id")
    private Long userId;

    @JsonProperty("cold_start")
    private Boolean coldStart;

    private Integer returned;

    private List<CreatorRecommendationItemDTO> items;
}
