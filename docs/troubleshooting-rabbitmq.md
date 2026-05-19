# 🐰 분산 환경 채팅 메시지 일관성 — RabbitMQ Fanout 도입

## 📌 문제 정의

EC2 두 대 + nginx 로드밸런서(`least_conn`) 구조에서 채팅 메시지가
**같은 EC2에 연결된 사용자에게만 전달**되는 현상 발생.

```
시나리오:
- User A → EC2 #1에 WebSocket 연결
- User B → EC2 #2에 WebSocket 연결
- A와 B가 같은 채팅방에 입장
- A가 메시지 전송
  → EC2 #1의 SimpleBroker가 메시지를 수신
  → EC2 #1의 구독자에게만 broadcast
  ❌ User B (EC2 #2) 는 메시지 수신 실패
```

---

## 🔍 원인 분석

Spring WebSocket의 기본 broker 인 `SimpleBroker` 는
**JVM 인메모리(In-memory)** 방식이라 여러 인스턴스 간 메시지 공유가 불가능.

```java
// WebSocketConfig.java (수정 전)
config.enableSimpleBroker("/topic");
```

`SimpMessagingTemplate.convertAndSend("/topic/room.X", ...)` 호출 시
같은 JVM 내 구독자에게만 전달되고, 다른 EC2의 구독자에게는 도달하지 않음.

---

## ✅ 해결 — RabbitMQ Fanout Exchange

### 설계 방향

| 항목 | 결정 |
|---|---|
| Broker 방식 | **AMQP** (강사님 가이드 준수) — STOMP relay 미사용 |
| Exchange 타입 | **Fanout** — 바인딩된 모든 큐로 메시지 복사 |
| Queue 타입 | **AnonymousQueue** — 각 인스턴스가 고유한 임시 큐 보유 |
| 패턴 | Service 에서 직접 `RabbitTemplate.convertAndSend()` → 모든 인스턴스의 `@RabbitListener` 가 수신 → 각자 자기 WebSocket 구독자에게 broadcast |

### 메시지 흐름

```
User A (EC2 #1) → WebSocket → MessageService
                       ↓
                   DB 저장
                       ↓
        rabbitTemplate.convertAndSend(EXCHANGE)
                       ↓
            ┌──────[RabbitMQ Fanout]──────┐
            ↓                              ↓
   EC2 #1 @RabbitListener         EC2 #2 @RabbitListener
            ↓                              ↓
  simpMessagingTemplate          simpMessagingTemplate
     .convertAndSend                .convertAndSend
            ↓                              ↓
       User A 본인                  User B (다른 EC2)
```

---

## 📦 변경 파일

### 1. `build.gradle`
```gradle
implementation 'org.springframework.boot:spring-boot-starter-amqp'
```

### 2. `application.yaml`
```yaml
spring:
  rabbitmq:
    host: ${RABBITMQ_HOST}
    port: ${RABBITMQ_PORT:5672}
    username: ${RABBITMQ_USER:guest}
    password: ${RABBITMQ_PASS:guest}

app:
  websocket:
    broker:
      relay:
        enabled: false   # STOMP relay 미사용, SimpleBroker 유지
```

### 3. `config/RabbitConfig.java` (신규)
```java
@Configuration
public class RabbitConfig {

    public static final String CHAT_EXCHANGE = "bideo.chat.exchange";

    @Bean
    public FanoutExchange chatExchange() {
        return new FanoutExchange(CHAT_EXCHANGE, true, false);
    }

    @Bean
    public Queue chatQueue() {
        return new AnonymousQueue();   // 인스턴스별 고유 큐
    }

    @Bean
    public Binding chatBinding(Queue chatQueue, FanoutExchange chatExchange) {
        return BindingBuilder.bind(chatQueue).to(chatExchange);
    }

    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory cf, MessageConverter mc) {
        RabbitTemplate t = new RabbitTemplate(cf);
        t.setMessageConverter(mc);
        return t;
    }
}
```

### 4. `dto/message/ChatRelayMessage.java` (신규)
인스턴스 간 전달용 envelope.
```java
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ChatRelayMessage {
    private String destination;   // "/topic/room.42"
    private Object payload;       // 실제 메시지
}
```

### 5. `service/message/ChatRelayListener.java` (신규)
```java
@Component
@RequiredArgsConstructor
public class ChatRelayListener {

    private final SimpMessagingTemplate messagingTemplate;

    @RabbitListener(queues = "#{chatQueue.name}")
    public void onRelay(ChatRelayMessage relay) {
        if (relay == null || relay.getDestination() == null) return;
        messagingTemplate.convertAndSend(relay.getDestination(), relay.getPayload());
    }
}
```

### 6. `service/message/MessageService.java` (수정)
```java
// Before
private final SimpMessagingTemplate messagingTemplate;

private void broadcast(Long roomId, String type, MessageResponseDTO message) {
    messagingTemplate.convertAndSend("/topic/room." + roomId, ...);
}

// After
private final RabbitTemplate rabbitTemplate;

private void broadcast(Long roomId, String type, MessageResponseDTO message) {
    publishRelay("/topic/room." + roomId, ...);
}

private void publishRelay(String destination, Object payload) {
    rabbitTemplate.convertAndSend(
            RabbitConfig.CHAT_EXCHANGE, "",
            ChatRelayMessage.builder()
                    .destination(destination)
                    .payload(payload)
                    .build()
    );
}
```

### 7. `.github/workflows/deploy.yml`
```yaml
-e RABBITMQ_HOST=${{ secrets.RABBITMQ_HOST }} \
-e RABBITMQ_PORT=${{ secrets.RABBITMQ_PORT }} \
-e RABBITMQ_USER=${{ secrets.RABBITMQ_USER }} \
-e RABBITMQ_PASS=${{ secrets.RABBITMQ_PASS }} \
```

---

## 🐳 인프라 — RabbitMQ 컨테이너

### 설치 (한 EC2에만 띄움)
```bash
docker run -d \
    --name rabbitmq \
    -p 5672:5672 \      # AMQP
    -p 15672:15672 \    # 관리 콘솔
    -e RABBITMQ_DEFAULT_USER=guest \
    -e RABBITMQ_DEFAULT_PASS=guest \
    rabbitmq:3-management
```

### guest 외부 접속 허용 (또는 새 유저 생성)
```bash
# 옵션 1: guest 로 외부 접속 허용
docker exec rabbitmq sh -c 'echo "loopback_users = none" > /etc/rabbitmq/conf.d/10-loopback.conf'
docker restart rabbitmq

# 옵션 2: 새 유저 생성 (권장)
docker exec rabbitmq rabbitmqctl add_user bideo bideo1234!
docker exec rabbitmq rabbitmqctl set_user_tags bideo administrator
docker exec rabbitmq rabbitmqctl set_permissions -p / bideo ".*" ".*" ".*"
```

### AWS 보안그룹

RabbitMQ EC2 인바운드:
| 포트 | 소스 | 용도 |
|---|---|---|
| 5672 | Spring EC2 보안그룹 (또는 VPC CIDR) | AMQP |
| 15672 | 본인 IP | 관리 콘솔 |

⚠️ 5672 를 `0.0.0.0/0` 으로 열지 말 것.

---

## 🔐 GitHub Secrets

| 이름 | 값 |
|---|---|
| `RABBITMQ_HOST` | RabbitMQ EC2 **내부 IP** (`172.31.x.x`) |
| `RABBITMQ_PORT` | `5672` |
| `RABBITMQ_USER` | `guest` 또는 `bideo` |
| `RABBITMQ_PASS` | 위와 동일 |

---

## ✅ 검증

### 1. Spring Boot 로그
```bash
sudo docker logs bideo 2>&1 | grep -iE "rabbit|amqp|listener"
```
기대 출력:
```
o.s.a.r.l.SimpleMessageListenerContainer  : Started.
```

### 2. RabbitMQ 관리 콘솔
브라우저 → `http://<RabbitMQ EC2 퍼블릭 IP>:15672`
- Exchanges 탭에 `bideo.chat.exchange` (fanout) 표시
- Queues 탭에 `amq.gen-XXXX` 큐 2개 (각 Spring 인스턴스용) 표시
- 각 큐의 Bindings 에 `bideo.chat.exchange` 연결 확인

### 3. 채팅 시나리오 테스트
1. 시크릿 창 2개로 서로 다른 계정 로그인 (가능하면 IP 다르게)
2. 같은 채팅방 입장
3. 한쪽에서 메시지 전송 → **즉시 다른 쪽에 표시되면 성공**

---

## 📚 트러블슈팅 (작업 중 발생한 이슈)

### 이슈 1 — RabbitMQ 컨테이너에 STOMP 포트 매핑 누락
**현상**: 처음에 STOMP relay 방식으로 접근했으나 `61613` 포트가 매핑되지 않음
**대처**: 강사님 가이드는 AMQP 기반이므로 STOMP 포트 매핑 불필요. 코드를 `enableStompBrokerRelay` 가 아닌 **RabbitTemplate + @RabbitListener** 패턴으로 작성

### 이슈 2 — 양쪽 Spring EC2 에 RabbitMQ 중복 설치
**현상**: Spring1, Spring2 양쪽에 각자 RabbitMQ 컨테이너를 띄우니 메시지가 인스턴스별로 격리되어 분산 처리 의미 상실
**대처**: 한 EC2 의 RabbitMQ 만 살리고 나머지는 제거. 두 Spring 이 같은 RabbitMQ 를 바라보도록 GitHub Secrets `RABBITMQ_HOST` 통일

### 이슈 3 — guest 계정 외부 접속 차단
**현상**: 다른 EC2에서 RabbitMQ 의 guest 계정으로 접속 시도 → 인증 실패
**원인**: RabbitMQ 기본 정책상 `guest` 는 localhost 에서만 접속 허용
**대처**: `loopback_users = none` 설정 추가, 또는 별도 운영 계정(`bideo`) 생성

---

## 🎯 발표 어필 포인트

> **"분산 환경의 핵심은 무상태성입니다."**
>
> JVM 메모리에 있던 메시지를 RabbitMQ Fanout Exchange 로 옮기면서,
> 두 Spring 인스턴스가 어떤 사용자를 받든 메시지 일관성을 보장하게 됐습니다.
>
> OAuth state(쿠키), 세션(Redis), 메시지(RabbitMQ) — 분산 환경에서 무상태로 만들어야 할
> 세 가지 데이터를 모두 외부 저장소로 옮긴 정석적 아키텍처입니다.
