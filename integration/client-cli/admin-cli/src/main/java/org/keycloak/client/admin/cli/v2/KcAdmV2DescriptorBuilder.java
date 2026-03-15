package org.keycloak.client.admin.cli.v2;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.smallrye.openapi.api.SmallRyeOpenAPI;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigValue;
import org.eclipse.microprofile.config.spi.ConfigSource;
import org.eclipse.microprofile.config.spi.Converter;
import org.eclipse.microprofile.openapi.models.OpenAPI;
import org.eclipse.microprofile.openapi.models.Operation;
import org.eclipse.microprofile.openapi.models.PathItem;
import org.eclipse.microprofile.openapi.models.media.MediaType;
import org.eclipse.microprofile.openapi.models.media.Schema;
import org.eclipse.microprofile.openapi.models.parameters.RequestBody;

import static org.keycloak.client.cli.util.HttpUtil.APPLICATION_JSON;
import static org.keycloak.common.util.ObjectUtil.capitalize;

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
                populateOptionsAndVariants(cmd, operation, openApi);

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

    private static void populateOptionsAndVariants(
            KcAdmV2CommandDescriptor.CommandDescriptor cmd, Operation operation, OpenAPI openApi) {
        Schema schema = extractRequestBodySchema(operation, openApi);

        if (schema == null && operation.getRequestBody() != null) {
            schema = extractResponseSchema(operation, openApi);
        }

        if (schema == null) {
            cmd.setOptions(List.of());
            return;
        }

        if (schema.getDiscriminator() != null && schema.getDiscriminator().getMapping() != null) {
            cmd.setOptions(List.of());
            cmd.setVariants(buildVariants(schema, openApi));
        } else {
            cmd.setOptions(toOptionDescriptors(collectProperties(schema, openApi), openApi));
        }
    }

    private static List<KcAdmV2CommandDescriptor.VariantDescriptor> buildVariants(
            Schema baseSchema, OpenAPI openApi) {
        String discriminatorField = baseSchema.getDiscriminator().getPropertyName();
        Map<String, Schema> baseProperties = collectProperties(baseSchema, openApi);

        List<KcAdmV2CommandDescriptor.VariantDescriptor> variants = new ArrayList<>();
        for (var mapping : baseSchema.getDiscriminator().getMapping().entrySet()) {
            String discriminatorValue = mapping.getKey();
            String ref = mapping.getValue();
            Schema subtypeSchema = resolveSchema(ref, openApi);

            Map<String, Schema> allProperties = new LinkedHashMap<>(baseProperties);
            if (subtypeSchema != null) {
                allProperties.putAll(collectProperties(subtypeSchema, openApi));
            }
            allProperties.remove(discriminatorField);

            String variantName = toVariantName(discriminatorValue);

            KcAdmV2CommandDescriptor.VariantDescriptor variant = new KcAdmV2CommandDescriptor.VariantDescriptor();
            variant.setName(variantName);
            variant.setDiscriminatorField(discriminatorField);
            variant.setDiscriminatorValue(discriminatorValue);
            variant.setOptions(toOptionDescriptors(allProperties, openApi));
            variants.add(variant);
        }
        return variants;
    }

    private static List<KcAdmV2CommandDescriptor.OptionDescriptor> toOptionDescriptors(
            Map<String, Schema> properties, OpenAPI openApi) {
        List<KcAdmV2CommandDescriptor.OptionDescriptor> options = new ArrayList<>();
        for (var propEntry : properties.entrySet()) {
            String fieldName = propEntry.getKey();
            Schema propSchema = propEntry.getValue();

            Schema resolved = propSchema.getRef() != null ? resolveSchema(propSchema, openApi) : propSchema;

            if (isObjectType(resolved)) {
                flattenNestedObject(fieldName, resolved, openApi, options);
            } else {
                options.add(createOptionDescriptor(fieldName, null, propSchema, openApi));
            }
        }
        return options;
    }

    private static void flattenNestedObject(String parentFieldName, Schema schema,
            OpenAPI openApi, List<KcAdmV2CommandDescriptor.OptionDescriptor> options) {
        Map<String, Schema> nestedProperties = collectProperties(schema, openApi);
        for (var propEntry : nestedProperties.entrySet()) {
            KcAdmV2CommandDescriptor.OptionDescriptor opt =
                    createOptionDescriptor(propEntry.getKey(), parentFieldName, propEntry.getValue(), openApi);
            opt.setName(camelToKebab(parentFieldName) + "-" + camelToKebab(propEntry.getKey()));
            options.add(opt);
        }
    }

    private static KcAdmV2CommandDescriptor.OptionDescriptor createOptionDescriptor(
            String fieldName, String parentFieldName, Schema propSchema, OpenAPI openApi) {
        KcAdmV2CommandDescriptor.OptionDescriptor opt = new KcAdmV2CommandDescriptor.OptionDescriptor();
        opt.setFieldName(fieldName);
        opt.setParentFieldName(parentFieldName);
        opt.setName(camelToKebab(fieldName));
        opt.setDescription(propSchema.getDescription());
        opt.setArray(isArrayType(propSchema));
        opt.setType(resolveType(propSchema));
        opt.setEnumValues(extractEnumValues(propSchema, openApi));
        return opt;
    }

    private static List<String> extractEnumValues(Schema schema, OpenAPI openApi) {
        Schema target = schema;
        if (isArrayType(schema) && schema.getItems() != null) {
            target = schema.getItems();
            if (target.getRef() != null) {
                target = resolveSchema(target, openApi);
            }
        }
        if (target != null && target.getEnumeration() != null && !target.getEnumeration().isEmpty()) {
            return target.getEnumeration().stream().map(Object::toString).toList();
        }
        return null;
    }

    private static boolean isObjectType(Schema schema) {
        return schema != null && schema.getProperties() != null && !schema.getProperties().isEmpty();
    }

    private static Schema extractRequestBodySchema(Operation operation, OpenAPI openApi) {
        RequestBody requestBody = operation.getRequestBody();
        if (requestBody == null || requestBody.getContent() == null) {
            return null;
        }
        MediaType mediaType = requestBody.getContent().getMediaType(APPLICATION_JSON);
        if (mediaType == null || mediaType.getSchema() == null) {
            return null;
        }
        return resolveSchema(mediaType.getSchema(), openApi);
    }

    private static Schema extractResponseSchema(Operation operation, OpenAPI openApi) {
        if (operation.getResponses() == null) {
            return null;
        }
        for (var response : operation.getResponses().getAPIResponses().values()) {
            if (response.getContent() == null) {
                continue;
            }
            MediaType mediaType = response.getContent().getMediaType(APPLICATION_JSON);
            if (mediaType != null && mediaType.getSchema() != null) {
                return resolveSchema(mediaType.getSchema(), openApi);
            }
        }
        return null;
    }

    private static Schema resolveSchema(Schema schema, OpenAPI openApi) {
        if (schema.getRef() != null) {
            return resolveSchema(schema.getRef(), openApi);
        }
        return schema;
    }

    private static Schema resolveSchema(String ref, OpenAPI openApi) {
        String schemaName = ref.substring(ref.lastIndexOf('/') + 1);
        return openApi.getComponents().getSchemas().get(schemaName);
    }

    private static Map<String, Schema> collectProperties(Schema schema, OpenAPI openApi) {
        Map<String, Schema> result = new LinkedHashMap<>();
        if (schema.getAllOf() != null) {
            for (Schema part : schema.getAllOf()) {
                Schema resolved = resolveSchema(part, openApi);
                if (resolved != null) {
                    result.putAll(collectProperties(resolved, openApi));
                }
            }
        }

        if (schema.getProperties() != null) {
            result.putAll(schema.getProperties());
        }

        return result;
    }

    private static boolean isArrayType(Schema schema) {
        return schema.getType() != null && schema.getType().contains(Schema.SchemaType.ARRAY);
    }

    private static String resolveType(Schema schema) {
        if (isArrayType(schema)) {
            if (schema.getItems() != null && schema.getItems().getType() != null
                    && !schema.getItems().getType().isEmpty()) {
                return schema.getItems().getType().get(0).name().toLowerCase();
            }
            return "string";
        }
        if (schema.getType() != null && !schema.getType().isEmpty()) {
            return schema.getType().get(0).name().toLowerCase();
        }
        if (schema.getRef() != null) {
            return "object";
        }
        return "string";
    }

    private static final Map<String, String> VARIANT_NAME_MAP = Map.of(
            "openid-connect", "oidc"
    );

    private static String toVariantName(String discriminatorValue) {
        return VARIANT_NAME_MAP.getOrDefault(discriminatorValue, discriminatorValue);
    }

    private static String camelToKebab(String camelCase) {
        return camelCase.replaceAll("([a-z])([A-Z])", "$1-$2").toLowerCase();
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
