package org.keycloak.tests.providers.rest;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.keycloak.email.DefaultEmailSenderProvider;
import org.keycloak.email.DefaultEmailSenderProvider.SmtpTlsSessionInfo;
import org.keycloak.services.resource.RealmResourceProvider;

public class SmtpPqcTlsResource implements RealmResourceProvider {

    @Override
    public Object getResource() {
        return this;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getSmtpTlsInfo() {
        SmtpTlsSessionInfo info = DefaultEmailSenderProvider.getLastSmtpTlsSessionInfo();
        if (info == null) {
            return Response.ok("{\"available\":false}", MediaType.APPLICATION_JSON).build();
        }
        String namedGroups = info.namedGroups() != null
                ? String.join(",", info.namedGroups())
                : "N/A";
        String json = "{\"available\":true,\"protocol\":\"%s\",\"valid\":%s,\"namedGroups\":\"%s\"}"
                .formatted(info.protocol(), info.valid(), namedGroups);
        return Response.ok(json, MediaType.APPLICATION_JSON).build();
    }

    @Override
    public void close() {
    }
}
