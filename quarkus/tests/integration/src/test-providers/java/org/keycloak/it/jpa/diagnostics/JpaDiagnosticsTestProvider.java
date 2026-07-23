package org.keycloak.it.jpa.diagnostics;

import java.util.Map;

import org.keycloak.it.TestProvider;

public class JpaDiagnosticsTestProvider implements TestProvider {

    @Override
    public String getName() {
        return "jpa-diagnostics";
    }

    @Override
    public Class[] getClasses() {
        return new Class[] { JpaDiagnosticsResource.class, JpaDiagnosticsResourceFactory.class };
    }

    @Override
    public Map<String, String> getManifestResources() {
        return Map.of(
                "org.keycloak.services.resource.RealmResourceProviderFactory",
                "services/org.keycloak.services.resource.RealmResourceProviderFactory"
        );
    }
}
