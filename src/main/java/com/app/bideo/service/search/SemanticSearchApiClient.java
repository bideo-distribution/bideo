package com.app.bideo.service.search;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * FastAPI 의 시맨틱 검색 (`/api/search/works`) 호출 클라이언트.
 *
 * 검색 호출:
 *   GET /api/search/works?q=...&k=20 → [{work_id, score}]
 *
 * 인덱스 단건 추가 (작품 등록 직후 — refresh 없이 즉시 검색 반영):
 *   POST /api/search/index/add  { work_id, embedding[] }
 *
 * 실패 시 빈 리스트 — 호출 측이 단어 LIKE fallback 으로 전환.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SemanticSearchApiClient {

    @Qualifier("mlApiRestTemplate")
    private final RestTemplate restTemplate;

    @Value("${ml.api.base-url}")
    private String baseUrl;

    public List<SemanticHit> searchWorks(String query, int k) {
        if (query == null || query.isBlank()) {
            return Collections.emptyList();
        }
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/api/search/works")
                .queryParam("q", query)
                .queryParam("k", k)
                .toUriString();
        try {
            SemanticSearchResponse resp = restTemplate.getForObject(url, SemanticSearchResponse.class);
            return resp == null || resp.getItems() == null ? Collections.emptyList() : resp.getItems();
        } catch (RestClientException e) {
            log.warn("[SemanticSearch] /api/search/works 호출 실패: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /** 작품 등록 직후 인덱스에 단건 추가 — 실패해도 등록 흐름은 계속. */
    public void addToIndex(Long workId, double[] embedding) {
        if (workId == null || embedding == null || embedding.length == 0) {
            return;
        }
        try {
            restTemplate.postForObject(
                    baseUrl + "/api/search/index/add",
                    Map.of("work_id", workId, "embedding", embedding),
                    Map.class);
        } catch (RestClientException e) {
            log.warn("[SemanticSearch] index/add 실패 (다음 refresh 까지 검색에 안 잡힘): {}", e.getMessage());
        }
    }

    @Data
    public static class SemanticHit {
        private Long work_id;
        private Double score;
    }

    @Data
    public static class SemanticSearchResponse {
        private String query;
        private Integer returned;
        private List<SemanticHit> items;
    }
}
