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
@DistributionTest(reInstall = DistributionTest.ReInstall.BEFORE_TEST)
public class PathWithSpacesDistTest {

    private static final String JAVA_IO_TMPDIR = "java.io.tmpdir";
    private static String originalTmpDir = null;

    @BeforeAll
    static void changeTmpDir() {
        originalTmpDir = System.getProperty("java.io.tmpdir");
        System.setProperty(JAVA_IO_TMPDIR, originalTmpDir + File.separator + " o o o o");
    }

    @AfterAll
    static void switchBackToOriginalTmpDir() {
        if (originalTmpDir != null) {
            System.setProperty(JAVA_IO_TMPDIR, originalTmpDir);
            originalTmpDir = null;
        }
    }

    @RawDistOnly(reason = "Testing installation path handling")
    @Test
    @BeforeStartDistribution(AddCustomScriptsProvider.class)
    @Launch({"build", "--db=dev-file"})
    void testBuildWithPathContainingSpaces(CLIResult result, KeycloakDistribution dist) {
        // Verify the test actually ran with a path containing spaces
        RawKeycloakDistribution rawDist = dist.unwrap(RawKeycloakDistribution.class);
        String distPath = rawDist.getDistPath().toString();
        assertTrue(distPath.contains(" "),
                "Test must run with spaces in path. Actual path: " + distPath);

        // This test ensures that the build succeeds even though provider JARs with scripts are discovered
        // If the bug existed, this would fail with "Failed to discover script providers"
        // and show a path with %20 instead of spaces
        result.assertBuild();
    }

    @RawDistOnly(reason = "Testing installation path handling")
    @Test
    @BeforeStartDistribution(AddCustomScriptsProvider.class)
    @Launch({"start-dev"})
    void testStartDevWithPathContainingSpaces(CLIResult result, KeycloakDistribution dist) {
        // Verify the test actually ran with a path containing spaces
        RawKeycloakDistribution rawDist = dist.unwrap(RawKeycloakDistribution.class);
        String distPath = rawDist.getDistPath().toString();
        assertTrue(distPath.contains(" "),
                "Test must run with spaces in path. Actual path: " + distPath);

        // Verify that server can start successfully in a directory with spaces
        result.assertStartedDevMode();
    }

    /**
     * Adds a test provider with keycloak-scripts.json to trigger script provider discovery.
     * This is where the bug manifests when the installation path contains spaces.
     */
    public static class AddCustomScriptsProvider implements Consumer<KeycloakDistribution> {
        @Override
        public void accept(KeycloakDistribution distribution) {
            RawKeycloakDistribution rawDist = distribution.unwrap(RawKeycloakDistribution.class);
            rawDist.copyProvider(new ScriptProviderForTest());
        }
    }

    /**
     * A minimal TestProvider that includes only a keycloak-scripts.json file.
     * This triggers the script provider discovery code in KeycloakProcessor.
     * No actual provider classes are needed - just the scripts descriptor.
     */
    public static class ScriptProviderForTest implements TestProvider {
        @Override
        public String getName() {
            return "test-script-provider";
        }

        @Override
        public Class[] getClasses() {
            // No classes needed - just the keycloak-scripts.json file
            return new Class[0];
        }

        @Override
        public Map<String, String> getManifestResources() {
            // Map the keycloak-scripts.json from the test resources to META-INF
            return Map.of("keycloak-scripts.json", "keycloak-scripts.json");
        }
    }
}
