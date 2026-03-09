package org.keycloak.client.admin.cli.v2;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigValue;
import org.eclipse.microprofile.config.spi.ConfigSource;
import org.eclipse.microprofile.config.spi.Converter;
import org.eclipse.microprofile.openapi.models.OpenAPI;
import org.keycloak.client.cli.common.BaseGlobalOptionsCmd;

import io.smallrye.openapi.api.SmallRyeOpenAPI;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

import java.util.List;
import java.util.Optional;

@Command(name = "kcadm",
        header = {
                "Keycloak Admin CLI v2 (experimental)",
                "",
                "Find more information at: https://www.keycloak.org/docs/latest"
        },
        description = "%nCOMMAND [ARGUMENTS]"
)
public class KcAdmV2Cmd extends BaseGlobalOptionsCmd {

    private static final String BUNDLED_OPENAPI = "/META-INF/openapi.json";

    @Spec
    CommandSpec spec;

    @Override
    protected boolean nothingToDo() {
        return true;
    }

    @Override
    protected String help() {
        return "";
    }

    @Override
    protected void printHelpIfNeeded() {
        spec.commandLine().usage(System.out);
        System.exit(CommandLine.ExitCode.OK);
    }

    @Override
    protected void configureCommandLine(CommandLine cli) {
        OpenAPI openApi = loadOpenApi();
        V2CommandBuilder.addCommands(cli, openApi);
    }

    private OpenAPI loadOpenApi() {
        // TODO: for long-lived sessions, check ~/.keycloak/ cache for server-specific spec
        // ConfigData already has serverUrl — use it as cache key:
        //   1. check ~/.keycloak/openapi-<hash(serverUrl)>.json
        //   2. if stale or missing, fetch from server: GET {serverUrl}/admin/api/openapi.json
        //   3. cache the response
        // For now, always use the bundled default
        return loadBundledOpenApi();
    }

    private OpenAPI loadBundledOpenApi() {
        SmallRyeOpenAPI result = SmallRyeOpenAPI.builder()
                .withCustomStaticFile(() -> getClass().getResourceAsStream(BUNDLED_OPENAPI))
                .enableModelReader(false)
                .enableAnnotationScan(false)
                .enableStandardFilter(false)
                .enableStandardStaticFiles(false)
                .withConfig(new Config() {
                    @Override
                    public <T> T getValue(String s, Class<T> aClass) {
                        return null;
                    }

                    @Override
                    public ConfigValue getConfigValue(String s) {
                        return null;
                    }

                    @Override
                    public <T> Optional<T> getOptionalValue(String s, Class<T> aClass) {
                        return Optional.empty();
                    }

                    @Override
                    public Iterable<String> getPropertyNames() {
                        return List.of();
                    }

                    @Override
                    public Iterable<ConfigSource> getConfigSources() {
                        return List.of();
                    }

                    @Override
                    public <T> Optional<Converter<T>> getConverter(Class<T> aClass) {
                        return Optional.empty();
                    }

                    @Override
                    public <T> T unwrap(Class<T> aClass) {
                        return null;
                    }
                })
                .build();
        return result.model();
    }
}
