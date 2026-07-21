package org.keycloak.it.cli.dist;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.keycloak.it.jpa.diagnostics.JpaDiagnosticsTestProvider;
import org.keycloak.it.junit5.extension.DistributionTest;
import org.keycloak.it.junit5.extension.KeycloakRunner;
import org.keycloak.it.junit5.extension.RawDistOnly;
import org.keycloak.it.junit5.extension.StopServer.Mode;
import org.keycloak.it.junit5.extension.TestProvider;
import org.keycloak.util.JsonSerialization;

import com.acme.provider.legacy.jpa.entity.CustomJpaEntityProvider;
import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.when;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DistributionTest(stopServer = Mode.MANUAL)
@RawDistOnly(reason = "Containers are immutable")
@TestProvider(CustomJpaEntityProvider.class)
public class NamedPuConfigDistTest {

    private static final String START_DEV_ARGS = "start-dev";
    private static final String DB_ARG = "--db=dev-file";
    private static final String DB_KIND_NEW_USER_STORE = "--db-kind-new-user-store=dev-mem";
    private static final String DB_KIND_CLIENT_STORE = "--db-kind-client-store=dev-file";
    private static final String DB_KIND_PU_WITHOUT_DIALECT = "--db-kind-pu-without-dialect-store=dev-mem";

    @Test
    @TestProvider(JpaDiagnosticsTestProvider.class)
    void testDialectFromPersistenceXmlPreserved(KeycloakRunner runner) throws IOException {
        var result = runner.run(START_DEV_ARGS, DB_ARG, DB_KIND_NEW_USER_STORE, DB_KIND_CLIENT_STORE, DB_KIND_PU_WITHOUT_DIALECT);
        result.assertStartedDevMode();

        Map<String, String> props = fetchPuProperties("new-user-store");
        assertNotNull(props);
        assertEquals("org.hibernate.dialect.H2Dialect", props.get("hibernate.dialect"));
    }

    @Test
    @TestProvider(JpaDiagnosticsTestProvider.class)
    void testHbm2ddlFromPersistenceXmlPreserved(KeycloakRunner runner) throws IOException {
        var result = runner.run(START_DEV_ARGS, DB_ARG, DB_KIND_NEW_USER_STORE, DB_KIND_CLIENT_STORE, DB_KIND_PU_WITHOUT_DIALECT);
        result.assertStartedDevMode();

        Map<String, String> props = fetchPuProperties("new-user-store");
        assertNotNull(props);
        assertEquals("update", props.get("hibernate.hbm2ddl.auto"));
    }

    @Test
    @TestProvider(JpaDiagnosticsTestProvider.class)
    void testTransactionTypeIsJta(KeycloakRunner runner) throws IOException {
        var result = runner.run(START_DEV_ARGS, DB_ARG, DB_KIND_NEW_USER_STORE, DB_KIND_CLIENT_STORE, DB_KIND_PU_WITHOUT_DIALECT);
        result.assertStartedDevMode();

        Map<String, String> props = fetchPuProperties("new-user-store");
        assertNotNull(props);
        assertEquals("JTA", props.get("jakarta.persistence.transactionType"));
    }

    @Test
    @TestProvider(JpaDiagnosticsTestProvider.class)
    void testEntityIsolation(KeycloakRunner runner) throws IOException {
        var result = runner.run(START_DEV_ARGS, DB_ARG, DB_KIND_NEW_USER_STORE, DB_KIND_CLIENT_STORE, DB_KIND_PU_WITHOUT_DIALECT);
        result.assertStartedDevMode();

        List<String> newUserStoreEntities = fetchPuEntities("new-user-store");
        List<String> defaultEntities = fetchPuEntities("default");

        assertTrue(newUserStoreEntities.contains("com.acme.provider.legacy.jpa.entity.Realm"),
                "Realm entity should be in new-user-store PU");
        assertFalse(defaultEntities.contains("com.acme.provider.legacy.jpa.entity.Realm"),
                "Realm entity should NOT be in default PU");
    }

    @Test
    @TestProvider(JpaDiagnosticsTestProvider.class)
    void testCustomPropertySurvival(KeycloakRunner runner) throws IOException {
        var result = runner.run(START_DEV_ARGS, DB_ARG, DB_KIND_NEW_USER_STORE, DB_KIND_CLIENT_STORE, DB_KIND_PU_WITHOUT_DIALECT);
        result.assertStartedDevMode();

        Map<String, String> props = fetchPuProperties("new-user-store");
        assertNotNull(props);
        assertEquals("false", props.get("hibernate.show_sql"));
    }

    private Map<String, String> fetchPuProperties(String puName) throws IOException {
        var json = when()
                .get("/realms/master/jpa-diagnostics/" + puName + "/properties")
                .thenReturn()
                .getBody()
                .asString();
        return JsonSerialization.readValue(json, new TypeReference<>() {});
    }

    private List<String> fetchPuEntities(String puName) throws IOException {
        var json = when()
                .get("/realms/master/jpa-diagnostics/" + puName + "/entities")
                .thenReturn()
                .getBody()
                .asString();
        return JsonSerialization.readValue(json, new TypeReference<>() {});
    }
}
