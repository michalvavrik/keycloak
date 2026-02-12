package org.keycloak.models.mapper;

import org.keycloak.representations.idm.ClientRepresentation;

/**
 * @author Vaclav Muzikar <vmuzikar@redhat.com>
 */
public interface RepModelMapper <T, U> {
    T fromModel(U model);

    default U toModel(T rep) {
        return toModel(rep, null);
    }

    U toModel(T rep, U existingModel);

    ClientRepresentation createOldClientRepresentation(T rep);
}
