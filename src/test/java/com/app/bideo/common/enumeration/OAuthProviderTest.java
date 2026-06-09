package com.app.bideo.common.enumeration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * OAuthProvider enum 의 문자열 ↔ enum 변환 규칙을 잠가두는 단위 테스트.
 *
 * 소셜 로그인은 provider 문자열(naver/kakao/google)을 외부에서 받아오므로
 * 대소문자/공백/미지원 값에 대한 동작이 깨지면 로그인 전체가 망가진다.
 */
class OAuthProviderTest {

    @Nested
    @DisplayName("from(String) — 문자열을 enum 으로")
    class From {

        @Test
        @DisplayName("정확한 소문자 값은 해당 provider 로 매핑된다")
        void exactLowercaseMapsToProvider() {
            assertThat(OAuthProvider.from("naver")).isEqualTo(OAuthProvider.NAVER);
            assertThat(OAuthProvider.from("kakao")).isEqualTo(OAuthProvider.KAKAO);
            assertThat(OAuthProvider.from("google")).isEqualTo(OAuthProvider.GOOGLE);
        }

        @Test
        @DisplayName("대문자/혼합 케이스도 대소문자 무시하고 매핑된다")
        void caseInsensitiveMapping() {
            assertThat(OAuthProvider.from("NAVER")).isEqualTo(OAuthProvider.NAVER);
            assertThat(OAuthProvider.from("Kakao")).isEqualTo(OAuthProvider.KAKAO);
            assertThat(OAuthProvider.from("GOOGLE")).isEqualTo(OAuthProvider.GOOGLE);
        }

        @Test
        @DisplayName("null 이면 null 을 돌려준다 (예외를 던지지 않는다)")
        void nullReturnsNull() {
            assertThat(OAuthProvider.from(null)).isNull();
        }

        @Test
        @DisplayName("지원하지 않는 provider 는 null 을 돌려준다")
        void unknownProviderReturnsNull() {
            assertThat(OAuthProvider.from("facebook")).isNull();
            assertThat(OAuthProvider.from("")).isNull();
        }
    }

    @Test
    @DisplayName("getValue() 는 각 provider 의 소문자 식별자를 돌려준다")
    void getValueReturnsLowercaseIdentifier() {
        assertThat(OAuthProvider.NAVER.getValue()).isEqualTo("naver");
        assertThat(OAuthProvider.KAKAO.getValue()).isEqualTo("kakao");
        assertThat(OAuthProvider.GOOGLE.getValue()).isEqualTo("google");
    }

    @Test
    @DisplayName("from ↔ getValue 라운드트립이 보존된다")
    void roundTripIsStable() {
        for (OAuthProvider provider : OAuthProvider.values()) {
            assertThat(OAuthProvider.from(provider.getValue())).isEqualTo(provider);
        }
    }
}
