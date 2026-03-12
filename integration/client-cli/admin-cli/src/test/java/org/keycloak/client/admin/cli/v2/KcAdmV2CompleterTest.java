package org.keycloak.client.admin.cli.v2;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;

import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class KcAdmV2CompleterTest {

    @Test
    public void testEmptyInputShowsResourceGroups() {
        List<String> candidates = complete("");
        assertTrue("Should suggest 'client'", candidates.contains("client"));
    }

    @Test
    public void testPartialResourceName() {
        List<String> candidates = complete("cl");
        assertTrue("Should match 'client'", candidates.contains("client"));
    }

    @Test
    public void testNoMatch() {
        List<String> candidates = complete("xyz");
        assertNoSubcommands(candidates);
    }

    @Test
    public void testResourceGroupShowsCommands() {
        List<String> candidates = complete("client", "");
        assertTrue("Should suggest 'list'", candidates.contains("list"));
        assertTrue("Should suggest 'create'", candidates.contains("create"));
        assertTrue("Should suggest 'get'", candidates.contains("get"));
        assertTrue("Should suggest 'update'", candidates.contains("update"));
        assertTrue("Should suggest 'delete'", candidates.contains("delete"));
    }

    @Test
    public void testPartialCommand() {
        List<String> candidates = complete("client", "l");
        assertTrue("Should match 'list'", candidates.contains("list"));
        assertSubcommandsDoNotContain(candidates);
    }

    @Test
    public void testDashSuggestsOptions() {
        List<String> candidates = complete("client", "list", "--");
        assertTrue("Should suggest '--help'", candidates.contains("--help"));
    }

    @Test
    public void testUnknownSubcommandStaysAtCurrentLevel() {
        List<String> candidates = complete("client", "nonexistent", "");
        assertTrue("Should still suggest commands under 'client'", candidates.contains("list"));
    }

    private List<String> complete(String... args) {
        StringWriter sw = new StringWriter();
        KcAdmV2Completer.complete(args, new PrintWriter(sw));
        String output = sw.toString().trim();
        if (output.isEmpty()) {
            return List.of();
        }
        return List.of(output.split(System.lineSeparator()));
    }

    private void assertNoSubcommands(List<String> candidates) {
        for (String c : candidates) {
            if (!c.startsWith("-")) {
                throw new AssertionError("Expected no subcommand candidates but found: " + c);
            }
        }
    }

    private void assertSubcommandsDoNotContain(List<String> candidates) {
        for (String name : new String[]{"create", "get", "update", "delete"}) {
            if (candidates.contains(name)) {
                throw new AssertionError("Should not contain '" + name + "' but did");
            }
        }
    }
}
