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

final class KcAdmV2CommandBuilder {

    private static final String OPT_HELP = "--help";
    static final String OPT_FILE = "-f";
    static final String OPT_COMPRESSED = "--compressed";
    private static final String CMD_EDIT = "edit";
    private static final String CONNECTION_OPTIONS_HINT = " [CONNECTION OPTIONS]";

    private final KcAdmV2Cmd root;

    KcAdmV2CommandBuilder(KcAdmV2Cmd root) {
        this.root = root;
    }

    void addCommands(CommandLine cli, KcAdmV2CommandDescriptor descriptor) {
        for (ResourceDescriptor resource : descriptor.getResources()) {
            GroupCommand groupCommand = new GroupCommand(resource.getName());
            CommandSpec groupSpec = CommandSpec.wrapWithoutInspection(groupCommand);
            groupSpec.name(resource.getName());
            groupSpec.usageMessage().header(capitalize(resource.getName()) + " operations");
            addHelpOption(groupSpec);

            CommandLine groupCli = new CommandLine(groupSpec);
            groupCommand.setSpec(groupSpec);

            CommandDescriptor getDescriptor = null;
            CommandDescriptor putDescriptor = null;

            for (CommandDescriptor cmd : resource.getCommands()) {
                groupCli.addSubcommand(cmd.getName(), buildSubcommand(cmd));
                if (KcAdmV2DescriptorBuilder.CMD_NAME_GET.equals(cmd.getName())) {
                    getDescriptor = cmd;
                } else if (KcAdmV2DescriptorBuilder.CMD_NAME_APPLY.equals(cmd.getName())) {
                    putDescriptor = cmd;
                }
            }

            if (getDescriptor != null && putDescriptor != null) {
                groupCli.addSubcommand(CMD_EDIT, buildEditCommand(getDescriptor, putDescriptor));
            }

            setConnectionOptionsSynopsis(groupCli);
            cli.addSubcommand(resource.getName(), groupCli);
        }
    }

    private CommandLine buildSubcommand(CommandDescriptor cmd) {
        if (cmd.hasVariants()) {
            return buildVariantParentCommand(cmd);
        }

        return buildLeafCommand(cmd, cmd.getOptions(), null);
    }

    private CommandLine buildVariantParentCommand(CommandDescriptor cmd) {
        CommandLine parentCli = buildLeafCommand(cmd, null, null);

        for (VariantDescriptor variant : cmd.getVariants()) {
            parentCli.addSubcommand(variant.getName(),
                    buildLeafCommand(cmd, variant.getOptions(), variant));
        }

        return parentCli;
    }

    private CommandLine buildLeafCommand(CommandDescriptor cmd,
            List<OptionDescriptor> options, VariantDescriptor variant) {
        boolean isVariantParent = variant == null && cmd.hasVariants();

        CommandSpec spec = new KcAdmV2RequestExecutor(root, cmd, variant).getSpec();
        spec.name(variant != null ? variant.getName() : cmd.getName());
        spec.usageMessage().description(cmd.getDescription());
        addHelpOption(spec);

        if (cmd.isHasResponseBody()) {
            addOutputGroup(spec);
        }

        if (!isVariantParent && cmd.isRequiresId()) {
            addIdPositional(spec, cmd.getResourceName());
        }

        boolean hasFieldOptions = options != null && !options.isEmpty();
        if (hasFieldOptions || isVariantParent) {
            ArgGroupSpec.Builder fieldGroup = ArgGroupSpec.builder()
                    .heading("%nOptions:%n")
                    .exclusive(false)
                    .validate(false)
                    .order(1);

            fieldGroup.addArg(buildFileOption(hasFieldOptions));

            if (hasFieldOptions) {
                for (OptionDescriptor opt : options) {
                    fieldGroup.addArg(buildOption(opt));
                }
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
                .paramLabel("<value>")
                .description(description);

        if (opt.isArray()) {
            builder.splitRegex(",");
        }

        if (enumValues != null && !enumValues.isEmpty()) {
            builder.completionCandidates(enumValues);
        }

        return builder.build();
    }

    private static OptionSpec buildFileOption(boolean hasFieldOptions) {
        String description = hasFieldOptions
                ? "JSON file with request body (mutually exclusive with field options)"
                : "JSON file with request body";
        return OptionSpec.builder(OPT_FILE, "--file")
                .type(String.class)
                .paramLabel("<file>")
                .description(description)
                .build();
    }

    private CommandLine buildEditCommand(CommandDescriptor getCmd, CommandDescriptor putCmd) {
        String resourceName = getCmd.getResourceName();

        CommandSpec spec = new KcAdmV2EditCmd(root, getCmd, putCmd).getSpec();
        spec.name(CMD_EDIT);
        spec.usageMessage().description(KcAdmV2EditCmd.createDescription(resourceName));
        addHelpOption(spec);
        addOutputGroup(spec);
        addIdPositional(spec, resourceName);

        return new CommandLine(spec);
    }

    private static void addIdPositional(CommandSpec spec, String resourceName) {
        spec.addPositional(PositionalParamSpec.builder()
                .index("0")
                .paramLabel("<id>")
                .description(capitalize(resourceName) + " identifier")
                .required(true)
                .type(String.class)
                .build());
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
                .build());
    }

    private static void setConnectionOptionsSynopsis(CommandLine cli) {
        String rootName = CMD + " " + V2_FLAG + CONNECTION_OPTIONS_HINT;
        setConnectionOptionsSynopsis(cli, rootName);
    }

    private static void setConnectionOptionsSynopsis(CommandLine cli, String parentPath) {
        String name = cli.getCommandName();
        String fullPath = parentPath + " " + name;
        cli.getCommandSpec().usageMessage().customSynopsis(fullPath + " [OPTIONS]");
        for (CommandLine sub : cli.getSubcommands().values()) {
            setConnectionOptionsSynopsis(sub, fullPath);
        }
    }

    private static void addHelpOption(CommandSpec spec) {
        spec.addOption(OptionSpec.builder("-h", OPT_HELP)
                .usageHelp(true)
                .hidden(true)
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
