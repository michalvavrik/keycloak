package org.keycloak.tests.oauth;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.util.EntityUtils;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.keycloak.OAuthErrorException;
import org.keycloak.models.OAuth2DeviceConfig;
import org.keycloak.testframework.annotations.InjectRealm;
import org.keycloak.testframework.annotations.KeycloakIntegrationTest;
import org.keycloak.testframework.oauth.OAuthClient;
import org.keycloak.testframework.oauth.annotations.InjectOAuthClient;
import org.keycloak.testframework.realm.ClientBuilder;
import org.keycloak.testframework.realm.ClientConfig;
import org.keycloak.testframework.realm.ManagedRealm;
import org.keycloak.testframework.realm.RealmBuilder;
import org.keycloak.testframework.realm.RealmConfig;
import org.keycloak.testframework.realm.UserBuilder;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * RFC 6749 Section 5.2 requires OAuth token endpoints to return HTTP 400 with
 * {@code "error": "invalid_request"} for malformed requests, including requests
 * with malformed Content-Type headers.
 *
 * <p>Tests all OIDC/OAuth2 POST endpoints that declare
 * {@code @Consumes(APPLICATION_FORM_URLENCODED)} and are therefore subject to
 * Content-Type validation by the JAX-RS runtime.
 */
@KeycloakIntegrationTest
class MalformedContentTypeTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

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
    void tokenEndpoint(String contentType) throws Exception {
        assertOAuthInvalidRequest(oauth.getEndpoints().getToken(), contentType);
    }

    @ParameterizedTest
    @MethodSource("malformedContentTypes")
    void deviceAuthorizationEndpoint(String contentType) throws Exception {
        assertOAuthInvalidRequest(oauth.getEndpoints().getDeviceAuthorization(), contentType);
    }

    @ParameterizedTest
    @MethodSource("malformedContentTypes")
    void logoutEndpoint(String contentType) throws Exception {
        assertOAuthInvalidRequest(oauth.getEndpoints().getLogout(), contentType);
    }

    @ParameterizedTest
    @MethodSource("malformedContentTypes")
    void tokenRevocationEndpoint(String contentType) throws Exception {
        assertOAuthInvalidRequest(oauth.getEndpoints().getRevocation(), contentType);
    }

    @ParameterizedTest
    @MethodSource("malformedContentTypes")
    void backchannelLogoutEndpoint(String contentType) throws Exception {
        assertOAuthInvalidRequest(oauth.getEndpoints().getBackChannelLogout(), contentType);
    }

    @ParameterizedTest
    @MethodSource("malformedContentTypes")
    void parEndpoint(String contentType) throws Exception {
        assertOAuthInvalidRequest(oauth.getEndpoints().getPushedAuthorizationRequest(), contentType);
    }

    @ParameterizedTest
    @MethodSource("malformedContentTypes")
    void cibaEndpoint(String contentType) throws Exception {
        assertOAuthInvalidRequest(oauth.getEndpoints().getBackchannelAuthentication(), contentType);
    }

    /**
     * Sends a POST with form-encoded body using ONLY the specified Content-Type header.
     * The body content is irrelevant — the JAX-RS {@code @Consumes} check rejects the
     * request before any endpoint logic runs.
     */
    private void assertOAuthInvalidRequest(String endpoint, String contentType) throws IOException {
        HttpPost post = new HttpPost(endpoint);
        StringEntity entity = new StringEntity("param=value", StandardCharsets.UTF_8);
        entity.setContentType(contentType);
        post.setEntity(entity);

        try (CloseableHttpResponse response = oauth.httpClient().get().execute(post)) {
            int status = response.getStatusLine().getStatusCode();
            String body = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);

            assertEquals(400, status,
                    "Expected 400 for Content-Type '" + contentType + "' but got " + status + ": " + body);

            JsonNode json = MAPPER.readTree(body);
            assertEquals(OAuthErrorException.INVALID_REQUEST, json.get("error").asText(),
                    "Expected 'invalid_request' error for Content-Type '" + contentType + "': " + body);
        }
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
