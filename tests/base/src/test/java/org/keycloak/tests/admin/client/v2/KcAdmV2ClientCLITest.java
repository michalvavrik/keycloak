package org.keycloak.tests.admin.client.v2;

import java.io.File;
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
import org.keycloak.testframework.annotations.TestSetup;
import org.keycloak.testframework.config.Config;
import org.keycloak.testframework.server.KeycloakServerConfig;
import org.keycloak.testframework.server.KeycloakServerConfigBuilder;
import org.keycloak.testframework.server.KeycloakUrls;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

@KeycloakIntegrationTest(config = KcAdmV2ClientCLITest.V2ApiServerConfig.class)
public class KcAdmV2ClientCLITest {

    @InjectKeycloakUrls
    KeycloakUrls keycloakUrls;

    @TempDir
    static File tempDir;

    private static String configFilePath;

    @TestSetup
    public void login() {
        configFilePath = new File(tempDir, "kcadm.config").getAbsolutePath();

        CommandResult result = kcAdmV2Cmd(
                "config", "credentials",
                "--server", keycloakUrls.getBase(),
                "--realm", "master",
                "--client", Config.getAdminClientId(),
                "--secret", Config.getAdminClientSecret());

        assertThat("login should succeed: " + result.err(), result.exitCode(), is(0));
    }

    @Test
    void testCreateClientValidationError() {
        CommandResult result = kcAdmV2Cmd("client", "create", "oidc");

        assertThat("create without clientId should fail", result.exitCode(), is(not(0)));
        assertThat("error should clarify that provided data are invalid: " + result.err(),
                result.err().toLowerCase(), containsString("invalid"));
    }

    @Test
    void testCreateClientFileNotFound() {
        CommandResult result = kcAdmV2Cmd("client", "create", "oidc", "-f", "/nonexistent/file.json");

        assertThat("create with missing file should fail", result.exitCode(), is(not(0)));
        assertThat(result.err(), containsString("not found"));
    }

    @Test
    void testFileAndFieldOptionsMutuallyExclusive() throws Exception {
        Path jsonFile = new File(tempDir, "exclusive.json").toPath();
        Files.writeString(jsonFile, """
                {"clientId": "test-exclusive", "protocol": "openid-connect"}
                """);

        CommandResult result = kcAdmV2Cmd("client", "create", "oidc",
                "-f", jsonFile.toString(), "--client-id", "test-exclusive");

        assertThat("should fail when both -f and field options used", result.exitCode(), is(not(0)));
        assertThat(result.err(), containsString("mutually exclusive"));
    }

    @Test
    void testAuthNestedObjectMerged() {
        CommandResult result = kcAdmV2Cmd("client", "create", "oidc",
                "--client-id", "test-auth-nested",
                "--auth-method", "client_secret",
                "--auth-secret", "my-secret-value");

        assertThat("create should succeed: " + result.err(), result.exitCode(), is(0));

        String id = extractId(result);
        CommandResult getResult = kcAdmV2Cmd("client", "get", id);
        assertThat("get should succeed", getResult.exitCode(), is(0));
        assertThat(getResult.out(), containsString("client_secret"));
        assertThat(getResult.out(), containsString("my-secret-value"));
    }

    @Test
    void testListClients() {
        kcAdmV2Cmd("client", "create", "oidc", "--client-id", "test-for-list-1");
        kcAdmV2Cmd("client", "create", "oidc", "--client-id", "test-for-list-2");

        CommandResult result = kcAdmV2Cmd("client", "list");

        assertThat("list should succeed: " + result.err(), result.exitCode(), is(0));
        assertThat(result.out(), containsString("test-for-list-1"));
        assertThat(result.out(), containsString("test-for-list-2"));
    }

    @Test
    void testGetClient() {
        String id = createClientWithAllParams("test-for-get");

        CommandResult result = kcAdmV2Cmd("client", "get", id);

        assertThat("get should succeed: " + result.err(), result.exitCode(), is(0));
        assertThat(result.out(), containsString("test-for-get"));
        assertThat(result.out(), containsString("A test client with all params"));
        assertThat(result.out(), containsString("https://example.com/callback"));
        assertThat(result.out(), containsString("https://example.com/logout"));
        assertThat(result.out(), containsString("role1"));
        assertThat(result.out(), containsString("role2"));
        assertThat(result.out(), containsString("STANDARD"));
        assertThat(result.out(), containsString("SERVICE_ACCOUNT"));
        assertThat(result.out(), containsString("client_secret"));
    }

    @Test
    void testPatchClient() {
        CommandResult createResult = kcAdmV2Cmd("client", "create", "oidc",
                "--client-id", "test-for-patch", "--enabled", "true");
        assertThat("setup: create should succeed", createResult.exitCode(), is(0));
        assertThat(createResult.out(), containsString("\"enabled\":true"));

        String id = extractId(createResult);
        CommandResult patchResult = kcAdmV2Cmd("client", "patch", "oidc", id, "--enabled", "false");
        assertThat("patch should succeed: " + patchResult.err(), patchResult.exitCode(), is(0));
        assertThat(patchResult.out(), containsString("\"enabled\":false"));
    }

    @Test
    void testDeleteClient() throws Exception {
        String id = createClientFromFile("test-for-delete");

        CommandResult getResult = kcAdmV2Cmd("client", "get", id);
        assertThat("get after delete should fail", getResult.exitCode(), is(0));

        CommandResult deleteResult = kcAdmV2Cmd("client", "delete", id);
        assertThat("delete should succeed: " + deleteResult.err(), deleteResult.exitCode(), is(0));

        getResult = kcAdmV2Cmd("client", "get", id);
        assertThat("get after delete should fail", getResult.exitCode(), is(not(0)));
    }

    private String createClientWithAllParams(String clientId) {
        CommandResult result = kcAdmV2Cmd("client", "create", "oidc",
                "--client-id", clientId,
                "--display-name", "Test Full Client",
                "--description", "A test client with all params",
                "--enabled", "true",
                "--app-url", "https://example.com",
                "--redirect-uris", "https://example.com/callback,https://example.com/logout",
                "--roles", "role1,role2",
                "--login-flows", "STANDARD,SERVICE_ACCOUNT",
                "--auth-method", "client_secret");
        assertThat("create should succeed: " + result.err(), result.exitCode(), is(0));
        return extractId(result);
    }

    private String createClientFromFile(String clientId) throws Exception {
        Path jsonFile = new File(tempDir, clientId + ".json").toPath();
        Files.writeString(jsonFile, """
                {
                    "clientId": "%s",
                    "protocol": "saml",
                    "enabled": true
                }
                """.formatted(clientId));
        CommandResult result = kcAdmV2Cmd("client", "create", "saml", "-f", jsonFile.toString());
        assertThat("create from file should succeed: " + result.err(), result.exitCode(), is(0));
        return extractId(result);
    }

    private CommandResult kcAdmV2Cmd(String... args) {
        CommandLine cli = Globals.createCommandLine(new KcAdmV2Cmd(), KcAdmMain.CMD, new PrintWriter(System.err, true));

        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        cli.setOut(new PrintWriter(out));
        cli.setErr(new PrintWriter(err));

        String[] fullArgs = new String[args.length + 2];
        System.arraycopy(args, 0, fullArgs, 0, args.length);
        fullArgs[args.length] = "--config";
        fullArgs[args.length + 1] = configFilePath;

        int exitCode = cli.execute(fullArgs);
        return new CommandResult(exitCode, out.toString(), err.toString());
    }

    private String extractId(CommandResult result) {
        try {
            return new ObjectMapper().readTree(result.out()).get("clientId").asText();
        } catch (Exception e) {
            throw new AssertionError("Could not extract clientId from: " + result.out(), e);
        }
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
