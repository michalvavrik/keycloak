/*
 * Copyright 2025 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.keycloak.it.storage.database.dist;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;

import org.keycloak.it.junit5.extension.CLIResult;
import org.keycloak.it.junit5.extension.DistributionTest;
import org.keycloak.it.junit5.extension.KeycloakRunner;
import org.keycloak.it.junit5.extension.RawDistOnly;
import org.keycloak.it.junit5.extension.StopServer;
import org.keycloak.it.junit5.extension.TestProvider;
import org.keycloak.it.resource.pqc.PqcTlsTestProvider;
import org.keycloak.it.utils.RawKeycloakDistribution;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.utility.DockerImageName;

import static io.restassured.RestAssured.given;
import static io.restassured.RestAssured.when;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Tests that Keycloak can connect to PostgreSQL over TLS 1.3 with PQC hybrid key exchange (X25519MLKEM768).
 *
 * <p>The test uses a Fedora 44-based PostgreSQL container configured with OpenSSL 3.5+,
 * which supports the {@code x25519_mlkem768} named group for post-quantum cryptography.
 * Certificates are generated inside the container to avoid host-to-container permission issues.
 * The CA certificate is then extracted from the container for the Keycloak truststore.
 */
@DistributionTest(stopServer = StopServer.Mode.MANUAL)
@RawDistOnly(reason = "Needs filesystem access for TLS certificates")
@EnabledIfSystemProperty(named = "kc.test.storage.database", matches = "true",
        disabledReason = "Database tests require Docker and are opt-in via -Dkc.test.storage.database=true")
@Tag(DistributionTest.STORAGE)
public class PostgreSQLPqcTlsDistTest {

    private static final String DB_NAME = "keycloak";
    private static final String DB_USER = "keycloak";
    private static final String DB_PASSWORD = "Password1!";
    private static final int PG_PORT = 5432;
    private static final String CA_CERT_CONTAINER_PATH = "/var/lib/pgsql/ca.crt";
    private static final int FILE_MODE_EXECUTABLE = 33_261;    // tar mode 0100755

    private static GenericContainer<?> postgres;
    private static Path caCertFile;

    @TempDir
    private static Path tempDir;

    @BeforeAll
    @SuppressWarnings("resource") // lifecycle managed by @BeforeAll / @AfterAll
    static void startPostgresWithPqcTls() {
        postgres = new GenericContainer<>(DockerImageName.parse("fedora:44"))
                .withExposedPorts(PG_PORT)
                .withCopyToContainer(Transferable.of(buildStartupScript(), FILE_MODE_EXECUTABLE), "/start-postgres.sh")
                .withCommand("/bin/bash", "/start-postgres.sh")
                .waitingFor(Wait.forLogMessage(".*database system is ready to accept connections.*\\n", 1))
                .withStartupTimeout(Duration.ofMinutes(5));

        postgres.start();

        caCertFile = tempDir.resolve("ca.crt");
        postgres.copyFileFromContainer(CA_CERT_CONTAINER_PATH, caCertFile.toString());
    }

    @AfterAll
    static void stopPostgres() {
        if (postgres != null) {
            postgres.stop();
        }
    }

    @Test
    @TestProvider(PqcTlsTestProvider.class)
    void testKeycloakConnectsWithPqcTls(KeycloakRunner runner) throws IOException, InterruptedException {
        RawKeycloakDistribution rawDist = runner.getDistribution(RawKeycloakDistribution.class);

        rawDist.copyOrReplaceFile(caCertFile, Path.of("conf", "pg-ca.crt"));
        Path caCertInDist = rawDist.getDistPath().resolve("conf").resolve("pg-ca.crt").toAbsolutePath();

        String jdbcUrl = String.format("jdbc:postgresql://%s:%d/%s",
                postgres.getHost(), postgres.getMappedPort(PG_PORT), DB_NAME);

        runner.setEnvVar("KC_BOOTSTRAP_ADMIN_USERNAME", "admin");
        runner.setEnvVar("KC_BOOTSTRAP_ADMIN_PASSWORD", "admin123");

        CLIResult startResult = runner.run("start-dev",
                "--db=postgres",
                "--db-url=" + jdbcUrl,
                "--db-username=" + DB_USER,
                "--db-password=" + DB_PASSWORD,
                "--db-tls-mode=verify-server",
                "--db-tls-trust-store-file=" + caCertInDist);

        startResult.assertStartedDevMode();

        assertTlsConnectionUsesPqc();
        performCrudOperations();
        assertJdbcConnectionUsesPqc();
    }

    private static void assertTlsConnectionUsesPqc() throws IOException, InterruptedException {
        String query = "SELECT ssl, version, cipher FROM pg_stat_ssl JOIN pg_stat_activity"
                + " ON pg_stat_ssl.pid = pg_stat_activity.pid"
                + " WHERE pg_stat_activity.usename = '" + DB_USER + "' AND ssl = true";

        Container.ExecResult result = postgres.execInContainer(
                "su", "-", "postgres", "-c", "psql -t -A -F'|' -d " + DB_NAME + " -c \"" + query + "\"");

        assertThat("pg_stat_ssl query must succeed", result.getExitCode(), is(0));

        String sslInfo = result.getStdout().trim();
        assertThat("Keycloak JDBC connection must use TLS but pg_stat_ssl returned: " + sslInfo,
                sslInfo.isEmpty(), is(false));
        assertThat("JDBC connection must use TLS 1.3", sslInfo, containsString("TLSv1.3"));
    }

    private static void performCrudOperations() {
        String token = given()
                .formParam("grant_type", "password")
                .formParam("client_id", "admin-cli")
                .formParam("username", "admin")
                .formParam("password", "admin123")
                .when().post("/realms/master/protocol/openid-connect/token")
                .jsonPath().getString("access_token");
        assertNotNull(token, "Admin token must be obtained");

        given().auth().oauth2(token)
                .contentType("application/json")
                .body("{\"realm\": \"pqc-test\", \"enabled\": true}")
                .when().post("/admin/realms")
                .then().statusCode(201);

        given().auth().oauth2(token)
                .contentType("application/json")
                .body("{\"username\": \"pqc-user\", \"enabled\": true}")
                .when().post("/admin/realms/pqc-test/users")
                .then().statusCode(201);

        String userId = given().auth().oauth2(token)
                .when().get("/admin/realms/pqc-test/users?username=pqc-user")
                .jsonPath().getString("[0].id");
        assertNotNull(userId, "User ID must exist");

        given().auth().oauth2(token)
                .contentType("application/json")
                .body("{\"firstName\": \"PQC\", \"lastName\": \"User\"}")
                .when().put("/admin/realms/pqc-test/users/" + userId)
                .then().statusCode(204);

        given().auth().oauth2(token)
                .when().delete("/admin/realms/pqc-test/users/" + userId)
                .then().statusCode(204);

        given().auth().oauth2(token)
                .when().delete("/admin/realms/pqc-test")
                .then().statusCode(204);
    }

    private static void assertJdbcConnectionUsesPqc() {
        String response = when().get("/realms/master/pqc-tls-info").asString();
        assertThat("JDBC PQC TLS info must indicate SSL", response, containsString("\"ssl\":true"));
        assertThat("JDBC must use TLS 1.3", response, containsString("\"protocol\":\"TLSv1.3\""));
        assertThat("JDBC must use PQC named group", response, containsString("X25519MLKEM768"));
    }

    private static String buildStartupScript() {
        return """
                #!/bin/bash
                set -e

                dnf install -y postgresql-server openssl > /dev/null 2>&1
                mkdir -p /var/run/postgresql && chown postgres:postgres /var/run/postgresql

                # Generate CA certificate
                openssl req -new -x509 -days 365 -nodes \
                    -subj "/CN=PostgreSQL PQC TLS Test CA" \
                    -keyout /var/lib/pgsql/ca.key \
                    -out /var/lib/pgsql/ca.crt

                # Generate server key and CSR
                openssl req -new -nodes \
                    -subj "/CN=localhost" \
                    -keyout /var/lib/pgsql/server.key \
                    -out /var/lib/pgsql/server.csr

                # Sign server certificate with CA
                echo "subjectAltName=DNS:localhost,IP:127.0.0.1" > /var/lib/pgsql/server.ext
                openssl x509 -req -days 365 \
                    -in /var/lib/pgsql/server.csr \
                    -CA /var/lib/pgsql/ca.crt \
                    -CAkey /var/lib/pgsql/ca.key \
                    -CAcreateserial \
                    -extfile /var/lib/pgsql/server.ext \
                    -out /var/lib/pgsql/server.crt

                chown postgres:postgres /var/lib/pgsql/server.crt /var/lib/pgsql/server.key /var/lib/pgsql/ca.crt
                chmod 600 /var/lib/pgsql/server.key
                chmod 644 /var/lib/pgsql/server.crt /var/lib/pgsql/ca.crt

                su - postgres -c "initdb -D /var/lib/pgsql/data"

                cp /var/lib/pgsql/server.crt /var/lib/pgsql/data/server.crt
                cp /var/lib/pgsql/server.key /var/lib/pgsql/data/server.key
                chown postgres:postgres /var/lib/pgsql/data/server.crt /var/lib/pgsql/data/server.key

                printf 'hostssl all all 0.0.0.0/0 md5\\nhostssl all all ::/0 md5\\nlocal all all trust\\n' > /var/lib/pgsql/data/pg_hba.conf
                chown postgres:postgres /var/lib/pgsql/data/pg_hba.conf

                PG_VERSION=$(postgres --version | grep -oP '\\d+' | head -1)

                { echo "listen_addresses = '*'"
                  echo "ssl = on"
                  echo "ssl_cert_file = 'server.crt'"
                  echo "ssl_key_file = 'server.key'"
                  echo "ssl_min_protocol_version = 'TLSv1.3'"
                  echo "log_connections = on"
                  echo "log_disconnections = on"
                  echo "logging_collector = off"
                } >> /var/lib/pgsql/data/postgresql.conf

                if [ "$PG_VERSION" -ge 18 ]; then
                    echo "ssl_groups = 'X25519MLKEM768:x25519:secp256r1'" \
                        >> /var/lib/pgsql/data/postgresql.conf
                else
                    echo "ssl_ecdh_curve = 'X25519'" >> /var/lib/pgsql/data/postgresql.conf
                fi

                su - postgres -c "pg_ctl -w -D /var/lib/pgsql/data -l /var/lib/pgsql/initdb.log start" || { cat /var/lib/pgsql/initdb.log; exit 1; }

                su - postgres -c "psql -c \\"CREATE USER %s WITH PASSWORD '%s';\\""
                su - postgres -c "psql -c \\"CREATE DATABASE %s OWNER %s;\\""
                su - postgres -c "psql -d %s -c \\"CREATE SCHEMA foo;\\""

                su - postgres -c "pg_ctl -D /var/lib/pgsql/data -m fast stop"

                exec su - postgres -c "postgres -D /var/lib/pgsql/data"
                """.formatted(DB_USER, DB_PASSWORD, DB_NAME, DB_USER, DB_NAME);
    }
}
