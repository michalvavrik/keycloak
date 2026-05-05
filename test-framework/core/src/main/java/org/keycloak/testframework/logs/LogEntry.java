package org.keycloak.testframework.logs;

import java.time.Instant;
import java.util.logging.Level;

/**
 * Represents a captured log entry with relevant metadata.
 * This is an immutable value object that encapsulates log data.
 */
public class LogEntry {
    private final Level level;
    private final String message;
    private final String loggerName;
    private final Instant timestamp;
    private final Throwable throwable;
    private final Object[] parameters;

    public LogEntry(Level level, String message, String loggerName, 
                    Instant timestamp, Throwable throwable, Object[] parameters) {
        this.level = level;
        this.message = message;
        this.loggerName = loggerName;
        this.timestamp = timestamp;
        this.throwable = throwable;
        this.parameters = parameters;
    }

    /**
     * Returns the log level
     */
    public Level getLevel() {
        return level;
    }

    /**
     * Returns the raw log message (may contain format placeholders)
     */
    public String getMessage() {
        return message;
    }

    /**
     * Returns the logger name
     */
    public String getLoggerName() {
        return loggerName;
    }

    /**
     * Returns the timestamp when the log was created
     */
    public Instant getTimestamp() {
        return timestamp;
    }

    /**
     * Returns the throwable associated with this log entry, or null if none
     */
    public Throwable getThrowable() {
        return throwable;
    }

    /**
     * Returns the parameters for message formatting, or null if none
     */
    public Object[] getParameters() {
        return parameters;
    }
    
    /**
     * Returns the formatted message with parameters substituted.
     * If no parameters exist, returns the raw message.
     */
    public String getFormattedMessage() {
        if (parameters == null || parameters.length == 0) {
            return message;
        }
        try {
            return String.format(message, parameters);
        } catch (Exception e) {
            // If formatting fails, return raw message
            return message;
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(level).append("] ");
        sb.append(loggerName).append(": ");
        sb.append(getFormattedMessage());
        if (throwable != null) {
            sb.append(" (").append(throwable.getClass().getSimpleName()).append(": ");
            sb.append(throwable.getMessage()).append(")");
        }
        return sb.toString();
    }
}
