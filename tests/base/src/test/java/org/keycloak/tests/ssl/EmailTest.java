package org.keycloak.tests.ssl;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import jakarta.mail.internet.MimeMessage;

import org.keycloak.config.TruststoreOptions;
import org.keycloak.events.Details;
import org.keycloak.events.Errors;
import org.keycloak.events.EventType;
import org.keycloak.representations.idm.EventRepresentation;
import org.keycloak.testframework.annotations.KeycloakIntegrationTest;
import org.keycloak.testframework.events.EventAssertion;
import org.keycloak.testframework.server.KeycloakServerConfig;
import org.keycloak.testframework.server.KeycloakServerConfigBuilder;
import org.keycloak.tests.utils.MailUtils;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

@KeycloakIntegrationTest(config = EmailTest.ServerConfig.class)
class EmailTest extends AbstractSslEmailTest {

    @Test
    void testVerifyEmailWithSslEnabled() throws Exception {
        oauth.openLoginForm();
        loginPage.fillLogin(user.getUsername(), "password");
        loginPage.submit();

        EventRepresentation sendEvent = events.poll();
        EventAssertion.assertSuccess(sendEvent)
                .type(EventType.SEND_VERIFY_EMAIL)
                .details(Details.USERNAME, user.getUsername())
                .details(Details.EMAIL, user.getUsername());

        assertThat("Verify email page should show instructions",
                verifyEmailPage.getFeedbackText(),
                is("You need to verify your email address to activate your account."));

        MimeMessage message = getLastReceivedMessage();
        assertThat("Email should have been received over SSL", message, is(notNullValue()));
        assertEmailContent(message, user.getUsername());

        String verifyEmailUrl = MailUtils.getPasswordResetEmailLink(message);
        driver.open(verifyEmailUrl);

        EventAssertion.assertSuccess(events.poll()).type(EventType.VERIFY_EMAIL);
        EventAssertion.assertSuccess(events.poll()).type(EventType.LOGIN);

        logoutAndVerifyReLogin();
        assertSmtpPqcTls();
    }

    private void assertSmtpPqcTls() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(realm.getBaseUrl() + "/smtp-pqc-tls-info"))
                .GET()
                .build();
        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> httpResponse = client
                .send(request, HttpResponse.BodyHandlers.ofString());
        assertThat("SMTP PQC TLS endpoint must return 200", httpResponse.statusCode(), is(200));
        String response = httpResponse.body();

        assertThat("SMTP TLS session info must be available after sending email",
                response, containsString("\"available\":true"));
        assertThat("SMTP TLS must use TLS 1.3",
                response, containsString("\"protocol\":\"TLSv1.3\""));
        assertThat("SMTP TLS session must be valid",
                response, containsString("\"valid\":true"));
        assertThat("SMTP TLS must use PQC named group X25519MLKEM768",
                response, containsString("X25519MLKEM768"));
    }

    @Test
    void testVerifyEmailWithSslWrongHostname() {
        realm.updateWithCleanup(r -> {
            r.build().getSmtpServer().put("host", "localhost.localdomain");
            return r;
        });

        oauth.openLoginForm();
        loginPage.fillLogin(user.getUsername(), "password");
        loginPage.submit();

        EventRepresentation event = events.poll();
        EventAssertion.assertError(event)
                .type(EventType.SEND_VERIFY_EMAIL_ERROR)
                .error(Errors.EMAIL_SEND_FAILED)
                .details(Details.USERNAME, user.getUsername());

        assertThat("Email should not have been received with hostname mismatch",
                getLastReceivedMessage(), is(nullValue()));
        assertThat("Error page should show email failure message",
                errorPage.getError(), is("Failed to send email, please try again later."));
    }

    static class ServerConfig implements KeycloakServerConfig {
        @Override
        public KeycloakServerConfigBuilder configure(KeycloakServerConfigBuilder config) {
            String path = resourcePath(SMTP_SERVER_CERTIFICATE);
            return config
                    .dependency("org.keycloak.tests", "keycloak-tests-custom-providers")
                    .option(TruststoreOptions.TRUSTSTORE_PATHS.getKey(), path);
        }
    }
}
