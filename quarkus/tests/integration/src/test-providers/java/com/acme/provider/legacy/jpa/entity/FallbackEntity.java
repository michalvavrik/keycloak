package com.acme.provider.legacy.jpa.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/**
 * Exists to assert that an unmapped {@code @Entity} in a provider JAR (without persistence.xml or orm.xml)
 * falls back to the default persistence unit.
 */
@Entity
public class FallbackEntity {

    @Id
    private String id;
}
