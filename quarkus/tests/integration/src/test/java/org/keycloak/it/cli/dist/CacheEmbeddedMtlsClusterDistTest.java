/*
 * Copyright 2025 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.keycloak.it.cli.dist;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.keycloak.it.utils.RawKeycloakDistribution;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class CacheEmbeddedMtlsClusterDistTest {

    private static final long START_TIMEOUT_MS = 120_000;
    private static final int CLUSTER_VIEW_TIMEOUT_SECONDS = 60;

    @Test
    public void testClusterPqcMtls() {
        final RawKeycloakDistribution node1 = new RawKeycloakDistribution(true);
        final RawKeycloakDistribution node2 = new RawKeycloakDistribution(false);
        try {
            node1.runKc(List.of(
                    "start-dev",
                    "--cache=ispn",
                    "--cache-embedded-mtls-enabled=true",
                    "--cache-embedded-network-bind-port=7800",
                    "--db-url-properties=;AUTO_SERVER=TRUE;DB_CLOSE_ON_EXIT=TRUE"
            ));
            node1.waitFor(true, START_TIMEOUT_MS);

            node2.runKc(List.of(
                    "start-dev",
                    "--cache=ispn",
                    "--cache-embedded-mtls-enabled=true",
                    "--http-port=8081",
                    "--http-management-port=9001",
                    "--cache-embedded-network-bind-port=7801",
                    "--db-url-properties=;AUTO_SERVER=TRUE;DB_CLOSE_ON_EXIT=TRUE"
            ));
            node2.waitFor(true, START_TIMEOUT_MS);

            Awaitility.await()
                    .atMost(CLUSTER_VIEW_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .until(() -> getCombinedOutput(node1, node2).stream()
                            .anyMatch(line -> line.contains("ISPN000094") && line.contains("(2)")));

            final List<String> combinedLogs = getCombinedOutput(node1, node2);

            assertTrue(combinedLogs.stream().anyMatch(
                            line -> line.contains("JGroups mTLS actual session: protocol=TLSv1.3")),
                    "Expected JGroups mTLS TLSv1.3 handshake in logs");
            assertTrue(combinedLogs.stream().anyMatch(
                            line -> line.contains("JGroups mTLS handshake socket named groups: [X25519MLKEM768]")),
                    "Expected JGroups mTLS PQC named groups from actual handshake");
        } finally {
            node2.stop();
            node1.stop();
        }
    }

    private static List<String> getCombinedOutput(RawKeycloakDistribution... nodes) {
        final List<String> combined = new ArrayList<>();
        for (final RawKeycloakDistribution node : nodes) {
            combined.addAll(node.getOutputStream());
        }
        return combined;
    }
}
