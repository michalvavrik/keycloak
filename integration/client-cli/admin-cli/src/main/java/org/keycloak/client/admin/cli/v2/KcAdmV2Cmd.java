package org.keycloak.client.admin.cli.v2;

import java.util.ServiceLoader;

import org.keycloak.client.cli.common.BaseGlobalOptionsCmd;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

@Command(name = "kcadm",
        header = {
                "Keycloak Admin CLI v2 (experimental)",
                "",
                "Find more information at: https://www.keycloak.org/docs/latest"
        },
        description = "%nCOMMAND [ARGUMENTS]"
)
public class KcAdmV2Cmd extends BaseGlobalOptionsCmd {

    @Spec
    CommandSpec spec;

    @Override
    protected boolean nothingToDo() {
        return true;
    }

    @Override
    protected String help() {
        // not used — printHelpIfNeeded delegates to PicoCLI
        return "";
    }

    @Override
    protected void printHelpIfNeeded() {
        // let PicoCLI render usage with all discovered subcommands
        spec.commandLine().usage(System.out);
        System.exit(CommandLine.ExitCode.OK);
    }

    @Override
    protected void configureCommandLine(CommandLine cli) {
        // these commands are generated via admin-cli-v2-commands module from the actual REST client
        for (KcAdmV2Subcommand cmd : ServiceLoader.load(KcAdmV2Subcommand.class)) {
            cli.addSubcommand(cmd);
        }
    }
}
