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
import org.keycloak.client.cli.config.ConfigData;
import org.keycloak.client.cli.config.FileConfigHandler;
import org.keycloak.client.cli.util.AuthUtil;
import org.keycloak.client.cli.util.ConfigUtil;
import org.keycloak.client.cli.util.Headers;
import org.keycloak.client.cli.util.HeadersBody;
import org.keycloak.client.cli.util.HeadersBodyStatus;
import org.keycloak.client.cli.util.HttpUtil;
import org.keycloak.util.JsonSerialization;

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
            addConfigOption(groupSpec);

            CommandLine groupCli = new CommandLine(groupSpec);

            for (CommandDescriptor cmd : resource.getCommands()) {
                groupCli.addSubcommand(cmd.getName(), buildSubcommand(cmd));
            }

            cli.addSubcommand(resource.getName(), groupCli);
        }
    }

    private static CommandLine buildSubcommand(CommandDescriptor cmd) {
        V2Command v2cmd = new V2Command(cmd);
        CommandSpec spec = CommandSpec.forAnnotatedObject(v2cmd);
        spec.name(cmd.getName());
        spec.usageMessage().description(cmd.getDescription());
        addHelpOption(spec);
        addConfigOption(spec);

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

    private static void addConfigOption(CommandSpec spec) {
        spec.addOption(OptionSpec.builder("--config")
                .type(String.class)
                .paramLabel("<path>")
                .description("Path to the config file (~/.keycloak/kcadm.config by default)")
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
    static class V2Command implements Runnable {
        @Spec CommandSpec spec;
        private final CommandDescriptor descriptor;

        V2Command(CommandDescriptor descriptor) {
            this.descriptor = descriptor;
        }

        @Override
        public void run() {
            PrintWriter out = spec.commandLine().getOut();
            PrintWriter err = spec.commandLine().getErr();

            try {
                String configPath = spec.commandLine().getParseResult()
                        .matchedOptionValue("--config", KcAdmMain.DEFAULT_CONFIG_FILE_PATH);

                FileConfigHandler.setConfigFile(configPath);
                ConfigUtil.setHandler(new FileConfigHandler());
                ConfigData config = ConfigUtil.loadConfig();

                setupTruststore(config);

                String token = AuthUtil.ensureToken(config, KcAdmMain.CMD);
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
                    out.println(responseBody);
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

            if (file != null) {
                if (!new File(file).isFile()) {
                    throw new RuntimeException("File not found: " + file);
                }
                return Files.readString(new File(file).toPath());
            }

            List<OptionDescriptor> options = descriptor.getOptions();
            if (options == null || options.isEmpty()) {
                return null;
            }

            boolean anyFieldSet = false;
            Map<String, Object> fields = new LinkedHashMap<>();
            for (OptionDescriptor opt : options) {
                Object value = spec.commandLine().getParseResult()
                        .matchedOptionValue("--" + opt.getName(), null);
                if (value != null) {
                    anyFieldSet = true;
                    if (opt.isArray() && value instanceof String[]) {
                        fields.put(opt.getFieldName(), List.of((String[]) value));
                    } else if ("boolean".equals(opt.getType())) {
                        fields.put(opt.getFieldName(), Boolean.parseBoolean(value.toString()));
                    } else {
                        fields.put(opt.getFieldName(), value);
                    }
                }
            }

            if (!anyFieldSet) {
                return null;
            }

            return JsonSerialization.writeValueAsString(fields);
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
