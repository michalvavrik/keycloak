package org.keycloak.client.admin.cli.v2;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.List;

import org.keycloak.client.cli.common.Globals;

import org.junit.Test;
import picocli.CommandLine;

import static org.junit.Assert.assertEquals;
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
        for (String cmd : List.of("list", "create", "get", "update", "patch", "delete")) {
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

    private void assertCommandOutput(String[] command, String expectedOutput) {
        CommandLine cli = createCli();

        PrintStream originalOut = System.out;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        System.setOut(new PrintStream(baos));
        try {
            int exitCode = cli.execute(command);
            assertEquals("Exit code should be 0", 0, exitCode);

            String output = baos.toString();
            assertTrue("Output should contain '" + expectedOutput + "'",
                    output.contains(expectedOutput));
        } finally {
            System.setOut(originalOut);
        }
    }

    private CommandLine createCli() {
        return Globals.createCommandLine(new KcAdmV2Cmd(), "kcadm", new PrintWriter(System.err, true));
    }
}
