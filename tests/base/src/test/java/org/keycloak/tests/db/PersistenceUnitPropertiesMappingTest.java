package org.keycloak.tests.db;

import java.util.Map;

import jakarta.enterprise.inject.spi.CDI;

import org.keycloak.connections.jpa.JpaConnectionProvider;
import org.keycloak.models.KeycloakSession;
import org.keycloak.testframework.annotations.KeycloakIntegrationTest;
import org.keycloak.testframework.remote.annotations.TestOnServer;
import org.keycloak.testframework.server.KeycloakServerConfig;
import org.keycloak.testframework.server.KeycloakServerConfigBuilder;
import org.keycloak.tests.suites.DatabaseTest;

import io.quarkus.hibernate.orm.PersistenceUnit.PersistenceUnitLiteral;
import org.eclipse.microprofile.config.ConfigProvider;
import org.hibernate.SessionFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@KeycloakIntegrationTest(config = PersistenceUnitPropertiesMappingTest.SqlDebugAndSlowQueryServerConfig.class)
@DatabaseTest
public class PersistenceUnitPropertiesMappingTest {

    @TestOnServer
    public void configDerivedHibernatePropertiesReachSessionFactory(KeycloakSession session) {
        var sf = session.getProvider(JpaConnectionProvider.class)
                .getEntityManager().getEntityManagerFactory()
                .unwrap(SessionFactory.class);
        Map<String, Object> props = sf.getProperties();

        assertEquals("true", String.valueOf(props.get("hibernate.use_sql_comments")));
        assertEquals("5000", String.valueOf(props.get("hibernate.log_slow_query")));

        var config = ConfigProvider.getConfig();
        assertEquals("50", config.getValue("quarkus.datasource.jdbc.initial-size", String.class));
        assertEquals("50", config.getValue("quarkus.datasource.jdbc.min-size", String.class));
        assertEquals("200", config.getValue("quarkus.datasource.jdbc.max-size", String.class));
        assertEquals("30s", config.getValue("quarkus.datasource.jdbc.max-lifetime", String.class));
        assertEquals("30s", config.getValue("quarkus.datasource.jdbc.acquisition-timeout", String.class));
        assertEquals("20s", config.getValue("quarkus.datasource.jdbc.login-timeout", String.class));
        
        // Named PU assertions - these check the actual SmallRye config
        assertEquals("10", config.getValue("quarkus.datasource.\"my-store\".jdbc.initial-size", String.class));
        assertEquals("10", config.getValue("quarkus.datasource.\"my-store\".jdbc.min-size", String.class));
        assertEquals("50", config.getValue("quarkus.datasource.\"my-store\".jdbc.max-size", String.class));
        assertEquals("true", config.getValue("quarkus.datasource.\"my-store\".health-exclude", String.class));
        assertEquals("true", config.getValue("quarkus.datasource.\"my-store\".active", String.class));

        // Hibernate properties for named PU must be ignored when packages are not configured
        assertFalse(config.getOptionalValue("quarkus.hibernate-orm.\"my-store\".unsupported-properties.\"hibernate.use_sql_comments\"", String.class).isPresent());
        assertFalse(config.getOptionalValue("quarkus.hibernate-orm.\"my-store\".log.queries-slower-than-ms", String.class).isPresent());
        assertFalse(CDI.current().select(SessionFactory.class, new PersistenceUnitLiteral("my-store")).isResolvable());
    }

    @TestOnServer
    public void configDerivedHibernatePropertiesReachSessionFactoryWithPackages(KeycloakSession ignored) {
        var sf = CDI.current().select(SessionFactory.class, new PersistenceUnitLiteral("my-store-pkg")).get();
        Map<String, Object> props = sf.getProperties();

        assertEquals("true", String.valueOf(props.get("hibernate.use_sql_comments")));
        assertEquals("3000", String.valueOf(props.get("hibernate.log_slow_query")));

        var config = ConfigProvider.getConfig();

        // Named PU with packages assertions
        assertEquals("10", config.getValue("quarkus.datasource.\"my-store-pkg\".jdbc.initial-size", String.class));
        assertEquals("true", config.getValue("quarkus.hibernate-orm.\"my-store-pkg\".unsupported-properties.\"hibernate.use_sql_comments\"", String.class));
        assertEquals("3000", config.getValue("quarkus.hibernate-orm.\"my-store-pkg\".log.queries-slower-than-ms", String.class));
        assertEquals("org.example.entities", config.getValue("quarkus.hibernate-orm.\"my-store-pkg\".packages", String.class));
    }

    public static class SqlDebugAndSlowQueryServerConfig implements KeycloakServerConfig {
        @Override
        public KeycloakServerConfigBuilder configure(KeycloakServerConfigBuilder config) {
            return config
                    .option("db-debug-jpql", "true")
                    .option("db-log-slow-queries-threshold", "5000")
                    .option("db-pool-initial-size", "50")
                    .option("db-pool-min-size", "50")
                    .option("db-pool-max-size", "200")
                    .option("db-pool-max-lifetime", "30s")
                    .option("db-pool-acquisition-timeout", "30s")
                    .option("db-connect-timeout", "20s")
                    .option("db-schema", "public")
                    
                    // Named Datasource (doesn't create a PU, but maps config)
                    .option("db-kind-my-store", "dev-mem")
                    .option("db-url-full-my-store", "jdbc:h2:mem:my-store")
                    .option("db-username-my-store", "sa")
                    .option("db-password-my-store", "sa")
                    .option("db-pool-initial-size-my-store", "10")
                    .option("db-pool-min-size-my-store", "10")
                    .option("db-pool-max-size-my-store", "50")
                    .option("db-schema-my-store", "public")
                    .option("db-health-exclude-my-store", "true")
                    .option("db-enabled-my-store", "true")
                    .option("db-debug-jpql-my-store", "true")
                    .option("db-log-slow-queries-threshold-my-store", "7000")

                    // Named Datasource with packages (creates a PU)
                    .option("db-kind-my-store-pkg", "dev-mem")
                    .option("db-url-full-my-store-pkg", "jdbc:h2:mem:my-store-pkg")
                    .option("db-pool-initial-size-my-store-pkg", "10")
                    .option("db-debug-jpql-my-store-pkg", "true")
                    .option("db-log-slow-queries-threshold-my-store-pkg", "3000")
                    .option("db-jpa-packages-my-store-pkg", "org.example.entities");
        }
    }
}
