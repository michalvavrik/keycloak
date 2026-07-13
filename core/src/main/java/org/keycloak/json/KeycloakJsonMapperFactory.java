package org.keycloak.json;

import java.util.Iterator;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;

/**
 * Factory for obtaining the {@link KeycloakJsonMapper} instance. The implementation is
 * discovered once via {@link ServiceLoader} and cached statically.
 */
public final class KeycloakJsonMapperFactory {

    private KeycloakJsonMapperFactory() {
    }

    public static KeycloakJsonMapper mapper() {
        return Holder.INSTANCE;
    }

    private static final class Holder {
        private static final KeycloakJsonMapper INSTANCE = load();

        private static KeycloakJsonMapper load() {
            Iterator<KeycloakJsonMapper> providers = ServiceLoader.load(KeycloakJsonMapper.class).iterator();
            while (providers.hasNext()) {
                try {
                    return providers.next();
                } catch (ServiceConfigurationError e) {
                    // provider's Jackson dependency not on classpath, try next
                }
            }
            throw new IllegalStateException("No KeycloakJsonMapper implementation found via ServiceLoader");
        }
    }
}
