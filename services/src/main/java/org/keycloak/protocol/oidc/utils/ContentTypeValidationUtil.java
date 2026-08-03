package org.keycloak.protocol.oidc.utils;

import jakarta.ws.rs.NotSupportedException;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;

// FIXME: drop when ... is fixed
public final class ContentTypeValidationUtil {

    public static void requireValidContentType(HttpHeaders headers, MediaType requiredMediaType) {
        String contentType = headers.getHeaderString(HttpHeaders.CONTENT_TYPE);
        if (contentType == null) {
            return;
        }
        MediaType requestMediaType;
        try {
            requestMediaType = MediaType.valueOf(contentType);
        } catch (IllegalArgumentException e) {
            throw new NotSupportedException(
                    "The content-type header value did not correspond to a valid media type");
        }
        if (!requestMediaType.isCompatible(requiredMediaType)) {
            throw new NotSupportedException(
                    "The content-type header value does not match consumed media type " + requiredMediaType);
        }
    }


}
