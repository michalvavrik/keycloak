package org.keycloak.tests.admin.client.v2;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;

import org.keycloak.client.admin.cli.KcAdmMain;
import org.keycloak.client.admin.cli.v2.KcAdmV2Cmd;
import org.keycloak.client.cli.common.Globals;
import org.keycloak.common.Profile;
import org.keycloak.testframework.annotations.InjectKeycloakUrls;
import org.keycloak.testframework.annotations.KeycloakIntegrationTest;
import org.keycloak.testframework.config.Config;
import org.keycloak.testframework.server.KeycloakServerConfig;
import org.keycloak.testframework.server.KeycloakServerConfigBuilder;
import org.keycloak.testframework.server.KeycloakUrls;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

@KeycloakIntegrationTest(config = KcAdmV2ClientCLITest.V2ApiServerConfig.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class KcAdmV2ClientCLITest {

    @InjectKeycloakUrls
    KeycloakUrls keycloakUrls;

    @TempDir
    Path tempDir;

    private String configFilePath;

    @BeforeAll
    void login() {
        configFilePath = tempDir.resolve("kcadm.config").toString();

        CommandResult result = kcAdmV2Cmd(
                "config", "credentials",
                "--server", keycloakUrls.getBase(),
                "--realm", "master",
                "--client", Config.getAdminClientId(),
                "--secret", Config.getAdminClientSecret());

        assertThat("login should succeed: " + result.err(), result.exitCode(), is(0));
    }

    @Test
    void testCreateClientMinimalParams() {
        CommandResult result = kcAdmV2Cmd("client", "create",
                "--client-id", "test-minimal",
                "--protocol", "openid-connect");

        assertThat("create should succeed: " + result.err(), result.exitCode(), is(0));
    }

    @Test
    void testCreateClientWithAllParams() {
        CommandResult result = kcAdmV2Cmd("client", "create",
                "--client-id", "test-full",
                "--protocol", "openid-connect",
                "--display-name", "Test Full Client",
                "--description", "A test client with all params",
                "--enabled", "true",
                "--app-url", "https://example.com",
                "--redirect-uris", "https://example.com/callback,https://example.com/logout",
                "--roles", "role1,role2");

        assertThat("create should succeed: " + result.err(), result.exitCode(), is(0));
    }

    @Test
    void testCreateClientFromFile() throws Exception {
        Path jsonFile = tempDir.resolve("client.json");
        Files.writeString(jsonFile, """
                {
                    "clientId": "test-from-file",
                    "protocol": "openid-connect",
                    "enabled": true
                }
                """);

        CommandResult result = kcAdmV2Cmd("client", "create",
                "-f", jsonFile.toString());

        assertThat("create from file should succeed: " + result.err(), result.exitCode(), is(0));
    }

    @Test
    void testCreateClientValidationError() {
        CommandResult result = kcAdmV2Cmd("client", "create",
                "--protocol", "openid-connect");

        assertThat("create without clientId should fail", result.exitCode(), is(not(0)));
    }

    @Test
    void testCreateClientFileNotFound() {
        CommandResult result = kcAdmV2Cmd("client", "create",
                "-f", "/nonexistent/file.json");

        assertThat("create with missing file should fail", result.exitCode(), is(not(0)));
        assertThat(result.err(), containsString("not found"));
    }

    @Test
    void testListClients() {
        kcAdmV2Cmd("client", "create",
                "--client-id", "test-for-list",
                "--protocol", "openid-connect");

        CommandResult result = kcAdmV2Cmd("client", "list");

        assertThat("list should succeed: " + result.err(), result.exitCode(), is(0));
        assertThat(result.out(), containsString("test-for-list"));
    }

    @Test
    void testGetClient() {
        CommandResult createResult = kcAdmV2Cmd("client", "create",
                "--client-id", "test-for-get",
                "--protocol", "openid-connect");
        assertThat("setup: create should succeed", createResult.exitCode(), is(0));

        String id = extractId(createResult.out());
        CommandResult result = kcAdmV2Cmd("client", "get", id);

        assertThat("get should succeed: " + result.err(), result.exitCode(), is(0));
        assertThat(result.out(), containsString("test-for-get"));
    }

    @Test
    void testPatchClient() {
        CommandResult createResult = kcAdmV2Cmd("client", "create",
                "--client-id", "test-for-patch",
                "--protocol", "openid-connect",
                "--enabled", "true");
        assertThat("setup: create should succeed", createResult.exitCode(), is(0));

        String id = extractId(createResult.out());
        CommandResult patchResult = kcAdmV2Cmd("client", "patch", id,
                "--enabled", "false");
        assertThat("patch should succeed: " + patchResult.err(), patchResult.exitCode(), is(0));

        CommandResult getResult = kcAdmV2Cmd("client", "get", id);
        assertThat(getResult.out(), containsString("\"enabled\""));
        assertThat(getResult.out(), containsString("false"));
    }

    @Test
    void testDeleteClient() {
        CommandResult createResult = kcAdmV2Cmd("client", "create",
                "--client-id", "test-for-delete",
                "--protocol", "openid-connect");
        assertThat("setup: create should succeed", createResult.exitCode(), is(0));

        String id = extractId(createResult.out());
        CommandResult deleteResult = kcAdmV2Cmd("client", "delete", id);
        assertThat("delete should succeed: " + deleteResult.err(), deleteResult.exitCode(), is(0));

        CommandResult getResult = kcAdmV2Cmd("client", "get", id);
        assertThat("get after delete should fail", getResult.exitCode(), is(not(0)));
    }

    private CommandResult kcAdmV2Cmd(String... args) {
        CommandLine cli = Globals.createCommandLine(new KcAdmV2Cmd(), KcAdmMain.CMD, new PrintWriter(System.err, true));

        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        cli.setOut(new PrintWriter(out));
        cli.setErr(new PrintWriter(err));

        String[] fullArgs = new String[args.length + 2];
        fullArgs[0] = "--config";
        fullArgs[1] = configFilePath;
        System.arraycopy(args, 0, fullArgs, 2, args.length);

        int exitCode = cli.execute(fullArgs);
        return new CommandResult(exitCode, out.toString(), err.toString());
    }

    private String extractId(String output) {
        // TODO: depends on HTTP implementation response format
        return output.trim();
    }

    record CommandResult(int exitCode, String out, String err) {
    }

    public static class V2ApiServerConfig implements KeycloakServerConfig {
        @Override
        public KeycloakServerConfigBuilder configure(KeycloakServerConfigBuilder config) {
            return config.features(Profile.Feature.CLIENT_ADMIN_API_V2);
        }
    }
}
