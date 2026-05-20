package com.app.bideo.service.embedding;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * FastAPI 의 텍스트 임베딩 호출 클라이언트 — OpenAI text-embedding-3-small (1536d).
 *
 * 작품 등록 시 LLM 묘사 / 사용자 description 등을 임베딩해 DB 에 저장하고,
 * 검색 시점에 쿼리를 임베딩하는 두 자리에서 사용.
 *
 * 실패 시 Optional.empty() — 호출 측이 임베딩 없이 진행할지 결정.
 * (RestTemplate 동기 호출 — 작품 등록 트랜잭션 안에서 자연스럽게 흐름)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmbeddingApiClient {

    @Qualifier("mlApiRestTemplate")
    private final RestTemplate restTemplate;

    @Value("${ml.api.base-url}")
    private String baseUrl;

    public Optional<double[]> embed(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }

        try {
            EmbedTextResponse resp = restTemplate.postForObject(
                    baseUrl + "/api/embed/text",
                    Map.of("text", text),
                    EmbedTextResponse.class);

            if (resp == null || resp.getEmbedding() == null || resp.getEmbedding().isEmpty()) {
                return Optional.empty();
            }
            double[] arr = new double[resp.getEmbedding().size()];
            for (int i = 0; i < arr.length; i++) {
                arr[i] = resp.getEmbedding().get(i);
            }
            return Optional.of(arr);
        } catch (RestClientException e) {
            log.warn("[Embedding] /api/embed/text 호출 실패: {}", e.getMessage());
            return Optional.empty();
        }
    }

    @Data
    public static class EmbedTextResponse {
        private List<Double> embedding;
        private String model;
        @JsonProperty("dimensions")
        private Integer dimensions;
    }
}
