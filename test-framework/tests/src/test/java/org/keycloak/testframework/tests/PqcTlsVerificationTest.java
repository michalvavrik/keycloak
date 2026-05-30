package org.keycloak.testframework.tests;

import java.net.URL;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import org.keycloak.testframework.annotations.InjectKeycloakUrls;
import org.keycloak.testframework.annotations.KeycloakIntegrationTest;
import org.keycloak.testframework.https.CertificatesConfig;
import org.keycloak.testframework.https.CertificatesConfigBuilder;
import org.keycloak.testframework.https.InjectCertificates;
import org.keycloak.testframework.https.ManagedCertificates;
import org.keycloak.testframework.server.KeycloakUrls;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@KeycloakIntegrationTest
public class PqcTlsVerificationTest {

    private static final String PQC_NAMED_GROUP = "X25519MLKEM768";

    @InjectCertificates(config = TlsConfig.class)
    ManagedCertificates managedCertificates;

    @InjectKeycloakUrls
    KeycloakUrls keycloakUrls;

    @Test
    public void testPqcConstraintActiveAndHandshakeSucceeds() throws Exception {
        // verify jdk.tls.namedGroups system property constrains to PQC
        String[] defaultGroups = SSLContext.getDefault().getDefaultSSLParameters().getNamedGroups();
        Assertions.assertArrayEquals(new String[]{PQC_NAMED_GROUP}, defaultGroups,
                "jdk.tls.namedGroups must constrain to PQC — set JAVA_TOOL_OPTIONS=-Djdk.tls.namedGroups=" + PQC_NAMED_GROUP);

        // verify Keycloak HTTPS server accepts PQC-only handshake
        URL baseUrl = keycloakUrls.getBaseUrl();
        Assertions.assertEquals("https", baseUrl.getProtocol());

        SSLSocketFactory factory = managedCertificates.getClientSSLContext().getSocketFactory();
        try (SSLSocket socket = (SSLSocket) factory.createSocket(baseUrl.getHost(), baseUrl.getPort())) {
            socket.startHandshake();

            SSLSession session = socket.getSession();
            Assertions.assertTrue(session.isValid());
            Assertions.assertEquals("TLSv1.3", session.getProtocol());
            Assertions.assertArrayEquals(new String[]{PQC_NAMED_GROUP},
                    socket.getSSLParameters().getNamedGroups(),
                    "named groups must remain PQC-only after handshake");
        }
    }

    private static class TlsConfig implements CertificatesConfig {
        @Override
        public CertificatesConfigBuilder configure(CertificatesConfigBuilder config) {
            return config.tlsEnabled(true);
        }
    }
}
