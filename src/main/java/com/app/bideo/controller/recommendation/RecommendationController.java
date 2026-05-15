package com.app.bideo.controller.recommendation;

import com.app.bideo.auth.member.CustomUserDetails;
import com.app.bideo.dto.recommendation.CreatorRecommendationResponseDTO;
import com.app.bideo.service.recommendation.RecommendationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * [모델 분류] 추천 (Recommendation) 컨트롤러 — 작가 추천 (Item-CF).
 *
 * - GET /api/recommend/creators/me                    → 로그인 사용자 맞춤 추천
 * - GET /api/recommend/creators/similar/{creatorId}   → 비슷한 작가
 */
@RestController
@RequestMapping("/api/recommend")
@RequiredArgsConstructor
public class RecommendationController {

    private final RecommendationService recommendationService;

    /** 로그인 사용자 맞춤 — follow 패턴 기반 CF, cold start 는 인기 작가. */
    @GetMapping("/creators/me")
    public ResponseEntity<CreatorRecommendationResponseDTO> forMe(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(defaultValue = "10") int k,
            @RequestParam(name = "growth_weight", defaultValue = "0.0") double growthWeight
    ) {
        if (userDetails == null || userDetails.getId() == null) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(
                recommendationService.forUser(userDetails.getId(), k, growthWeight)
        );
    }

    /** 비슷한 작가 — 작가 상세 페이지용. */
    @GetMapping("/creators/similar/{creatorId}")
    public ResponseEntity<CreatorRecommendationResponseDTO> similar(
            @PathVariable Long creatorId,
            @RequestParam(defaultValue = "10") int k
    ) {
        return ResponseEntity.ok(recommendationService.similar(creatorId, k));
    }
}
