package com.app.bideo.mapper.notification;

import com.app.bideo.domain.notification.NotificationVO;
import com.app.bideo.dto.notification.NotificationResponseDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface NotificationMapper {
    void insert(NotificationVO notificationVO);

    List<NotificationResponseDTO> selectByMemberId(@Param("memberId") Long memberId,
                                                    @Param("offset") int offset,
                                                    @Param("limit") int limit);

    int selectUnreadCount(@Param("memberId") Long memberId);

    void updateIsRead(@Param("id") Long id, @Param("memberId") Long memberId);

    void updateAllRead(@Param("memberId") Long memberId);

    void deleteNotification(@Param("id") Long id, @Param("memberId") Long memberId);

    /** 같은 (받는사람, 보낸사람, 타입) 알림 모두 삭제 — 반복 토글되는 알림 정리용. */
    int deleteByMemberAndSenderAndType(@Param("memberId") Long memberId,
                                       @Param("senderId") Long senderId,
                                       @Param("notiType") String notiType);
}
