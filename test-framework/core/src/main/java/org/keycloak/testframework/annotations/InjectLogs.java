package org.keycloak.testframework.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Injects a {@link org.keycloak.testframework.logs.Logs} instance that can be 
 * used to assert server logs during test execution.
 * 
 * <p>Example usage:</p>
 * <pre>
 * {@code
 * @InjectLogs
 * Logs logs;
 * 
 * @Test
 * public void testNoErrors() {
 *     // perform operations
 *     logs.assertNoErrors();
 * }
 * }
 * </pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface InjectLogs {

    /**
     * A ref must be set if a test requires multiple instances
     */
    String ref() default "";

    /**
     * Minimum log level to capture (defaults to ALL).
     * Supported values: ERROR, SEVERE, WARN, WARNING, INFO, DEBUG, FINE, TRACE, FINER, ALL
     */
    String level() default "ALL";
}
