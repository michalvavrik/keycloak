package org.keycloak.quarkus.runtime.configuration.mappers;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.keycloak.common.Profile;
import org.keycloak.common.crypto.FipsMode;
import org.keycloak.common.util.KeystoreUtil;
import org.keycloak.config.HttpOptions;
import org.keycloak.config.ManagementOptions;
import org.keycloak.config.Option;
import org.keycloak.config.OptionBuilder;
import org.keycloak.config.OptionsUtil;
import org.keycloak.config.ProxyOptions;
import org.keycloak.config.SecurityOptions;
import org.keycloak.quarkus.runtime.Environment;
import org.keycloak.quarkus.runtime.Messages;
import org.keycloak.quarkus.runtime.cli.Picocli;
import org.keycloak.quarkus.runtime.cli.PropertyException;
import org.keycloak.quarkus.runtime.cli.command.AbstractCommand;
import org.keycloak.quarkus.runtime.configuration.Configuration;

import io.quarkus.runtime.LaunchMode;
import io.quarkus.runtime.configuration.DurationConverter;
import io.quarkus.runtime.util.ClassPathUtils;
import io.smallrye.config.ConfigSourceInterceptorContext;

import static org.keycloak.quarkus.runtime.configuration.Configuration.getOptionalKcValue;
import static org.keycloak.quarkus.runtime.configuration.mappers.PropertyMapper.fromFeature;
import static org.keycloak.quarkus.runtime.configuration.mappers.PropertyMapper.fromOption;

public final class HttpPropertyMappers implements PropertyMapperGrouping {
    private static final int MIN_MAX_THREADS = 50;

    public static final String TLS_BUCKET = "keycloak-https-server";
    public static final String TLS_PREFIX = "quarkus.tls.\"" + TLS_BUCKET + "\".";
    public static final String QUARKUS_HTTPS_SNI = TLS_PREFIX + "key-store.sni";

    private static final Option<String> SYNTHETIC_TLS_CONFIG_NAME = new OptionBuilder<>("https-tls-config-name-hidden", String.class)
            .buildTime(false)
            .synthetic()
            .build();

    enum StoreType {
        PKCS12, JKS, OTHER
    }

    enum StoreRole {
        KEY_STORE, TRUST_STORE
    }

    private static boolean isWSL() {
        var sysEnv = System.getenv();
        return sysEnv.containsKey("IS_WSL") || sysEnv.containsKey("WSL_DISTRO_NAME");
    }

    String getHttpHost(String value) {
        if (value != null) {
            return value;
        }
        // account for modes that always need to be all interfaces
        if (Environment.isRunInContainer() || LaunchMode.current().isRemoteDev()
                || isWSL()) {
            return "0.0.0.0";
        }
        // using start-dev from the cli, is not the same as LaunchMode dev or test, so we need a specific override
        if (Environment.isDevMode()) {
            return "localhost";
        }
        return null;
    }

    @Override
    public List<PropertyMapper<?>> getPropertyMappers() {
        var mappers = new ArrayList<PropertyMapper<?>>();

        mappers.addAll(List.of(
                fromOption(HttpOptions.HTTP_ENABLED)
                        .to("quarkus.http.insecure-requests")
                        .transformer(HttpPropertyMappers::getHttpEnabledTransformer)
                        .build(),
                fromOption(HttpOptions.HTTP_HOST)
                        .to("quarkus.http.host")
                        .transformer((v, c) -> getHttpHost(v))
                        .paramLabel("host")
                        .build(),
                fromOption(HttpOptions.HTTP_RELATIVE_PATH)
                        .to("quarkus.http.root-path")
                        .paramLabel("path")
                        .build(),
                fromOption(HttpOptions.HTTP_PORT)
                        .to("quarkus.http.port")
                        .paramLabel("port")
                        .build(),
                fromOption(HttpOptions.HTTPS_PORT)
                        .to("quarkus.http.ssl-port")
                        .paramLabel("port")
                        .build(),
                fromOption(HttpOptions.HTTPS_CLIENT_AUTH)
                        .to("quarkus.http.ssl.client-auth")
                        .paramLabel("auth")
                        .build()
        ));

        mappers.add(
                fromOption(SYNTHETIC_TLS_CONFIG_NAME)
                        .to("quarkus.http.tls-configuration-name")
                        .transformer((v, c) -> isHttpsEnabled() ? TLS_BUCKET : null)
                        .build()
        );

        mappers.addAll(List.of(
                fromOption(HttpOptions.HTTPS_CIPHER_SUITES)
                        .to(TLS_PREFIX + "cipher-suites")
                        .paramLabel("ciphers")
                        .build(),
                fromOption(HttpOptions.HTTPS_PROTOCOLS)
                        .to(TLS_PREFIX + "protocols")
                        .paramLabel("protocols")
                        .build(),
                fromOption(HttpOptions.HTTPS_CERTIFICATES_RELOAD_PERIOD)
                        .to(TLS_PREFIX + "reload-period")
                        .transformer(HttpPropertyMappers::transformNegativeReloadPeriod)
                        .paramLabel("reload period")
                        .build()
        ));

        mappers.addAll(List.of(
                fromOption(HttpOptions.HTTPS_CERTIFICATE_FILE)
                        .to(TLS_PREFIX + "key-store.pem.default.cert")
                        .transformer(HttpPropertyMappers::transformPath)
                        .paramLabel("file")
                        .build(),
                fromOption(HttpOptions.HTTPS_CERTIFICATE_KEY_FILE)
                        .to(TLS_PREFIX + "key-store.pem.default.key")
                        .transformer(HttpPropertyMappers::transformPath)
                        .paramLabel("file")
                        .build(),
                fromOption(HttpOptions.HTTPS_CERTIFICATE_KEY_FILE_PASSWORD)
                        .to(TLS_PREFIX + "key-store.pem.default.password")
                        .paramLabel("password")
                        .isMasked(true)
                        .build()
        ));

        addKeyStoreMappers(mappers);
        addTrustStoreMappers(mappers);

        // SNI is enabled via KeycloakHttpServerOptionsCustomizer to bypass TLS Registry's
        // overly strict validation that rejects SNI with single-alias keystores (quarkusio/quarkus#55943).
        // Keycloak uses SNI for misdirected request detection (HTTP 421), not for cert selection.
        // Once that issue is fixed, move SNI back to a config property mapper here.

        mappers.addAll(List.of(
                fromOption(HttpOptions.HTTP_MAX_QUEUED_REQUESTS)
                        .to("quarkus.thread-pool.queue-size")
                        .paramLabel("requests")
                        .build(),
                fromOption(HttpOptions.HTTP_POOL_MAX_THREADS)
                        .to("quarkus.thread-pool.max-threads")
                        .transformer(HttpPropertyMappers::resolveMaxThreads)
                        .paramLabel("threads")
                        .build(),
                fromOption(HttpOptions.HTTP_METRICS_HISTOGRAMS_ENABLED)
                        .isEnabled(MetricsPropertyMappers::metricsEnabled, MetricsPropertyMappers.METRICS_ENABLED_MSG)
                        .build(),
                fromOption(HttpOptions.HTTP_METRICS_SLOS)
                        .isEnabled(MetricsPropertyMappers::metricsEnabled, MetricsPropertyMappers.METRICS_ENABLED_MSG)
                        .paramLabel("list of buckets")
                        .build(),
                fromFeature(Profile.Feature.HTTP_OPTIMIZED_SERIALIZERS)
                        .to("quarkus.rest.jackson.optimization.enable-reflection-free-serializers")
                        .build(),
                fromOption(HttpOptions.HTTP_ACCEPT_NON_NORMALIZED_PATHS)
                        .build(),
                fromOption(HttpOptions.SHUTDOWN_TIMEOUT)
                        .to("quarkus.shutdown.timeout")
                        .paramLabel("timeout")
                        .validator(HttpPropertyMappers::validateShutdownDuration)
                        .build(),
                fromOption(HttpOptions.SHUTDOWN_DELAY)
                        .to("quarkus.shutdown.delay")
                        .paramLabel("delay")
                        .validator(HttpPropertyMappers::validateShutdownDuration)
                        .build(),
                fromOption(HttpOptions.SHUTDOWN_TIMEOUT)
                        .mapFrom(HttpOptions.SHUTDOWN_TIMEOUT)
                        .to("kc.spi-connections-infinispan--default--shutdown-timeout")
                        .paramLabel("timeout")
                        .validator(HttpPropertyMappers::validateShutdownDuration)
                        .build()
        ));

        return mappers;
    }

    private void addKeyStoreMappers(List<PropertyMapper<?>> mappers) {
        Option<File> ksFileWithDefault = HttpOptions.HTTPS_KEY_STORE_FILE
                .withRuntimeSpecificDefault(getDefaultKeystorePathValue());

        mappers.add(fromOption(ksFileWithDefault).paramLabel("file").build());

        for (StoreType type : StoreType.values()) {
            mappers.add(
                    fromOption(ksFileWithDefault)
                            .mapFrom(ksFileWithDefault, (name, fileValue, context) ->
                                    dispatchStoreFile(fileValue, HttpOptions.HTTPS_KEY_STORE_TYPE, type, StoreRole.KEY_STORE))
                            .to(TLS_PREFIX + keyStoreSubPath(type, "path"))
                            .paramLabel("file")
                            .build()
            );
        }

        mappers.add(fromOption(HttpOptions.HTTPS_KEY_STORE_PASSWORD).paramLabel("password").isMasked(true).build());

        for (StoreType type : StoreType.values()) {
            mappers.add(
                    fromOption(HttpOptions.HTTPS_KEY_STORE_PASSWORD)
                            .mapFrom(HttpOptions.HTTPS_KEY_STORE_PASSWORD, (name, passwordValue, context) ->
                                    dispatchStoreProperty(passwordValue, HttpOptions.HTTPS_KEY_STORE_FILE, HttpOptions.HTTPS_KEY_STORE_TYPE, type, StoreRole.KEY_STORE))
                            .to(TLS_PREFIX + keyStoreSubPath(type, "password"))
                            .paramLabel("password")
                            .isMasked(true)
                            .build()
            );
        }

        mappers.add(
                fromOption(HttpOptions.HTTPS_KEY_STORE_TYPE)
                        .mapFrom(SecurityOptions.FIPS_MODE, HttpPropertyMappers::resolveKeyStoreType)
                        .to(TLS_PREFIX + "key-store.other.type")
                        .transformer(HttpPropertyMappers::filterOtherStoreType)
                        .paramLabel("type")
                        .build()
        );
    }

    private void addTrustStoreMappers(List<PropertyMapper<?>> mappers) {
        mappers.add(fromOption(HttpOptions.HTTPS_TRUST_STORE_FILE).paramLabel("file").build());

        for (StoreType type : StoreType.values()) {
            mappers.add(
                    fromOption(HttpOptions.HTTPS_TRUST_STORE_FILE)
                            .mapFrom(HttpOptions.HTTPS_TRUST_STORE_FILE, (name, fileValue, context) ->
                                    dispatchStoreFile(fileValue, HttpOptions.HTTPS_TRUST_STORE_TYPE, type, StoreRole.TRUST_STORE))
                            .to(TLS_PREFIX + trustStoreSubPath(type, "path"))
                            .paramLabel("file")
                            .build()
            );
        }

        mappers.add(fromOption(HttpOptions.HTTPS_TRUST_STORE_PASSWORD).paramLabel("password").isMasked(true).build());

        for (StoreType type : StoreType.values()) {
            mappers.add(
                    fromOption(HttpOptions.HTTPS_TRUST_STORE_PASSWORD)
                            .mapFrom(HttpOptions.HTTPS_TRUST_STORE_PASSWORD, (name, passwordValue, context) ->
                                    dispatchStoreProperty(passwordValue, HttpOptions.HTTPS_TRUST_STORE_FILE, HttpOptions.HTTPS_TRUST_STORE_TYPE, type, StoreRole.TRUST_STORE))
                            .to(TLS_PREFIX + trustStoreSubPath(type, "password"))
                            .paramLabel("password")
                            .isMasked(true)
                            .build()
            );
        }

        mappers.add(
                fromOption(HttpOptions.HTTPS_TRUST_STORE_TYPE)
                        .mapFrom(SecurityOptions.FIPS_MODE, HttpPropertyMappers::resolveKeyStoreType)
                        .to(TLS_PREFIX + "trust-store.other.type")
                        .transformer(HttpPropertyMappers::filterOtherStoreType)
                        .paramLabel("type")
                        .build()
        );
    }

    static String keyStoreSubPath(StoreType type, String property) {
        return switch (type) {
            case PKCS12 -> "key-store.p12." + property;
            case JKS -> "key-store.jks." + property;
            case OTHER -> "key-store.other." + property;
        };
    }

    static String trustStoreSubPath(StoreType type, String property) {
        return switch (type) {
            case PKCS12 -> "trust-store.p12." + property;
            case JKS -> "trust-store.jks." + property;
            case OTHER -> "trust-store.other." + property;
        };
    }

    static String dispatchStoreFile(String fileValue, Option<String> storeTypeOption, StoreType targetType, StoreRole role) {
        if (fileValue == null) {
            return null;
        }
        // PEM takes precedence over keystore when both are configured
        if (role == StoreRole.KEY_STORE && getOptionalKcValue(HttpOptions.HTTPS_CERTIFICATE_FILE.getKey()).isPresent()) {
            return null;
        }
        String explicitType = getOptionalKcValue(storeTypeOption.getKey()).orElse(null);
        StoreType detected = resolveStoreType(explicitType, fileValue, storeTypeOption, role);
        return detected == targetType ? ClassPathUtils.toResourceName(Path.of(fileValue)) : null;
    }

    static String dispatchStoreProperty(String value, Option<File> storeFileOption, Option<String> storeTypeOption,
            StoreType targetType, StoreRole role) {
        if (value == null) {
            return null;
        }
        if (role == StoreRole.KEY_STORE && getOptionalKcValue(HttpOptions.HTTPS_CERTIFICATE_FILE.getKey()).isPresent()) {
            return null;
        }
        String filePath = getOptionalKcValue(storeFileOption.getKey()).orElse(null);
        if (filePath == null) {
            return null;
        }
        String explicitType = getOptionalKcValue(storeTypeOption.getKey()).orElse(null);
        StoreType detected = resolveStoreType(explicitType, filePath, storeTypeOption, role);
        return detected == targetType ? value : null;
    }

    private static StoreType resolveStoreType(String explicitType, String filePath, Option<String> storeTypeOption,
            StoreRole role) {
        StoreType type = detectStoreType(explicitType, filePath, role);
        if (type == null) {
            return StoreType.PKCS12;
        }
        return type;
    }

    static StoreType detectStoreType(String explicitType, String filePath, StoreRole role) {
        if (explicitType != null) {
            return switch (explicitType.toUpperCase()) {
                case "PKCS12", "P12", "PFX" -> StoreType.PKCS12;
                case "JKS" -> StoreType.JKS;
                default -> StoreType.OTHER;
            };
        }
        if (filePath != null) {
            Optional<KeystoreUtil.KeystoreFormat> format = KeystoreUtil.getKeystoreFormat(filePath);
            if (format.isPresent()) {
                return switch (format.get()) {
                    case PKCS12 -> StoreType.PKCS12;
                    case JKS -> StoreType.JKS;
                    case BCFKS -> StoreType.OTHER;
                };
            }
            String lower = filePath.toLowerCase();
            if (role == StoreRole.KEY_STORE && lower.endsWith(".keystore")) {
                return StoreType.JKS;
            }
            if (role == StoreRole.TRUST_STORE && lower.endsWith(".truststore")) {
                return StoreType.JKS;
            }
            return null;
        }
        return StoreType.PKCS12;
    }

    static String filterOtherStoreType(String value, ConfigSourceInterceptorContext context) {
        if (value == null) {
            return null;
        }
        return detectStoreType(value, null, null) == StoreType.OTHER ? value : null;
    }

    @Override
    public void validateConfig(Picocli picocli) {
        if (picocli.getParsedCommand().filter(AbstractCommand::isServing).isPresent()) {
            boolean enabled = isHttpEnabled(getOptionalKcValue(HttpOptions.HTTP_ENABLED.getKey()).orElse(null));
            if (!enabled && !isHttpsEnabled()) {
                throw new PropertyException(Messages.httpsConfigurationNotSet());
            }
        }
        validateStoreType(HttpOptions.HTTPS_KEY_STORE_FILE, HttpOptions.HTTPS_KEY_STORE_TYPE, StoreRole.KEY_STORE);
        validateStoreType(HttpOptions.HTTPS_TRUST_STORE_FILE, HttpOptions.HTTPS_TRUST_STORE_TYPE, StoreRole.TRUST_STORE);
        validateStoreType(ManagementOptions.HTTPS_MANAGEMENT_KEY_STORE_FILE, ManagementOptions.HTTPS_MANAGEMENT_KEY_STORE_TYPE, StoreRole.KEY_STORE);
        validateStoreType(ManagementOptions.HTTPS_MANAGEMENT_TRUST_STORE_FILE, ManagementOptions.HTTPS_MANAGEMENT_TRUST_STORE_TYPE, StoreRole.TRUST_STORE);
        validateTrustStorePassword(HttpOptions.HTTPS_TRUST_STORE_FILE, HttpOptions.HTTPS_TRUST_STORE_PASSWORD, HttpOptions.HTTPS_TRUST_STORE_TYPE);
        validateTrustStorePassword(ManagementOptions.HTTPS_MANAGEMENT_TRUST_STORE_FILE, ManagementOptions.HTTPS_MANAGEMENT_TRUST_STORE_PASSWORD, ManagementOptions.HTTPS_MANAGEMENT_TRUST_STORE_TYPE);
        validateNoFipsPem();
    }

    private static void validateTrustStorePassword(Option<File> trustStoreFileOption, Option<String> passwordOption,
            Option<String> typeOption) {
        String trustStoreFile = getOptionalKcValue(trustStoreFileOption.getKey()).orElse(null);
        if (trustStoreFile == null) {
            return;
        }
        String explicitType = getOptionalKcValue(typeOption.getKey()).orElse(null);
        StoreType type = detectStoreType(explicitType, trustStoreFile, StoreRole.TRUST_STORE);
        if (type == StoreType.PKCS12 || type == StoreType.JKS) {
            String password = getOptionalKcValue(passwordOption.getKey()).orElse(null);
            if (password == null) {
                throw new PropertyException("No trust store password provided. Set the '%s' option."
                        .formatted(passwordOption.getKey()));
            }
        }
    }

    private static void validateNoFipsPem() {
        String fipsMode = getOptionalKcValue(SecurityOptions.FIPS_MODE.getKey()).orElse(null);
        if (fipsMode == null || FipsMode.DISABLED.toString().equals(fipsMode)) {
            return;
        }
        if (getOptionalKcValue(HttpOptions.HTTPS_CERTIFICATE_FILE.getKey()).isPresent()
                || getOptionalKcValue(ManagementOptions.HTTPS_MANAGEMENT_CERTIFICATE_FILE.getKey()).isPresent()) {
            throw new PropertyException(
                    "PEM certificates are not supported in FIPS mode. Use a BCFKS keystore with the 'https-key-store-file' option instead.");
        }
    }

    private static void validateStoreType(Option<File> storeFileOption, Option<String> storeTypeOption, StoreRole role) {
        String filePath = getOptionalKcValue(storeFileOption.getKey()).orElse(null);
        if (filePath == null) {
            return;
        }
        String explicitType = getOptionalKcValue(storeTypeOption.getKey()).orElse(null);
        StoreType detected = detectStoreType(explicitType, filePath, role);
        if (detected == null) {
            throw new PropertyException("Unable to determine '%s' automatically. Adjust the file extension or specify the property."
                    .formatted(storeTypeOption.getKey()));
        }
        if (detected == StoreType.OTHER && explicitType == null) {
            throw new PropertyException("Unable to determine '%s' automatically. Adjust the file extension or specify the property."
                    .formatted(storeTypeOption.getKey()));
        }
    }

    public static boolean isHttpsEnabled() {
        Optional<String> certFile = getOptionalKcValue(HttpOptions.HTTPS_CERTIFICATE_FILE.getKey());
        Optional<String> keystoreFile = getOptionalKcValue(HttpOptions.HTTPS_KEY_STORE_FILE.getKey());
        if (keystoreFile.isEmpty() && getDefaultKeystorePathValue() != null) {
            return true;
        }
        return certFile.isPresent() || keystoreFile.isPresent();
    }

    public static boolean isSniEnabled() {
        return isHttpsEnabled() && Configuration.getConfigValue(ProxyOptions.PROXY_HEADERS).getValue() == null;
    }

    private static String transformPath(String value, ConfigSourceInterceptorContext context) {
        return value == null ? value : ClassPathUtils.toResourceName(Path.of(value));
    }

    private static String getHttpEnabledTransformer(String value, ConfigSourceInterceptorContext context) {
        return isHttpEnabled(value) ? "enabled" : "disabled";
    }

    static String transformNegativeReloadPeriod(String value, ConfigSourceInterceptorContext context) {
        // -1 means no reload
        return "-1".equals(value) ? null : value;
    }

    private static boolean isHttpEnabled(String value) {
        if (Environment.isDevMode() || org.keycloak.common.util.Environment.isNonServerMode()) {
            return true;
        }
        return Boolean.parseBoolean(value);
    }

    static File getDefaultKeystorePathValue() {
        return Environment.getHomeDir().map(f -> Paths.get(f, "conf", "server.keystore").toFile()).filter(File::exists)
                .orElse(null);
    }

    static String resolveKeyStoreType(String value,
            ConfigSourceInterceptorContext configSourceInterceptorContext) {
        if (FipsMode.STRICT.toString().equals(value)) {
            return "BCFKS";
        }
        return null;
    }

    private static String resolveMaxThreads(String value,
            ConfigSourceInterceptorContext configSourceInterceptorContext) {
        if (value == null) {
            return String.valueOf(Math.max(MIN_MAX_THREADS, 4 * Runtime.getRuntime().availableProcessors()));
        }
        return value;
    }

    private static void validateShutdownDuration(String value) {
        try {
            Duration duration = DurationConverter.parseDuration(value);
            if (duration == null || duration.isNegative()) {
                throw new PropertyException("Invalid duration '%s'. Duration must be zero or positive.".formatted(value));
            }
        } catch (IllegalArgumentException e) {
            throw new PropertyException("Invalid duration format '%s'. %s".formatted(value, OptionsUtil.DURATION_DESCRIPTION));
        }
    }
}
