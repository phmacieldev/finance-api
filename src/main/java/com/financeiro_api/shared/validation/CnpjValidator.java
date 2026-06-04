package com.financeiro_api.shared.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class CnpjValidator implements ConstraintValidator<ValidCnpj, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) return true; // @NotBlank handles null separately

        String digits = value.replaceAll("[.\\-/]", "");

        if (!digits.matches("\\d{14}")) return false;
        if (digits.chars().distinct().count() == 1) return false; // e.g. "00000000000000"

        return checkDigit(digits, 12) && checkDigit(digits, 13);
    }

    private boolean checkDigit(String digits, int position) {
        int sum = 0;
        int weight = position == 12 ? 5 : 6;
        for (int i = 0; i < position; i++) {
            sum += (digits.charAt(i) - '0') * weight;
            weight = weight == 2 ? 9 : weight - 1;
        }
        int remainder = sum % 11;
        int expected = remainder < 2 ? 0 : 11 - remainder;
        return (digits.charAt(position) - '0') == expected;
    }
}
