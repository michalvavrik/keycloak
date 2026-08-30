package org.keycloak.tests.db;

import java.util.List;
import java.util.Map;

import org.keycloak.connections.jpa.JpaConnectionProvider;
import org.keycloak.testframework.annotations.KeycloakIntegrationTest;
import org.keycloak.testframework.remote.runonserver.InjectRunOnServer;
import org.keycloak.testframework.remote.runonserver.RunOnServerClient;
import org.keycloak.tests.suites.DatabaseTest;

import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@KeycloakIntegrationTest
@DatabaseTest
public class DefaultPuPropertiesTest {

    @InjectRunOnServer
    RunOnServerClient runOnServer;

    @Test
    public void hibernatePropertiesSurvival() {
        runOnServer.run(session -> {
            var sf = session.getProvider(JpaConnectionProvider.class)
                    .getEntityManager().getEntityManagerFactory()
                    .unwrap(SessionFactoryImplementor.class);
            Map<String, Object> props = sf.getProperties();

            assertEquals("32", String.valueOf(props.get("hibernate.jdbc.batch_size")));
            assertEquals("true", String.valueOf(props.get("hibernate.order_inserts")));
            assertEquals("true", String.valueOf(props.get("hibernate.order_updates")));
            assertEquals("8", String.valueOf(props.get("hibernate.default_batch_fetch_size")));
            assertEquals("true", String.valueOf(props.get("hibernate.query.in_clause_parameter_padding")));
            assertEquals("64", String.valueOf(props.get("hibernate.jdbc.fetch_size")));

            // These two come from the default-PU static listener (KeycloakProcessor.configureStaticPersistenceUnitProperties),
            // NOT from application.properties. They are the guard against the "<default>" vs "keycloak-default" listener
            // name mismatch: if the listener targeted the wrong PU name, they would be absent here.
            assertEquals("false", String.valueOf(props.get("hibernate.query.startup_check")));
            assertEquals("false", String.valueOf(props.get("hibernate.jdbc.log.errors")));
        });
    }

    @Test
    public void coreEntitiesRegistered() {
        var entityNames = runOnServer.fetch(session -> {
            var sf = session.getProvider(JpaConnectionProvider.class)
                    .getEntityManager().getEntityManagerFactory()
                    .unwrap(SessionFactoryImplementor.class);
            return sf.getMetamodel().getEntities().stream()
                    .map(e -> e.getJavaType().getName())
                    .sorted()
                    .toList();
        }, List.class);

        assertNotNull(entityNames);
        assertTrue(entityNames.size() >= 70,
                "Expected at least 70 entities but found " + entityNames.size());
        assertTrue(entityNames.contains("org.keycloak.models.jpa.entities.UserEntity"));
        assertTrue(entityNames.contains("org.keycloak.models.jpa.entities.RealmEntity"));
        assertTrue(entityNames.contains("org.keycloak.models.jpa.entities.ClientEntity"));
        assertTrue(entityNames.contains("org.keycloak.models.jpa.entities.RoleEntity"));
        assertTrue(entityNames.contains("org.keycloak.models.jpa.entities.GroupEntity"));
    }
}
