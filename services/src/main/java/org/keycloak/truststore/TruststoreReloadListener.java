package org.keycloak.truststore;

import org.keycloak.models.KeycloakSession;
import org.keycloak.provider.ProviderFactory;

/**
 * {@link ProviderFactory} that are using the system truststore should implement this interface
 * in order to stay up to date.
 */
public interface TruststoreReloadListener {

    void truststoreReloaded(KeycloakSession session);
}
