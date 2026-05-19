package com.app.bideo.dto.message;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 채팅 메시지를 다른 Spring 인스턴스로 전파하기 위한 RabbitMQ envelope.
 * destination: STOMP 목적지 (예: "/topic/room.42", "/topic/user.7")
 * payload: 실제 보낼 페이로드 (DTO 또는 Map)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChatRelayMessage {
    private String destination;
    private Object payload;
}
