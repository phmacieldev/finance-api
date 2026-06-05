package com.financeiro_api.shared.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class CpfValidator implements ConstraintValidator<ValidCpf, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) return false;
        String digits = value.replaceAll("[.\\-]", "");
        if (digits.length() != 11) return false;
        if (digits.chars().distinct().count() == 1) return false;

        int sum = 0;
        for (int i = 0; i < 9; i++) sum += (digits.charAt(i) - '0') * (10 - i);
        int d1 = (sum * 10) % 11;
        if (d1 == 10 || d1 == 11) d1 = 0;
        if (d1 != (digits.charAt(9) - '0')) return false;

        sum = 0;
        for (int i = 0; i < 10; i++) sum += (digits.charAt(i) - '0') * (11 - i);
        int d2 = (sum * 10) % 11;
        if (d2 == 10 || d2 == 11) d2 = 0;
        return d2 == (digits.charAt(10) - '0');
    }
}
