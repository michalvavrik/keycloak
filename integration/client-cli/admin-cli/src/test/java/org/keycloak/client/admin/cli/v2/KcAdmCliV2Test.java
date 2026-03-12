package org.keycloak.client.admin.cli.v2;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;

import org.keycloak.client.cli.common.Globals;

import org.junit.Test;
import picocli.CommandLine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class KcAdmCliV2Test {

    @Test
    public void testHelpShowsResourceGroup() {
        String help = createCli().getUsageMessage();

        assertTrue("Help should mention v2", help.contains("v2"));
        assertTrue("Help should list 'client' resource group", help.contains("client"));
    }

    @Test
    public void testClientHelpShowsAllCommands() {
        CommandLine cli = createCli();
        CommandLine clientCli = cli.getSubcommands().get("client");

        String help = clientCli.getUsageMessage();
        for (String cmd : List.of("list", "create", "get", "patch", "delete")) {
            assertTrue("Client help should list '" + cmd + "'", help.contains(cmd));
        }
    }

    @Test
    public void testClientListExecutes() {
        assertCommandOutput(new String[]{"client", "list"},
                "v2: GET /admin/api/{realmName}/clients/{version}");
    }

    @Test
    public void testClientGetExecutes() {
        assertCommandOutput(new String[]{"client", "get", "some-id"},
                "v2: GET /admin/api/{realmName}/clients/{version}/{id}");
    }

    @Test
    public void testClientDeleteExecutes() {
        assertCommandOutput(new String[]{"client", "delete", "some-id"},
                "v2: DELETE /admin/api/{realmName}/clients/{version}/{id}");
    }

    @Test
    public void testFileOptionAvailableOnCreate() {
        String help = getSubcommandHelp("client", "create");
        assertTrue("create should have -f option", help.contains("-f"));
        assertTrue("create should have --file option", help.contains("--file"));
    }

    @Test
    public void testFileOptionAvailableOnPatch() {
        String help = getSubcommandHelp("client", "patch");
        assertTrue("patch should have -f option", help.contains("-f"));
        assertTrue("patch should have --file option", help.contains("--file"));
    }

    @Test
    public void testFileOptionNotAvailableOnGet() {
        String help = getSubcommandHelp("client", "get");
        assertFalse("get should not have --file option", help.contains("--file"));
    }

    @Test
    public void testFileOptionNotAvailableOnList() {
        String help = getSubcommandHelp("client", "list");
        assertFalse("list should not have --file option", help.contains("--file"));
    }

    @Test
    public void testFileOptionNotAvailableOnDelete() {
        String help = getSubcommandHelp("client", "delete");
        assertFalse("delete should not have --file option", help.contains("--file"));
    }

    @Test
    public void testFileOptionRejectsNonExistentFile() {
        CommandLine cli = createCli();

        StringWriter err = new StringWriter();
        cli.setErr(new PrintWriter(err));

        int exitCode = cli.execute("client", "create", "-f", "/nonexistent/file.json");
        assertNotEquals("Should fail for non-existent file", 0, exitCode);
    }

    private void assertCommandOutput(String[] command, String expectedOutput) {
        CommandLine cli = createCli();

        StringWriter sw = new StringWriter();
        cli.setOut(new PrintWriter(sw));

        int exitCode = cli.execute(command);
        assertEquals("Exit code should be 0", 0, exitCode);
        assertTrue("Output should contain '" + expectedOutput + "'",
                sw.toString().contains(expectedOutput));
    }

    private String getSubcommandHelp(String group, String command) {
        CommandLine cli = createCli();
        return cli.getSubcommands().get(group)
                .getSubcommands().get(command)
                .getUsageMessage();
    }

    private CommandLine createCli() {
        return Globals.createCommandLine(new KcAdmV2Cmd(), "kcadm", new PrintWriter(System.err, true));
    }
}
