package com.app.bideo.auth.member;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Base64;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * JWT 발급/검증/파싱 로직 단위 테스트. (README §3)
 *
 * Redis/Spring 컨텍스트 없이 RedisTemplate·UserDetailsService 를 모킹하고,
 * @Value 로 주입되던 secret 은 ReflectionTestUtils 로 채운 뒤 init() 을
 * 직접 호출해 실제 서명 키를 구성한다.
 */
@ExtendWith(MockitoExtension.class)
class JwtTokenProviderTest {

    // HS256 은 256bit 이상 키가 필요 — 충분히 긴 시크릿을 Base64 로 인코딩해서 사용
    private static final String SECRET = Base64.getEncoder().encodeToString(
            "bideo-test-secret-key-bideo-test-secret-key-256bit!!".getBytes(StandardCharsets.UTF_8));

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @Mock
    private UserDetailsService userDetailsService;

    @InjectMocks
    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void initKey() {
        ReflectionTestUtils.setField(jwtTokenProvider, "secretKey", SECRET);
        jwtTokenProvider.init();
    }

    @Nested
    @DisplayName("Access Token 발급")
    class CreateAccessToken {

        @Test
        @DisplayName("발급한 토큰에서 email(subject)·provider 클레임을 다시 꺼낼 수 있다")
        void encodesEmailAndProviderClaims() {
            MockHttpServletResponse response = new MockHttpServletResponse();

            String token = jwtTokenProvider.createAccessToken("junsu@bideo.com", "LOCAL", response);

            assertThat(jwtTokenProvider.getEmail(token)).isEqualTo("junsu@bideo.com");
            assertThat(jwtTokenProvider.getProvider(token)).isEqualTo("LOCAL");
        }

        @Test
        @DisplayName("accessToken 을 HttpOnly 쿠키로 응답에 심는다")
        void writesHttpOnlyCookie() {
            MockHttpServletResponse response = new MockHttpServletResponse();

            jwtTokenProvider.createAccessToken("junsu@bideo.com", "kakao", response);

            Cookie cookie = response.getCookie("accessToken");
            assertThat(cookie).isNotNull();
            assertThat(cookie.isHttpOnly()).isTrue();
            assertThat(cookie.getValue()).isNotBlank();
            assertThat(cookie.getPath()).isEqualTo("/");
        }
    }

    @Nested
    @DisplayName("Refresh Token 발급")
    class CreateRefreshToken {

        @Test
        @DisplayName("refresh 토큰을 Redis 에 저장하고 쿠키로 내려준다")
        void persistsToRedisAndCookie() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            MockHttpServletResponse response = new MockHttpServletResponse();

            String token = jwtTokenProvider.createRefreshToken("junsu@bideo.com", "LOCAL", response);

            // refresh:{email} 키로 Redis 저장
            org.mockito.Mockito.verify(valueOperations).set(
                    org.mockito.ArgumentMatchers.eq("refresh:junsu@bideo.com"),
                    org.mockito.ArgumentMatchers.eq(token),
                    org.mockito.ArgumentMatchers.anyLong(),
                    org.mockito.ArgumentMatchers.any());
            assertThat(response.getCookie("refreshToken")).isNotNull();
        }
    }

    @Nested
    @DisplayName("토큰 검증 validateToken")
    class Validate {

        @Test
        @DisplayName("정상 발급되고 블랙리스트에 없는 토큰은 유효하다")
        void validTokenPasses() {
            when(redisTemplate.hasKey(anyString())).thenReturn(false);
            String token = jwtTokenProvider.createAccessToken("junsu@bideo.com", "LOCAL",
                    new MockHttpServletResponse());

            assertThat(jwtTokenProvider.validateToken(token)).isTrue();
        }

        @Test
        @DisplayName("블랙리스트에 등록된 토큰은 무효다")
        void blacklistedTokenFails() {
            when(redisTemplate.hasKey(anyString())).thenReturn(true);
            String token = jwtTokenProvider.createAccessToken("junsu@bideo.com", "LOCAL",
                    new MockHttpServletResponse());

            assertThat(jwtTokenProvider.validateToken(token)).isFalse();
        }

        @Test
        @DisplayName("만료된 토큰은 무효다 (Redis 조회 없이 파싱 단계에서 걸러진다)")
        void expiredTokenFails() {
            String expired = signedTokenExpiringAt(new Date(System.currentTimeMillis() - 1000));

            assertThat(jwtTokenProvider.validateToken(expired)).isFalse();
        }

        @Test
        @DisplayName("다른 키로 서명된(위조) 토큰은 무효다")
        void tamperedSignatureFails() {
            Key foreignKey = Keys.hmacShaKeyFor(Base64.getEncoder()
                    .encodeToString("another-secret-key-another-secret-key-256bit-x".getBytes(StandardCharsets.UTF_8))
                    .getBytes(StandardCharsets.UTF_8));
            String forged = Jwts.builder()
                    .setSubject("junsu@bideo.com")
                    .setExpiration(new Date(System.currentTimeMillis() + 60_000))
                    .signWith(foreignKey, SignatureAlgorithm.HS256)
                    .compact();

            assertThat(jwtTokenProvider.validateToken(forged)).isFalse();
        }

        @Test
        @DisplayName("null/빈 문자열은 무효다")
        void blankTokenFails() {
            assertThat(jwtTokenProvider.validateToken(null)).isFalse();
            assertThat(jwtTokenProvider.validateToken("")).isFalse();
            assertThat(jwtTokenProvider.validateToken("   ")).isFalse();
        }
    }

    @Nested
    @DisplayName("provider 클레임 해석")
    class Provider {

        @Test
        @DisplayName("provider 클레임이 없으면 LOCAL 로 간주한다")
        void defaultsToLocalWhenAbsent() {
            String noProviderToken = Jwts.builder()
                    .setSubject("junsu@bideo.com")
                    .setExpiration(new Date(System.currentTimeMillis() + 60_000))
                    .signWith(signingKey(), SignatureAlgorithm.HS256)
                    .compact();

            assertThat(jwtTokenProvider.getProvider(noProviderToken)).isEqualTo("LOCAL");
        }
    }

    @Nested
    @DisplayName("쿠키에서 토큰 추출")
    class ResolveFromCookie {

        @Test
        @DisplayName("accessToken 쿠키 값을 추출한다")
        void resolvesAccessTokenCookie() {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setCookies(new Cookie("accessToken", "abc.def.ghi"),
                    new Cookie("refreshToken", "rrr.sss.ttt"));

            assertThat(jwtTokenProvider.resolveAccessToken(request)).isEqualTo("abc.def.ghi");
            assertThat(jwtTokenProvider.resolveRefreshToken(request)).isEqualTo("rrr.sss.ttt");
        }

        @Test
        @DisplayName("쿠키가 전혀 없으면 null 을 돌려준다")
        void returnsNullWhenNoCookies() {
            MockHttpServletRequest request = new MockHttpServletRequest();

            assertThat(jwtTokenProvider.resolveAccessToken(request)).isNull();
        }
    }

    @Nested
    @DisplayName("Refresh Token 일치 검사")
    class RefreshTokenMatch {

        @Test
        @DisplayName("쿠키 토큰과 Redis 토큰이 같으면 true")
        void matchesWhenEqual() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get("refresh:junsu@bideo.com")).thenReturn("same-token");

            assertThat(jwtTokenProvider.checkRefreshTokenBetweenCookieAndRedis(
                    "junsu@bideo.com", "same-token")).isTrue();
        }

        @Test
        @DisplayName("Redis 에 토큰이 없으면(로그아웃 등) false")
        void failsWhenRedisEmpty() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get("refresh:junsu@bideo.com")).thenReturn(null);

            assertThat(jwtTokenProvider.checkRefreshTokenBetweenCookieAndRedis(
                    "junsu@bideo.com", "cookie-token")).isFalse();
        }
    }

    // --- helpers ---

    private Key signingKey() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET));
    }

    private String signedTokenExpiringAt(Date expiry) {
        return Jwts.builder()
                .setSubject("junsu@bideo.com")
                .setIssuedAt(new Date(expiry.getTime() - 1000))
                .setExpiration(expiry)
                .signWith(signingKey(), SignatureAlgorithm.HS256)
                .compact();
    }
}
