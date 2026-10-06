package org.keycloak.tests.forms;

import java.util.List;
import java.util.Map;

import org.keycloak.OAuthErrorException;
import org.keycloak.authentication.authenticators.browser.OTPFormAuthenticatorFactory;
import org.keycloak.authentication.authenticators.browser.PasswordFormFactory;
import org.keycloak.authentication.authenticators.browser.UsernameFormFactory;
import org.keycloak.authentication.authenticators.conditional.ConditionalLoaAuthenticator;
import org.keycloak.authentication.authenticators.conditional.ConditionalLoaAuthenticatorFactory;
import org.keycloak.authentication.authenticators.conditional.ConditionalUserConfiguredAuthenticatorFactory;
import org.keycloak.events.Details;
import org.keycloak.events.Errors;
import org.keycloak.models.AuthenticationExecutionModel.Requirement;
import org.keycloak.models.Constants;
import org.keycloak.protocol.oidc.OIDCLoginProtocol;
import org.keycloak.representations.ClaimsRepresentation;
import org.keycloak.representations.IDToken;
import org.keycloak.testframework.annotations.InjectEvents;
import org.keycloak.testframework.annotations.InjectUser;
import org.keycloak.testframework.annotations.KeycloakIntegrationTest;
import org.keycloak.testframework.events.EventAssertion;
import org.keycloak.testframework.events.Events;
import org.keycloak.testframework.injection.LifeCycle;
import org.keycloak.testframework.oauth.OAuthClient;
import org.keycloak.testframework.oauth.annotations.InjectOAuthClient;
import org.keycloak.testframework.realm.ManagedUser;
import org.keycloak.testframework.remote.runonserver.InjectRunOnServer;
import org.keycloak.testframework.remote.runonserver.RunOnServerClient;
import org.keycloak.testframework.ui.annotations.InjectPage;
import org.keycloak.testframework.ui.annotations.InjectWebDriver;
import org.keycloak.testframework.ui.page.ErrorPage;
import org.keycloak.testframework.ui.page.LoginUsernamePage;
import org.keycloak.testframework.ui.page.PasswordPage;
import org.keycloak.testframework.ui.webdriver.ManagedWebDriver;
import org.keycloak.tests.common.BasicUserConfig;
import org.keycloak.testsuite.util.FlowUtil;
import org.keycloak.testsuite.util.oauth.AccessTokenResponse;
import org.keycloak.testsuite.util.oauth.AuthorizationEndpointResponse;
import org.keycloak.testsuite.util.oauth.LoginUrlBuilder;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@KeycloakIntegrationTest
public class LevelOfAssuranceEssentialAcrTest {

    private static final String FLOW_ALIAS = "step-up-flow";

    @InjectUser(config = BasicUserConfig.class)
    ManagedUser user;

    @InjectWebDriver(lifecycle = LifeCycle.METHOD)
    ManagedWebDriver driver;

    @InjectOAuthClient
    OAuthClient oauth;

    @InjectRunOnServer
    RunOnServerClient runOnServer;

    @InjectEvents
    Events events;

    @InjectPage
    LoginUsernamePage loginUsernamePage;

    @InjectPage
    PasswordPage passwordPage;

    @InjectPage
    ErrorPage errorPage;

    @BeforeEach
    public void setUpFlow() {
        runOnServer.run(session -> FlowUtil.inCurrentRealm(session).copyBrowserFlow(FLOW_ALIAS));
        runOnServer.run(session -> FlowUtil.inCurrentRealm(session)
                .selectFlow(FLOW_ALIAS)
                .inForms(forms -> forms
                        .clear()
                        .addAuthenticatorExecution(Requirement.REQUIRED, UsernameFormFactory.PROVIDER_ID)
                        .addSubFlowExecution(Requirement.CONDITIONAL, subflow -> subflow
                                .addAuthenticatorExecution(Requirement.REQUIRED, ConditionalLoaAuthenticatorFactory.PROVIDER_ID,
                                        config -> {
                                            config.getConfig().put(ConditionalLoaAuthenticator.LEVEL, "1");
                                            config.getConfig().put(ConditionalLoaAuthenticator.MAX_AGE, String.valueOf(ConditionalLoaAuthenticator.DEFAULT_MAX_AGE));
                                        })
                                .addAuthenticatorExecution(Requirement.REQUIRED, PasswordFormFactory.PROVIDER_ID)
                        )
                        .addSubFlowExecution(Requirement.CONDITIONAL, subflow -> subflow
                                .addAuthenticatorExecution(Requirement.REQUIRED, ConditionalLoaAuthenticatorFactory.PROVIDER_ID,
                                        config -> {
                                            config.getConfig().put(ConditionalLoaAuthenticator.LEVEL, "2");
                                            config.getConfig().put(ConditionalLoaAuthenticator.MAX_AGE, String.valueOf(ConditionalLoaAuthenticator.DEFAULT_MAX_AGE));
                                        })
                                .addAuthenticatorExecution(Requirement.REQUIRED, ConditionalUserConfiguredAuthenticatorFactory.PROVIDER_ID)
                                .addAuthenticatorExecution(Requirement.REQUIRED, OTPFormAuthenticatorFactory.PROVIDER_ID)
                        )
                )
                .defineAsBrowserFlow()
        );
    }

    @Test
    public void essentialAcrNotReachedWithSsoFails() {
        loginWithUsernamePassword();

        // the user has no OTP, so level 2 can't be reached and no step is interactive
        loginFormWithAcrClaim(true, "2").open();

        assertAcrNotFulfilled(1);
    }

    @Test
    public void essentialAcrNotReachedWithSsoAndPromptNoneFails() {
        loginWithUsernamePassword();

        loginFormWithAcrClaim(true, "2").prompt(OIDCLoginProtocol.PROMPT_VALUE_NONE).open();

        AuthorizationEndpointResponse response = oauth.parseLoginResponse();
        assertEquals(OAuthErrorException.UNMET_AUTHENTICATION_REQUIREMENTS, response.getError());
        assertNull(response.getErrorDescription());
        assertNull(response.getCode());
        assertAcrNotFulfilledEvent(1);
        assertNull(events.poll());
    }

    @Test
    public void essentialAcrNotReachedWithoutSsoFails() {
        // the user is authenticated outside of level subflows and can't reach level 2 without OTP
        configureFlowWithFirstFactorOutsideLevelSubflows(false);

        loginFormWithAcrClaim(true, "2").open();
        fillUsernameAndPassword();

        assertAcrNotFulfilled(Constants.NO_LOA);
    }

    @Test
    public void essentialAcrNotReachedWithoutLevelConditionEvaluatedFails() {
        // the level 2 subflow is disabled by the condition evaluated before the level condition, so no level condition is evaluated at all
        configureFlowWithFirstFactorOutsideLevelSubflows(true);

        loginFormWithAcrClaim(true, "2").open();
        fillUsernameAndPassword();

        assertAcrNotFulfilled(Constants.NO_LOA);
    }

    @Test
    public void essentialAcrNotReachedWithSsoAndPromptLoginFails() {
        configureFlowWithFirstFactorOutsideLevelSubflows(true);
        loginWithUsernamePassword();

        // prompt=login skips the step-up branch of the cookie authenticator, and the user is already known so only the password is asked
        loginFormWithAcrClaim(true, "2").prompt(OIDCLoginProtocol.PROMPT_VALUE_LOGIN).open();
        passwordPage.fillPassword(user.getPassword());
        passwordPage.submit();

        assertAcrNotFulfilled(Constants.NO_LOA);
    }

    @Test
    public void optionalAcrNotReachedWithSsoSucceeds() {
        loginWithUsernamePassword();

        loginFormWithAcrClaim(false, "2").open();

        driver.waiting().waitForOAuthCallback();
        EventAssertion.expectLoginSuccess(events.poll());
        AccessTokenResponse tokenResponse = oauth.doAccessTokenRequest(oauth.parseLoginResponse().getCode());
        assertEquals("1", oauth.parseToken(tokenResponse.getIdToken(), IDToken.class).getAcr());
    }

    private void loginWithUsernamePassword() {
        oauth.openLoginForm();
        fillUsernameAndPassword();
        assertTrue(oauth.parseLoginResponse().isSuccess());
        EventAssertion.expectLoginSuccess(events.poll());
    }

    private void fillUsernameAndPassword() {
        loginUsernamePage.fillLoginWithUsernameOnly(user.getUsername());
        loginUsernamePage.submit();
        passwordPage.fillPassword(user.getPassword());
        passwordPage.submit();
    }

    /**
     * Username and password forms outside of any level subflow, followed by a conditional level 2 subflow with the OTP form.
     * The "user configured" condition disables the level 2 subflow for a user without OTP. When it is placed before the level
     * condition, the level condition is never evaluated.
     */
    private void configureFlowWithFirstFactorOutsideLevelSubflows(boolean userConfiguredConditionFirst) {
        runOnServer.run(session -> FlowUtil.inCurrentRealm(session)
                .selectFlow(FLOW_ALIAS)
                .inForms(forms -> forms
                        .clear()
                        .addAuthenticatorExecution(Requirement.REQUIRED, UsernameFormFactory.PROVIDER_ID)
                        .addAuthenticatorExecution(Requirement.REQUIRED, PasswordFormFactory.PROVIDER_ID)
                        .addSubFlowExecution(Requirement.CONDITIONAL, subflow -> {
                            if (userConfiguredConditionFirst) {
                                subflow.addAuthenticatorExecution(Requirement.REQUIRED, ConditionalUserConfiguredAuthenticatorFactory.PROVIDER_ID);
                            }
                            subflow.addAuthenticatorExecution(Requirement.REQUIRED, ConditionalLoaAuthenticatorFactory.PROVIDER_ID,
                                    config -> {
                                        config.getConfig().put(ConditionalLoaAuthenticator.LEVEL, "2");
                                        config.getConfig().put(ConditionalLoaAuthenticator.MAX_AGE, String.valueOf(ConditionalLoaAuthenticator.DEFAULT_MAX_AGE));
                                    });
                            if (!userConfiguredConditionFirst) {
                                subflow.addAuthenticatorExecution(Requirement.REQUIRED, ConditionalUserConfiguredAuthenticatorFactory.PROVIDER_ID);
                            }
                            subflow.addAuthenticatorExecution(Requirement.REQUIRED, OTPFormAuthenticatorFactory.PROVIDER_ID);
                        })
                )
        );
    }

    private void assertAcrNotFulfilled(int fulfilledLevel) {
        errorPage.assertCurrent();
        assertThat(errorPage.getError(), is("Authentication requirements not fulfilled"));
        assertAcrNotFulfilledEvent(fulfilledLevel);
    }

    private void assertAcrNotFulfilledEvent(int fulfilledLevel) {
        EventAssertion.expectLoginError(events.poll())
                .error(Errors.GENERIC_AUTHENTICATION_ERROR)
                .details(Details.AUTHENTICATION_ERROR_DETAIL, "Forced level of authentication did not meet the requirements. Requested level: 2, Fulfilled level: " + fulfilledLevel);
    }

    private LoginUrlBuilder loginFormWithAcrClaim(boolean essential, String acr) {
        ClaimsRepresentation.ClaimValue<String> acrClaim = new ClaimsRepresentation.ClaimValue<>();
        acrClaim.setEssential(essential);
        acrClaim.setValues(List.of(acr));

        ClaimsRepresentation claims = new ClaimsRepresentation();
        claims.setIdTokenClaims(Map.of(IDToken.ACR, acrClaim));

        return oauth.loginForm().claims(claims);
    }
}
