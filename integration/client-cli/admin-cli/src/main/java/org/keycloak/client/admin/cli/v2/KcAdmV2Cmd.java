package org.keycloak.client.admin.cli.v2;

import java.io.IOException;
import java.io.InputStream;

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
        KcAdmV2CommandDescriptor descriptor = loadDescriptor();
        KcAdmV2CommandBuilder.addCommands(cli, descriptor);
    }

    private KcAdmV2CommandDescriptor loadDescriptor() {
        // TODO: for long-lived sessions, check ~/.keycloak/ cache for server-specific descriptor
        // ConfigData already has serverUrl — use it as cache key:
        //   1. check ~/.keycloak/kcadm-v2-commands-<hash(serverUrl)>.json
        //   2. if stale or missing, fetch OpenAPI from server, convert with KcAdmV2DescriptorBuilder, cache
        //   3. return cached descriptor
        // For now, always use the bundled default (pre-built at compile time, no SmallRye on this path)
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
