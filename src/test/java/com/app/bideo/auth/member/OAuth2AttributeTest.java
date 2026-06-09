package com.app.bideo.auth.member;

import com.app.bideo.common.enumeration.OAuthProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * provider 마다 응답 JSON 구조가 다른 소셜 로그인 attribute 를 하나의
 * OAuth2Attribute 로 정규화하는 규칙을 검증한다. (README §4)
 *
 * 각 provider 의 중첩 구조(kakao_account.profile, naver.response 등)에서
 * id/email/name/profileImage 가 정확히 뽑히는지, 누락 필드에 NPE 없이
 * 방어되는지를 단언한다.
 */
class OAuth2AttributeTest {

    @Nested
    @DisplayName("카카오")
    class Kakao {

        @Test
        @DisplayName("kakao_account.profile 중첩 구조에서 모든 필드를 정규화한다")
        void normalizesNestedKakaoStructure() {
            Map<String, Object> profile = new HashMap<>();
            profile.put("nickname", "준수");
            profile.put("profile_image_url", "https://k.example/p.png");

            Map<String, Object> kakaoAccount = new HashMap<>();
            kakaoAccount.put("email", "junsu@kakao.com");
            kakaoAccount.put("profile", profile);

            Map<String, Object> attributes = new HashMap<>();
            attributes.put("id", 1234567890L);
            attributes.put("kakao_account", kakaoAccount);

            OAuth2Attribute result = OAuth2Attribute.of("kakao", attributes);

            assertThat(result.getProvider()).isEqualTo(OAuthProvider.KAKAO);
            assertThat(result.getId()).isEqualTo("1234567890");
            assertThat(result.getEmail()).isEqualTo("junsu@kakao.com");
            assertThat(result.getName()).isEqualTo("준수");
            assertThat(result.getProfileImage()).isEqualTo("https://k.example/p.png");
        }

        @Test
        @DisplayName("kakao_account 가 통째로 없어도 NPE 없이 id 만 채운다")
        void survivesMissingKakaoAccount() {
            Map<String, Object> attributes = new HashMap<>();
            attributes.put("id", 42L);

            OAuth2Attribute result = OAuth2Attribute.of("kakao", attributes);

            assertThat(result.getId()).isEqualTo("42");
            assertThat(result.getEmail()).isNull();
            assertThat(result.getName()).isNull();
            assertThat(result.getProfileImage()).isNull();
        }
    }

    @Nested
    @DisplayName("네이버")
    class Naver {

        @Test
        @DisplayName("response 래퍼 안의 필드를 정규화한다")
        void normalizesNaverResponseWrapper() {
            Map<String, Object> response = new HashMap<>();
            response.put("id", "naver-uid-9");
            response.put("email", "junsu@naver.com");
            response.put("name", "이준수");
            response.put("profile_image", "https://n.example/p.png");

            Map<String, Object> attributes = new HashMap<>();
            attributes.put("response", response);

            OAuth2Attribute result = OAuth2Attribute.of("NAVER", attributes);

            assertThat(result.getProvider()).isEqualTo(OAuthProvider.NAVER);
            assertThat(result.getId()).isEqualTo("naver-uid-9");
            assertThat(result.getEmail()).isEqualTo("junsu@naver.com");
            assertThat(result.getName()).isEqualTo("이준수");
            assertThat(result.getProfileImage()).isEqualTo("https://n.example/p.png");
        }

        @Test
        @DisplayName("response 래퍼가 없으면 모든 필드가 null 이지만 예외는 없다")
        void survivesMissingResponseWrapper() {
            OAuth2Attribute result = OAuth2Attribute.of("naver", new HashMap<>());

            assertThat(result.getProvider()).isEqualTo(OAuthProvider.NAVER);
            assertThat(result.getId()).isNull();
            assertThat(result.getEmail()).isNull();
        }
    }

    @Nested
    @DisplayName("구글")
    class Google {

        @Test
        @DisplayName("flat 한 구글 응답에서 sub→id, picture→profileImage 로 매핑한다")
        void normalizesFlatGoogleStructure() {
            Map<String, Object> attributes = new HashMap<>();
            attributes.put("sub", "google-sub-7");
            attributes.put("email", "junsu@gmail.com");
            attributes.put("name", "JUNSU");
            attributes.put("picture", "https://g.example/p.png");

            OAuth2Attribute result = OAuth2Attribute.of("google", attributes);

            assertThat(result.getProvider()).isEqualTo(OAuthProvider.GOOGLE);
            assertThat(result.getId()).isEqualTo("google-sub-7");
            assertThat(result.getEmail()).isEqualTo("junsu@gmail.com");
            assertThat(result.getName()).isEqualTo("JUNSU");
            assertThat(result.getProfileImage()).isEqualTo("https://g.example/p.png");
        }
    }

    @Test
    @DisplayName("지원하지 않는 provider 는 IllegalArgumentException 을 던진다")
    void unsupportedProviderThrows() {
        assertThatThrownBy(() -> OAuth2Attribute.of("facebook", new HashMap<>()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("facebook");
    }

    @Test
    @DisplayName("toMap() 은 provider 를 소문자 식별자로 직렬화하고 나머지 필드를 담는다")
    void toMapSerializesProviderAsValue() {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("sub", "id-1");
        attributes.put("email", "a@b.com");
        attributes.put("name", "n");
        attributes.put("picture", "pic");

        Map<String, Object> map = OAuth2Attribute.of("google", attributes).toMap();

        assertThat(map.get("provider")).isEqualTo("google");
        assertThat(map.get("id")).isEqualTo("id-1");
        assertThat(map.get("email")).isEqualTo("a@b.com");
        assertThat(map.get("name")).isEqualTo("n");
        assertThat(map.get("profileImage")).isEqualTo("pic");
    }
}
