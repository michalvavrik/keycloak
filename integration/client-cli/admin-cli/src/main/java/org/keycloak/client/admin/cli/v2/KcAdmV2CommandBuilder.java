package org.keycloak.client.admin.cli.v2;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.keycloak.client.admin.cli.KcAdmMain;
import org.keycloak.client.admin.cli.v2.KcAdmV2CommandDescriptor.CommandDescriptor;
import org.keycloak.client.admin.cli.v2.KcAdmV2CommandDescriptor.OptionDescriptor;
import org.keycloak.client.admin.cli.v2.KcAdmV2CommandDescriptor.ResourceDescriptor;
import org.keycloak.client.admin.cli.v2.KcAdmV2CommandDescriptor.VariantDescriptor;
import org.keycloak.client.cli.config.ConfigData;
import org.keycloak.client.cli.config.FileConfigHandler;
import org.keycloak.client.cli.util.AuthUtil;
import org.keycloak.client.cli.util.ConfigUtil;
import org.keycloak.client.cli.util.FilterUtil;
import org.keycloak.client.cli.util.Headers;
import org.keycloak.client.cli.util.HeadersBody;
import org.keycloak.client.cli.util.HeadersBodyStatus;
import org.keycloak.client.cli.util.HttpUtil;
import org.keycloak.client.cli.util.OutputUtil;
import org.keycloak.client.cli.util.ReturnFields;
import org.keycloak.util.JsonSerialization;

import com.fasterxml.jackson.databind.JsonNode;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.OptionSpec;
import picocli.CommandLine.Model.PositionalParamSpec;
import picocli.CommandLine.Spec;

import static org.keycloak.client.cli.util.HttpUtil.APPLICATION_JSON;
import static org.keycloak.client.cli.util.IoUtil.readFully;
import static org.keycloak.common.util.ObjectUtil.capitalize;

/**
 * Builds PicoCLI commands programmatically from a {@link KcAdmV2CommandDescriptor}.
 */
public class KcAdmV2CommandBuilder {

    private static final String MERGE_PATCH_JSON = "application/merge-patch+json";

    public static void addCommands(CommandLine cli, KcAdmV2CommandDescriptor descriptor) {
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
        KcAdmV2RequestExecutor v2cmd = new KcAdmV2RequestExecutor(cmd, variant);
        CommandSpec spec = CommandSpec.forAnnotatedObject(v2cmd);
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
        spec.addOption(OptionSpec.builder("-c", "--compressed")
                .type(boolean.class)
                .description("Don't pretty print the output")
                .build());
        spec.addOption(OptionSpec.builder("-F", "--fields")
                .type(String.class)
                .paramLabel("<filter>")
                .description("Filter which fields to output")
                .build());
        spec.addOption(OptionSpec.builder("--format")
                .type(String.class)
                .paramLabel("<format>")
                .description("Output format: json (default), csv")
                .defaultValue("json")
                .build());
        spec.addOption(OptionSpec.builder("--noquotes")
                .type(boolean.class)
                .description("Don't quote strings in CSV output")
                .build());
    }

    private static void addConnectionOptions(CommandSpec spec) {
        spec.addOption(OptionSpec.builder("--config")
                .type(String.class)
                .paramLabel("<path>")
                .description("Path to the config file (~/.keycloak/kcadm.config by default)")
                .build());
        spec.addOption(OptionSpec.builder("-r", "--realm")
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

        @Override
        public void run() {
            System.err.println("Use '" + name + " --help' for available commands.");
        }
    }

    @Command
    static class KcAdmV2RequestExecutor implements Runnable {
        @Spec CommandSpec spec;
        private final CommandDescriptor descriptor;
        private final VariantDescriptor variant;

        KcAdmV2RequestExecutor(CommandDescriptor descriptor, VariantDescriptor variant) {
            this.descriptor = descriptor;
            this.variant = variant;
        }

        @Override
        public void run() {
            PrintWriter out = spec.commandLine().getOut();
            PrintWriter err = spec.commandLine().getErr();

            try {
                String configPath = spec.commandLine().getParseResult()
                        .matchedOptionValue("--config", KcAdmMain.DEFAULT_CONFIG_FILE_PATH);

                String realmOverride = spec.commandLine().getParseResult()
                        .matchedOptionValue("--realm", null);

                FileConfigHandler.setConfigFile(configPath);
                ConfigUtil.setHandler(new FileConfigHandler());
                ConfigData config = ConfigUtil.loadConfig();

                setupTruststore(config);

                String token = AuthUtil.ensureToken(config, KcAdmMain.CMD);

                if (realmOverride != null) {
                    config.setRealm(realmOverride);
                }

                String url = buildUrl(config);
                String body = buildRequestBody();

                Headers headers = new Headers();
                headers.add("Authorization", "Bearer " + token);
                headers.add("Accept", APPLICATION_JSON);

                InputStream bodyStream = null;
                if (body != null) {
                    String contentType = "PATCH".equals(descriptor.getHttpMethod())
                            ? MERGE_PATCH_JSON : APPLICATION_JSON;
                    headers.add("Content-Type", contentType);
                    bodyStream = new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8));
                }

                HeadersBodyStatus response = HttpUtil.doRequest(
                        descriptor.getHttpMethod().toLowerCase(),
                        url,
                        new HeadersBody(headers, bodyStream));

                String responseBody = response.getBody() != null ? readFully(response.getBody()) : "";

                if (response.getStatusCode() >= 400) {
                    String message = !responseBody.isBlank() ? responseBody : response.getStatus();
                    err.println("Error: " + message);
                    throw new CommandLine.ExecutionException(
                            spec.commandLine(), message, new RuntimeException(message));
                }

                if (!responseBody.isBlank()) {
                    out.println(formatOutput(responseBody));
                }
            } catch (CommandLine.ExecutionException e) {
                throw e;
            } catch (Exception e) {
                err.println("Error: " + e.getMessage());
                throw new CommandLine.ExecutionException(spec.commandLine(), e.getMessage(), e);
            }
        }

        private String buildUrl(ConfigData config) {
            String serverUrl = config.getServerUrl();
            String realm = config.getRealm();
            String path = descriptor.getPath()
                    .replace("{realmName}", realm)
                    .replace("{version}", "v2");

            if (descriptor.isRequiresId()) {
                String id = spec.commandLine().getParseResult().matchedPositional(0).getValue();
                path = path.replace("{id}", id);
            }

            return serverUrl + path;
        }

        private String buildRequestBody() throws IOException {
            String file = spec.commandLine().getParseResult().matchedOptionValue("-f", null);
            boolean hasFieldOptions = hasAnyFieldOptionSet();

            if (file != null && hasFieldOptions) {
                throw new RuntimeException(
                        "Options -f/--file and field options are mutually exclusive");
            }

            if (file != null) {
                if (!new File(file).isFile()) {
                    throw new RuntimeException("File not found: " + file);
                }
                return Files.readString(new File(file).toPath());
            }

            List<OptionDescriptor> options = variant != null
                    ? variant.getOptions() : descriptor.getOptions();
            if (options == null || options.isEmpty()) {
                return null;
            }

            boolean anyFieldSet = false;
            Map<String, Object> fields = new LinkedHashMap<>();

            if (variant != null) {
                fields.put(variant.getDiscriminatorField(), variant.getDiscriminatorValue());
                anyFieldSet = true;
            }

            for (OptionDescriptor opt : options) {
                Object value = spec.commandLine().getParseResult()
                        .matchedOptionValue("--" + opt.getName(), null);
                if (value != null) {
                    anyFieldSet = true;
                    Object converted;
                    if (opt.isArray() && value instanceof String[]) {
                        converted = List.of((String[]) value);
                    } else if ("boolean".equals(opt.getType())) {
                        converted = Boolean.parseBoolean(value.toString());
                    } else {
                        converted = value;
                    }

                    if (opt.getParentFieldName() != null) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> nested = (Map<String, Object>)
                                fields.computeIfAbsent(opt.getParentFieldName(), k -> new LinkedHashMap<>());
                        nested.put(opt.getFieldName(), converted);
                    } else {
                        fields.put(opt.getFieldName(), converted);
                    }
                }
            }

            if (!anyFieldSet) {
                return null;
            }

            return JsonSerialization.writeValueAsString(fields);
        }

        private boolean hasAnyFieldOptionSet() {
            List<OptionDescriptor> options = variant != null
                    ? variant.getOptions() : descriptor.getOptions();
            if (options == null) {
                return false;
            }
            for (OptionDescriptor opt : options) {
                if (spec.commandLine().getParseResult()
                        .matchedOptionValue("--" + opt.getName(), null) != null) {
                    return true;
                }
            }
            return false;
        }

        private String formatOutput(String json) {
            try {
                var parseResult = spec.commandLine().getParseResult();
                boolean compressed = parseResult.matchedOptionValue("--compressed", false);
                String fieldsFilter = parseResult.matchedOptionValue("--fields", null);
                String format = parseResult.matchedOptionValue("--format", "json");
                boolean noquotes = parseResult.matchedOptionValue("--noquotes", false);

                JsonNode node = OutputUtil.MAPPER.readTree(json);

                if (fieldsFilter != null) {
                    ReturnFields returnFields = new ReturnFields(fieldsFilter);
                    node = FilterUtil.copyFilteredObject(node, returnFields);
                }

                if ("csv".equalsIgnoreCase(format)) {
                    ReturnFields returnFields = fieldsFilter != null
                            ? new ReturnFields(fieldsFilter) : null;
                    StringBuilder sb = new StringBuilder();
                    OutputUtil.printAsCsv(node, returnFields, noquotes, line -> {
                        sb.append(line);
                        sb.append(System.lineSeparator());
                    });
                    return sb.toString().stripTrailing();
                }

                if (compressed) {
                    return node.toString();
                }

                return OutputUtil.MAPPER.writeValueAsString(node);
            } catch (Exception e) {
                return json;
            }
        }

        private void setupTruststore(ConfigData config) {
            if (config.getServerUrl() == null || !config.getServerUrl().startsWith("https:")) {
                return;
            }
            String truststore = config.getTruststore();
            if (truststore != null) {
                String pass = config.getTrustpass();
                if (pass == null) {
                    pass = System.getenv("KC_CLI_TRUSTSTORE_PASSWORD");
                }
                try {
                    HttpUtil.setTruststore(new File(truststore), pass);
                } catch (Exception e) {
                    throw new RuntimeException("Failed to load truststore: " + truststore, e);
                }
            }
        }
    }
}
