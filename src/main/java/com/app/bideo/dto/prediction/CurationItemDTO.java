package com.app.bideo.dto.prediction;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CurationItemDTO {

    @JsonProperty("auction_id")
    private Long auctionId;

    @JsonProperty("work_id")
    private Long workId;

    @JsonProperty("work_title")
    private String workTitle;

    @JsonProperty("work_category")
    private String workCategory;

    @JsonProperty("starting_price")
    private Integer startingPrice;

    private Double score;

    @JsonProperty("is_won_predicted")
    private Boolean isWonPredicted;

    /** 실제 작품 썸네일(S3 presigned URL). FastAPI 응답에는 없고 백엔드가 workId 로 채운다. */
    @JsonProperty("thumbnail_url")
    private String thumbnailUrl;
}
