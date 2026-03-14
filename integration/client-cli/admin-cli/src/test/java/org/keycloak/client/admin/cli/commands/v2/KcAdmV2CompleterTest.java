package org.keycloak.client.admin.cli.commands.v2;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;

import org.keycloak.client.admin.cli.v2.KcAdmV2Completer;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
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
        assertTrue("Should suggest 'update'", candidates.contains("patch"));
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
    public void testFileOptionInAutocompleteForCreate() {
        List<String> candidates = complete("client", "create", "-");
        assertTrue("Should suggest '-f'", candidates.contains("-f"));
    }

    @Test
    public void testFileOptionNotInAutocompleteForList() {
        List<String> candidates = complete("client", "list", "-");
        assertFalse("list should not suggest '-f'", candidates.contains("-f"));
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
        for (String name : new String[]{"create", "get", "patch", "delete"}) {
            if (candidates.contains(name)) {
                throw new AssertionError("Should not contain '" + name + "' but did");
            }
        }
    }
}
