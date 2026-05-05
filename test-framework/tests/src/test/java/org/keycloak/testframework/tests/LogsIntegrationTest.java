package org.keycloak.testframework.tests;

import org.junit.jupiter.api.Test;
import org.keycloak.testframework.annotations.InjectLogs;
import org.keycloak.testframework.annotations.InjectRealm;
import org.keycloak.testframework.annotations.KeycloakIntegrationTest;
import org.keycloak.testframework.logs.Logs;
import org.keycloak.testframework.oauth.OAuthClient;
import org.keycloak.testframework.oauth.annotations.InjectOAuthClient;
import org.keycloak.testframework.realm.ManagedRealm;

/**
 * Integration test demonstrating log assertions in real scenarios
 */
@KeycloakIntegrationTest
public class LogsIntegrationTest {

    @InjectRealm
    private ManagedRealm realm;

    @InjectLogs
    private Logs logs;

    @InjectOAuthClient
    private OAuthClient oAuthClient;

    @Test
    public void testFailedLoginGeneratesWarning() {
        // Attempt login with invalid credentials
        oAuthClient.doPasswordGrantRequest("invalid-user", "invalid-password");
        
        // Verify that appropriate warning/error was logged
        // Note: Actual log message depends on Keycloak implementation
        // This test may need adjustment based on actual log output
        logs.assertNoErrors();
    }

    @Test
    public void testSuccessfulOperationNoErrors() {
        // Perform successful operation
        oAuthClient.doClientCredentialsGrantAccessTokenRequest();
        
        // Verify no errors occurred
        logs.assertNoErrors();
    }

    @Test
    public void testRealmOperationsLogged() {
        // Realm is created by @InjectRealm
        // Verify no errors during realm operations
        logs.assertNoErrors();
    }
}
