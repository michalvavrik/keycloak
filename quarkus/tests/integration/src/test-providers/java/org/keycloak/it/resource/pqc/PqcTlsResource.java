package org.keycloak.it.resource.pqc;

import java.lang.reflect.Field;
import java.net.Socket;
import java.sql.Connection;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import io.agroal.api.AgroalDataSource;
import io.quarkus.arc.Arc;
import org.keycloak.models.KeycloakSession;
import org.keycloak.services.resource.RealmResourceProvider;

public class PqcTlsResource implements RealmResourceProvider {

    private final KeycloakSession session;

    public PqcTlsResource(KeycloakSession session) {
        this.session = session;
    }

    @Override
    public Object getResource() {
        return this;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getJdbcTlsInfo() {
        try {
            AgroalDataSource ds = Arc.container().select(AgroalDataSource.class).get();
            try (Connection conn = ds.getConnection()) {
                Object pgConn = conn.unwrap(Class.forName("org.postgresql.jdbc.PgConnection").asSubclass(Connection.class));
                Object queryExecutor = pgConn.getClass().getMethod("getQueryExecutor").invoke(pgConn);
                Field pgStreamField = queryExecutor.getClass().getSuperclass().getDeclaredField("pgStream");
                pgStreamField.setAccessible(true);
                Object pgStream = pgStreamField.get(queryExecutor);
                Socket socket = (Socket) pgStream.getClass().getMethod("getSocket").invoke(pgStream);
                if (socket instanceof SSLSocket sslSocket) {
                    SSLSession sslSession = sslSocket.getSession();
                    String protocol = sslSession.getProtocol();
                    String cipherSuite = sslSession.getCipherSuite();
                    String[] namedGroups = null;
                    try {
                        namedGroups = (String[]) sslSocket.getSSLParameters().getClass()
                                .getMethod("getNamedGroups").invoke(sslSocket.getSSLParameters());
                    } catch (NoSuchMethodException e) {
                        // JDK < 20
                    }
                    String json = "{\"ssl\":true,\"protocol\":\"%s\",\"cipherSuite\":\"%s\",\"namedGroups\":\"%s\"}"
                            .formatted(protocol, cipherSuite, namedGroups != null ? String.join(",", namedGroups) : "N/A");
                    return Response.ok(json, MediaType.APPLICATION_JSON).build();
                }
                return Response.ok("{\"ssl\":false}", MediaType.APPLICATION_JSON).build();
            }
        } catch (Exception e) {
            return Response.serverError().entity("{\"error\":\"" + e.getMessage() + "\"}").build();
        }
    }

    @Override
    public void close() {
    }
}
