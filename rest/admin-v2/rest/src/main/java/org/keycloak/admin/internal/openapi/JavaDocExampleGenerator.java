package org.keycloak.admin.internal.openapi;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.eclipse.microprofile.openapi.models.OpenAPI;
import org.eclipse.microprofile.openapi.models.Operation;
import org.eclipse.microprofile.openapi.models.PathItem;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.MethodInfo;
import org.jboss.jandex.MethodParameterInfo;
import org.jboss.jandex.Type;

import static java.util.stream.Collectors.joining;

final class JavaDocExampleGenerator {

    private static final String CLASSES_DIR_PROPERTY = "keycloak.classes.dir";
    private static final String EXAMPLES_CHECK_OUTPUT_DIR =
            "generated-test-sources/openapi/org/keycloak/admin/internal/openapi";
    private static final String PARAM_SEPARATOR = ", ";
    private static final DotName PATH_ANNOTATION = DotName.createSimple(jakarta.ws.rs.Path.class);
    private static final DotName ADMIN_API = DotName.createSimple(org.keycloak.admin.api.AdminApi.class);
    private static final Set<DotName> HTTP_METHOD_ANNOTATIONS = Set.of(
            DotName.createSimple(jakarta.ws.rs.GET.class),
            DotName.createSimple(jakarta.ws.rs.POST.class),
            DotName.createSimple(jakarta.ws.rs.PUT.class),
            DotName.createSimple(jakarta.ws.rs.PATCH.class),
            DotName.createSimple(jakarta.ws.rs.DELETE.class));
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL);

    record DocCategory(String interfaceName, List<DocEndpoint> endpoints) {
        DocCategory(String interfaceName) {
            this(interfaceName, new ArrayList<>());
        }
    }

    record DocEndpoint(String operationId, String httpMethod, String path,
            String summary, String description,
            RequestBodyInfo requestBody,
            List<ParamInfo> parameters,
            Map<String, ResponseInfo> responses,
            String javaExample) {}

    record RequestBodyInfo(String contentType) {}
    record ParamInfo(String name, String in, String description) {}
    record ResponseInfo(String description) {}
    record JavaExample(String interfaceName, String example) {}

    private final IndexView indexView;

    JavaDocExampleGenerator(IndexView indexView) {
        this.indexView = indexView;
    }

    void generate(OpenAPI openAPI) {
        if (openAPI.getPaths() == null || openAPI.getPaths().getPathItems().isEmpty()) {
            throw new IllegalStateException("OpenAPI spec has no paths — cannot generate documentation examples");
        }

        String classesDir = System.getProperty(CLASSES_DIR_PROPERTY);
        if (classesDir == null) {
            throw new IllegalStateException("System property '" + CLASSES_DIR_PROPERTY
                    + "' is not set — configure it in the smallrye-open-api-maven-plugin systemPropertyVariables");
        }

        StringBuilder checkBody = new StringBuilder();
        Map<String, JavaExample> javaExamples = collectJavaExamples(checkBody);
        Map<String, DocCategory> doc = buildDocModel(openAPI, javaExamples);
        Path targetDir = Path.of(classesDir).getParent();

        try {
            MAPPER.writeValue(targetDir.resolve("admin-v2-doc.json").toFile(), doc);
            writeExamplesCompilationCheck(targetDir, checkBody);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write documentation files", e);
        }
    }

    private static Map<String, DocCategory> buildDocModel(OpenAPI openAPI,
            Map<String, JavaExample> javaExamples) {
        Map<String, DocCategory> categories = new LinkedHashMap<>();

        openAPI.getPaths().getPathItems().forEach((path, pathItem) ->
            pathItem.getOperations().forEach((method, operation) -> {
                String operationId = operation.getOperationId();
                if (operationId == null) {
                    throw new IllegalStateException("Operation without operationId at " + method + " " + path);
                }

                JavaExample javaExample = javaExamples.get(operationId);
                if (javaExample == null) {
                    throw new IllegalStateException("No Java example generated for operation: " + operationId);
                }

                String category = extractResourceName(operation, operationId);
                DocCategory categoryData = categories.computeIfAbsent(category, k -> new DocCategory(javaExample.interfaceName()));

                categoryData.endpoints().add(toEndpoint(method, path, operation, javaExample.example()));
            }));

        return categories;
    }

    private static DocEndpoint toEndpoint(PathItem.HttpMethod method, String path,
            Operation operation, String javaExample) {
        return new DocEndpoint(
                operation.getOperationId(),
                method.name(),
                path,
                operation.getSummary(),
                operation.getDescription(),
                toRequestBody(operation),
                toParameters(operation),
                toResponses(operation),
                javaExample);
    }

    private static RequestBodyInfo toRequestBody(Operation operation) {
        var requestBody = operation.getRequestBody();
        if (requestBody == null || requestBody.getContent() == null) {
            return null;
        }
        var mediaTypes = requestBody.getContent().getMediaTypes();
        if (mediaTypes.isEmpty()) {
            return null;
        }
        return new RequestBodyInfo(mediaTypes.keySet().iterator().next());
    }

    private static List<ParamInfo> toParameters(Operation operation) {
        if (operation.getParameters() == null) {
            return null;
        }
        return operation.getParameters().stream()
                .map(p -> new ParamInfo(p.getName(), p.getIn() != null ? p.getIn().toString() : null, p.getDescription()))
                .toList();
    }

    private static Map<String, ResponseInfo> toResponses(Operation operation) {
        if (operation.getResponses() == null) {
            return null;
        }
        Map<String, ResponseInfo> responses = new LinkedHashMap<>();
        operation.getResponses().getAPIResponses()
                .forEach((code, response) -> responses.put(code, new ResponseInfo(response.getDescription())));
        return responses;
    }

    private Map<String, JavaExample> collectJavaExamples(StringBuilder checkBody) {
        Map<String, JavaExample> examples = new LinkedHashMap<>();
        ClassInfo adminApi = indexView.getClassByName(ADMIN_API);
        if (adminApi == null) {
            throw new IllegalStateException(ADMIN_API + " not found in Jandex index");
        }

        // AdminApi sub-resource locators mirror the wrapper methods on Keycloak admin client.
        // E.g. AdminApi.clients(version) → Keycloak.clients(realm).v2() both return ClientsApi.
        // Variable name is derived from interface: ClientsApi → clientsApi
        // so the doc template can show: ClientsApi clientsApi = adminClient.clients(realm).v2();
        for (MethodInfo method : adminApi.methods()) {
            ClassInfo subResource = resolveSubResourceInterface(method);
            if (subResource != null) {
                String varName = toVariableName(subResource.name());
                checkBody.append("        ").append(subResource.name()).append(" ").append(varName).append(" = null;\n");
                collectExamples(subResource, varName, varName, examples, checkBody);
            }
        }
        return examples;
    }

    private void collectExamples(ClassInfo iface, String docPrefix, String checkPrefix,
            Map<String, JavaExample> examples, StringBuilder checkBody) {
        for (MethodInfo method : iface.methods()) {
            String docCall = methodCall(docPrefix, method.name(), paramNames(method));
            String checkCall = methodCall(checkPrefix, method.name(), paramNulls(method));

            if (HTTP_METHOD_ANNOTATIONS.stream().anyMatch(method::hasAnnotation)) {
                if (examples.containsKey(method.name())) {
                    throw new IllegalStateException("Duplicate operationId: " + method.name()
                            + " on " + iface.name() + " — operationId must be unique across all interfaces");
                }
                examples.put(method.name(), new JavaExample(iface.name().toString(), docCall));
                checkBody.append("        ").append(checkCall).append(";\n");
            } else {
                ClassInfo subResource = resolveSubResourceInterface(method);
                if (subResource != null) {
                    collectExamples(subResource, docCall, checkCall, examples, checkBody);
                }
            }
        }
    }

    private ClassInfo resolveSubResourceInterface(MethodInfo method) {
        if (method.annotation(PATH_ANNOTATION) == null) {
            return null;
        }
        Type returnType = method.returnType();
        if (returnType.kind() != Type.Kind.CLASS) {
            return null;
        }
        ClassInfo returnClass = indexView.getClassByName(returnType.asClassType().name());
        if (returnClass == null || !Modifier.isInterface(returnClass.flags())) {
            return null;
        }
        return returnClass;
    }

    private static void writeExamplesCompilationCheck(Path targetDir, StringBuilder checkBody) throws IOException {
        Path checkDir = targetDir.resolve(EXAMPLES_CHECK_OUTPUT_DIR);
        Files.createDirectories(checkDir);
        Files.writeString(checkDir.resolve("AdminApiV2DocExamplesCheck.java"), """
                package org.keycloak.admin.internal.openapi;

                final class AdminApiV2DocExamplesCheck {
                    static void verify() {
                        %s
                    }
                }
                """.formatted(checkBody));
    }

    private static String paramNames(MethodInfo method) {
        return method.parameters().stream().map(JavaDocExampleGenerator::paramName).collect(joining(PARAM_SEPARATOR));
    }

    private static String paramNulls(MethodInfo method) {
        return method.parameterTypes().stream().map(type -> "(" + type.name() + ") null").collect(joining(PARAM_SEPARATOR));
    }

    private static String paramName(MethodParameterInfo param) {
        String name = param.name();
        if (name == null) {
            throw new IllegalStateException("Parameter name not available for " + param.method().declaringClass().name()
                    + "." + param.method().name() + " — compile with -parameters flag");
        }
        return name;
    }

    private static String methodCall(String prefix, String methodName, String args) {
        return prefix + "." + methodName + "(" + args + ")";
    }

    // TODO: replace tag parsing when https://github.com/keycloak/keycloak/issues/47881 is implemented
    private static String extractResourceName(Operation operation, String operationId) {
        if (operation.getTags() == null) {
            throw new IllegalStateException("Operation " + operationId + " has no tags");
        }
        var pattern = Pattern.compile("(.+) \\(v\\d+\\)");
        for (String tag : operation.getTags()) {
            var matcher = pattern.matcher(tag);
            if (matcher.matches()) {
                return matcher.group(1).toLowerCase();
            }
        }
        throw new IllegalStateException(
                "Operation " + operationId + " has no versioned tag matching 'Name (vN)': " + operation.getTags());
    }

    private static String toVariableName(DotName name) {
        String simpleName = name.withoutPackagePrefix();
        return Character.toLowerCase(simpleName.charAt(0)) + simpleName.substring(1);
    }
}
