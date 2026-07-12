package org.keycloak.tests.oauth;

import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.keycloak.testframework.annotations.InjectRealm;
import org.keycloak.testframework.annotations.KeycloakIntegrationTest;
import org.keycloak.testframework.oauth.OAuthClient;
import org.keycloak.testframework.oauth.annotations.InjectOAuthClient;
import org.keycloak.models.OAuth2DeviceConfig;
import org.keycloak.testframework.realm.ClientBuilder;
import org.keycloak.testframework.realm.ClientConfig;
import org.keycloak.testframework.realm.ManagedRealm;
import org.keycloak.testframework.realm.RealmBuilder;
import org.keycloak.testframework.realm.RealmConfig;
import org.keycloak.testframework.realm.UserBuilder;
import org.keycloak.testsuite.util.oauth.AccessTokenResponse;
import org.keycloak.testsuite.util.oauth.LogoutResponse;
import org.keycloak.testsuite.util.oauth.device.DeviceAuthorizationResponse;

import org.keycloak.OAuthErrorException;
import org.keycloak.testsuite.util.oauth.AbstractHttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;

@KeycloakIntegrationTest
class MalformedContentTypeTest {

    @InjectRealm(config = TestRealm.class)
    ManagedRealm realm;

    @InjectOAuthClient(config = TestClient.class)
    OAuthClient oauth;

    static Stream<String> malformedContentTypes() {
        return Stream.of(
                "invalid/@@##",
                "</><script>alert(1)</script>",
                "text",
                "text/[html]"
        );
    }

    @ParameterizedTest
    @MethodSource("malformedContentTypes")
    void tokenEndpoint(String contentType) {
        AccessTokenResponse response = oauth.passwordGrantRequest("test-user@localhost", "password")
                .header("Content-Type", contentType)
                .send();
        assertOAuthInvalidRequest(response);
    }

    @ParameterizedTest
    @MethodSource("malformedContentTypes")
    void deviceAuthorizationEndpoint(String contentType) {
        DeviceAuthorizationResponse response = oauth.device()
                .deviceAuthorizationRequest()
                .header("Content-Type", contentType)
                .send();
        assertOAuthInvalidRequest(response);
    }

    @ParameterizedTest
    @MethodSource("malformedContentTypes")
    void logoutEndpoint(String contentType) {
        AccessTokenResponse tokenResponse = oauth.doPasswordGrantRequest("test-user@localhost", "password");
        LogoutResponse response = oauth.logoutRequest()
                .refreshToken(tokenResponse.getRefreshToken())
                .header("Content-Type", contentType)
                .send();
        assertOAuthInvalidRequest(response);
    }

    private static void assertOAuthInvalidRequest(AbstractHttpResponse response) {
        assertEquals(400, response.getStatusCode());
        assertEquals(OAuthErrorException.INVALID_REQUEST, response.getError());
    }

    public static class TestRealm implements RealmConfig {
        @Override
        public RealmBuilder configure(RealmBuilder realm) {
            return realm.users(UserBuilder.create("test-user@localhost")
                    .email("test-user@localhost")
                    .password("password")
                    .name("first", "last")
                    .enabled(true));
        }
    }

    public static class TestClient implements ClientConfig {
        @Override
        public ClientBuilder configure(ClientBuilder client) {
            return client.clientId("test-app")
                    .secret("test-secret")
                    .directAccessGrantsEnabled(true)
                    .attribute(OAuth2DeviceConfig.OAUTH2_DEVICE_AUTHORIZATION_GRANT_ENABLED, "true");
        }
    }

}
