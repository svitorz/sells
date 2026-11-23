package com.github.svitorz.sells.domain.shared;

public final class Texts {

    private Texts() {
    }

    /** Trims the value and fails if it is blank or longer than maxLength. */
    public static String required(String value, String label, int maxLength) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty()) {
            throw new DomainException("O campo " + label + " é obrigatório.");
        }
        return checkLength(trimmed, label, maxLength);
    }

    /** Trims the value and returns null if it is blank. */
    public static String optional(String value, String label, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return checkLength(value.trim(), label, maxLength);
    }

    /** Removes every character that is not a digit. Returns null for null or for no digits. */
    public static String digitsOnly(String value) {
        if (value == null) {
            return null;
        }
        String digits = value.replaceAll("\\D", "");
        return digits.isEmpty() ? null : digits;
    }

    private static String checkLength(String value, String label, int maxLength) {
        if (value.length() > maxLength) {
            throw new DomainException("O campo " + label + " deve ter no máximo " + maxLength + " caracteres.");
        }
        return value;
    }
}
