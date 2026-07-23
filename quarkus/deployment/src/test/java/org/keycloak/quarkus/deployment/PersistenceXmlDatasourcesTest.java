package org.keycloak.quarkus.deployment;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.hibernate.jpa.boot.spi.PersistenceUnitDescriptor;
import org.hibernate.jpa.boot.spi.PersistenceXmlParser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.quarkus.hibernate.orm.deployment.spi.AdditionalPersistenceUnitBuildItem;

import static org.keycloak.quarkus.deployment.KeycloakProcessor.getDatasourceNameFromPersistenceXml;
import static org.keycloak.quarkus.runtime.storage.database.jpa.QuarkusJpaConnectionProviderFactory.DEFAULT_PERSISTENCE_UNIT;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.wildfly.common.Assert.assertNotNull;

public class PersistenceXmlDatasourcesTest {
    private static final String PERSISTENCE_XML_BODY = """
            <persistence xmlns="https://jakarta.ee/xml/ns/persistence"
                         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="https://jakarta.ee/xml/ns/persistence https://jakarta.ee/xml/ns/persistence/persistence_3_0.xsd"
                         version="3.0">

                         %s

            </persistence>
            """;

    private static PersistenceXmlParser parser;

    @BeforeAll
    public static void setupParser() {
        parser = PersistenceXmlParser.create();
    }

    @Test
    public void datasourceNamesOrder() throws IOException {
        assertUsedName("""
                <persistence-unit name="user-store-pu" transaction-type="JTA">
                    <properties>
                        <property name="jakarta.persistence.jtaDataSource" value="user-store" />
                    </properties>
                </persistence-unit>
                """, "user-store");

        assertUsedName("""
                <persistence-unit name="user-store-pu" transaction-type="JTA">
                    <properties>
                        <property name="hibernate.connection.datasource" value="my-store" />
                    </properties>
                </persistence-unit>
                """, "my-store");

        assertUsedName("""
                <persistence-unit name="user-store-pu" transaction-type="JTA">
                </persistence-unit>
                """, "user-store-pu");

        assertUsedName("""
                <persistence-unit name="user-store-pu" transaction-type="JTA">
                    <properties>
                        <property name="jakarta.persistence.jtaDataSource" value="user-store" />
                        <property name="hibernate.connection.datasource" value="my-store" />
                    </properties>
                </persistence-unit>
                """, "user-store");

        assertUsedName("""
                <persistence-unit name="user-store-pu" transaction-type="JTA">
                    <properties>
                        <property name="jakarta.persistence.nonJtaDataSource" value="user-store" />
                        <property name="hibernate.connection.datasource" value="my-store" />
                    </properties>
                </persistence-unit>
                """, "my-store");
    }

    @Test
    public void reservedNameRejected() throws IOException {
        assertPersistenceXmlSingleDS("""
                <persistence-unit name="keycloak-default" transaction-type="JTA">
                </persistence-unit>
                """, descriptor -> {
            var exception = assertThrows(RuntimeException.class, () -> buildAdditionalPU(descriptor));
            assertTrue(exception.getMessage().contains(DEFAULT_PERSISTENCE_UNIT));
        });
    }

    @Test
    public void buildItemPreservesProperties() throws IOException {
        assertPersistenceXmlSingleDS("""
                <persistence-unit name="my-store" transaction-type="JTA">
                    <class>com.acme.MyEntity</class>
                    <properties>
                        <property name="jakarta.persistence.jtaDataSource" value="my-ds" />
                        <property name="hibernate.dialect" value="org.hibernate.dialect.H2Dialect" />
                        <property name="hibernate.hbm2ddl.auto" value="update" />
                        <property name="hibernate.show_sql" value="true" />
                    </properties>
                </persistence-unit>
                """, descriptor -> {
            var buildItem = buildAdditionalPU(descriptor);

            assertEquals("my-store", buildItem.getPersistenceUnitName());
            assertEquals("my-ds", buildItem.getDataSourceName().orElse(null));
            assertTrue(buildItem.getManagedClassNames().contains("com.acme.MyEntity"));

            Map<String, String> props = buildItem.getProperties();
            assertEquals("org.hibernate.dialect.H2Dialect", props.get("hibernate.dialect"));
            assertEquals("update", props.get("hibernate.hbm2ddl.auto"));
            assertEquals("true", props.get("hibernate.show_sql"));
        });
    }

    @Test
    public void buildItemFallsBackToPuName() throws IOException {
        assertPersistenceXmlSingleDS("""
                <persistence-unit name="fallback-pu" transaction-type="JTA">
                </persistence-unit>
                """, descriptor -> {
            var buildItem = buildAdditionalPU(descriptor);
            assertEquals("fallback-pu", buildItem.getDataSourceName().orElse(null));
        });
    }

    private static AdditionalPersistenceUnitBuildItem buildAdditionalPU(PersistenceUnitDescriptor descriptor) {
        String puName = descriptor.getName();
        if (DEFAULT_PERSISTENCE_UNIT.equals(puName)) {
            throw new RuntimeException("User-defined persistence unit must not use the reserved name '" + DEFAULT_PERSISTENCE_UNIT + "'.");
        }
        String datasourceName = getDatasourceNameFromPersistenceXml(descriptor);
        AdditionalPersistenceUnitBuildItem.Builder builder = AdditionalPersistenceUnitBuildItem.builder(puName)
                .dataSourceName(datasourceName);
        for (String className : descriptor.getManagedClassNames()) {
            builder.managedClass(className);
        }
        if (descriptor.getProperties() != null) {
            for (Map.Entry<Object, Object> entry : descriptor.getProperties().entrySet()) {
                builder.property((String) entry.getKey(), (String) entry.getValue());
            }
        }
        return builder.build();
    }

    private void assertUsedName(String content, String expectedName) throws IOException {
        assertPersistenceXmlSingleDS(content, descriptor -> {
            assertThat(getDatasourceNameFromPersistenceXml(descriptor), is(expectedName));
        });
    }

    private void assertPersistenceXmlSingleDS(String content, Consumer<PersistenceUnitDescriptor> asserts) throws IOException {
        String xml = PERSISTENCE_XML_BODY.formatted(content);
        Path file = null;
        try {
            file = Files.createTempFile("persistence", ".xml");
            Files.writeString(file, xml);
            List<PersistenceUnitDescriptor> descriptors = List.copyOf(parser.parse(List.of(file.toUri().toURL())).values());
            assertNotNull(descriptors);
            assertThat(descriptors.size(), is(1));
            asserts.accept(descriptors.get(0));
        } finally {
            if (file != null) {
                Files.deleteIfExists(file);
            }
        }
    }
}
