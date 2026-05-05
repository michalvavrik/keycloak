package org.keycloak.testframework.logs;

import org.keycloak.testframework.annotations.InjectLogs;
import org.keycloak.testframework.injection.InstanceContext;
import org.keycloak.testframework.injection.LifeCycle;
import org.keycloak.testframework.injection.RequestedInstance;
import org.keycloak.testframework.injection.Supplier;
import org.keycloak.testframework.injection.DependenciesBuilder;
import org.keycloak.testframework.injection.Dependency;
import org.keycloak.testframework.server.KeycloakServer;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.logging.Level;

/**
 * Supplier for {@link Logs} instances. Manages the lifecycle of log capture
 * and integrates with the JBoss LogManager.
 * 
 * <p>Log capture works differently depending on the server type:</p>
 * <ul>
 *   <li><b>EmbeddedKeycloakServer</b>: Captures logs from the root logger (same JVM)</li>
 *   <li><b>DistributionKeycloakServer</b>: Captures logs from the "managed.keycloak" logger (process output)</li>
 *   <li><b>RemoteKeycloakServer</b>: Log capture not available (separate process/machine)</li>
 * </ul>
 */
public class LogsSupplier implements Supplier<Logs, InjectLogs> {

    private static final Logger LOGGER = Logger.getLogger(LogsSupplier.class);
    private static LogCapture globalLogCapture;
    private static String attachedLoggerName;

    @Override
    public Class<InjectLogs> getAnnotationClass() {
        return InjectLogs.class;
    }

    @Override
    public Class<Logs> getValueType() {
        return Logs.class;
    }

    @Override
    public List<Dependency> getDependencies(RequestedInstance<Logs, InjectLogs> instanceContext) {
        // Logs depend on the KeycloakServer to determine how to capture logs
        return DependenciesBuilder.create(KeycloakServer.class).build();
    }

    @Override
    public Logs getValue(InstanceContext<Logs, InjectLogs> instanceContext) {
        InjectLogs annotation = instanceContext.getAnnotation();
        Level minimumLevel = parseLevel(annotation.level());
        
        // Get the server to determine the logger name
        KeycloakServer server = instanceContext.getDependency(KeycloakServer.class);
        String loggerName = server.getLoggerName();
        
        if (loggerName == null) {
            // Remote server or server without log capture support
            LOGGER.warnv("Log capture not available for server type: {0}. " +
                "Logs instance will be created but will not capture any logs.", 
                server.getClass().getSimpleName());
            // Return a Logs instance with a disabled LogCapture
            LogCapture disabledCapture = new LogCapture(minimumLevel);
            disabledCapture.setEnabled(false);
            return new Logs(disabledCapture);
        }
        
        // Create or retrieve the global LogCapture handler
        if (globalLogCapture == null || !loggerName.equals(attachedLoggerName)) {
            synchronized (LogsSupplier.class) {
                if (globalLogCapture == null || !loggerName.equals(attachedLoggerName)) {
                    // Clean up old handler if logger changed
                    if (globalLogCapture != null && attachedLoggerName != null) {
                        detachFromLogger(attachedLoggerName, globalLogCapture);
                    }
                    
                    globalLogCapture = new LogCapture(minimumLevel);
                    attachToLogger(loggerName, globalLogCapture);
                    attachedLoggerName = loggerName;
                }
            }
        }
        
        Logs logs = new Logs(globalLogCapture);
        
        LOGGER.debugv("Created Logs instance with ref={0}, level={1}, logger={2}", 
            annotation.ref(), annotation.level(), loggerName);
        
        return logs;
    }

    @Override
    public LifeCycle getDefaultLifecycle() {
        return LifeCycle.GLOBAL;
    }

    @Override
    public boolean compatible(InstanceContext<Logs, InjectLogs> a, 
                             RequestedInstance<Logs, InjectLogs> b) {
        // Compatible if refs match (or both empty)
        String refA = a.getAnnotation().ref();
        String refB = b.getAnnotation().ref();
        return refA.equals(refB);
    }

    @Override
    public void onBeforeEach(InstanceContext<Logs, InjectLogs> instanceContext) {
        // Clear logs before each test method
        instanceContext.getValue().testStarted();
        LOGGER.debug("Cleared logs before test method");
    }

    @Override
    public void close(InstanceContext<Logs, InjectLogs> instanceContext) {
        // Cleanup - remove handler when supplier is closed
        if (globalLogCapture != null && attachedLoggerName != null) {
            synchronized (LogsSupplier.class) {
                if (globalLogCapture != null && attachedLoggerName != null) {
                    detachFromLogger(attachedLoggerName, globalLogCapture);
                    globalLogCapture = null;
                    attachedLoggerName = null;
                }
            }
        }
    }

    /**
     * Attaches the LogCapture handler to the specified JBoss LogManager logger
     */
    private void attachToLogger(String loggerName, LogCapture logCapture) {
        try {
            org.jboss.logmanager.Logger logger = 
                org.jboss.logmanager.Logger.getLogger(loggerName);
            
            // Remove any existing LogCapture handlers
            for (java.util.logging.Handler handler : logger.getHandlers()) {
                if (handler instanceof LogCapture) {
                    logger.removeHandler(handler);
                }
            }
            
            logger.addHandler(logCapture);
            LOGGER.infov("Attached LogCapture handler to logger ''{0}'' with level={1}", 
                loggerName, logCapture.getLevel());
        } catch (Exception e) {
            LOGGER.error("Failed to attach LogCapture handler to logger: " + loggerName, e);
            throw new RuntimeException("Failed to initialize log capture", e);
        }
    }

    /**
     * Detaches the LogCapture handler from the specified logger
     */
    private void detachFromLogger(String loggerName, LogCapture logCapture) {
        try {
            org.jboss.logmanager.Logger logger = 
                org.jboss.logmanager.Logger.getLogger(loggerName);
            logger.removeHandler(logCapture);
            LOGGER.debugv("Removed LogCapture handler from logger ''{0}''", loggerName);
        } catch (Exception e) {
            LOGGER.warn("Failed to remove LogCapture handler from logger: " + loggerName, e);
        }
    }

    private Level parseLevel(String level) {
        return switch (level.toUpperCase()) {
            case "ERROR", "SEVERE" -> Level.SEVERE;
            case "WARN", "WARNING" -> Level.WARNING;
            case "INFO" -> Level.INFO;
            case "DEBUG", "FINE" -> Level.FINE;
            case "TRACE", "FINER" -> Level.FINER;
            case "ALL" -> Level.ALL;
            default -> Level.ALL;
        };
    }
}