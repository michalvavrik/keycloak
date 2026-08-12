package org.keycloak.tests.cookies;

import org.keycloak.cookie.CookieType;

import org.apache.http.Header;
import org.apache.http.HttpResponse;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;

final class CookieAssertions {

    private CookieAssertions() {
    }

    static void assertSetCookieCount(HttpResponse response, int expected) {
        Header[] headers = response.getHeaders("Set-Cookie");
        assertThat("Set-Cookie header count", headers.length, equalTo(expected));
    }

    static void assertCookie(HttpResponse response, CookieType cookieType, String value, String path,
            int maxAge, boolean secure, boolean httpOnly, String sameSite, boolean verifyLegacyNotSent) {
        assertCookie(response, cookieType.getName(), value, path, maxAge, secure, httpOnly, sameSite, verifyLegacyNotSent);
    }

    static void assertCookie(HttpResponse response, String name, String value, String path,
            int maxAge, boolean secure, boolean httpOnly, String sameSite, boolean verifyLegacyNotSent) {
        String header = getSetCookieHeader(response, name);

        if (value.isEmpty()) {
            assertThat("Cookie " + name + " value", header, startsWith(name + "=;"));
        } else {
            assertThat("Cookie " + name + " value", header,
                    containsString(name + "=" + value));
        }

        assertThat("Cookie " + name + " Path", header, containsString("Path=" + path));

        if (maxAge >= 0) {
            assertThat("Cookie " + name + " Max-Age", header, containsString("Max-Age=" + maxAge));
        }

        if (secure) {
            assertThat("Cookie " + name + " Secure", header, containsString("Secure"));
        } else {
            assertThat("Cookie " + name + " Secure", header, not(containsString("Secure")));
        }

        if (httpOnly) {
            assertThat("Cookie " + name + " HttpOnly", header, containsString("HttpOnly"));
        } else {
            assertThat("Cookie " + name + " HttpOnly", header, not(containsString("HttpOnly")));
        }

        if (sameSite == null) {
            assertThat("Cookie " + name + " SameSite", header, not(containsString("SameSite")));
        } else {
            assertThat("Cookie " + name + " SameSite", header, containsString("SameSite=" + sameSite));
        }

        if (verifyLegacyNotSent) {
            assertThat("Legacy cookie " + name + "_LEGACY should not be sent",
                    getSetCookieHeaderOrNull(response, name + "_LEGACY"), nullValue());
        }
    }

    static String getSetCookieHeader(HttpResponse response, String name) {
        String header = getSetCookieHeaderOrNull(response, name);
        assertThat("Set-Cookie header for " + name, header, notNullValue());
        return header;
    }

    static String getSetCookieHeaderOrNull(HttpResponse response, String name) {
        for (Header header : response.getHeaders("Set-Cookie")) {
            if (header.getValue().startsWith(name + "=")) {
                return header.getValue();
            }
        }
        return null;
    }
}
