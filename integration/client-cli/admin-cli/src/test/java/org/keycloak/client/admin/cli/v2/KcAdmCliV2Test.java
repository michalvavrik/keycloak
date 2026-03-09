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
    public void testHelpShowsAllGeneratedCommands() {
        String help = createCli().getUsageMessage();

        assertTrue("Help should mention v2", help.contains("v2"));
        for (String cmd : List.of("list", "create", "get", "update", "patch", "delete")) {
            assertTrue("Help should list the '" + cmd + "' subcommand", help.contains(cmd));
        }
    }

    @Test
    public void testListCommandExecutes() {
        assertCommandOutput("list", "v2: ClientsApi.getClients() invoked");
    }

    @Test
    public void testGetCommandExecutes() {
        assertCommandOutput("get", "v2: ClientApi.getClient() invoked");
    }

    @Test
    public void testCreateCommandExecutes() {
        assertCommandOutput("create", "v2: ClientsApi.createClient() invoked");
    }

    @Test
    public void testDeleteCommandExecutes() {
        assertCommandOutput("delete", "v2: ClientApi.deleteClient() invoked");
    }

    private void assertCommandOutput(String command, String expectedOutput) {
        CommandLine cli = createCli();

        PrintStream originalOut = System.out;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        System.setOut(new PrintStream(baos));
        try {
            int exitCode = cli.execute(command);
            assertEquals("Exit code should be 0 for '" + command + "'", 0, exitCode);

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
