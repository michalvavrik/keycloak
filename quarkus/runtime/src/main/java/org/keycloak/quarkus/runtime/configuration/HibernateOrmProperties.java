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
package org.keycloak.quarkus.runtime.configuration;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.TreeMap;

/**
 * The properties of the Quarkus Hibernate ORM extension, which Keycloak exposes as {@code db-orm-*} options (see
 * {@link org.keycloak.config.DatabaseOptions#DB_ORM_PREFIX}).
 * <p>
 * The properties are collected from the extension when this module is built, and shipped in its jar as {@value #RESOURCE},
 * see {@link HibernateOrmPropertiesGenerator}: the build time properties are defined by the deployment module of the
 * extension, which is not on the class path of the server.
 */
public final class HibernateOrmProperties {

    /**
     * The prefix of the Quarkus Hibernate ORM properties, without the trailing dot.
     */
    public static final String PREFIX = "quarkus.hibernate-orm";

    /**
     * The generated properties: a properties file with a line {@code <name>=<build-time|run-time>,<unit|global>,<type>} per
     * property, where the type is one of {@code boolean}, {@code integer}, {@code long} and {@code string}.
     */
    public static final String RESOURCE = "META-INF/keycloak-hibernate-orm.properties";

    static final String BUILD_TIME = "build-time";
    static final String RUN_TIME = "run-time";
    static final String PERSISTENCE_UNIT = "unit";
    static final String GLOBAL = "global";

    private static volatile Map<String, HibernateOrmProperty> properties;

    private HibernateOrmProperties() {
    }

    /**
     * A property of the Quarkus Hibernate ORM extension.
     *
     * @param name the name of the property, e.g. {@code quarkus.hibernate-orm.query.query-plan-cache-max-size}
     * @param buildTime whether the property is a build time property, i.e. the phase of its config root is not
     *        {@code RUN_TIME}
     * @param perUnit whether the property is a property of a persistence unit, which applies to a named persistence unit
     *        as {@code quarkus.hibernate-orm."<unit>".<property>}, as opposed to a property of the extension as a whole
     *        such as {@code quarkus.hibernate-orm.enabled}
     * @param type the type of the value as Keycloak validates it: {@link Boolean}, {@link Integer}, {@link Long} or
     *        {@link String} for any other type, which Quarkus validates
     */
    public record HibernateOrmProperty(String name, boolean buildTime, boolean perUnit, Class<?> type) {

        public HibernateOrmProperty {
            Objects.requireNonNull(name);
            Objects.requireNonNull(type);
            if (!name.startsWith(PREFIX + ".")) {
                throw new IllegalArgumentException("Not a Hibernate ORM property: " + name);
            }
        }

        /**
         * @return the name without the {@link #PREFIX}, e.g. {@code query.query-plan-cache-max-size}
         */
        public String suffix() {
            return name.substring(PREFIX.length() + 1);
        }
    }

    /**
     * The properties of the Quarkus Hibernate ORM extension by name, sorted by name.
     */
    public static Map<String, HibernateOrmProperty> getProperties() {
        Map<String, HibernateOrmProperty> result = properties;
        if (result == null) {
            synchronized (HibernateOrmProperties.class) {
                result = properties;
                if (result == null) {
                    result = load();
                    properties = result;
                }
            }
        }
        return result;
    }

    private static Map<String, HibernateOrmProperty> load() {
        try (InputStream in = HibernateOrmProperties.class.getClassLoader().getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException(RESOURCE + " not found: it is generated when the server module is built, see " + HibernateOrmPropertiesGenerator.class.getName());
            }
            return Collections.unmodifiableMap(parse(in));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read " + RESOURCE, e);
        }
    }

    /**
     * Parses properties in the format of {@link #RESOURCE}.
     */
    public static Map<String, HibernateOrmProperty> parse(InputStream in) throws IOException {
        Properties properties = new Properties();
        properties.load(in);
        Map<String, HibernateOrmProperty> result = new TreeMap<>();
        for (String name : properties.stringPropertyNames()) {
            String[] values = properties.getProperty(name).split(",");
            if (values.length != 3) {
                throw new IllegalArgumentException("Invalid Hibernate ORM property record: " + name + "=" + properties.getProperty(name));
            }
            result.put(name, new HibernateOrmProperty(name, BUILD_TIME.equals(values[0].trim()), PERSISTENCE_UNIT.equals(values[1].trim()), parseType(values[2].trim())));
        }
        return result;
    }

    private static Class<?> parseType(String type) {
        return switch (type) {
            case "boolean" -> Boolean.class;
            case "integer" -> Integer.class;
            case "long" -> Long.class;
            default -> String.class;
        };
    }
}
