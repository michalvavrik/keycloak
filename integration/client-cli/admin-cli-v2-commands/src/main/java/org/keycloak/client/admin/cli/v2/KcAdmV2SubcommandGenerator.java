package org.keycloak.client.admin.cli.v2;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.quarkus.gizmo2.Const;
import io.quarkus.gizmo2.Gizmo;
import io.quarkus.gizmo2.ClassOutput;
import io.quarkus.gizmo2.desc.FieldDesc;
import io.quarkus.gizmo2.desc.MethodDesc;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.Indexer;
import org.jboss.jandex.MethodInfo;
import picocli.CommandLine.Command;

/**
 * Generates PicoCLI command classes from JAX-RS API interfaces using Jandex + Gizmo 2.
 * <p>
 * Scans for {@code @GET}, {@code @POST}, {@code @PUT}, {@code @PATCH}, {@code @DELETE}
 * annotations on interface methods. Uses {@code @Operation(summary=...)} for descriptions.
 * Skips sub-resource locator methods (those with {@code @Path} but no HTTP verb).
 * <p>
 * Generated classes implement {@link KcAdmV2Subcommand} and are registered via
 * {@code META-INF/services} for {@link java.util.ServiceLoader} discovery.
 */
public class KcAdmV2SubcommandGenerator {

    private static final String GENERATED_PACKAGE = "org.keycloak.client.admin.cli.v2.generated";
    private static final String SERVICE_FILE = "META-INF/services/" + KcAdmV2Subcommand.class.getName();

    private static final Map<String, String> HTTP_VERB_TO_COMMAND = Map.of(
            "jakarta.ws.rs.GET", "get",
            "jakarta.ws.rs.POST", "create",
            "jakarta.ws.rs.PUT", "update",
            "jakarta.ws.rs.PATCH", "patch",
            "jakarta.ws.rs.DELETE", "delete"
    );

    /**
     * Generates PicoCLI command classes and the ServiceLoader registration file.
     */
    public void generate(Path outputDir, Class<?>... apiClasses) throws IOException {
        IndexView index = buildIndex(apiClasses);

        List<String> generatedClassNames = new ArrayList<>();
        Gizmo gizmo = Gizmo.create(ClassOutput.fileWriter(outputDir));

        for (ClassInfo classInfo : index.getKnownClasses()) {
            if (!Modifier.isInterface(classInfo.flags())) {
                continue;
            }

            String resourceName = deriveResourceName(classInfo.simpleName());

            for (MethodInfo method : classInfo.methods()) {
                String commandName = resolveCommandName(method);
                if (commandName == null) {
                    continue;
                }

                // disambiguate: "get" from ClientsApi (collection) becomes "list"
                if ("get".equals(commandName) && isCollectionReturn(method)) {
                    commandName = "list";
                }

                String description = resolveDescription(method, commandName);
                String className = GENERATED_PACKAGE + "."
                        + capitalize(resourceName) + capitalize(commandName) + "Cmd";

                generateSubcommand(gizmo, className, commandName, description, classInfo, method);
                generatedClassNames.add(className);
            }
        }

        writeServiceFile(outputDir, generatedClassNames);
    }

    /**
     * Resolves the CLI command name from JAX-RS HTTP verb annotations.
     * Returns null for sub-resource locators (methods with no HTTP verb).
     */
    private String resolveCommandName(MethodInfo method) {
        for (var entry : HTTP_VERB_TO_COMMAND.entrySet()) {
            if (method.hasAnnotation(DotName.createSimple(entry.getKey()))) {
                return entry.getValue();
            }
        }
        return null;
    }

    private String resolveDescription(MethodInfo method, String commandName) {
        AnnotationInstance operation = method.annotation(
                DotName.createSimple("org.eclipse.microprofile.openapi.annotations.Operation"));
        if (operation != null) {
            AnnotationValue summary = operation.value("summary");
            if (summary != null) {
                return summary.asString();
            }
        }
        return capitalize(commandName) + " " + method.declaringClass().simpleName();
    }

    private boolean isCollectionReturn(MethodInfo method) {
        String returnType = method.returnType().name().toString();
        return returnType.equals("java.util.stream.Stream")
                || returnType.equals("java.util.List")
                || returnType.equals("java.util.Collection");
    }

    private void generateSubcommand(Gizmo gizmo, String className, String commandName,
            String description, ClassInfo api, MethodInfo method) {
        gizmo.class_(className, cc -> {
            cc.public_();
            cc.implements_(KcAdmV2Subcommand.class);

            cc.addAnnotation(Command.class, ann -> {
                ann.add("name", commandName);
                ann.addArray("description", description);
                ann.add("mixinStandardHelpOptions", true);
            });

            cc.constructor(con -> {
                con.public_();
                con.body(b0 -> {
                    b0.invokeSpecial(MethodDesc.of(Object.class, "<init>", void.class), con.this_());
                    b0.return_();
                });
            });

            cc.method("run", mc -> {
                mc.public_();
                mc.body(b0 -> {
                    var sysOut = b0.getStaticField(FieldDesc.of(System.class, "out"));
                    b0.invokeVirtual(
                            MethodDesc.of(PrintStream.class, "println", void.class, String.class),
                            sysOut,
                            Const.of("v2: " + api.simpleName() + "." + method.name() + "() invoked"));
                    b0.return_();
                });
            });
        });
    }

    private void writeServiceFile(Path outputDir, List<String> classNames) throws IOException {
        Path serviceFile = outputDir.resolve(SERVICE_FILE);
        Files.createDirectories(serviceFile.getParent());
        Files.writeString(serviceFile, String.join("\n", classNames) + "\n");
    }

    private IndexView buildIndex(Class<?>... classes) throws IOException {
        Indexer indexer = new Indexer();
        for (Class<?> cls : classes) {
            String classFile = cls.getName().replace('.', '/') + ".class";
            try (InputStream is = cls.getClassLoader().getResourceAsStream(classFile)) {
                if (is == null) {
                    throw new IOException("Cannot find class file for: " + cls.getName());
                }
                indexer.index(is);
            }
        }
        return indexer.complete();
    }

    static String deriveResourceName(String interfaceName) {
        String name = interfaceName;
        // ClientsApi -> client, ClientApi -> client (both map to the same resource)
        if (name.endsWith("sApi")) {
            name = name.substring(0, name.length() - 4);
        } else if (name.endsWith("Api")) {
            name = name.substring(0, name.length() - 3);
        }
        return name.substring(0, 1).toLowerCase() + name.substring(1);
    }

    private static String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /**
     * Entry point for exec-maven-plugin during the build.
     * @param args single argument: the output directory (typically {@code target/classes})
     */
    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.err.println("Usage: KcAdmV2SubcommandGenerator <output-dir>");
            System.exit(1);
        }
        new KcAdmV2SubcommandGenerator().generate(Path.of(args[0]),
                Class.forName("org.keycloak.admin.api.client.ClientsApi"),
                Class.forName("org.keycloak.admin.api.client.ClientApi"));
    }
}
