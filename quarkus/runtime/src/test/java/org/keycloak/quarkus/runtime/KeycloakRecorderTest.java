package org.keycloak.quarkus.runtime;

import java.util.HashMap;
import java.util.Map;

import io.quarkus.hibernate.orm.runtime.integration.HibernateOrmIntegrationRuntimeInitListener;
import org.junit.Test;

import static org.hibernate.cfg.AvailableSettings.JAKARTA_HBM2DDL_DATABASE_ACTION;
import static org.junit.Assert.assertEquals;

public class KeycloakRecorderTest {

    private static Map<String, Object> reappliedRuntimeProperties(Map<String, String> persistenceXmlProperties) {
        HibernateOrmIntegrationRuntimeInitListener listener =
                new KeycloakRecorder().createUserDefinedUnitRuntimeListener(persistenceXmlProperties);
        Map<String, Object> captured = new HashMap<>();
        listener.contributeRuntimeProperties(captured::put);
        return captured;
    }

    @Test
    public void reappliesHbm2ddlAutoUnchangedForNonCreateValues() {
        assertEquals("update",
                reappliedRuntimeProperties(Map.of("hibernate.hbm2ddl.auto", "update"))
                        .get(JAKARTA_HBM2DDL_DATABASE_ACTION));
    }

    @Test
    public void mapsLegacyHbm2ddlCreateToDropAndCreate() {
        assertEquals("drop-and-create",
                reappliedRuntimeProperties(Map.of("hibernate.hbm2ddl.auto", "create"))
                        .get(JAKARTA_HBM2DDL_DATABASE_ACTION));
    }

    @Test
    public void reappliesSchemaActionFromJpaStandardKey() {
        assertEquals("drop-and-create",
                reappliedRuntimeProperties(Map.of("jakarta.persistence.schema-generation.database.action", "drop-and-create"))
                        .get(JAKARTA_HBM2DDL_DATABASE_ACTION));
    }

    @Test
    public void reappliesSchemaActionFromLegacyJavaxKey() {
        assertEquals("drop-and-create",
                reappliedRuntimeProperties(Map.of("javax.persistence.schema-generation.database.action", "drop-and-create"))
                        .get(JAKARTA_HBM2DDL_DATABASE_ACTION));
    }

    @Test
    public void jpaStandardActionTakesPrecedenceOverHbm2ddlAuto() {
        assertEquals("none",
                reappliedRuntimeProperties(Map.of(
                        "hibernate.hbm2ddl.auto", "update",
                        "jakarta.persistence.schema-generation.database.action", "none"))
                        .get(JAKARTA_HBM2DDL_DATABASE_ACTION));
    }
}
