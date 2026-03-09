package org.keycloak.client.admin.cli.v2;

import java.util.LinkedHashMap;
import java.util.Map;

import org.eclipse.microprofile.openapi.models.OpenAPI;
import org.eclipse.microprofile.openapi.models.Operation;
import org.eclipse.microprofile.openapi.models.PathItem;

import picocli.CommandLine;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.PositionalParamSpec;

/**
 * Builds PicoCLI commands programmatically from a parsed {@link OpenAPI} model.
 */
public class V2CommandBuilder {

    private static final Map<PathItem.HttpMethod, String> HTTP_METHOD_TO_COMMAND = Map.of(
            PathItem.HttpMethod.GET, "get",
            PathItem.HttpMethod.POST, "create",
            PathItem.HttpMethod.PUT, "update",
            PathItem.HttpMethod.PATCH, "patch",
            PathItem.HttpMethod.DELETE, "delete"
    );

    public static void addCommands(CommandLine cli, OpenAPI openApi) {
        // Group operations by resource name
        Map<String, CommandLine> resourceGroups = new LinkedHashMap<>();

        for (var entry : openApi.getPaths().getPathItems().entrySet()) {
            String path = entry.getKey();
            PathItem pathItem = entry.getValue();
            boolean hasId = path.contains("{id}");
            String resourceName = extractResourceName(path);

            CommandLine groupCli = resourceGroups.computeIfAbsent(resourceName, name -> {
                CommandSpec groupSpec = CommandSpec.wrapWithoutInspection(new GroupCommand(name));
                groupSpec.name(name);
                groupSpec.mixinStandardHelpOptions(true);
                groupSpec.usageMessage().header(capitalize(name) + " operations");
                return new CommandLine(groupSpec);
            });

            for (var opEntry : pathItem.getOperations().entrySet()) {
                PathItem.HttpMethod httpMethod = opEntry.getKey();
                Operation operation = opEntry.getValue();

                String cmdName = HTTP_METHOD_TO_COMMAND.get(httpMethod);
                if (cmdName == null) {
                    continue;
                }

                if (httpMethod == PathItem.HttpMethod.GET && !hasId) {
                    cmdName = "list";
                }

                String description = operation.getSummary();
                if (description == null || description.isBlank()) {
                    description = capitalize(cmdName) + " " + resourceName;
                }

                groupCli.addSubcommand(cmdName, buildSubcommand(cmdName, description,
                        httpMethod.name(), path, hasId));
            }
        }

        for (var group : resourceGroups.entrySet()) {
            cli.addSubcommand(group.getKey(), group.getValue());
        }
    }

    private static CommandLine buildSubcommand(String name, String description,
            String httpMethod, String path, boolean requiresId) {
        CommandSpec spec = CommandSpec.wrapWithoutInspection(
                new StubCommand(httpMethod, path));
        spec.name(name);
        spec.mixinStandardHelpOptions(true);
        spec.usageMessage().description(description);

        if (requiresId) {
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

    private static String extractResourceName(String path) {
        String[] segments = path.split("/");
        String name = "unknown";
        for (int i = segments.length - 1; i >= 0; i--) {
            String seg = segments[i];
            if (!seg.isEmpty() && !seg.startsWith("{")) {
                name = seg;
                break;
            }
        }
        if (name.endsWith("s") && name.length() > 1) {
            name = name.substring(0, name.length() - 1);
        }
        return name;
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
