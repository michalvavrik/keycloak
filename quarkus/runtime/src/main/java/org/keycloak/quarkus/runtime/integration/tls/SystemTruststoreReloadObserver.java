package org.keycloak.quarkus.runtime.integration.tls;

import java.util.Collection;

import jakarta.enterprise.event.Observes;

import org.keycloak.models.utils.KeycloakModelUtils;
import org.keycloak.quarkus.runtime.integration.QuarkusKeycloakSessionFactory;
import org.keycloak.truststore.SystemTruststoreReload;
import org.keycloak.truststore.TruststoreReloadListener;

import io.quarkus.tls.CertificateUpdatedEvent;

class SystemTruststoreReloadObserver {

    void onSystemTruststoreUpdated(@Observes CertificateUpdatedEvent event, QuarkusKeycloakSessionFactory sessionFactory,
                                   SystemTruststoreReload systemTruststoreReload) {
        if (!SystemTruststoreReload.TLS_BUCKET_NAME.equalsIgnoreCase(event.name())) {
            return;
        }
        if (!systemTruststoreReload.hasPendingConsumerNotification()) {
            // The provider's getTrustStore already re-merged (or detected no change) for this reload period,
            // so there is nothing new to propagate. Skip early to avoid opening a transaction on no-op periods.
            return;
        }
        Collection<TruststoreReloadListener> listeners = sessionFactory
                .getProviderFactoriesStream()
                .<TruststoreReloadListener>mapMulti((providerFactory, consumer) -> {
                    if (providerFactory instanceof TruststoreReloadListener truststoreReloadListener) {
                        consumer.accept(truststoreReloadListener);
                    }
                })
                .toList();
        KeycloakModelUtils.runJobInTransaction(sessionFactory, session -> systemTruststoreReload.notifyConsumers(listeners, session));
    }
}
