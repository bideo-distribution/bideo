package com.app.bideo.service.notification;

import com.app.bideo.domain.notification.NotificationSettingVO;
import com.app.bideo.domain.notification.NotificationVO;
import com.app.bideo.repository.notification.NotificationDAO;
import com.app.bideo.repository.notification.NotificationSettingDAO;
import com.app.bideo.service.common.S3FileService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 알림 생성 정책 단위 테스트. (README §8)
 *
 * createNotification 이 만족해야 하는 두 가지 핵심 규칙을 잠근다.
 *  1) 셀프 알림 차단 — 보낸 사람 == 받는 사람이면 저장하지 않는다.
 *  2) 사용자 설정 존중 — pauseAll 또는 해당 type 의 알림이 꺼져 있으면 저장하지 않는다.
 */
@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationDAO notificationDAO;

    @Mock
    private NotificationSettingDAO notificationSettingDAO;

    @Mock
    private S3FileService s3FileService;

    @InjectMocks
    private NotificationService notificationService;

    @Nested
    @DisplayName("셀프 알림 차단")
    class SelfNotification {

        @Test
        @DisplayName("보낸 사람과 받는 사람이 같으면 설정 조회조차 하지 않고 저장하지 않는다")
        void skipsWhenSenderEqualsReceiver() {
            notificationService.createNotification(7L, 7L, "LIKE", "WORK", 100L, "내가 내 작품에 좋아요");

            verify(notificationDAO, never()).save(any());
            verify(notificationSettingDAO, never()).findByMemberId(anyLong());
        }
    }

    @Nested
    @DisplayName("사용자 설정 존중")
    class RespectSettings {

        @Test
        @DisplayName("설정 자체가 없으면(기본값) 알림을 저장한다")
        void savesWhenNoSettingRow() {
            when(notificationSettingDAO.findByMemberId(1L)).thenReturn(null);

            notificationService.createNotification(1L, 2L, "FOLLOW", "MEMBER", 2L, "팔로우함");

            verify(notificationDAO).save(any(NotificationVO.class));
        }

        @Test
        @DisplayName("pauseAll 이 켜져 있으면 어떤 type 이든 저장하지 않는다")
        void skipsWhenPauseAll() {
            when(notificationSettingDAO.findByMemberId(1L))
                    .thenReturn(NotificationSettingVO.builder().pauseAll(true).build());

            notificationService.createNotification(1L, 2L, "COMMENT", "WORK", 5L, "댓글");

            verify(notificationDAO, never()).save(any());
        }

        @Test
        @DisplayName("해당 type 알림(FOLLOW)이 꺼져 있으면 저장하지 않는다")
        void skipsWhenTypeDisabled() {
            when(notificationSettingDAO.findByMemberId(1L))
                    .thenReturn(NotificationSettingVO.builder().followNotify(false).build());

            notificationService.createNotification(1L, 2L, "FOLLOW", "MEMBER", 2L, "팔로우함");

            verify(notificationDAO, never()).save(any());
        }

        @Test
        @DisplayName("끈 type 과 다른 type(LIKE)의 알림은 정상 저장된다")
        void savesUnrelatedTypeEvenWhenAnotherDisabled() {
            when(notificationSettingDAO.findByMemberId(1L))
                    .thenReturn(NotificationSettingVO.builder().followNotify(false).build());

            notificationService.createNotification(1L, 2L, "LIKE", "WORK", 9L, "좋아요");

            verify(notificationDAO).save(any(NotificationVO.class));
        }
    }

    @Nested
    @DisplayName("정상 저장")
    class HappyPath {

        @Test
        @DisplayName("전달한 인자들이 그대로 NotificationVO 에 담겨 저장된다")
        void persistsGivenFieldsIntoVO() {
            when(notificationSettingDAO.findByMemberId(10L)).thenReturn(null);

            notificationService.createNotification(10L, 20L, "SALE", "ORDER", 333L,
                    "'풍경' 작품이 판매되었습니다.");

            ArgumentCaptor<NotificationVO> captor = ArgumentCaptor.forClass(NotificationVO.class);
            verify(notificationDAO).save(captor.capture());
            NotificationVO saved = captor.getValue();
            assertThat(saved.getMemberId()).isEqualTo(10L);
            assertThat(saved.getSenderId()).isEqualTo(20L);
            assertThat(saved.getNotiType()).isEqualTo("SALE");
            assertThat(saved.getTargetType()).isEqualTo("ORDER");
            assertThat(saved.getTargetId()).isEqualTo(333L);
            assertThat(saved.getMessage()).isEqualTo("'풍경' 작품이 판매되었습니다.");
        }
    }

    @Nested
    @DisplayName("replaceNotification — 토글 이벤트 누적 방지")
    class ReplaceNotification {

        @Test
        @DisplayName("기존 동일 (받는사람,보낸사람,type) 알림을 지운 뒤 새로 저장한다")
        void deletesOldThenSaves() {
            when(notificationSettingDAO.findByMemberId(1L)).thenReturn(null);

            notificationService.replaceNotification(1L, 2L, "FOLLOW", "MEMBER", 2L, "팔로우함");

            verify(notificationDAO).deleteByMemberAndSenderAndType(1L, 2L, "FOLLOW");
            verify(notificationDAO).save(any(NotificationVO.class));
        }

        @Test
        @DisplayName("셀프 알림이면 삭제도 저장도 하지 않는다")
        void skipsSelfReplace() {
            notificationService.replaceNotification(5L, 5L, "FOLLOW", "MEMBER", 5L, "self");

            verify(notificationDAO, never()).deleteByMemberAndSenderAndType(anyLong(), anyLong(), anyString());
            verify(notificationDAO, never()).save(any());
        }
    }
}
