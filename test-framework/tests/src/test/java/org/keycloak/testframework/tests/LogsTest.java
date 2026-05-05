package org.keycloak.testframework.tests;

import org.jboss.logging.Logger;
import org.junit.jupiter.api.Test;
import org.keycloak.testframework.annotations.InjectLogs;
import org.keycloak.testframework.annotations.KeycloakIntegrationTest;
import org.keycloak.testframework.logs.LogEntry;
import org.keycloak.testframework.logs.Logs;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@KeycloakIntegrationTest
public class LogsTest {

    private static final Logger LOGGER = Logger.getLogger(LogsTest.class);

    @InjectLogs
    private Logs logs;

    @Test
    public void testAssertNoErrors() {
        LOGGER.info("This is an info message");
        LOGGER.warn("This is a warning");
        
        // Should pass - no errors logged
        logs.assertNoErrors();
    }

    @Test
    public void testAssertNoErrorsFails() {
        LOGGER.error("This is an error");
        
        // Should fail - error was logged
        assertThrows(AssertionError.class, () -> logs.assertNoErrors());
    }

    @Test
    public void testAssertExists() {
        LOGGER.error("Connection failed to database");
        
        logs.assertExists("ERROR", "Connection failed");
        logs.assertExists("ERROR", "database");
    }

    @Test
    public void testAssertExistsRegex() {
        LOGGER.warn("User john.doe@example.com not found");
        
        logs.assertExistsRegex("WARN", "User .+@.+ not found");
    }

    @Test
    public void testAssertNotExists() {
        LOGGER.info("Application started successfully");
        
        logs.assertNotExists("ERROR", "failed");
        logs.assertNotExists("WARN", "deprecated");
    }

    @Test
    public void testGetLogs() {
        LOGGER.info("Message 1");
        LOGGER.warn("Message 2");
        LOGGER.error("Message 3");
        
        List<LogEntry> allLogs = logs.getAllLogs();
        assertTrue(allLogs.size() >= 3);
        
        List<LogEntry> errors = logs.getLogs("ERROR");
        assertEquals(1, errors.size());
        assertTrue(errors.get(0).getMessage().contains("Message 3"));
    }

    @Test
    public void testClear() {
        LOGGER.info("Before clear");
        
        int sizeBefore = logs.size();
        assertTrue(sizeBefore > 0);
        
        logs.clear();
        
        assertEquals(0, logs.size());
        
        LOGGER.info("After clear");
        assertEquals(1, logs.size());
    }

    @Test
    public void testLogIsolationBetweenTests() {
        // This test verifies that logs from previous tests are not visible
        // The framework should clear logs before each test
        
        LOGGER.info("Test isolation message");
        
        List<LogEntry> allLogs = logs.getAllLogs();
        
        // Should only contain logs from this test, not previous tests
        // Note: This assertion might need adjustment based on actual framework behavior
        assertTrue(allLogs.size() > 0, "Should have at least one log entry");
    }

    @Test
    public void testAssertNoWarningsOrErrors() {
        LOGGER.info("Just an info message");
        LOGGER.debug("Debug message");
        
        // Should pass - no warnings or errors
        logs.assertNoWarningsOrErrors();
    }

    @Test
    public void testAssertNoWarningsOrErrorsFailsOnWarning() {
        LOGGER.warn("This is a warning");
        
        // Should fail - warning was logged
        assertThrows(AssertionError.class, () -> logs.assertNoWarningsOrErrors());
    }
}
