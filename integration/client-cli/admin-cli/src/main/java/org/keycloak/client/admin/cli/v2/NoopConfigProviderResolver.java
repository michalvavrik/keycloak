package org.keycloak.client.admin.cli.v2;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigValue;
import org.eclipse.microprofile.config.spi.ConfigBuilder;
import org.eclipse.microprofile.config.spi.ConfigProviderResolver;
import org.eclipse.microprofile.config.spi.ConfigSource;
import org.eclipse.microprofile.config.spi.Converter;

/**
 * Minimal no-op {@link ConfigProviderResolver} so that SmallRye OpenAPI parser
 * can be used without pulling in the full SmallRye Config implementation.
 */
public class NoopConfigProviderResolver extends ConfigProviderResolver {

    public static void install() {
        ConfigProviderResolver.setInstance(new NoopConfigProviderResolver());
    }

    @Override
    public Config getConfig() {
        return getConfig(Thread.currentThread().getContextClassLoader());
    }

    @Override
    public Config getConfig(ClassLoader loader) {
        return EmptyConfig.INSTANCE;
    }

    @Override
    public ConfigBuilder getBuilder() {
        return null;
    }

    @Override
    public void registerConfig(Config config, ClassLoader classLoader) {
    }

    @Override
    public void releaseConfig(Config config) {
    }

    private enum EmptyConfig implements Config {
        INSTANCE;

        @Override
        public <T> T getValue(String propertyName, Class<T> propertyType) {
            throw new NoSuchElementException(propertyName);
        }

        @Override
        public ConfigValue getConfigValue(String propertyName) {
            return null;
        }

        @Override
        public <T> Optional<T> getOptionalValue(String propertyName, Class<T> propertyType) {
            return Optional.empty();
        }

        @Override
        public Iterable<String> getPropertyNames() {
            return List.of();
        }

        @Override
        public Iterable<ConfigSource> getConfigSources() {
            return List.of();
        }

        @Override
        public <T> Optional<Converter<T>> getConverter(Class<T> forType) {
            return Optional.empty();
        }

        @Override
        public <T> T unwrap(Class<T> type) {
            throw new IllegalArgumentException();
        }
    }
}
