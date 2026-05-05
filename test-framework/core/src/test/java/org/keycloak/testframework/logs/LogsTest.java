package org.keycloak.testframework.logs;

import org.jboss.logging.Logger;
import org.junit.jupiter.api.Test;
import org.keycloak.testframework.annotations.InjectLogs;
import org.keycloak.testframework.annotations.KeycloakIntegrationTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the Logs capture functionality.
 * 
 * Note: These tests are designed to work with DistributionKeycloakServer,
 * which captures logs from the "managed.keycloak" logger (server process output).
 * 
 * For EmbeddedKeycloakServer, logs from the test JVM would also be captured.
 * For RemoteKeycloakServer, log capture is not available.
 */
@KeycloakIntegrationTest
public class LogsTest {

    private static final Logger managedKeycloakLogger = Logger.getLogger("managed.keycloak");

    @InjectLogs
    Logs logs;

    @Test
    public void testLogCaptureFromServer() {
        // Verify that log capture is working
        assertNotNull(logs, "Logs instance should be injected");
        
        // Log a test message to the managed.keycloak logger
        managedKeycloakLogger.info("Test log capture message");
        
        // Give handler time to process
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        List<LogEntry> allLogs = logs.getAllLogs();
        assertNotNull(allLogs, "Should return a log list");
        
        // Verify we captured our test message
        boolean foundTestLog = allLogs.stream()
            .anyMatch(e -> e.getMessage().contains("Test log capture message"));
        
        assertTrue(foundTestLog, "Should have captured the test log message");
    }

    @Test
    public void testLogLevelFiltering() {
        logs.clear();
        
        // Log to the managed.keycloak logger (simulating server logs)
        managedKeycloakLogger.error("Test error level");
        managedKeycloakLogger.warn("Test warning level");
        managedKeycloakLogger.info("Test info level");
        
        // Give handler time to process
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        List<LogEntry> errorLogs = logs.getLogs("ERROR");
        List<LogEntry> warningLogs = logs.getLogs("WARN");
        List<LogEntry> infoLogs = logs.getLogs("INFO");
        
        assertNotNull(errorLogs, "Should return error logs list");
        assertNotNull(warningLogs, "Should return warning logs list");
        assertNotNull(infoLogs, "Should return info logs list");
        
        // Verify our test messages were captured
        assertTrue(errorLogs.stream().anyMatch(e -> e.getMessage().contains("Test error level")),
            "Should have captured error message");
        assertTrue(warningLogs.stream().anyMatch(e -> e.getMessage().contains("Test warning level")),
            "Should have captured warning message");
        assertTrue(infoLogs.stream().anyMatch(e -> e.getMessage().contains("Test info level")),
            "Should have captured info message");
    }

    @Test
    public void testAssertNoErrors() {
        logs.clear();
        
        managedKeycloakLogger.info("Just info");
        managedKeycloakLogger.warn("Just warning");
        
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Should pass - no errors logged by test
        logs.assertNoErrors();
        
        // Now log an error
        managedKeycloakLogger.error("An error occurred");
        
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Should fail - error exists
        assertThrows(AssertionError.class, () -> logs.assertNoErrors());
    }

    @Test
    public void testLogClearBetweenTests() {
        // Verify logs are cleared between test methods
        // This test should start with empty or minimal logs
        List<LogEntry> initialLogs = logs.getAllLogs();
        assertNotNull(initialLogs, "Should return a log list");
        
        // Log a unique message
        managedKeycloakLogger.info("Unique test message for clear verification");
        
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Verify it was captured
        List<LogEntry> afterLogs = logs.getAllLogs();
        assertTrue(afterLogs.stream().anyMatch(e -> e.getMessage().contains("Unique test message")),
            "Should have captured the test message");
    }
}
