package org.keycloak.tests.admin.client.v2.validation;

import org.keycloak.representations.admin.v2.validators.ValidWebOriginsValidator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link ValidWebOriginsValidator} covering web origin format validation
 * per RFC 6454 (scheme://host[:port]) plus special values {@code *} and {@code +}.
 */
class WebOriginValidationTest {

    @Nested
    @DisplayName("Special values")
    class SpecialValues {

        @Test
        @DisplayName("accepts wildcard - *")
        void acceptsWildcard() {
            assertTrue(ValidWebOriginsValidator.isValidWebOrigin("*"));
        }

        @Test
        @DisplayName("accepts plus (derive from redirects) - +")
        void acceptsPlus() {
            assertTrue(ValidWebOriginsValidator.isValidWebOrigin("+"));
        }
    }

    @Nested
    @DisplayName("Valid origins")
    class ValidOrigins {

        @ParameterizedTest
        @ValueSource(strings = {
                "https://example.com",
                "http://example.com",
                "https://example.com:8443",
                "http://localhost:3000",
                "http://127.0.0.1:8080",
                "https://sub.domain.example.com",
                "http://[::1]:8080",
                "https://example.com:443"
        })
        @DisplayName("accepts valid scheme://host[:port] origins")
        void acceptsValidOrigins(String origin) {
            assertTrue(ValidWebOriginsValidator.isValidWebOrigin(origin));
        }

        @Test
        @DisplayName("accepts custom scheme - myapp://auth")
        void acceptsCustomScheme() {
            assertTrue(ValidWebOriginsValidator.isValidWebOrigin("myapp://auth"));
        }

        @Test
        @DisplayName("accepts null (field is optional)")
        void acceptsNull() {
            assertTrue(ValidWebOriginsValidator.isValidWebOrigin(null));
        }
    }

    @Nested
    @DisplayName("Invalid origins")
    class InvalidOrigins {

        @Test
        @DisplayName("rejects plain string without scheme - not-an-origin")
        void rejectsPlainString() {
            assertFalse(ValidWebOriginsValidator.isValidWebOrigin("not-an-origin"));
        }

        @Test
        @DisplayName("rejects origin with path - https://example.com/path")
        void rejectsOriginWithPath() {
            assertFalse(ValidWebOriginsValidator.isValidWebOrigin("https://example.com/path"));
        }

        @Test
        @DisplayName("rejects origin with trailing slash - https://example.com/")
        void rejectsOriginWithTrailingSlash() {
            assertFalse(ValidWebOriginsValidator.isValidWebOrigin("https://example.com/"));
        }

        @Test
        @DisplayName("rejects origin with query - https://example.com?foo=bar")
        void rejectsOriginWithQuery() {
            assertFalse(ValidWebOriginsValidator.isValidWebOrigin("https://example.com?foo=bar"));
        }

        @Test
        @DisplayName("rejects origin with fragment - https://example.com#section")
        void rejectsOriginWithFragment() {
            assertFalse(ValidWebOriginsValidator.isValidWebOrigin("https://example.com#section"));
        }

        @Test
        @DisplayName("rejects empty string")
        void rejectsEmptyString() {
            assertFalse(ValidWebOriginsValidator.isValidWebOrigin(""));
        }

        @Test
        @DisplayName("rejects blank string")
        void rejectsBlankString() {
            assertFalse(ValidWebOriginsValidator.isValidWebOrigin("   "));
        }

        @Test
        @DisplayName("rejects scheme only - https://")
        void rejectsSchemeOnly() {
            assertFalse(ValidWebOriginsValidator.isValidWebOrigin("https://"));
        }

        @Test
        @DisplayName("rejects missing scheme - example.com")
        void rejectsMissingScheme() {
            assertFalse(ValidWebOriginsValidator.isValidWebOrigin("example.com"));
        }

        @Test
        @DisplayName("rejects scheme starting with digit - 1http://example.com")
        void rejectsSchemeStartingWithDigit() {
            assertFalse(ValidWebOriginsValidator.isValidWebOrigin("1http://example.com"));
        }

        @Test
        @DisplayName("rejects origin with path and wildcard - https://example.com/*")
        void rejectsOriginWithPathAndWildcard() {
            assertFalse(ValidWebOriginsValidator.isValidWebOrigin("https://example.com/*"));
        }

        @Test
        @DisplayName("rejects origin with userinfo - https://user:pass@example.com")
        void rejectsOriginWithUserinfo() {
            assertFalse(ValidWebOriginsValidator.isValidWebOrigin("https://user:pass@example.com"));
        }

        @Test
        @DisplayName("rejects origin with matrix parameter - https://example.com;param=value")
        void rejectsOriginWithMatrixParam() {
            assertFalse(ValidWebOriginsValidator.isValidWebOrigin("https://example.com;param=value"));
        }
    }
}
