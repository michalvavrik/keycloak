/*
 * Copyright 2021 Red Hat, Inc. and/or its affiliates
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

package org.keycloak.quarkus.runtime.storage.database.jpa;

import java.util.function.Supplier;

import io.agroal.api.AgroalDataSource;
import io.quarkus.agroal.DataSource;
import io.quarkus.arc.Arc;
import jakarta.persistence.EntityManagerFactory;
import org.jboss.logging.Logger;
import org.keycloak.models.KeycloakSessionFactory;

public final class NamedJpaConnectionProviderFactory extends AbstractJpaConnectionProviderFactory {

    private static final Logger LOG = Logger.getLogger(NamedJpaConnectionProviderFactory.class);

    private String unitName;

    @Override
    public void postInit(KeycloakSessionFactory factory) {
        var dsInstance = Arc.requireContainer().select(AgroalDataSource.class, new DataSource.DataSourceLiteral(unitName));
        if (dsInstance.isResolvable() && !dsInstance.getHandle().getBean().isActive()) {
            LOG.warnf("Datasource '%s' was deactivated automatically because its URL is not set."
                    + " To activate the datasource, set configuration property 'quarkus.datasource.\"%s\".jdbc.url'."
                    + " Refer to https://quarkus.io/guides/datasource for guidance.", unitName, unitName);
            return;
        }
        super.postInit(factory);
    }

    @Override
    protected EntityManagerFactory getEntityManagerFactory() {
        return getEntityManagerFactory(unitName).orElseThrow(new Supplier<IllegalStateException>() {
            @Override
            public IllegalStateException get() {
                return new IllegalStateException("Could not resolve named EntityManagerFactory [" + unitName + "]");
            }
        });
    }

    public String getUnitName() {
        return unitName;
    }

    public void setUnitName(String unitName) {
        this.unitName = unitName;
    }

    @Override
    public String getId() {
        return unitName;
    }
}
