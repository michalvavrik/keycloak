/*
 * Copyright 2026 Red Hat, Inc. and/or its affiliates
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

import io.quarkus.test.junit.main.Launch;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.keycloak.it.TestProvider;

import static org.junit.jupiter.api.Assertions.assertTrue;
import org.keycloak.it.junit5.extension.BeforeStartDistribution;
import org.keycloak.it.junit5.extension.CLIResult;
import org.keycloak.it.junit5.extension.DistributionTest;
import org.keycloak.it.junit5.extension.RawDistOnly;
import org.keycloak.it.utils.KeycloakDistribution;
import org.keycloak.it.utils.RawKeycloakDistribution;

import java.io.File;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Runs test with distribution placed in a directory that has spaces in name.
 *
 * @see <a href="https://github.com/keycloak/keycloak/issues/45971">Issue #45971</a> for motivation of this test
 */
@BeforeStartDistribution(PathWithSpacesDistTest.AddCustomScriptsProvider.class)
@RawDistOnly(reason = "Testing installation path handling")
@DistributionTest(reInstall = DistributionTest.ReInstall.BEFORE_TEST)
class PathWithSpacesDistTest {

    private static final String JAVA_IO_TMPDIR = "java.io.tmpdir";
    private static String originalTmpDir = null;

    @BeforeAll
    static void changeTmpDir() {
        originalTmpDir = System.getProperty(JAVA_IO_TMPDIR);
        System.setProperty(JAVA_IO_TMPDIR, originalTmpDir + File.separator + " o o o o");
    }

    @AfterAll
    static void switchBackToOriginalTmpDir() {
        if (originalTmpDir != null) {
            System.setProperty(JAVA_IO_TMPDIR, originalTmpDir);
            originalTmpDir = null;
        }
    }

    @Test
    @Launch({"start-dev"})
    void testStartDevWithPathContainingSpaces(CLIResult result, KeycloakDistribution dist) {
        RawKeycloakDistribution rawDist = dist.unwrap(RawKeycloakDistribution.class);
        String distPath = rawDist.getDistPath().toString();
        assertTrue(distPath.contains(" "), "Test must run with spaces in path. Actual path: " + distPath);

        result.assertBuild();
        result.assertStartedDevMode();
    }

    public static final class AddCustomScriptsProvider implements Consumer<KeycloakDistribution> {
        @Override
        public void accept(KeycloakDistribution distribution) {
            RawKeycloakDistribution rawDist = distribution.unwrap(RawKeycloakDistribution.class);
            rawDist.copyProvider(new ScriptProviderForTest());
        }
    }

    /**
     * Triggers provider discovery in KeycloakProcessor.
     */
    private static final class ScriptProviderForTest implements TestProvider {
        @Override
        public String getName() {
            return "test-script-provider";
        }

        @Override
        public Class[] getClasses() {
            return new Class[0];
        }

        @Override
        public Map<String, String> getManifestResources() {
            return Map.of("keycloak-scripts.json", "keycloak-scripts.json");
        }
    }
}
