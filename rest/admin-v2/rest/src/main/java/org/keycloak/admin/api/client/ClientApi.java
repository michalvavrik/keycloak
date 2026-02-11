package org.keycloak.admin.api.client;

import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;

import org.keycloak.representations.admin.v2.BaseClientRepresentation;
import org.keycloak.representations.idm.ErrorRepresentation;

import com.fasterxml.jackson.databind.JsonNode;

import static org.keycloak.admin.api.AdminApi.CONTENT_TYPE_MERGE_PATCH;

public interface ClientApi {

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    BaseClientRepresentation getClient();

    /**
     * @return {@link BaseClientRepresentation} of created/updated client
     */
    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    Response createOrUpdateClient(@Valid BaseClientRepresentation client);

    @PATCH
    @Consumes(CONTENT_TYPE_MERGE_PATCH)
    @Produces(MediaType.APPLICATION_JSON)
    BaseClientRepresentation patchClient(JsonNode patch);

    // TODO marked as producing json, but does not return anything
    @DELETE
    @Produces(MediaType.APPLICATION_JSON)
    void deleteClient();

    @Path("generate-secret")
    @POST
    @Produces(MediaType.TEXT_PLAIN)
    @Operation(summary = "Generates a new client secret", description = "Generates a new client secret for clients using client-secret authentication method. Updates the client with this new secret and returns it.")
    @APIResponses(value = {
        @APIResponse(responseCode = "200", description = "Success - returns the newly generated secret"),
        @APIResponse(responseCode = "400", description = "Bad Request - client authentication method is not 'client-secret'"),
        @APIResponse(responseCode = "403", description = "Forbidden"),
        @APIResponse(responseCode = "404", description = "Not Found - client does not exist")
    })
    String generateSecret();
}
