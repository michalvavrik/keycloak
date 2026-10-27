package org.keycloak.tests.federation.kerberos;

import java.util.List;

import org.keycloak.common.constants.KerberosConstants;
import org.keycloak.common.util.MultivaluedHashMap;
import org.keycloak.events.Details;
import org.keycloak.events.Errors;
import org.keycloak.events.EventType;
import org.keycloak.models.LDAPConstants;
import org.keycloak.representations.idm.ComponentRepresentation;
import org.keycloak.storage.UserStorageProvider;
import org.keycloak.testframework.annotations.InjectEvents;
import org.keycloak.testframework.annotations.InjectRealm;
import org.keycloak.testframework.annotations.KeycloakIntegrationTest;
import org.keycloak.testframework.events.EventAssertion;
import org.keycloak.testframework.events.Events;
import org.keycloak.testframework.oauth.OAuthClient;
import org.keycloak.testframework.oauth.annotations.InjectOAuthClient;
import org.keycloak.testframework.realm.ManagedRealm;
import org.keycloak.testframework.ui.annotations.InjectPage;
import org.keycloak.testframework.ui.page.LoginPage;
import org.keycloak.testframework.util.ApiUtil;
import org.keycloak.tests.utils.KerberosUtils;
import org.keycloak.testsuite.util.oauth.AccessTokenResponse;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@KeycloakIntegrationTest
public class KerberosRealmAllowlistTest {

    private static final String USERNAME = "jduke@OTHER.INVALID";
    // The escaped separator makes JAAS ignore the realm Keycloak checked and use the JVM default realm instead
    private static final String ESCAPED_USERNAME = "jduke\\@KEYCLOAK.ORG";

    @InjectRealm
    ManagedRealm managedRealm;

    @InjectOAuthClient
    OAuthClient oauth;

    @InjectEvents
    Events events;

    @InjectPage
    LoginPage loginPage;

    // No KDC is reachable from the test. A login with a realm the provider does not allow must fail as for an unknown
    // user instead of ending with an unexpected error after the provider tried to locate a KDC for that realm
    @Test
    public void formLoginWithRealmNotAllowedFailsWithoutContactingKdc() {
        KerberosUtils.assumeKerberosSupportExpected();
        addKerberosProvider();

        for (String username : List.of(USERNAME, ESCAPED_USERNAME)) {
            oauth.openLoginForm();
            loginPage.fillLogin(username, "theduke");
            loginPage.submit();

            loginPage.assertCurrent();
            Assertions.assertEquals("Invalid username or password.", loginPage.getUsernameInputError());
            EventAssertion.expectLoginError(events.poll()).error(Errors.USER_NOT_FOUND).details(Details.USERNAME, username);
        }
        Assertions.assertTrue(managedRealm.admin().users().search("jduke", false).isEmpty(), "No user must be imported");
    }

    @Test
    public void directGrantWithRealmNotAllowedFailsWithoutContactingKdc() {
        KerberosUtils.assumeKerberosSupportExpected();
        addKerberosProvider();

        AccessTokenResponse response = oauth.doPasswordGrantRequest(USERNAME, "theduke");

        Assertions.assertEquals(400, response.getStatusCode());
        Assertions.assertEquals("invalid_grant", response.getError());
        Assertions.assertEquals("Invalid user credentials", response.getErrorDescription());
        EventAssertion.assertError(events.poll()).type(EventType.LOGIN_ERROR).error(Errors.USER_NOT_FOUND).details(Details.USERNAME, USERNAME);
        Assertions.assertTrue(managedRealm.admin().users().search("jduke", false).isEmpty(), "No user must be imported");
    }

    private void addKerberosProvider() {
        ComponentRepresentation provider = new ComponentRepresentation();
        provider.setName("kerberos");
        provider.setProviderId("kerberos");
        provider.setProviderType(UserStorageProvider.class.getName());
        provider.setConfig(new MultivaluedHashMap<>());
        provider.getConfig().putSingle("priority", "0");
        provider.getConfig().putSingle(KerberosConstants.KERBEROS_REALM, "KEYCLOAK.ORG");
        provider.getConfig().putSingle(KerberosConstants.SERVER_PRINCIPAL, "HTTP/localhost@KEYCLOAK.ORG");
        provider.getConfig().putSingle(KerberosConstants.KEYTAB, "/etc/krb5.keytab");
        provider.getConfig().putSingle(KerberosConstants.ALLOW_PASSWORD_AUTHENTICATION, "true");
        provider.getConfig().putSingle(LDAPConstants.EDIT_MODE, UserStorageProvider.EditMode.READ_ONLY.name());

        String id = ApiUtil.getCreatedId(managedRealm.admin().components().add(provider));
        managedRealm.cleanup().add(realm -> realm.components().component(id).remove());
    }
}
