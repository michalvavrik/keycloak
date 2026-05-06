package org.keycloak.testframework.server;

import java.util.concurrent.TimeoutException;
import java.util.logging.Handler;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;

import org.keycloak.Keycloak;
import org.keycloak.common.Version;
import org.keycloak.testframework.util.MavenProjectUtil;

public class EmbeddedKeycloakServer implements KeycloakServer {

    private Keycloak keycloak;
    private boolean tlsEnabled = false;
    private Handler logCaptureHandler;

    @Override
    public void start(KeycloakServerConfigBuilder keycloakServerConfigBuilder, boolean tlsEnabled) {
        Keycloak.Builder builder = Keycloak.builder().setVersion(Version.VERSION);
        this.tlsEnabled = tlsEnabled;

        for(KeycloakDependency dependency : keycloakServerConfigBuilder.toDependencies()) {
            KeycloakDependency updatedDependency = MavenProjectUtil.updateDependencyDetails(dependency);
            builder.addDependency(updatedDependency.getGroupId(), updatedDependency.getArtifactId(), updatedDependency.getVersion());
        }

        Logs logs = keycloakServerConfigBuilder.getLogs(0);
        if (logs != null) {
            installLogCaptureHandler(logs);
        }
        keycloak = builder.start(keycloakServerConfigBuilder.toArgs());
        ReadinessProbe.waitUntilReady(this);
    }

    @Override
    public void stop() {
        removeLogCaptureHandler();
        try {
            keycloak.stop();
        } catch (TimeoutException e) {
            throw new RuntimeException(e);
        }
    }

    private void installLogCaptureHandler(Logs logs) {
        logCaptureHandler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                if (record != null) {
                    logs.add(LogEntry.fromLogRecord(record));
                }
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        LogManager.getLogManager().getLogger("").addHandler(logCaptureHandler);
    }

    private void removeLogCaptureHandler() {
        if (logCaptureHandler != null) {
            LogManager.getLogManager().getLogger("").removeHandler(logCaptureHandler);
            logCaptureHandler = null;
        }
    }

    @Override
    public String getBaseUrl() {
        if (tlsEnabled) {
            return "https://localhost:8443";
        } else {
            return "http://localhost:8080";
        }
    }

    @Override
    public String getManagementBaseUrl() {
        if (tlsEnabled) {
            return "https://localhost:9001";
        } else {
            return "http://localhost:9001";
        }
    }
}
