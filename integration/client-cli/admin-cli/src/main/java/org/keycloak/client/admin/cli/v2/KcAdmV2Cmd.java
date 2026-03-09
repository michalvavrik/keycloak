package org.keycloak.client.admin.cli.v2;

import java.util.ServiceLoader;

import org.keycloak.client.cli.common.BaseGlobalOptionsCmd;

import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(name = "kcadm",
        header = {
                "Keycloak Admin CLI v2 (experimental)",
                "",
                "Find more information at: https://www.keycloak.org/docs/latest"
        },
        description = "%nCOMMAND [ARGUMENTS]"
)
public class KcAdmV2Cmd extends BaseGlobalOptionsCmd {

    @Override
    protected boolean nothingToDo() {
        return true;
    }

    @Override
    protected String help() {
        return "Keycloak Admin CLI v2 (experimental)\n\n"
                + "Use '--help' for available commands.";
    }

    @Override
    protected void configureCommandLine(CommandLine cli) {
        // these commands are generated via admin-cli-v2-commands module from the actual REST client
        for (KcAdmV2Subcommand cmd : ServiceLoader.load(KcAdmV2Subcommand.class)) {
            cli.addSubcommand(cmd);
        }
    }
}
