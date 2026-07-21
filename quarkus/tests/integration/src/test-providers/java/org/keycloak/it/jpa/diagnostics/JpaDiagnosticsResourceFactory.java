package org.keycloak.it.jpa.diagnostics;

import org.keycloak.Config;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.services.resource.RealmResourceProvider;
import org.keycloak.services.resource.RealmResourceProviderFactory;

public class JpaDiagnosticsResourceFactory implements RealmResourceProviderFactory {

    static final String ID = "jpa-diagnostics";

    @Override
    public RealmResourceProvider create(KeycloakSession session) {
        return new JpaDiagnosticsResource(session);
    }

    @Override
    public void init(Config.Scope config) {
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
    }

    @Override
    public void close() {
    }

    @Override
    public String getId() {
        return ID;
    }
}
