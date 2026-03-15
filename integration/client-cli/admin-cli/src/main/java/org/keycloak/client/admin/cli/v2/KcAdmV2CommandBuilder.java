package org.keycloak.client.admin.cli.v2;

import java.util.List;

import org.keycloak.client.admin.cli.v2.KcAdmV2CommandDescriptor.CommandDescriptor;
import org.keycloak.client.admin.cli.v2.KcAdmV2CommandDescriptor.OptionDescriptor;
import org.keycloak.client.admin.cli.v2.KcAdmV2CommandDescriptor.ResourceDescriptor;
import org.keycloak.client.admin.cli.v2.KcAdmV2CommandDescriptor.VariantDescriptor;

import picocli.CommandLine;
import picocli.CommandLine.Model.ArgGroupSpec;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.OptionSpec;
import picocli.CommandLine.Model.PositionalParamSpec;

import static org.keycloak.client.admin.cli.KcAdmMain.CMD;
import static org.keycloak.client.admin.cli.KcAdmMain.V2_FLAG;
import static org.keycloak.common.util.ObjectUtil.capitalize;

class KcAdmV2CommandBuilder {

    static final String OPT_CONFIG = "--config";
    static final String OPT_SERVER = "--server";
    static final String OPT_REALM = "--realm";
    static final String OPT_USER = "--user";
    static final String OPT_PASSWORD = "--password";
    static final String OPT_CLIENT = "--client";
    static final String OPT_SECRET = "--secret";
    static final String OPT_TOKEN = "--token";
    static final String OPT_TRUSTSTORE = "--truststore";
    static final String OPT_TRUSTPASS = "--trustpass";
    static final String OPT_INSECURE = "--insecure";
    static final String OPT_FILE = "-f";
    static final String OPT_COMPRESSED = "--compressed";
    static final String OPT_FIELDS = "--fields";
    static final String OPT_FORMAT = "--format";
    static final String OPT_NOQUOTES = "--noquotes";

    static void addCommands(CommandLine cli, KcAdmV2CommandDescriptor descriptor) {
        for (ResourceDescriptor resource : descriptor.getResources()) {
            GroupCommand groupCommand = new GroupCommand(resource.getName());
            CommandSpec groupSpec = CommandSpec.wrapWithoutInspection(groupCommand);
            groupSpec.name(resource.getName());
            groupSpec.usageMessage().header(capitalize(resource.getName()) + " operations");

            addConnectionGroup(groupSpec);

            CommandLine groupCli = new CommandLine(groupSpec);
            groupCommand.setSpec(groupSpec);

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
        GroupCommand groupCommand = new GroupCommand(cmd.getName());
        CommandSpec parentSpec = CommandSpec.wrapWithoutInspection(groupCommand);
        parentSpec.name(cmd.getName());
        parentSpec.usageMessage().description(cmd.getDescription());
        addConnectionGroup(parentSpec);

        CommandLine parentCli = new CommandLine(parentSpec);
        groupCommand.setSpec(parentSpec);

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

        addConnectionGroup(spec);
        addOutputGroup(spec);

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
            ArgGroupSpec.Builder fieldGroup = ArgGroupSpec.builder()
                    .heading("%nOptions:%n")
                    .exclusive(false)
                    .validate(false)
                    .order(1);

            fieldGroup.addArg(OptionSpec.builder(OPT_FILE, "--file")
                    .type(String.class)
                    .paramLabel("<file>")
                    .description("JSON file with request body (mutually exclusive with field options)")
                    .build());

            for (OptionDescriptor opt : options) {
                fieldGroup.addArg(buildOption(opt));
            }

            spec.addArgGroup(fieldGroup.build());
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

    private static void addOutputGroup(CommandSpec spec) {
        spec.addArgGroup(ArgGroupSpec.builder()
                .heading("%nOutput options:%n")
                .exclusive(false)
                .validate(false)
                .order(10)
                .addArg(OptionSpec.builder("-c", OPT_COMPRESSED)
                        .type(boolean.class)
                        .description("Don't pretty print the output")
                        .build())
                .addArg(OptionSpec.builder("-F", OPT_FIELDS)
                        .type(String.class)
                        .paramLabel("<filter>")
                        .description("Filter which fields to output")
                        .build())
                .addArg(OptionSpec.builder(OPT_FORMAT)
                        .type(String.class)
                        .paramLabel("<format>")
                        .description("Output format: json (default), csv")
                        .defaultValue("json")
                        .build())
                .addArg(OptionSpec.builder(OPT_NOQUOTES)
                        .type(boolean.class)
                        .description("Don't quote strings in CSV output")
                        .build())
                .build());
    }

    private static void addConnectionGroup(CommandSpec spec) {
        spec.addArgGroup(ArgGroupSpec.builder()
                .heading("%nConnection options:%n")
                .exclusive(false)
                .validate(false)
                .order(20)
                .addArg(OptionSpec.builder(OPT_CONFIG)
                        .type(String.class)
                        .paramLabel("<path>")
                        .description("Path to the config file (~/.keycloak/kcadm.config by default)")
                        .build())
                .addArg(OptionSpec.builder(OPT_SERVER)
                        .type(String.class)
                        .paramLabel("<url>")
                        .description("Server URL (overrides config)")
                        .build())
                .addArg(OptionSpec.builder("-r", OPT_REALM)
                        .type(String.class)
                        .paramLabel("<realm>")
                        .description("Target realm (defaults to 'master'). For inline auth, also the realm to authenticate against")
                        .build())
                .addArg(OptionSpec.builder(OPT_USER)
                        .type(String.class)
                        .paramLabel("<user>")
                        .description("Username for inline authentication")
                        .build())
                .addArg(OptionSpec.builder(OPT_PASSWORD)
                        .type(String.class)
                        .paramLabel("<password>")
                        .description("Password (prompted for if --user is set and KC_CLI_PASSWORD is not defined)")
                        .build())
                .addArg(OptionSpec.builder(OPT_CLIENT)
                        .type(String.class)
                        .paramLabel("<client>")
                        .description("Client ID (overrides config, defaults to 'admin-cli')")
                        .build())
                .addArg(OptionSpec.builder(OPT_SECRET)
                        .type(String.class)
                        .paramLabel("<secret>")
                        .description("Client secret (prompted for if --client is set and KC_CLI_CLIENT_SECRET is not defined)")
                        .build())
                .addArg(OptionSpec.builder(OPT_TOKEN)
                        .type(String.class)
                        .paramLabel("<token>")
                        .description("Use an existing token (skip authentication)")
                        .build())
                .addArg(OptionSpec.builder(OPT_TRUSTSTORE)
                        .type(String.class)
                        .paramLabel("<path>")
                        .description("Path to a truststore containing trusted certificates")
                        .build())
                .addArg(OptionSpec.builder(OPT_TRUSTPASS)
                        .type(String.class)
                        .paramLabel("<password>")
                        .description("Truststore password (prompted if not specified and KC_CLI_TRUSTSTORE_PASSWORD is not set)")
                        .build())
                .addArg(OptionSpec.builder(OPT_INSECURE)
                        .type(boolean.class)
                        .description("Turns off TLS validation")
                        .build())
                .addArg(OptionSpec.builder("-h", "--help")
                        .usageHelp(true)
                        .hidden(true)
                        .build())
                .build());
    }


    static class GroupCommand implements Runnable {
        private final String name;
        private CommandSpec spec;

        GroupCommand(String name) {
            this.name = name;
        }

        void setSpec(CommandSpec spec) {
            this.spec = spec;
        }

        @Override
        public void run() {
            spec.commandLine().getErr().println(
                    "Use '" + CMD + " " + V2_FLAG + " " + name + " --help' for available commands.");
        }
    }
}
