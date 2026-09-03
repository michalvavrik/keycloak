package org.keycloak.quarkus.runtime.integration.tls;

import java.security.GeneralSecurityException;
import java.security.KeyStore;
import javax.net.ssl.TrustManagerFactory;

import jakarta.enterprise.context.ApplicationScoped;

import org.keycloak.truststore.SystemTruststoreReload;
import org.keycloak.truststore.TruststoreBuilder;

import io.quarkus.arc.properties.IfBuildProperty;
import io.quarkus.tls.TrustStoreAndTrustOptions;
import io.quarkus.tls.TrustStoreProvider;
import io.smallrye.common.annotation.Identifier;
import io.vertx.core.Vertx;
import io.vertx.core.net.TrustOptions;
import org.jboss.logging.Logger;

import static org.keycloak.config.TruststoreOptions.TRUSTSTORE_PATHS_RELOAD_PERIOD_KEY;
import static org.keycloak.quarkus.runtime.configuration.MicroProfileConfigProvider.NS_KEYCLOAK_PREFIX;

import static io.quarkus.arc.properties.StringValueMatch.REGEX;

@IfBuildProperty(name = NS_KEYCLOAK_PREFIX + TRUSTSTORE_PATHS_RELOAD_PERIOD_KEY, stringValue = ".*\\S.*", match = REGEX)
@Identifier(SystemTruststoreReload.TLS_BUCKET_NAME)
@ApplicationScoped
class SystemTruststoreProvider implements TrustStoreProvider {

    private static final Logger LOGGER = Logger.getLogger(SystemTruststoreProvider.class);

    private final SystemTruststoreReload systemTruststoreReload;

    SystemTruststoreProvider(SystemTruststoreReload systemTruststoreReload) {
        this.systemTruststoreReload = systemTruststoreReload;
    }

    @Override
    public TrustStoreAndTrustOptions getTrustStore(Vertx vertx) {
        KeyStore ks = TruststoreBuilder.getSystemTruststore();
        if (ks == null) {
            // Default deployments never build a system truststore, so this bucket stays inert. Returning null
            // leaves the TLS holder without a trust store, so VertxCertificateHolder.reload() keeps returning
            // false and no reload timer runs for it.
            LOGGER.debug("System truststore not built; serving an inert trust-store bucket");
            return null;
        }
        // The TLS registry re-invokes this provider once per reload period (it never diffs the material itself),
        // so re-merge the sources here, before serving, and the bucket reflects a source change within the same
        // period it happens instead of one period later. The check no-ops when nothing changed; the
        // CertificateUpdatedEvent that follows drives the refresh of the non-registry consumers.
        if (systemTruststoreReload.reloadIfChanged()) {
            ks = TruststoreBuilder.getSystemTruststore();
        }
        try {
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(ks);
            return new TrustStoreAndTrustOptions(ks, TrustOptions.wrap(tmf));
        } catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
        }
    }
}
