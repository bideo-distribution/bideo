package com.app.bideo.controller.prediction;

import com.app.bideo.auth.member.CustomUserDetails;
import com.app.bideo.dto.prediction.FollowerGrowthRequestDTO;
import com.app.bideo.dto.prediction.FollowerGrowthResponseDTO;
import com.app.bideo.service.prediction.FollowerGrowthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * [모델 분류] 회귀 (Regression) 컨트롤러 — 작가 팔로워 성장 예측.
 * cf. PredictionController 는 분류 — 경매 낙찰 예측.
 *
 * - GET  /api/growth/me              → 본인 12주 후 팔로워 예측 (마이페이지)
 * - POST /api/growth/forecast        → 임의 입력 예측 (테스트)
 */
@RestController
@RequestMapping("/api/growth")
@RequiredArgsConstructor
public class FollowerGrowthController {

    private final FollowerGrowthService followerGrowthService;

    /** 마이페이지 — 로그인한 본인의 팔로워 성장 예측. */
    @GetMapping("/me")
    public ResponseEntity<FollowerGrowthResponseDTO> myForecast(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        if (userDetails == null || userDetails.getId() == null) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(followerGrowthService.forecastForMember(userDetails.getId()));
    }

    /** 임의 입력 — 테스트/관리자용. */
    @PostMapping("/forecast")
    public ResponseEntity<FollowerGrowthResponseDTO> forecast(
            @RequestBody FollowerGrowthRequestDTO request
    ) {
        return ResponseEntity.ok(followerGrowthService.forecast(request));
    }
}
