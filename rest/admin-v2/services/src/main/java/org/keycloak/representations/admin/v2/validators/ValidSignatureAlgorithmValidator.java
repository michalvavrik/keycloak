package org.keycloak.representations.admin.v2.validators;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import org.keycloak.representations.admin.v2.validation.ValidSignatureAlgorithm;

public class ValidSignatureAlgorithmValidator implements ConstraintValidator<ValidSignatureAlgorithm, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        return value.startsWith("RSA_SHA") || value.startsWith("DSA_SHA");
    }
}
