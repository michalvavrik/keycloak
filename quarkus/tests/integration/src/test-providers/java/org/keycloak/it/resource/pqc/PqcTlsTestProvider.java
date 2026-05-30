package org.keycloak.it.resource.pqc;

import java.util.Map;

import org.keycloak.it.TestProvider;

public class PqcTlsTestProvider implements TestProvider {

    @Override
    public Class[] getClasses() {
        return new Class[]{PqcTlsResource.class, PqcTlsResourceFactory.class};
    }

    @Override
    public Map<String, String> getManifestResources() {
        return Map.of("org.keycloak.services.resource.RealmResourceProviderFactory",
                "services/org.keycloak.services.resource.RealmResourceProviderFactory");
    }
}
