package org.keycloak.testframework.server;

import org.keycloak.testframework.config.Config;

public interface KeycloakServer {

    void start(KeycloakServerConfigBuilder keycloakServerConfigBuilder, boolean tlsEnabled);

    void stop();

    String getBaseUrl();

    String getManagementBaseUrl();

    /**
     * Returns the logger name used by this server implementation for log output.
     * Used by the log capture system to attach handlers to the appropriate logger.
     * 
     * @return the logger name, or null if logs are not available for capture
     */
    default String getLoggerName() {
        return null;
    }

    static boolean getDependencyHotDeployEnabled() {
        return Boolean.parseBoolean(Config.getValueTypeConfig(KeycloakServer.class, "hot.deploy", "false", String.class));
    }

}