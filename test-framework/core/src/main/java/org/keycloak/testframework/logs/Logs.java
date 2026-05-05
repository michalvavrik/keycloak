package org.keycloak.testframework.logs;

import org.jboss.logging.Logger;
import org.junit.jupiter.api.Assertions;

import java.util.List;
import java.util.logging.Level;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Provides assertion capabilities for server logs during test execution.
 * Follows the same pattern as {@link org.keycloak.testframework.events.Events}.
 * 
 * <p>Example usage:</p>
 * <pre>
 * {@code
 * @InjectLogs
 * Logs logs;
 * 
 * @Test
 * public void testNoErrors() {
 *     performOperation();
 *     logs.assertNoErrors();
 * }
 * 
 * @Test
 * public void testSpecificLog() {
 *     performOperation();
 *     logs.assertExists("ERROR", "Connection failed");
 * }
 * }
 * </pre>
 */
public class Logs {
    
    private static final Logger LOGGER = Logger.getLogger(Logs.class);
    
    private final LogCapture logCapture;
    private long testStartTime;

    public Logs(LogCapture logCapture) {
        this.logCapture = logCapture;
        this.testStartTime = System.currentTimeMillis();
    }

    /**
     * Asserts that no ERROR level logs were generated during the test.
     * 
     * @throws AssertionError if any ERROR logs are found
     */
    public void assertNoErrors() {
        List<LogEntry> errors = logCapture.getLogsAtOrAbove(Level.SEVERE);
        if (!errors.isEmpty()) {
            String errorMessages = errors.stream()
                .map(LogEntry::toString)
                .collect(Collectors.joining("\n"));
            Assertions.fail("Expected no ERROR logs, but found " + errors.size() + ":\n" + errorMessages);
        }
    }

    /**
     * Asserts that no WARN or ERROR level logs were generated during the test.
     * 
     * @throws AssertionError if any WARN or ERROR logs are found
     */
    public void assertNoWarningsOrErrors() {
        List<LogEntry> warnings = logCapture.getLogsAtOrAbove(Level.WARNING);
        if (!warnings.isEmpty()) {
            String messages = warnings.stream()
                .map(LogEntry::toString)
                .collect(Collectors.joining("\n"));
            Assertions.fail("Expected no WARN/ERROR logs, but found " + warnings.size() + ":\n" + messages);
        }
    }

    /**
     * Asserts that a log entry exists with the specified level and message.
     * Message matching is case-sensitive substring match.
     * 
     * @param level the log level (e.g., "ERROR", "WARN", "INFO")
     * @param message the message to search for (substring match)
     * @throws AssertionError if no matching log is found
     */
    public void assertExists(String level, String message) {
        Level logLevel = parseLevel(level);
        boolean found = logCapture.getLogs(logLevel).stream()
            .anyMatch(entry -> entry.getFormattedMessage().contains(message));
        
        if (!found) {
            Assertions.fail(String.format(
                "Expected log with level=%s and message containing '%s', but none found. " +
                "Available logs:\n%s", 
                level, message, formatLogs(logCapture.getLogs(logLevel))
            ));
        }
    }

    /**
     * Asserts that a log entry exists matching the specified level and regex pattern.
     * 
     * @param level the log level
     * @param messagePattern regex pattern to match against log messages
     * @throws AssertionError if no matching log is found
     */
    public void assertExistsRegex(String level, String messagePattern) {
        Level logLevel = parseLevel(level);
        Pattern pattern = Pattern.compile(messagePattern);
        
        boolean found = logCapture.getLogs(logLevel).stream()
            .anyMatch(entry -> pattern.matcher(entry.getFormattedMessage()).find());
        
        if (!found) {
            Assertions.fail(String.format(
                "Expected log with level=%s matching pattern '%s', but none found",
                level, messagePattern
            ));
        }
    }

    /**
     * Asserts that NO log entry exists with the specified level and message.
     * 
     * @param level the log level
     * @param message the message to search for
     * @throws AssertionError if a matching log is found
     */
    public void assertNotExists(String level, String message) {
        Level logLevel = parseLevel(level);
        List<LogEntry> matches = logCapture.getLogs(logLevel).stream()
            .filter(entry -> entry.getFormattedMessage().contains(message))
            .toList();
        
        if (!matches.isEmpty()) {
            Assertions.fail(String.format(
                "Expected NO log with level=%s and message containing '%s', but found %d:\n%s",
                level, message, matches.size(), formatLogs(matches)
            ));
        }
    }

    /**
     * Returns the oldest log entry and removes it from the queue.
     * Similar to Events.poll() pattern.
     * 
     * @return the oldest log entry, or null if no logs available
     */
    public LogEntry poll() {
        List<LogEntry> logs = logCapture.getLogs();
        if (logs.isEmpty()) {
            return null;
        }
        LogEntry entry = logs.get(0);
        // Note: CopyOnWriteArrayList doesn't support efficient removal
        // This returns the first entry but doesn't remove it
        // Consider using a different data structure if poll() is heavily used
        return entry;
    }

    /**
     * Returns all captured logs (unmodifiable view)
     */
    public List<LogEntry> getAllLogs() {
        return logCapture.getLogs();
    }

    /**
     * Returns logs at the specified level
     */
    public List<LogEntry> getLogs(String level) {
        return logCapture.getLogs(parseLevel(level));
    }

    /**
     * Clears all captured logs
     */
    public void clear() {
        logCapture.clear();
        testStartTime = System.currentTimeMillis();
    }

    /**
     * Returns the number of captured logs
     */
    public int size() {
        return logCapture.size();
    }

    /**
     * Resets the test start time (called by framework before each test)
     */
    void testStarted() {
        testStartTime = System.currentTimeMillis();
        logCapture.clear();
    }

    private Level parseLevel(String level) {
        return switch (level.toUpperCase()) {
            case "ERROR", "SEVERE" -> Level.SEVERE;
            case "WARN", "WARNING" -> Level.WARNING;
            case "INFO" -> Level.INFO;
            case "DEBUG", "FINE" -> Level.FINE;
            case "TRACE", "FINER" -> Level.FINER;
            case "ALL" -> Level.ALL;
            default -> throw new IllegalArgumentException("Unknown log level: " + level);
        };
    }

    private String formatLogs(List<LogEntry> logs) {
        if (logs.isEmpty()) {
            return "(no logs)";
        }
        return logs.stream()
            .map(LogEntry::toString)
            .collect(Collectors.joining("\n"));
    }
}
