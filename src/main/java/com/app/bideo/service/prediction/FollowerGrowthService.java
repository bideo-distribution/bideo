package com.app.bideo.service.prediction;

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
 * 실데이터 모델은 사용자 활동 피처를 보므로 호출 전에 DB 에서 수집해 채워준다.
 *  - 첫 팔로워 시점 → current_week_offset
 *  - 작품 수       → n_works
 *  - 최근 4주 증감 → recent_velocity_4w
 *  - 인증 여부     → creator_verified
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

        int followers = member.getFollowerCount() == null ? 0 : member.getFollowerCount();

        // 첫 팔로워가 없으면 학습 분포 밖 → 데뷔 전 작가로 보고 모든 활동 피처 0
        LocalDateTime debutAt = memberRepository.findFirstFollowAt(memberId).orElse(null);
        int currentWeekOffset = debutAt == null ? 0 : weeksBetween(debutAt, LocalDateTime.now());

        int nWorks    = memberRepository.countActiveWorksByMemberId(memberId);
        int verified  = Boolean.TRUE.equals(member.getCreatorVerified()) ? 1 : 0;
        int velocity  = followers - memberRepository.countFollowersBefore(
                memberId, LocalDateTime.now().minusWeeks(4));

        FollowerGrowthRequestDTO req = FollowerGrowthRequestDTO.builder()
                .currentWeekOffset(currentWeekOffset)
                .currentFollowers(followers)
                .weeksAhead(DEFAULT_WEEKS_AHEAD)
                .nWorks(nWorks)
                .recentVelocity4w(velocity)
                .creatorVerified(verified)
                .build();
        return apiClient.forecast(req);
    }

    /** 임의 입력 예측 — 테스트용. 활동 피처를 받지 못한 경우 0 으로 채움. */
    public FollowerGrowthResponseDTO forecast(FollowerGrowthRequestDTO request) {
        FollowerGrowthRequestDTO filled = FollowerGrowthRequestDTO.builder()
                .currentWeekOffset(request.getCurrentWeekOffset())
                .currentFollowers(request.getCurrentFollowers())
                .weeksAhead(request.getWeeksAhead())
                .nWorks(request.getNWorks() == null ? 0 : request.getNWorks())
                .recentVelocity4w(request.getRecentVelocity4w() == null ? 0 : request.getRecentVelocity4w())
                .creatorVerified(request.getCreatorVerified() == null ? 0 : request.getCreatorVerified())
                .build();
        return apiClient.forecast(filled);
    }

    private int weeksBetween(LocalDateTime from, LocalDateTime to) {
        long days = ChronoUnit.DAYS.between(from.toLocalDate(), to.toLocalDate());
        return (int) Math.max(days / 7L, 0L);
    }
}
