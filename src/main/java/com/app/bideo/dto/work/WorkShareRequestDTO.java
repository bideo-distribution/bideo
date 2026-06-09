package com.app.bideo.dto.work;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkShareRequestDTO {
    private List<Long> receiverIds;
    private String message;
    private String shareUrl;
}
