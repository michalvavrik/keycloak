/*
 * Copyright 2026 Red Hat, Inc. and/or its affiliates
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

package org.keycloak.it.cli.dist;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.keycloak.it.junit5.extension.CLIResult;
import org.keycloak.it.junit5.extension.DistributionTest;
import org.keycloak.it.junit5.extension.KeycloakRunner;
import org.keycloak.it.junit5.extension.RawDistOnly;
import org.keycloak.it.junit5.extension.StopServer.Mode;
import org.keycloak.it.junit5.extension.TestProvider;
import org.keycloak.it.utils.RawDistRootPath;

import com.acme.provider.configunit.ConfigUnitTestProvider;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.when;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A persistence unit defined through {@code db-jpa-packages-<datasource>} instead of a persistence.xml.
 */
@DistributionTest(stopServer = Mode.MANUAL)
@RawDistOnly(reason = "Containers are immutable")
@TestProvider(ConfigUnitTestProvider.class)
public class ConfigDefinedPersistenceUnitDistTest {

    private static final String UNIT = "my-store";
    private static final String ENTITY = "com.acme.provider.configunit.ConfigUnitEntity";
    private static final String[] BUILD = { "build", "--db=dev-file", "--db-kind-my-store=dev-mem", "--db-jpa-packages-my-store=com.acme.provider.configunit" };
    private static final String[] START = { "start", "--optimized", "--http-enabled=true", "--hostname-strict=false",
            // the unit has no schema migration, so the table of its entity is created with the connection
            "--db-url-full-my-store=jdbc:h2:mem:my-store;DB_CLOSE_DELAY=-1;INIT=CREATE TABLE IF NOT EXISTS CONFIG_UNIT_ENTITY(ID VARCHAR(255) PRIMARY KEY)",
            "--db-username-my-store=sa", "--db-password-my-store=sa", "--log-level=org.hibernate.orm.jpa:debug" };
    private static final String[] RUNTIME_OPTIONS = { "--db-debug-jpql-my-store=true", "--db-log-slow-queries-threshold-my-store=1234", "--db-schema-my-store=PUBLIC" };

    @Test
    void persistenceUnitConfiguredThroughOptions(KeycloakRunner runner, RawDistRootPath dist) throws IOException {
        // the runtime options are ignored at build time and, in particular, not recorded into the build
        CLIResult result = runner.run(concat(BUILD, RUNTIME_OPTIONS));
        result.assertBuild();
        result.assertMessage("kc.db-debug-jpql-my-store");
        result.assertMessage("will be ignored during build time");
        Map<String, String> recorded = hibernateOrmRecordedStrings(dist.getDistRootPath().resolve("lib").resolve("quarkus").resolve("generated-bytecode.jar"));
        assertTrue(recorded.containsValue("com.acme.provider.configunit"), "the packages of the unit are recorded into the build: " + recorded.keySet());
        assertFalse(recorded.containsValue("hibernate.use_sql_comments"), "the runtime option db-debug-jpql-my-store must not be recorded into the build");

        // started without the runtime options: the unit exists, the runtime options are at their defaults
        result = runner.run(START);
        result.assertStarted();
        assertUnitDefined(result);
        Map<String, String> settings = settings(UNIT);
        assertEquals("org.keycloak.connections.jpa.dialect.KeycloakH2Dialect", settings.get("hibernate.dialect"));
        assertEquals("10000", settings.get("hibernate.log_slow_query"));
        assertNull(settings.get("hibernate.use_sql_comments"), settings.toString());
        assertNull(settings.get("hibernate.default_schema"), settings.toString());
        runner.stop();

        // started with the runtime options: they reach the unit without a rebuild
        result = runner.run(concat(START, RUNTIME_OPTIONS));
        result.assertStarted();
        assertUnitDefined(result);
        result.assertNoStartupMessage("sets unsupported properties");
        settings = settings(UNIT);
        assertEquals("true", settings.get("hibernate.use_sql_comments"));
        assertEquals("1234", settings.get("hibernate.log_slow_query"));
        assertEquals("PUBLIC", settings.get("hibernate.default_schema"));
        runner.stop();
    }

    @Test
    void rawQuarkusPropertiesAndPersistence(KeycloakRunner runner, RawDistRootPath dist) throws IOException {
        Path quarkusProperties = dist.getDistRootPath().resolve("conf").resolve("quarkus.properties");
        try {
            // Hibernate ORM settings without a Keycloak option are set with the raw Quarkus properties, quoting the unit name
            Files.writeString(quarkusProperties, "quarkus.hibernate-orm.\"my-store\".jdbc.statement-batch-size=42\n" // build time
                    + "quarkus.hibernate-orm.\"my-store\".log.sql=true\n" // runtime
                    + "quarkus.hibernate-orm.\"my-store\".log.queries-slower-than-ms=777\n" // also mapped from a Keycloak option
                    + "quarkus.hibernate-orm.jdbc.statement-batch-size=33\n", StandardCharsets.UTF_8); // the default unit
            runner.run(BUILD).assertBuild();

            CLIResult result = runner.run(START);
            result.assertStarted();
            assertUnitDefined(result);
            Map<String, String> settings = settings(UNIT);
            assertEquals("42", settings.get("hibernate.jdbc.batch_size"));
            assertEquals("true", settings.get("hibernate.show_sql"));
            // a raw property wins over the default of the Keycloak option
            assertEquals("777", settings.get("hibernate.log_slow_query"));
            assertEquals("33", settings("default").get("hibernate.jdbc.batch_size"));

            // entities are persisted and read through the unit
            when().post("/realms/master/config-unit/" + UNIT + "/entities/b").then().statusCode(204);
            when().post("/realms/master/config-unit/" + UNIT + "/entities/a").then().statusCode(204);
            assertEquals(List.of("a", "b"), when().get("/realms/master/config-unit/" + UNIT + "/entities").then().statusCode(200).extract().jsonPath().getList("", String.class));
            runner.stop();

            // a Keycloak option wins over the raw property
            result = runner.run(concat(START, "--db-log-slow-queries-threshold-my-store=1234"));
            result.assertStarted();
            assertEquals("1234", settings(UNIT).get("hibernate.log_slow_query"));
            runner.stop();

            // combined with the Keycloak options of the datasource, which use the quoted form, the unquoted form is ignored
            Files.writeString(quarkusProperties, "quarkus.hibernate-orm.my-store.jdbc.statement-batch-size=42\n"
                    + "quarkus.hibernate-orm.my-store.log.sql=true\n", StandardCharsets.UTF_8);
            runner.run(BUILD).assertBuild();
            result = runner.run(START);
            result.assertStarted();
            assertUnitDefined(result);
            settings = settings(UNIT);
            assertNull(settings.get("hibernate.jdbc.batch_size"), settings.toString());
            assertNull(settings.get("hibernate.show_sql"), settings.toString());
            runner.stop();
        } finally {
            Files.deleteIfExists(quarkusProperties);
        }
    }

    @Test
    void persistenceUnitRequiresDbKind(KeycloakRunner runner) {
        CLIResult result = runner.run("build", "--db=dev-file", "--db-jpa-packages-my-store=com.acme.provider.configunit");
        result.assertError("Detected additional named datasources without a DB kind set, please specify: kc.db-kind-my-store");
    }

    @Test
    void persistenceUnitMustBeNamedAfterDatasource(KeycloakRunner runner, RawDistRootPath dist) throws IOException {
        Path quarkusProperties = dist.getDistRootPath().resolve("conf").resolve("quarkus.properties");
        Files.writeString(quarkusProperties, "quarkus.hibernate-orm.\"other\".packages=com.acme.provider.configunit\n"
                + "quarkus.hibernate-orm.\"other\".datasource=my-store\n", StandardCharsets.UTF_8);
        try {
            CLIResult result = runner.run("build", "--db=dev-file", "--db-kind-my-store=dev-mem");
            result.assertError("The persistence unit 'other' must use the datasource 'other' of the same name, but it uses the datasource 'my-store'. "
                    + "Define the persistence unit of a datasource with the 'kc.db-jpa-packages-other' option.");
        } finally {
            Files.deleteIfExists(quarkusProperties);
        }
    }

    private static void assertUnitDefined(CLIResult result) {
        // the server is still running (manual stop mode), so only startup output assertions are available
        result.assertNoStartupMessage("Could not find a suitable persistence unit for model classes/packages");
        result.assertNoStartupMessage("Datasource 'my-store' is not active");
        String output = result.getOutput();
        String unitBlock = extractPersistenceUnitBlock(output, UNIT);
        String defaultBlock = extractPersistenceUnitBlock(output, "<default>");
        assertNotNull(unitBlock, "'" + UNIT + "' PU info block should be present");
        assertNotNull(defaultBlock, "'<default>' PU info block should be present");
        assertTrue(unitBlock.contains(ENTITY), "the entity must be managed by the '" + UNIT + "' persistence unit");
        assertFalse(defaultBlock.contains(ENTITY), "the entity must not leak into the default persistence unit");
    }

    private static Map<String, String> settings(String unit) {
        return when().get("/realms/master/config-unit/" + unit + "/settings").then().statusCode(200).extract().jsonPath().getMap("", String.class, String.class);
    }

    /**
     * Strings the Hibernate ORM extension recorded into the generated bytecode, e.g. build time configuration.
     */
    private static Map<String, String> hibernateOrmRecordedStrings(Path generatedBytecode) throws IOException {
        Map<String, String> strings = new java.util.HashMap<>();
        try (JarFile jar = new JarFile(generatedBytecode.toFile())) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (!entry.getName().startsWith("io/quarkus/runner/recorded/HibernateOrmProcessor$")) {
                    continue;
                }
                try (InputStream in = jar.getInputStream(entry)) {
                    String content = new String(in.readAllBytes(), StandardCharsets.ISO_8859_1);
                    for (String needle : List.of("com.acme.provider.configunit", "hibernate.use_sql_comments")) {
                        if (content.contains(needle)) {
                            strings.put(entry.getName() + ":" + needle, needle);
                        }
                    }
                }
            }
        }
        return strings;
    }

    private static String[] concat(String[] first, String... second) {
        List<String> args = new ArrayList<>(Arrays.asList(first));
        args.addAll(Arrays.asList(second));
        return args.toArray(new String[0]);
    }

    static String extractPersistenceUnitBlock(String output, String puName) {
        String marker = "HHH008541: PersistenceUnitInfo [";
        String nameToken = "name: " + puName;
        int idx = 0;
        while ((idx = output.indexOf(marker, idx)) != -1) {
            int nextBlock = output.indexOf(marker, idx + marker.length());
            String block = nextBlock == -1 ? output.substring(idx) : output.substring(idx, nextBlock);
            if (block.contains(nameToken)) {
                return block;
            }
            idx += marker.length();
        }
        return null;
    }
}
