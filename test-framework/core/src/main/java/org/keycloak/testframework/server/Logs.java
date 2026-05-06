package org.keycloak.testframework.server;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import org.jboss.logging.Logger;

public final class Logs {

    private final List<LogEntry> entries = new CopyOnWriteArrayList<>();

    public List<LogEntry> getEntries() {
        return Collections.unmodifiableList(entries);
    }

    void add(LogEntry entry) {
        entries.add(entry);
    }

    public void assertContains(String message) {
        if (entries.stream().noneMatch(e -> e.message().contains(message))) {
            throw new AssertionError("Expected log output to contain: %s%n%s".formatted(message, formatEntries()));
        }
    }

    public void assertNotContains(String message) {
        entries.stream()
                .filter(e -> e.message().contains(message))
                .findFirst()
                .ifPresent(e -> {
                    throw new AssertionError("Expected log output to NOT contain: %s%nFound: %s".formatted(message, e.rawLine()));
                });
    }

    public void assertContains(Logger.Level level, String message) {
        if (entries.stream().noneMatch(e -> level.equals(e.level()) && e.message().contains(message))) {
            throw new AssertionError("Expected log output to contain message at level %s: %s%n%s".formatted(level, message, formatEntries()));
        }
    }

    public void assertContains(Logger.Level level, String category, String message) {
        if (entries.stream().noneMatch(e ->
                level.equals(e.level())
                        && e.category() != null && e.category().startsWith(category)
                        && e.message().contains(message))) {
            throw new AssertionError("Expected log output to contain message at level %s with category %s: %s%n%s".formatted(level, category, message, formatEntries()));
        }
    }

    public void assertNotContains(Logger.Level level, String message) {
        entries.stream()
                .filter(e -> level.equals(e.level()) && e.message().contains(message))
                .findFirst()
                .ifPresent(e -> {
                    throw new AssertionError("Expected log output to NOT contain message at level %s: %s%nFound: %s".formatted(level, message, e.rawLine()));
                });
    }

    public void assertCount(String message, int expectedCount) {
        long actualCount = entries.stream().filter(e -> e.message().contains(message)).count();
        if (actualCount != expectedCount) {
            throw new AssertionError("Expected log output to contain '%s' exactly %d time(s), but found %d".formatted(message, expectedCount, actualCount));
        }
    }

    public void assertStdErrContains(String message) {
        if (entries.stream().noneMatch(e -> e.stderr() && e.message().contains(message))) {
            throw new AssertionError("Expected stderr to contain: %s%n%s".formatted(message, formatStdErr()));
        }
    }

    public void assertStdErrNotContains(String message) {
        entries.stream()
                .filter(e -> e.stderr() && e.message().contains(message))
                .findFirst()
                .ifPresent(e -> {
                    throw new AssertionError("Expected stderr to NOT contain: %s%nFound: %s".formatted(message, e.rawLine()));
                });
    }

    public void clear() {
        entries.clear();
    }

    public String getOutput() {
        return entries.stream().map(LogEntry::rawLine).collect(Collectors.joining(System.lineSeparator()));
    }

    public String getStdErr() {
        return entries.stream().filter(LogEntry::stderr).map(LogEntry::rawLine).collect(Collectors.joining(System.lineSeparator()));
    }

    private String formatEntries() {
        if (entries.isEmpty()) {
            return "(no log entries)";
        }
        return entries.stream().map(LogEntry::rawLine).collect(Collectors.joining(System.lineSeparator()));
    }

    private String formatStdErr() {
        List<LogEntry> stderrEntries = entries.stream().filter(LogEntry::stderr).toList();
        if (stderrEntries.isEmpty()) {
            return "(no stderr entries)";
        }
        return stderrEntries.stream().map(LogEntry::rawLine).collect(Collectors.joining(System.lineSeparator()));
    }
}
