/**
 * Log capture functionality for Keycloak test framework.
 * 
 * <h2>Overview</h2>
 * <p>
 * The logs package provides a way to capture and assert on server logs during test execution,
 * similar to how the {@link org.keycloak.testframework.events} package works for events.
 * </p>
 * 
 * <h2>Usage</h2>
 * <pre>
 * {@code
 * @KeycloakIntegrationTest
 * public class MyTest {
 *     @InjectLogs
 *     Logs logs;
 *     
 *     @Test
 *     public void testNoErrors() {
 *         // Perform operations
 *         logs.assertNoErrors();
 *     }
 *     
 *     @Test
 *     public void testSpecificLog() {
 *         // Perform operations
 *         logs.assertExists("ERROR", "Connection failed");
 *     }
 * }
 * }
 * </pre>
 * 
 * <h2>Server Type Support</h2>
 * <p>
 * Log capture behavior depends on the Keycloak server type being used:
 * </p>
 * 
 * <h3>EmbeddedKeycloakServer</h3>
 * <ul>
 *   <li><b>Status:</b> ✅ Fully Supported</li>
 *   <li><b>Mechanism:</b> Captures logs from the root logger (empty string)</li>
 *   <li><b>Scope:</b> All logs in the same JVM, including test logs</li>
 *   <li><b>Use Case:</b> Unit and integration tests</li>
 * </ul>
 * 
 * <h3>DistributionKeycloakServer</h3>
 * <ul>
 *   <li><b>Status:</b> ✅ Fully Supported</li>
 *   <li><b>Mechanism:</b> Captures logs from the "managed.keycloak" logger</li>
 *   <li><b>Scope:</b> Only logs from the Keycloak server process output</li>
 *   <li><b>Use Case:</b> Integration tests with distribution builds</li>
 *   <li><b>Note:</b> Test logs are NOT captured, only server logs</li>
 * </ul>
 * 
 * <h3>RemoteKeycloakServer</h3>
 * <ul>
 *   <li><b>Status:</b> ❌ Not Supported</li>
 *   <li><b>Reason:</b> Server runs in a separate process/machine</li>
 *   <li><b>Behavior:</b> Logs instance is created but disabled (returns empty lists)</li>
 *   <li><b>Alternative:</b> Use log file parsing or monitoring tools</li>
 * </ul>
 * 
 * <h2>Configuration</h2>
 * <p>
 * The {@link org.keycloak.testframework.annotations.InjectLogs} annotation supports:
 * </p>
 * <ul>
 *   <li><b>ref:</b> Reference name for multiple log instances</li>
 *   <li><b>level:</b> Minimum log level to capture (ERROR, WARN, INFO, DEBUG, TRACE, ALL)</li>
 * </ul>
 * 
 * <h2>Lifecycle</h2>
 * <p>
 * Logs are automatically cleared before each test method via the 
 * {@link org.keycloak.testframework.logs.LogsSupplier#onBeforeEach} callback.
 * </p>
 * 
 * @see org.keycloak.testframework.logs.Logs
 * @see org.keycloak.testframework.logs.LogEntry
 * @see org.keycloak.testframework.annotations.InjectLogs
 */
package org.keycloak.testframework.logs;
