package org.keycloak.quarkus.runtime.services;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Arrays;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;

import jakarta.annotation.Priority;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;

import io.vertx.ext.web.RoutingContext;

@Provider
@Priority(Integer.MAX_VALUE)
public class PqcTlsVerificationFilter implements ContainerResponseFilter {

    private static final String PQC_HEADER = "X-PQC-Verified";
    private static final String PQC_NAMED_GROUP = "X25519MLKEM768";

    @Override
    public void filter(ContainerRequestContext requestContext,
                       ContainerResponseContext responseContext) throws IOException {
        Instance<RoutingContext> instances = CDI.current().select(RoutingContext.class);
        if (!instances.isResolvable()) {
            return;
        }

        SSLSession sslSession = instances.get().request().sslSession();
        if (sslSession == null) {
            return;
        }

        if (!"TLSv1.3".equals(sslSession.getProtocol())) {
            responseContext.getHeaders().putSingle(PQC_HEADER, "FAIL:protocol=" + sslSession.getProtocol());
            return;
        }

        try {
            SSLParameters params = SSLContext.getDefault().getDefaultSSLParameters();
            // SSLParameters.getNamedGroups() added in JDK 20; compile target is 17
            Method getNamedGroups = SSLParameters.class.getMethod("getNamedGroups");
            String[] groups = (String[]) getNamedGroups.invoke(params);
            if (groups != null && groups.length == 1 && PQC_NAMED_GROUP.equals(groups[0])) {
                responseContext.getHeaders().putSingle(PQC_HEADER, PQC_NAMED_GROUP);
            } else {
                responseContext.getHeaders().putSingle(PQC_HEADER, "FAIL:namedGroups=" + Arrays.toString(groups));
            }
        } catch (NoSuchMethodException e) {
            responseContext.getHeaders().putSingle(PQC_HEADER, "FAIL:JDK<20");
        } catch (Exception e) {
            responseContext.getHeaders().putSingle(PQC_HEADER, "FAIL:" + e.getMessage());
        }
    }
}
