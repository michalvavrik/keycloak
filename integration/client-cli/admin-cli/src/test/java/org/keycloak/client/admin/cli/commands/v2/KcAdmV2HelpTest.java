package org.keycloak.client.admin.cli.commands.v2;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;

import org.keycloak.client.admin.cli.KcAdmMain;
import org.keycloak.client.admin.cli.v2.KcAdmV2Cmd;
import org.keycloak.client.cli.common.Globals;

import org.junit.Test;
import picocli.CommandLine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class KcAdmV2HelpTest {

    @Test
    public void testHelpShowsResourceGroup() {
        String help = createCli().getUsageMessage();

        assertTrue("Help should list 'client' resource group", help.contains("client"));
        assertTrue("Help should list 'config' command", help.contains("config"));
    }

    @Test
    public void testHelpShowsConsistentDescriptions() {
        String help = createCli().getUsageMessage();

        assertTrue("config should have a proper description",
                help.contains("Configuration management"));
        assertTrue("client should have a proper description",
                help.contains("Client operations"));
    }

    @Test
    public void testGroupCommandWithoutSubcommand() {
        CommandLine cli = createCli();
        StringWriter err = new StringWriter();
        cli.setErr(new PrintWriter(err));
        int exitCode = cli.execute("client");
        assertEquals("should exit normally", 0, exitCode);
        assertTrue("should suggest full command with --v2",
                err.toString().contains("kcadm.sh --v2 client --help"));
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
    public void testCreateHasProtocolVariants() {
        CommandLine cli = createCli();
        CommandLine createCli = cli.getSubcommands().get("client").getSubcommands().get("create");
        assertTrue("create should have 'oidc' subcommand", createCli.getSubcommands().containsKey("oidc"));
        assertTrue("create should have 'saml' subcommand", createCli.getSubcommands().containsKey("saml"));
    }

    @Test
    public void testCreateOidcShowsOidcOptions() {
        String help = getVariantHelp("create", "oidc");
        assertTrue("should have --login-flows", help.contains("--login-flows"));
        assertTrue("should have --web-origins", help.contains("--web-origins"));
        assertTrue("should have --service-account-roles", help.contains("--service-account-roles"));
        assertTrue("should have -f", help.contains("-f"));
        assertFalse("should not have --sign-documents", help.contains("--sign-documents"));
    }

    @Test
    public void testAuthFlattenedToSubOptions() {
        String help = getVariantHelp("create", "oidc");
        assertTrue("should have --auth-method", help.contains("--auth-method"));
        assertTrue("should have --auth-secret", help.contains("--auth-secret"));
        assertTrue("should have --auth-certificate", help.contains("--auth-certificate"));
        assertFalse("should not have bare --auth ", help.contains("  --auth "));
    }

    @Test
    public void testLoginFlowsShowsEnumValues() {
        String help = getVariantHelp("create", "oidc");
        assertTrue("should show STANDARD", help.contains("STANDARD"));
        assertTrue("should show SERVICE_ACCOUNT", help.contains("SERVICE_ACCOUNT"));
        assertTrue("should show DIRECT_GRANT", help.contains("DIRECT_GRANT"));
    }

    @Test
    public void testCreateSamlShowsSamlOptions() {
        String help = getVariantHelp("create", "saml");
        assertTrue("should have --sign-documents", help.contains("--sign-documents"));
        assertTrue("should have --sign-assertions", help.contains("--sign-assertions"));
        assertTrue("should have --name-id-format", help.contains("--name-id-format"));
        assertTrue("should have -f", help.contains("-f"));
        assertFalse("should not have --login-flows", help.contains("--login-flows"));
    }

    @Test
    public void testPatchOidcShowsOidcOptions() {
        String help = getVariantHelp("patch", "oidc");
        assertTrue("should have --login-flows", help.contains("--login-flows"));
        assertTrue("should have -f", help.contains("-f"));
        assertFalse("should not have --sign-documents", help.contains("--sign-documents"));
    }

    @Test
    public void testPatchSamlShowsSamlOptions() {
        String help = getVariantHelp("patch", "saml");
        assertTrue("should have --sign-documents", help.contains("--sign-documents"));
        assertFalse("should not have --login-flows", help.contains("--login-flows"));
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
    public void testOutputOptionsGroupedOnSubcommand() {
        String help = getSubcommandHelp("client", "list");
        assertTrue("should have 'Output options:' heading", help.contains("Output options:"));
        assertTrue("should have -c", help.contains("-c"));
        assertTrue("should have --compressed", help.contains("--compressed"));
        assertTrue("should have -F", help.contains("-F"));
        assertTrue("should have --fields", help.contains("--fields"));
        assertTrue("should have --format", help.contains("--format"));
        assertTrue("should have --noquotes", help.contains("--noquotes"));
    }

    @Test
    public void testOutputOptionsAvailableOnVariant() {
        String help = getVariantHelp("create", "oidc");
        assertTrue("should have -c", help.contains("-c"));
        assertTrue("should have --fields", help.contains("--fields"));
        assertTrue("should have --format", help.contains("--format"));
    }

    @Test
    public void testConnectionOptionsGroupedOnSubcommand() {
        String help = getSubcommandHelp("client", "list");
        assertTrue("should have 'Connection options:' heading", help.contains("Connection options:"));
        assertTrue("should have --config", help.contains("--config"));
        assertTrue("should have -r", help.contains("-r"));
        assertTrue("should have --realm", help.contains("--realm"));
    }

    @Test
    public void testConnectionOptionsAvailableOnGroupCommand() {
        CommandLine cli = createCli();
        String help = cli.getSubcommands().get("client").getUsageMessage();
        assertTrue("should have --config", help.contains("--config"));
        assertTrue("should have --realm", help.contains("--realm"));
    }

    @Test
    public void testConnectionOptionsAvailableOnVariant() {
        String help = getVariantHelp("create", "oidc");
        assertTrue("should have --config", help.contains("--config"));
        assertTrue("should have --realm", help.contains("--realm"));
    }

    @Test
    public void testFileOptionRejectsNonExistentFile() {
        CommandLine cli = createCli();
        cli.setErr(new PrintWriter(new StringWriter()));
        int exitCode = cli.execute("client", "create", "oidc", "-f", "/nonexistent/file.json");
        assertNotEquals("Should fail for non-existent file", 0, exitCode);
    }


    private String getVariantHelp(String command, String variant) {
        CommandLine cli = createCli();
        return cli.getSubcommands().get("client").getSubcommands().get(command)
                .getSubcommands().get(variant).getUsageMessage();
    }

    private String getSubcommandHelp(String group, String command) {
        CommandLine cli = createCli();
        return cli.getSubcommands().get(group)
                .getSubcommands().get(command)
                .getUsageMessage();
    }

    private CommandLine createCli() {
        return Globals.createCommandLine(new KcAdmV2Cmd(), KcAdmMain.CMD, new PrintWriter(System.err, true));
    }
}
