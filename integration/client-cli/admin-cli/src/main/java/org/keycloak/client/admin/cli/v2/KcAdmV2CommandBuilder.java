package org.keycloak.client.admin.cli.v2;

import java.util.List;

import org.keycloak.client.admin.cli.v2.KcAdmV2CommandDescriptor.CommandDescriptor;
import org.keycloak.client.admin.cli.v2.KcAdmV2CommandDescriptor.OptionDescriptor;
import org.keycloak.client.admin.cli.v2.KcAdmV2CommandDescriptor.ResourceDescriptor;
import org.keycloak.client.admin.cli.v2.KcAdmV2CommandDescriptor.VariantDescriptor;

import picocli.CommandLine;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.OptionSpec;
import picocli.CommandLine.Model.PositionalParamSpec;
import picocli.CommandLine.Spec;

import static org.keycloak.common.util.ObjectUtil.capitalize;

class KcAdmV2CommandBuilder {

    static final String OPT_CONFIG = "--config";
    static final String OPT_REALM = "--realm";
    static final String OPT_FILE = "-f";
    static final String OPT_COMPRESSED = "--compressed";
    static final String OPT_FIELDS = "--fields";
    static final String OPT_FORMAT = "--format";
    static final String OPT_NOQUOTES = "--noquotes";

    static void addCommands(CommandLine cli, KcAdmV2CommandDescriptor descriptor) {
        for (ResourceDescriptor resource : descriptor.getResources()) {
            CommandSpec groupSpec = CommandSpec.wrapWithoutInspection(
                    new GroupCommand(resource.getName()));
            groupSpec.name(resource.getName());
            groupSpec.usageMessage().header(capitalize(resource.getName()) + " operations");
            addHelpOption(groupSpec);
            addConnectionOptions(groupSpec);

            CommandLine groupCli = new CommandLine(groupSpec);

            for (CommandDescriptor cmd : resource.getCommands()) {
                groupCli.addSubcommand(cmd.getName(), buildSubcommand(cmd));
            }

            cli.addSubcommand(resource.getName(), groupCli);
        }
    }

    private static CommandLine buildSubcommand(CommandDescriptor cmd) {
        List<VariantDescriptor> variants = cmd.getVariants();
        if (variants != null && !variants.isEmpty()) {
            return buildVariantParentCommand(cmd);
        }

        return buildLeafCommand(cmd, cmd.getOptions(), null);
    }

    private static CommandLine buildVariantParentCommand(CommandDescriptor cmd) {
        CommandSpec parentSpec = CommandSpec.wrapWithoutInspection(
                new GroupCommand(cmd.getName()));
        parentSpec.name(cmd.getName());
        parentSpec.usageMessage().description(cmd.getDescription());
        addHelpOption(parentSpec);
        addConnectionOptions(parentSpec);

        CommandLine parentCli = new CommandLine(parentSpec);

        for (VariantDescriptor variant : cmd.getVariants()) {
            parentCli.addSubcommand(variant.getName(),
                    buildLeafCommand(cmd, variant.getOptions(), variant));
        }

        return parentCli;
    }

    private static CommandLine buildLeafCommand(CommandDescriptor cmd,
            List<OptionDescriptor> options, VariantDescriptor variant) {
        KcAdmV2RequestExecutor executor = new KcAdmV2RequestExecutor(cmd, variant);
        CommandSpec spec = CommandSpec.forAnnotatedObject(executor);
        spec.name(variant != null ? variant.getName() : cmd.getName());
        spec.usageMessage().description(cmd.getDescription());
        addHelpOption(spec);
        addConnectionOptions(spec);
        addOutputOptions(spec);

        if (cmd.isRequiresId()) {
            spec.addPositional(PositionalParamSpec.builder()
                    .index("0")
                    .paramLabel("<id>")
                    .description("Resource identifier")
                    .required(true)
                    .type(String.class)
                    .build());
        }

        if (options != null && !options.isEmpty()) {
            spec.addOption(OptionSpec.builder(OPT_FILE, "--file")
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
        String description = opt.getDescription() != null ? opt.getDescription() : "";
        List<String> enumValues = opt.getEnumValues();
        if (enumValues != null && !enumValues.isEmpty()) {
            description += (description.isEmpty() ? "" : " ") + "Valid values: " + String.join(", ", enumValues);
        }

        OptionSpec.Builder builder = OptionSpec.builder("--" + opt.getName())
                .type(opt.isArray() ? String[].class : String.class)
                .description(description);

        if (opt.isArray()) {
            builder.splitRegex(",");
        }

        if (enumValues != null && !enumValues.isEmpty()) {
            builder.completionCandidates(enumValues);
        }

        return builder.build();
    }

    private static void addOutputOptions(CommandSpec spec) {
        spec.addOption(OptionSpec.builder("-c", OPT_COMPRESSED)
                .type(boolean.class)
                .description("Don't pretty print the output")
                .build());
        spec.addOption(OptionSpec.builder("-F", OPT_FIELDS)
                .type(String.class)
                .paramLabel("<filter>")
                .description("Filter which fields to output")
                .build());
        spec.addOption(OptionSpec.builder(OPT_FORMAT)
                .type(String.class)
                .paramLabel("<format>")
                .description("Output format: json (default), csv")
                .defaultValue("json")
                .build());
        spec.addOption(OptionSpec.builder(OPT_NOQUOTES)
                .type(boolean.class)
                .description("Don't quote strings in CSV output")
                .build());
    }

    private static void addConnectionOptions(CommandSpec spec) {
        spec.addOption(OptionSpec.builder(OPT_CONFIG)
                .type(String.class)
                .paramLabel("<path>")
                .description("Path to the config file (~/.keycloak/kcadm.config by default)")
                .build());
        spec.addOption(OptionSpec.builder("-r", OPT_REALM)
                .type(String.class)
                .paramLabel("<realm>")
                .description("Realm name (overrides the value from config)")
                .build());
    }

    private static void addHelpOption(CommandSpec spec) {
        spec.addOption(OptionSpec.builder("-h", "--help")
                .usageHelp(true)
                .description("Print command specific help")
                .build());
    }

    static class GroupCommand implements Runnable {
        private final String name;

        GroupCommand(String name) {
            this.name = name;
        }

        @Spec CommandSpec spec;

        @Override
        public void run() {
            spec.commandLine().getErr().println("Use '" + name + " --help' for available commands.");
        }
    }
}
