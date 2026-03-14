package org.keycloak.client.admin.cli.v2;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import picocli.AutoComplete;
import picocli.CommandLine;

/**
 * Handles the {@code __complete} request for dynamic shell completion.
 * Delegates to PicoCLI's {@link AutoComplete#complete} for candidate resolution.
 */
public class KcAdmV2Completer {

    public static void complete(String[] args, PrintWriter out) {
        KcAdmV2Cmd rootCmd = new KcAdmV2Cmd();
        CommandLine cli = new CommandLine(rootCmd);
        rootCmd.configureCommandLine(cli);

        String partial = args.length > 0 ? args[args.length - 1] : "";
        int cursor = 0;
        for (String arg : args) {
            cursor += arg.length() + 1; // +1 for space
        }
        if (cursor > 0) {
            cursor--; // remove trailing space
        }

        int argIndex = Math.max(0, args.length - 1);
        int posInArg = partial.length();

        List<CharSequence> candidates = new ArrayList<>();
        AutoComplete.complete(cli.getCommandSpec(), args, argIndex, posInArg, cursor, candidates);

        for (CharSequence candidate : candidates) {
            out.println(partial + candidate);
        }

        out.flush();
    }
}
