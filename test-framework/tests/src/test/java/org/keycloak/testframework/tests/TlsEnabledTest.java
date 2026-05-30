package org.keycloak.testframework.tests;

import java.io.IOException;
import java.net.URL;

import org.keycloak.admin.client.Keycloak;
import org.keycloak.testframework.annotations.InjectAdminClient;
import org.keycloak.testframework.annotations.InjectHttpClient;
import org.keycloak.testframework.annotations.InjectKeycloakUrls;
import org.keycloak.testframework.annotations.KeycloakIntegrationTest;
import org.keycloak.testframework.https.CertificatesConfig;
import org.keycloak.testframework.https.CertificatesConfigBuilder;
import org.keycloak.testframework.https.InjectCertificates;
import org.keycloak.testframework.https.ManagedCertificates;
import org.keycloak.testframework.oauth.OAuthClient;
import org.keycloak.testframework.oauth.annotations.InjectOAuthClient;
import org.keycloak.testframework.server.KeycloakUrls;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@KeycloakIntegrationTest
public class TlsEnabledTest {

    private static final String PQC_HEADER = "X-PQC-Verified";
    private static final String PQC_NAMED_GROUP = "X25519MLKEM768";

    @InjectHttpClient
    HttpClient httpClient;

    @InjectOAuthClient
    OAuthClient oAuthClient;

    @InjectAdminClient
    Keycloak adminClient;

    @InjectCertificates(config = TlsEnabledConfig.class)
    ManagedCertificates managedCertificates;

    @InjectKeycloakUrls
    KeycloakUrls keycloakUrls;

    @Test
    public void testCertSupplier() {
        Assertions.assertNotNull(managedCertificates);

        Assertions.assertNotNull(managedCertificates.getServerKeyStorePath());
        Assertions.assertNull(managedCertificates.getServerTrustStorePath());

        Assertions.assertNotNull(managedCertificates.getClientSSLContext());
    }

    @Test
    public void testHttpClient() throws IOException {
        URL baseUrl = keycloakUrls.getBaseUrl();
        Assertions.assertEquals("https", baseUrl.getProtocol());

        HttpGet req = new HttpGet(baseUrl.toString());
        HttpResponse resp = httpClient.execute(req);
        Assertions.assertEquals(200, resp.getStatusLine().getStatusCode());
        assertPqcHeader(resp);
    }

    @Test
    public void testAdminClient() {
        adminClient.realm("default");
    }

    @Test
    public void testOAuthClient() {
        Assertions.assertTrue(oAuthClient.doWellKnownRequest().getTokenEndpoint().startsWith("https://"));
    }

    private static void assertPqcHeader(HttpResponse resp) {
        var header = resp.getFirstHeader(PQC_HEADER);
        Assertions.assertNotNull(header, PQC_HEADER + " header must be present");
        Assertions.assertEquals(PQC_NAMED_GROUP, header.getValue(),
                PQC_HEADER + " header must confirm PQC key exchange");
    }

    private static class TlsEnabledConfig implements CertificatesConfig {

        @Override
        public CertificatesConfigBuilder configure(CertificatesConfigBuilder config) {
            return config.tlsEnabled(true);
        }
    }
}
