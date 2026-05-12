package org.keycloak.representations.admin.v2.validators;

import java.util.Set;
import java.util.regex.Pattern;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import org.keycloak.representations.admin.v2.OIDCClientRepresentation;
import org.keycloak.representations.admin.v2.validation.ValidWebOrigins;

public class ValidWebOriginsValidator implements ConstraintValidator<ValidWebOrigins, OIDCClientRepresentation> {

    private static final Pattern ORIGIN_PATTERN = Pattern.compile("^[a-zA-Z][a-zA-Z0-9+.-]*://[^/?#@;]+$");

    @Override
    public boolean isValid(OIDCClientRepresentation representation, ConstraintValidatorContext context) {
        Set<String> webOrigins = representation.getWebOrigins();
        if (webOrigins == null || webOrigins.isEmpty()) {
            return true;
        }
        for (String origin : webOrigins) {
            if (!isValidWebOrigin(origin)) {
                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                        .addPropertyNode("webOrigins")
                        .addConstraintViolation();
                return false;
            }
        }
        return true;
    }

    public static boolean isValidWebOrigin(String origin) {
        if (origin == null) {
            return true;
        }
        if ("*".equals(origin) || "+".equals(origin)) {
            return true;
        }
        if (origin.isBlank()) {
            return false;
        }
        return ORIGIN_PATTERN.matcher(origin).matches();
    }
}
