package org.keycloak.client.admin.cli.v2;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.keycloak.client.admin.cli.KcAdmMain;
import org.keycloak.client.admin.cli.v2.KcAdmV2CommandDescriptor.CommandDescriptor;
import org.keycloak.client.cli.common.Globals;
import org.keycloak.client.cli.config.ConfigData;
import org.keycloak.client.cli.util.OutputUtil;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import picocli.CommandLine;
import picocli.CommandLine.Command;

import static org.keycloak.client.admin.cli.KcAdmMain.CMD;
import static org.keycloak.client.admin.cli.KcAdmMain.V2_FLAG;
import static org.keycloak.client.cli.util.IoUtil.readFully;
import static org.keycloak.client.cli.util.OsUtil.OS_ARCH;

@Command
public final class KcAdmV2EditCmd extends KcAdmV2RequestExecutor {

    private static final String ENV_KC_CLI_EDITOR = "KC_CLI_EDITOR";
    private static final String ENV_VISUAL = "VISUAL";
    private static final String ENV_EDITOR = "EDITOR";
    private static final String DEFAULT_EDITOR_UNIX = "vi";
    private static final String DEFAULT_EDITOR_WINDOWS = "notepad";
    private static final int EXIT_CODE_COMMAND_NOT_FOUND = 127;

    private final String getMethod;
    private final String patchMethod;

    KcAdmV2EditCmd(CommandDescriptor getDescriptor, CommandDescriptor patchDescriptor) {
        super(getDescriptor, null);
        this.getMethod = getDescriptor.getHttpMethod().toLowerCase();
        this.patchMethod = patchDescriptor.getHttpMethod().toLowerCase();
    }

    @Override
    public void run() {
        if (Globals.help) {
            spec.commandLine().usage(spec.commandLine().getOut());
            return;
        }

        PrintWriter out = spec.commandLine().getOut();
        PrintWriter err = spec.commandLine().getErr();

        try {
            RequestContext ctx = prepareRequest();
            String url = buildUrl(ctx.configData(), null);

            String originalJson = readFully(executeRequest(getMethod, url, ctx.token(), null, null).getBody());
            JsonNode originalNode = OutputUtil.MAPPER.readTree(originalJson);

            String editor = resolveEditor(ctx.configData());
            String prettyOriginal = OutputUtil.MAPPER.writeValueAsString(originalNode);
            String modifiedContent = openInEditor(editor, prettyOriginal);

            final JsonNode modifiedNode;
            try {
                modifiedNode = OutputUtil.MAPPER.readTree(modifiedContent);
            } catch (Exception e) {
                throw new RuntimeException("Modified content is not valid JSON: " + e.getMessage());
            }

            ObjectNode diff = computeMergePatchDiff(originalNode, modifiedNode);
            if (diff.isEmpty()) {
                err.println("Edit cancelled, no changes made.");
                return;
            }

            String diffJson = OutputUtil.MAPPER.writeValueAsString(diff);
            err.println("Changes to apply:");
            if (CommandLine.Help.Ansi.AUTO.enabled()) {
                try {
                    err.println(CliJsonOutputHighlighter.highlight(diffJson));
                } catch (IOException ignored) {
                    err.println(diffJson);
                }
            } else {
                err.println(diffJson);
            }

            String responseBody = readFully(
                    executeRequest(patchMethod, url, ctx.token(), diffJson, MERGE_PATCH_JSON).getBody());
            out.println(formatOutput(responseBody));
        } catch (CommandLine.ExecutionException e) {
            throw e;
        } catch (Exception e) {
            String message = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            throw new CommandLine.ExecutionException(spec.commandLine(), message, e);
        }
    }

    private String resolveEditor(ConfigData configData) {
        String editor = configData.getEditor();
        if (editor != null && !editor.isBlank()) {
            return editor;
        }

        editor = System.getenv(ENV_KC_CLI_EDITOR);
        if (editor != null && !editor.isBlank()) {
            return editor;
        }

        editor = System.getenv(ENV_VISUAL);
        if (editor != null && !editor.isBlank()) {
            return editor;
        }

        editor = System.getenv(ENV_EDITOR);
        if (editor != null && !editor.isBlank()) {
            return editor;
        }

        return OS_ARCH.isWindows() ? DEFAULT_EDITOR_WINDOWS : DEFAULT_EDITOR_UNIX;
    }

    private String openInEditor(String editor, String content) throws IOException, InterruptedException {
        Path tempFile = Files.createTempFile("kcadm-edit-", ".json");
        try {
            Files.writeString(tempFile, content);

            ProcessBuilder pb;
            if (OS_ARCH.isWindows()) {
                pb = new ProcessBuilder("cmd", "/c", editor + " \"" + tempFile + "\"");
            } else {
                pb = new ProcessBuilder("/bin/sh", "-c", editor + " \"" + tempFile + "\"");
            }
            pb.inheritIO();

            int exitCode = pb.start().waitFor();
            if (exitCode == EXIT_CODE_COMMAND_NOT_FOUND) {
                throw new RuntimeException("Editor '" + editor + "' not found. "
                        + "Configure your editor with: " + KcAdmMain.CMD + " " + KcAdmMain.V2_FLAG
                        + " config editor <editor> or set the " + ENV_KC_CLI_EDITOR + " environment variable.");
            }
            if (exitCode != 0) {
                throw new RuntimeException("Editor exited with error (exit code " + exitCode + "). "
                        + "Verify your editor works correctly or reconfigure with: "
                        + KcAdmMain.CMD + " " + KcAdmMain.V2_FLAG + " config editor <editor>");
            }

            return Files.readString(tempFile);
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    // RFC 7396 algorithm implementation
    public static ObjectNode computeMergePatchDiff(JsonNode original, JsonNode modified) {
        ObjectNode diff = OutputUtil.MAPPER.createObjectNode();

        modified.properties().forEach(entry -> {
            String key = entry.getKey();
            JsonNode modifiedValue = entry.getValue();
            JsonNode originalValue = original.get(key);
            if (originalValue == null) {
                // new values
                diff.set(key, modifiedValue);
            } else if (!modifiedValue.equals(originalValue)) {
                if (originalValue.isObject() && modifiedValue.isObject()) {
                    ObjectNode nestedDiff = computeMergePatchDiff(originalValue, modifiedValue);
                    if (!nestedDiff.isEmpty()) {
                        // modified values
                        diff.set(key, nestedDiff);
                    }
                } else {
                    // modified values
                    diff.set(key, modifiedValue);
                }
            }
        });

        // removed values
        original.propertyStream().map(Map.Entry::getKey).filter(k -> !modified.has(k)).forEach(diff::putNull);

        return diff;
    }

    static String[] createDescription(String resourceName) {
        return new String[] {
                "Edit a " + resourceName + " by opening it in a text editor and applying the modifications.",
                "Editor is resolved from (in order): '" + CMD + " " + V2_FLAG + " config editor' setting,",
                ENV_KC_CLI_EDITOR + ", " + ENV_VISUAL + ", " + ENV_EDITOR + " environment variables, or "
                        + DEFAULT_EDITOR_UNIX + " (" + DEFAULT_EDITOR_WINDOWS + " on Windows)."
        };
    }
}
