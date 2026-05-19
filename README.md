# 🎬 BIDEO — 영상 크리에이터를 위한 프리미엄 마켓플레이스

> 영상은 누구나 만들 수 있는 시대,
> 정작 그 영상이 **작품으로 거래되는 시장**은 없었습니다.
> BIDEO는 그 빈 자리를 채우는, **영상 작품 거래·경매 플랫폼**입니다.

---

## 📌 프로젝트 기획 배경

| 문제 | BIDEO의 해결 |
|---|---|
| 영상 크리에이터에게 **정당한 수익 창구**가 없음 | 즉시 판매 + 경매, 두 가지 거래 방식 제공 |
| 좋은 작품을 **알아볼 안목**이 부족 | AI 기반 큐레이션·예측 모델 탑재 |
| 단순 콘텐츠 소비를 넘어선 **소장 욕구** 부재 | 작가별 예술관(갤러리)·팔로우·뱃지 시스템 |

영상은 단순한 콘텐츠가 아닌 **가치 있는 작품**이 될 수 있습니다.
BIDEO는 그 가치를 **작가와 컬렉터 사이에서 거래 가능한 형태로 연결**합니다.

---

## 🚀 핵심 기능

| 영역 | 기능 |
|---|---|
| 거래 | 즉시 구매 / 경매 입찰 / 결제 / 정산·환불 |
| 커뮤니티 | 작가별 예술관, 팔로우, 좋아요, 북마크, 댓글, 신고 |
| AI | 경매 낙찰 예측(분류) · 팔로워 성장 예측(회귀) · 작품 추천 · LLM Vision 자동 설명 |
| 실시간 | 1:1 채팅, 알림, 메시지 좋아요 |
| 인증 | JWT, OAuth 2.0 (네이버·카카오·구글) |

---

## 🛠 기술 스택

| 분류 | 사용 기술 |
|---|---|
| **Backend** | Spring Boot 3.5, Java 17, Spring Security, Spring WebSocket, MyBatis |
| **Frontend** | Thymeleaf, HTML / JavaScript / CSS, SockJS, STOMP |
| **Database** | PostgreSQL, Redis (세션·캐시) |
| **Infra** | AWS EC2 × 2 (Spring), AWS S3, nginx (load balancer / TLS 종료) |
| **Auth** | Spring Security, JWT, OAuth 2.0 (Naver / Kakao / Google) |
| **External API** | BootPay (결제), Solapi (SMS), Gmail SMTP, FastAPI (ML 서버) |
| **CI/CD** | GitHub Actions, Docker, Let's Encrypt (certbot) |
| **협업** | Git / GitHub, Notion, Figma, Discord |

### 인프라 구조

```
                 ┌─────────────────┐
                 │   Route 53      │  bideo.ai.kr
                 └────────┬────────┘
                          ▼
                 ┌─────────────────┐
                 │   nginx (LB)    │  Let's Encrypt SSL
                 │   least_conn    │
                 └────────┬────────┘
              ┌───────────┴───────────┐
              ▼                       ▼
       ┌─────────────┐         ┌─────────────┐
       │ Spring EC2 #1│         │ Spring EC2 #2│
       │  (Docker)   │         │  (Docker)   │
       └──────┬──────┘         └──────┬──────┘
              └───────────┬───────────┘
                          ▼
              ┌──────────────────────┐
              │ PostgreSQL │ Redis  │
              │     S3     │ ML API │
              └──────────────────────┘
```

---

## 🙋 담당 영역 (Frontend + Auth + Real-time)

### 1. **인트로 페이지** (`intro-main`)
- 서비스 소개 랜딩 페이지
- FAQ 탭패널, 기능 소개 섹션, CTA 버튼
- 비로그인 사용자 진입 시 첫 화면

### 2. **메인 페이지** (`main`)
- AI 큐레이션 기반 작품 추천 (`/api/prediction/curation`)
- 카테고리별 작품 슬라이드
- 무한 스크롤 피드

### 3. **로그인 / 회원가입**
- JWT 토큰 기반 인증 (Access + Refresh)
- 이메일·비밀번호 검증, 휴대폰 SMS 인증 (Solapi)
- 비밀번호 재설정 메일 발송

### 4. **소셜 로그인** (OAuth 2.0)
- 네이버 / 카카오 / 구글 통합
- Spring Security `oauth2Login` + 커스텀 `OAuth2SuccessHandler`
- **분산 환경에서도 동작하는 쿠키 기반 state 저장**

### 5. **공통 레이아웃**
- 헤더·사이드바·푸터
- 다크 / 라이트 테마 토글 (CSS 변수 기반)
- 반응형 (모바일 / 태블릿 / PC)

### 6. **실시간 채팅**
- WebSocket + STOMP + SockJS
- 1:1 채팅방, 메시지 송수신·수정·삭제·좋아요
- 안 읽은 메시지 카운트, 실시간 알림 배지

---

## 🔧 트러블슈팅

### 1. **분산 환경에서 OAuth 로그인 실패**

| 항목 | 내용 |
|---|---|
| 증상 | EC2 두 대 분산 환경에서 네이버 로그인 후 에러 페이지로 이동 |
| 원인 | Spring Security 기본 `HttpSessionOAuth2AuthorizationRequestRepository`가 OAuth state를 **JVM 메모리에 저장**. nginx `least_conn`이 콜백을 다른 EC2로 보내면서 state 불일치 |
| 해결 | `CookieOAuth2AuthorizationRequestRepository` 직접 구현 → state를 **HttpOnly + Secure 쿠키**에 base64 직렬화하여 저장. 어떤 EC2가 콜백 받아도 쿠키에서 복원 가능 |
| 결과 | `SessionCreationPolicy.STATELESS` 유지하면서 분산 환경 완전 호환 |

### 2. **실시간 채팅 메시지가 새로고침해야 표시됨**

| 항목 | 내용 |
|---|---|
| 증상 | HTTPS 배포 후 채팅 메시지가 즉시 안 뜨고 새로고침해야 보임 |
| 원인 | nginx에 `/ws` 경로용 `proxy_set_header Upgrade` 헤더 누락 → WebSocket 핸드셰이크 실패 → SockJS가 xhr_streaming으로 fallback했지만 `proxy_buffering`이 켜져 있어 SSE 응답이 깨짐 |
| 해결 | nginx 설정에 `/ws` location 블록 추가 + `proxy_buffering off` + `proxy_read_timeout 86400s` |
| 결과 | WebSocket 정상 동작, 실시간 메시지·읽음 상태 즉시 반영 |

### 3. **다크 모드에서 채팅 버블이 네모 박스로 표시됨**

| 항목 | 내용 |
|---|---|
| 증상 | 다크모드 시 채팅 말풍선이 둥근 형태가 아닌 사각형 보라색 박스로 보임 |
| 원인 | `.bd-chat-bubble--self` (버블 그리드 컨테이너)에 `background-color: accent`가 잘못 적용됨. 실제 둥근 버블(`__body`)보다 큰 영역에 색이 칠해져서 바깥쪽까지 번짐 |
| 해결 | 컨테이너 background를 `transparent`로 변경, 색상은 `.__body`에만 적용되도록 정리 |

### 4. **결제 시 IP 차단 (BootPay APP_FIREWALL_BLOCKED)**

| 항목 | 내용 |
|---|---|
| 증상 | 결제 요청 시 `접근이 허가된 IP가 아닙니다` 401 응답 |
| 원인 | BootPay 관리자 콘솔의 IP 화이트리스트에 EC2 공인 IP 미등록 |
| 해결 | BootPay 콘솔 → 결제설정 → 연동키 및 보안에서 EC2 Elastic IP 추가 |

### 5. **HTTPS 적용 (Let's Encrypt)**

| 항목 | 내용 |
|---|---|
| 배경 | 초기에 mkcert(self-signed) 사용 → 브라우저 신뢰 불가 + WebSocket 핸드셰이크 실패 |
| 해결 | `certbot --nginx`로 Let's Encrypt 정식 인증서 발급, nginx 자동 설정 |
| 결과 | 자물쇠 정상, OAuth 콜백 HTTPS, 자동 갱신 등록 (90일 주기) |

---

## 🤝 협업

| 항목 | 내용 |
|---|---|
| 기간 | 2026.04 ~ 2026.05 |
| 인원 | 백엔드 X명, AI X명, 디자인 X명 |
| 도구 | GitHub (이슈·PR 기반), Notion (회의록·태스크), Figma (디자인), Discord (실시간 소통) |

---

## 🎓 회고

### 잘한 점
- **분산 환경 트러블슈팅**: 단순히 ip_hash로 우회하지 않고 OAuth state를 쿠키로 옮기는 정석적 해결을 선택
- **CI/CD 자동화**: GitHub Actions로 master 브랜치 push 시 두 EC2에 자동 배포되도록 구성
- **실시간 기능 안정화**: SockJS fallback까지 고려한 nginx 설정으로 다양한 환경에서 동작 확보

### 아쉬운 점
- **테스트 코드 부재**: 시간상 단위·통합 테스트를 충분히 작성하지 못함. 다음 프로젝트에선 TDD까진 아니어도 핵심 비즈니스 로직은 테스트 작성 필요
- **로그 수집 시스템 미구축**: 운영 환경에서 발생한 에러를 EC2에 SSH로 들어가 확인. CloudWatch · ELK 같은 중앙 로그 시스템이 필요

### 배운 점
- **분산 환경의 무상태성**: 세션·캐시·state 같은 데이터는 메모리가 아닌 외부 저장소(Redis, Cookie)에 두어야 한다는 점을 실제 경험으로 학습
- **인프라 ↔ 애플리케이션 경계**: nginx 한 줄 설정이 백엔드 코드보다 더 큰 영향을 줄 수 있다는 점

---
