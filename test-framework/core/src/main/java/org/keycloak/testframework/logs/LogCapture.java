package org.keycloak.testframework.logs;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

/**
 * Captures log records during test execution for later assertion.
 * Thread-safe implementation using CopyOnWriteArrayList for concurrent test execution.
 */
public class LogCapture extends Handler {
    
    private final List<LogEntry> capturedLogs = new CopyOnWriteArrayList<>();
    private final Level minimumLevel;
    private volatile boolean enabled = true;

    public LogCapture() {
        this(Level.ALL);
    }

    public LogCapture(Level minimumLevel) {
        this.minimumLevel = minimumLevel;
        setLevel(minimumLevel);
    }

    @Override
    public void publish(LogRecord record) {
        if (!enabled || record == null || !isLoggable(record)) {
            return;
        }

        LogEntry entry = new LogEntry(
            record.getLevel(),
            record.getMessage(),
            record.getLoggerName(),
            Instant.ofEpochMilli(record.getMillis()),
            record.getThrown(),
            record.getParameters()
        );
        
        capturedLogs.add(entry);
    }

    @Override
    public void flush() {
        // No buffering, nothing to flush
    }

    @Override
    public void close() throws SecurityException {
        clear();
    }

    /**
     * Returns all captured logs (unmodifiable view)
     */
    public List<LogEntry> getLogs() {
        return Collections.unmodifiableList(new ArrayList<>(capturedLogs));
    }

    /**
     * Returns logs matching the specified level
     */
    public List<LogEntry> getLogs(Level level) {
        return capturedLogs.stream()
            .filter(entry -> entry.getLevel().equals(level))
            .toList();
    }

    /**
     * Returns logs at or above the specified level
     */
    public List<LogEntry> getLogsAtOrAbove(Level level) {
        return capturedLogs.stream()
            .filter(entry -> entry.getLevel().intValue() >= level.intValue())
            .toList();
    }

    /**
     * Clears all captured logs
     */
    public void clear() {
        capturedLogs.clear();
    }

    /**
     * Enables/disables log capture
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int size() {
        return capturedLogs.size();
    }
}
