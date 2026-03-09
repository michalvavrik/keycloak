package org.keycloak.client.admin.cli.v2;

import org.keycloak.client.admin.cli.v2.KcAdmV2CommandDescriptor.CommandDescriptor;
import org.keycloak.client.admin.cli.v2.KcAdmV2CommandDescriptor.ResourceDescriptor;

import picocli.CommandLine;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.PositionalParamSpec;

/**
 * Builds PicoCLI commands programmatically from a {@link KcAdmV2CommandDescriptor}.
 */
public class KcAdmV2CommandBuilder {

    public static void addCommands(CommandLine cli, KcAdmV2CommandDescriptor descriptor) {
        for (ResourceDescriptor resource : descriptor.getResources()) {
            CommandSpec groupSpec = CommandSpec.wrapWithoutInspection(
                    new GroupCommand(resource.getName()));
            groupSpec.name(resource.getName());
            groupSpec.mixinStandardHelpOptions(true);
            groupSpec.usageMessage().header(capitalize(resource.getName()) + " operations");

            CommandLine groupCli = new CommandLine(groupSpec);

            for (CommandDescriptor cmd : resource.getCommands()) {
                groupCli.addSubcommand(cmd.getName(), buildSubcommand(cmd));
            }

            cli.addSubcommand(resource.getName(), groupCli);
        }
    }

    private static CommandLine buildSubcommand(CommandDescriptor cmd) {
        CommandSpec spec = CommandSpec.wrapWithoutInspection(
                new StubCommand(cmd.getHttpMethod(), cmd.getPath()));
        spec.name(cmd.getName());
        spec.mixinStandardHelpOptions(true);
        spec.usageMessage().description(cmd.getDescription());

        if (cmd.isRequiresId()) {
            spec.addPositional(PositionalParamSpec.builder()
                    .index("0")
                    .paramLabel("<id>")
                    .description("Resource identifier")
                    .required(true)
                    .type(String.class)
                    .build());
        }

        return new CommandLine(spec);
    }

    private static String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    static class GroupCommand implements Runnable {
        private final String name;

        GroupCommand(String name) {
            this.name = name;
        }

        @Override
        public void run() {
            System.err.println("Use '" + name + " --help' for available commands.");
        }
    }

    /**
     * Stub command that prints what it would do.
     * Will be replaced with actual HTTP calls when the Java client is available.
     */
    static class StubCommand implements Runnable {
        private final String httpMethod;
        private final String path;

        StubCommand(String httpMethod, String path) {
            this.httpMethod = httpMethod;
            this.path = path;
        }

        @Override
        public void run() {
            System.out.println("v2: " + httpMethod + " " + path);
        }
    }
}
