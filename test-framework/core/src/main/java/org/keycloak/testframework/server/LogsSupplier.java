package org.keycloak.testframework.server;

import org.keycloak.testframework.annotations.InjectLogs;
import org.keycloak.testframework.injection.InstanceContext;
import org.keycloak.testframework.injection.LifeCycle;
import org.keycloak.testframework.injection.RequestedInstance;
import org.keycloak.testframework.injection.Supplier;
import org.keycloak.testframework.injection.SupplierOrder;

public class LogsSupplier implements Supplier<Logs, InjectLogs>, KeycloakServerConfigInterceptor<Logs, InjectLogs> {

    @Override
    public Logs getValue(InstanceContext<Logs, InjectLogs> instanceContext) {
        return new Logs();
    }

    @Override
    public KeycloakServerConfigBuilder intercept(KeycloakServerConfigBuilder serverConfig, InstanceContext<Logs, InjectLogs> instanceContext) {
        int node = instanceContext.getAnnotation().node();
        serverConfig.addLogs(node, instanceContext.getValue());
        return serverConfig;
    }

    @Override
    public LifeCycle getDefaultLifecycle() {
        return LifeCycle.GLOBAL;
    }

    @Override
    public boolean compatible(InstanceContext<Logs, InjectLogs> a, RequestedInstance<Logs, InjectLogs> b) {
        return a.getAnnotation().node() == b.getAnnotation().node();
    }

    @Override
    public String getRef(InjectLogs annotation) {
        return String.valueOf(annotation.node());
    }

    @Override
    public int order() {
        return SupplierOrder.BEFORE_KEYCLOAK_SERVER;
    }
}
