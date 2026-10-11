package org.keycloak.admin.client.jackson3.it;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.keycloak.OAuth2Constants;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.representations.info.ServerInfoRepresentation;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the Jackson 3 admin client on the classpath an external project gets, i.e. resolved from the published POM
 * without the dependency management of the Keycloak build.
 * <p>
 * The RESTEasy Jackson provider is built against RESTEasy 7 while the admin client uses the RESTEasy version of Keycloak.
 * Inside the Keycloak build every RESTEasy artifact is forced to one version, so only a build like this one can observe
 * what consumers of the artifact resolve.
 */
public class ConsumerClasspathTest {

    private static final String RESTEASY_GROUP_ID = "org.jboss.resteasy";
    private static final String REALM = "consumer";
    private static final String TOKEN = "consumer-token";
    private static final String SERVER_VERSION = "consumer-test";

    @Test
    public void resteasyArtifactsResolveToKeycloakResteasyVersion() throws IOException {
        String expected = System.getProperty("keycloak.resteasy.version");
        assertNotNull(expected, "keycloak.resteasy.version is expected to be set by the surefire configuration");

        Map<String, String> versions = resteasyArtifactVersions();
        assertFalse(versions.isEmpty(), "no RESTEasy artifacts found on the test classpath");
        versions.forEach((artifactId, version) -> assertEquals(expected, version,
                () -> RESTEASY_GROUP_ID + ":" + artifactId + " resolves to " + version + " for consumers, but the admin client"
                        + " is built and tested against RESTEasy " + expected + "; RESTEasy artifacts resolved: " + versions));
    }

    @Test
    public void adminClientRequestsSucceedAgainstFakeServer() throws Exception {
        AtomicReference<String> tokenRequest = new AtomicReference<>();
        AtomicReference<String> authorization = new AtomicReference<>();

        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/realms/" + REALM + "/protocol/openid-connect/token", exchange -> {
            tokenRequest.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(exchange, "{\"access_token\":\"" + TOKEN + "\",\"expires_in\":300,\"token_type\":\"Bearer\"}");
        });
        server.createContext("/admin/serverinfo", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            respond(exchange, "{\"systemInfo\":{\"version\":\"" + SERVER_VERSION + "\"}}");
        });
        server.start();
        try (Keycloak keycloak = KeycloakBuilder.builder()
                .serverUrl("http://127.0.0.1:" + server.getAddress().getPort())
                .realm(REALM)
                .grantType(OAuth2Constants.CLIENT_CREDENTIALS)
                .clientId("consumer-client")
                .clientSecret("consumer-secret")
                .build()) {
            ServerInfoRepresentation info = keycloak.serverInfo().getInfo();
            assertEquals(SERVER_VERSION, info.getSystemInfo().getVersion());
        } finally {
            server.stop(0);
        }

        assertTrue(tokenRequest.get().contains("grant_type=client_credentials"), "unexpected token request: " + tokenRequest.get());
        assertEquals("Bearer " + TOKEN, authorization.get());
    }

    private static void respond(HttpExchange exchange, String json) throws IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    private static Map<String, String> resteasyArtifactVersions() throws IOException {
        // surefire starts the forked JVM with a manifest-only jar and publishes the real test classpath in this property
        String classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        Map<String, String> versions = new TreeMap<>();
        for (String element : classpath.split(File.pathSeparator)) {
            File file = new File(element);
            if (!file.isFile()) {
                continue;
            }
            try (JarFile jar = new JarFile(file)) {
                Enumeration<JarEntry> entries = jar.entries();
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    if (entry.getName().startsWith("META-INF/maven/" + RESTEASY_GROUP_ID + "/") && entry.getName().endsWith("/pom.properties")) {
                        Properties pom = new Properties();
                        try (InputStream in = jar.getInputStream(entry)) {
                            pom.load(in);
                        }
                        versions.put(pom.getProperty("artifactId"), pom.getProperty("version"));
                    }
                }
            }
        }
        return versions;
    }
}
