package org.keycloak.client.admin.cli.v2;

import java.io.File;
import java.util.List;

import org.keycloak.client.admin.cli.v2.KcAdmV2CommandDescriptor.CommandDescriptor;
import org.keycloak.client.admin.cli.v2.KcAdmV2CommandDescriptor.OptionDescriptor;
import org.keycloak.client.admin.cli.v2.KcAdmV2CommandDescriptor.ResourceDescriptor;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.OptionSpec;
import picocli.CommandLine.Model.PositionalParamSpec;
import picocli.CommandLine.Spec;

import static org.keycloak.common.util.ObjectUtil.capitalize;

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
        StubCommand stub = new StubCommand(cmd.getHttpMethod(), cmd.getPath());
        CommandSpec spec = CommandSpec.forAnnotatedObject(stub);
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

        List<OptionDescriptor> options = cmd.getOptions();
        if (options != null && !options.isEmpty()) {
            spec.addOption(OptionSpec.builder("-f", "--file")
                    .type(String.class)
                    .paramLabel("<file>")
                    .description("JSON file with request body (mutually exclusive with field options)")
                    .build());

            for (OptionDescriptor opt : options) {
                spec.addOption(buildOption(opt));
            }
        }

        return new CommandLine(spec);
    }

    private static OptionSpec buildOption(OptionDescriptor opt) {
        OptionSpec.Builder builder = OptionSpec.builder("--" + opt.getName())
                .type(opt.isArray() ? String[].class : String.class)
                .description(opt.getDescription() != null ? opt.getDescription() : "");

        if (opt.isArray()) {
            builder.splitRegex(",");
        }

        return builder.build();
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
    @Command
    static class StubCommand implements Runnable {
        @Spec CommandSpec spec;
        private final String httpMethod;
        private final String path;

        StubCommand(String httpMethod, String path) {
            this.httpMethod = httpMethod;
            this.path = path;
        }

        @Override
        public void run() {
            String file = spec.commandLine().getParseResult().matchedOptionValue("-f", null);
            if (file != null && !new File(file).isFile()) {
                throw new CommandLine.ParameterException(
                        spec.commandLine(), "File not found: " + file);
            }
            spec.commandLine().getOut().println("v2: " + httpMethod + " " + path);
        }
    }
}
