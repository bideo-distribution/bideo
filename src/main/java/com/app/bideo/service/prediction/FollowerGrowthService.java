package com.app.bideo.service.prediction;

import com.app.bideo.auth.member.CustomUserDetails;
import com.app.bideo.domain.member.MemberVO;
import com.app.bideo.dto.prediction.FollowerGrowthRequestDTO;
import com.app.bideo.dto.prediction.FollowerGrowthResponseDTO;
import com.app.bideo.repository.member.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * [모델 분류] 회귀 (Regression) 서비스 — 작가 팔로워 성장 예측.
 * cf. PredictionService 는 분류 — 경매 낙찰 예측.
 *
 * 로그인 사용자의 가입 경과 주(週) + 현재 팔로워 수로 FastAPI 호출.
 */
@Service
@RequiredArgsConstructor
public class FollowerGrowthService {

    private static final int DEFAULT_WEEKS_AHEAD = 12;

    private final FollowerGrowthApiClient apiClient;
    private final MemberRepository memberRepository;

    /** 마이페이지용 — 본인 12주 후 팔로워 예측. */
    public FollowerGrowthResponseDTO forecastForMember(Long memberId) {
        MemberVO member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("member not found: " + memberId));

        int currentWeek = weeksSinceJoined(member.getCreatedDatetime());
        int followers   = member.getFollowerCount() == null ? 0 : member.getFollowerCount();

        FollowerGrowthRequestDTO req = FollowerGrowthRequestDTO.builder()
                .currentWeekOffset(currentWeek)
                .currentFollowers(followers)
                .weeksAhead(DEFAULT_WEEKS_AHEAD)
                .build();
        return apiClient.forecast(req);
    }

    /** 임의 입력 예측 — 테스트용. */
    public FollowerGrowthResponseDTO forecast(FollowerGrowthRequestDTO request) {
        return apiClient.forecast(request);
    }

    private int weeksSinceJoined(LocalDateTime joinedAt) {
        if (joinedAt == null) return 0;
        long days = ChronoUnit.DAYS.between(joinedAt.toLocalDate(),
                                            LocalDateTime.now().toLocalDate());
        return (int) Math.max(days / 7L, 0L);
    }
}
