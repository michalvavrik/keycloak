package org.keycloak.client.admin.cli.v2;

import java.io.IOException;
import java.io.InputStream;

import org.keycloak.client.admin.cli.commands.ConfigCmd;
import org.keycloak.client.cli.common.BaseGlobalOptionsCmd;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

@Command(name = "kcadm",
        header = {
                "Keycloak Admin CLI v2 (experimental)",
                "",
                "Find more information at: https://www.keycloak.org/docs/latest"
        },
        description = "%nCOMMAND [ARGUMENTS]"
)
public class KcAdmV2Cmd extends BaseGlobalOptionsCmd {

    private static final String BUNDLED_DESCRIPTOR = "/kcadm-v2-commands.json";

    @Spec
    CommandSpec spec;

    @Override
    protected boolean nothingToDo() {
        return true;
    }

    @Override
    protected String help() {
        return "";
    }

    @Override
    protected void printHelpIfNeeded() {
        spec.commandLine().usage(System.out);
        System.exit(CommandLine.ExitCode.OK);
    }

    @Override
    protected void configureCommandLine(CommandLine cli) {
        cli.addSubcommand(new ConfigCmd());
        KcAdmV2CommandDescriptor descriptor = loadDescriptor();
        KcAdmV2CommandBuilder.addCommands(cli, descriptor);
    }

    private KcAdmV2CommandDescriptor loadDescriptor() {
        // TODO: fetch and cache server-specific descriptor (follow-up PR)
        return loadBundledDescriptor();
    }

    private KcAdmV2CommandDescriptor loadBundledDescriptor() {
        try (InputStream is = getClass().getResourceAsStream(BUNDLED_DESCRIPTOR)) {
            if (is == null) {
                throw new RuntimeException("Bundled command descriptor not found: " + BUNDLED_DESCRIPTOR);
            }
            return KcAdmV2DescriptorBuilder.readDescriptor(is);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load command descriptor", e);
        }
    }
}
