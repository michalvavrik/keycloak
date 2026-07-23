package org.keycloak.it.jpa.diagnostics;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.keycloak.connections.jpa.JpaConnectionProvider;
import org.keycloak.models.KeycloakSession;
import org.keycloak.services.resource.RealmResourceProvider;
import org.keycloak.util.JsonSerialization;

public class JpaDiagnosticsResource implements RealmResourceProvider {

    private final KeycloakSession session;

    public JpaDiagnosticsResource(KeycloakSession session) {
        this.session = session;
    }

    @Override
    public Object getResource() {
        return this;
    }

    @Path("{pu}/properties")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response properties(@PathParam("pu") String puName) throws IOException {
        var sf = resolveSessionFactory(puName);
        if (sf == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        Map<String, String> filtered = new HashMap<>();
        for (Map.Entry<String, Object> entry : sf.getProperties().entrySet()) {
            if (entry.getValue() != null) {
                filtered.put(entry.getKey(), String.valueOf(entry.getValue()));
            }
        }
        return Response.ok(JsonSerialization.writeValueAsString(filtered), MediaType.APPLICATION_JSON_TYPE).build();
    }

    @Path("{pu}/entities")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response entities(@PathParam("pu") String puName) throws IOException {
        var sf = resolveSessionFactory(puName);
        if (sf == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        List<String> entityNames = sf.getMetamodel().getEntities().stream()
                .map(e -> e.getJavaType().getName())
                .sorted()
                .toList();
        return Response.ok(JsonSerialization.writeValueAsString(entityNames), MediaType.APPLICATION_JSON_TYPE).build();
    }

    private SessionFactoryImplementor resolveSessionFactory(String puName) {
        JpaConnectionProvider jpa;
        if ("default".equals(puName)) {
            jpa = session.getProvider(JpaConnectionProvider.class);
        } else {
            jpa = session.getProvider(JpaConnectionProvider.class, puName);
        }
        if (jpa == null) {
            return null;
        }
        return jpa.getEntityManager().getEntityManagerFactory().unwrap(SessionFactoryImplementor.class);
    }

    @Override
    public void close() {
    }
}
