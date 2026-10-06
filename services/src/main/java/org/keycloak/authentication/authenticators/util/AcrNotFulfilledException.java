package org.keycloak.authentication.authenticators.util;

import org.keycloak.authentication.AuthenticationFlowError;
import org.keycloak.authentication.AuthenticationFlowException;
import org.keycloak.services.messages.Messages;

/**
 * Thrown when an authentication finishes without reaching a forced level of authentication.
 */
public class AcrNotFulfilledException extends AuthenticationFlowException {

    public AcrNotFulfilledException(String eventDetails) {
        super(AuthenticationFlowError.GENERIC_AUTHENTICATION_ERROR, eventDetails, Messages.ACR_NOT_FULFILLED);
    }
}
