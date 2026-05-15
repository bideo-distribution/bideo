package com.app.bideo.dto.prediction;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class CurationResponseDTO {

    private Double threshold;

    @JsonProperty("total_active")
    private Integer totalActive;

    private Integer returned;

    private List<CurationItemDTO> items;
}
