package org.keycloak.services.client;

import java.util.Optional;
import java.util.stream.Stream;

import org.keycloak.models.RealmModel;
import org.keycloak.representations.admin.v2.BaseClientRepresentation;
import org.keycloak.services.PatchType;
import org.keycloak.services.Service;
import org.keycloak.services.ServiceException;

import com.fasterxml.jackson.databind.JsonNode;

public interface ClientService extends Service {

    class ClientSearchOptions {
        // TODO
    }

    class ClientProjectionOptions {
        // TODO
    }

    class ClientSortAndSliceOptions {
        // order by
        // offset
        // limit
        // NOTE: this is not always the most desirable way to do pagination
    }

    record CreateOrUpdateResult(BaseClientRepresentation representation, boolean created) {}

    default Optional<BaseClientRepresentation> getClient(RealmModel realm, String clientId) throws ServiceException {
        return getClient(realm, clientId, null);
    }

    Optional<BaseClientRepresentation> getClient(RealmModel realm, String clientId, ClientProjectionOptions projectionOptions) throws ServiceException;

    default Stream<BaseClientRepresentation> getClients(RealmModel realm) {
        return getClients(realm, null, null, null);
    }

    Stream<BaseClientRepresentation> getClients(RealmModel realm, ClientProjectionOptions projectionOptions, ClientSearchOptions searchOptions, ClientSortAndSliceOptions sortAndSliceOptions);

    Stream<BaseClientRepresentation> deleteClients(RealmModel realm, ClientSearchOptions searchOptions);

    void deleteClient(RealmModel realm, String clientId) throws ServiceException;

    CreateOrUpdateResult createOrUpdate(RealmModel realm, BaseClientRepresentation client, ModificationType modificationType) throws ServiceException;

    BaseClientRepresentation patchClient(RealmModel realm, String clientId, PatchType patchType, JsonNode patch) throws ServiceException;

    enum ModificationType {
        CREATE(false), CREATE_OR_UPDATE(true), PATCH(true);

        public final boolean allowUpdate;

        ModificationType(boolean allowUpdate) {
            this.allowUpdate = allowUpdate;
        }
    }
}
