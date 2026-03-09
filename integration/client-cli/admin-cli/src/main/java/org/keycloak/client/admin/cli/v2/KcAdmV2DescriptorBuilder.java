package org.keycloak.client.admin.cli.v2;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigValue;
import org.eclipse.microprofile.config.spi.ConfigSource;
import org.eclipse.microprofile.config.spi.Converter;
import org.eclipse.microprofile.openapi.models.OpenAPI;
import org.eclipse.microprofile.openapi.models.Operation;
import org.eclipse.microprofile.openapi.models.PathItem;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.smallrye.openapi.api.SmallRyeOpenAPI;

/**
 * Converts an {@link OpenAPI} model into a {@link KcAdmV2CommandDescriptor}.
 * Used at build time to produce the bundled default, and at runtime for server-fetch (future).
 */
public class KcAdmV2DescriptorBuilder {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    private static final Map<PathItem.HttpMethod, String> HTTP_METHOD_TO_COMMAND = Map.of(
            PathItem.HttpMethod.GET, "get",
            PathItem.HttpMethod.POST, "create",
            PathItem.HttpMethod.PUT, "update",
            PathItem.HttpMethod.PATCH, "patch",
            PathItem.HttpMethod.DELETE, "delete"
    );

    public static KcAdmV2CommandDescriptor convert(OpenAPI openApi) {
        String version = openApi.getInfo() != null ? openApi.getInfo().getVersion() : "unknown";

        Map<String, List<KcAdmV2CommandDescriptor.CommandDescriptor>> resourceCommands = new LinkedHashMap<>();

        for (var entry : openApi.getPaths().getPathItems().entrySet()) {
            String path = entry.getKey();
            PathItem pathItem = entry.getValue();
            boolean hasId = path.contains("{id}");
            String resourceName = extractResourceName(path);

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

                KcAdmV2CommandDescriptor.CommandDescriptor cmd = new KcAdmV2CommandDescriptor.CommandDescriptor();
                cmd.setName(cmdName);
                cmd.setHttpMethod(httpMethod.name());
                cmd.setPath(path);
                cmd.setDescription(description);
                cmd.setRequiresId(hasId);

                resourceCommands.computeIfAbsent(resourceName, k -> new ArrayList<>()).add(cmd);
            }
        }

        List<KcAdmV2CommandDescriptor.ResourceDescriptor> resources = new ArrayList<>();
        for (var resEntry : resourceCommands.entrySet()) {
            KcAdmV2CommandDescriptor.ResourceDescriptor res = new KcAdmV2CommandDescriptor.ResourceDescriptor();
            res.setName(resEntry.getKey());
            res.setCommands(resEntry.getValue());
            resources.add(res);
        }

        KcAdmV2CommandDescriptor descriptor = new KcAdmV2CommandDescriptor();
        descriptor.setVersion(version);
        descriptor.setResources(resources);
        return descriptor;
    }

    public static void writeDescriptor(KcAdmV2CommandDescriptor descriptor, Path outputFile) throws IOException {
        Files.createDirectories(outputFile.getParent());
        MAPPER.writeValue(outputFile.toFile(), descriptor);
    }

    public static KcAdmV2CommandDescriptor readDescriptor(InputStream is) throws IOException {
        return MAPPER.readValue(is, KcAdmV2CommandDescriptor.class);
    }

    static String extractResourceName(String path) {
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

    /**
     * Parses an OpenAPI JSON spec using SmallRye. Used at build time and for future server-fetch.
     */
    public static OpenAPI parseOpenApi(java.util.function.Supplier<InputStream> specSupplier) {
        return SmallRyeOpenAPI.builder()
                .withCustomStaticFile(specSupplier)
                .enableModelReader(false)
                .enableAnnotationScan(false)
                .enableStandardFilter(false)
                .enableStandardStaticFiles(false)
                .withConfig(EMPTY_CONFIG)
                .build()
                .model();
    }

    private static final Config EMPTY_CONFIG = new Config() {
        @Override public <T> T getValue(String s, Class<T> c) { return null; }
        @Override public ConfigValue getConfigValue(String s) { return null; }
        @Override public <T> Optional<T> getOptionalValue(String s, Class<T> c) { return Optional.empty(); }
        @Override public Iterable<String> getPropertyNames() { return List.of(); }
        @Override public Iterable<ConfigSource> getConfigSources() { return List.of(); }
        @Override public <T> Optional<Converter<T>> getConverter(Class<T> c) { return Optional.empty(); }
        @Override public <T> T unwrap(Class<T> c) { return null; }
    };

    /**
     * Build-time entry point: reads OpenAPI from classpath, writes descriptor to output directory.
     */
    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("Usage: KcAdmV2DescriptorBuilder <output-dir>");
            System.exit(1);
        }

        OpenAPI openApi = parseOpenApi(
                () -> KcAdmV2DescriptorBuilder.class.getResourceAsStream("/META-INF/openapi.json"));

        KcAdmV2CommandDescriptor descriptor = convert(openApi);
        writeDescriptor(descriptor, Path.of(args[0], "kcadm-v2-commands.json"));
    }
}
