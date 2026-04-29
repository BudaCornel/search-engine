package com.buda.searchengine.query.booleanq;


public final class SizeUnit {

    private SizeUnit() {}

    public static long parseBytes(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Empty size value");
        }
        String trimmed = value.trim();

        int splitAt = 0;
        while (splitAt < trimmed.length()
                && (Character.isDigit(trimmed.charAt(splitAt))
                || trimmed.charAt(splitAt) == '.'
                || (splitAt == 0 && trimmed.charAt(splitAt) == '-'))) {
            splitAt++;
        }
        if (splitAt == 0) {
            throw new IllegalArgumentException("Size must start with a number: " + value);
        }
        String numericPart = trimmed.substring(0, splitAt);
        String suffix = trimmed.substring(splitAt).trim().toUpperCase();

        double n;
        try {
            n = Double.parseDouble(numericPart);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid size number: " + numericPart);
        }

        long multiplier = switch (suffix) {
            case "", "B" -> 1L;
            case "K", "KB" -> 1024L;
            case "M", "MB" -> 1024L * 1024L;
            case "G", "GB" -> 1024L * 1024L * 1024L;
            default -> throw new IllegalArgumentException("Unknown size suffix: " + suffix);
        };
        return (long) (n * multiplier);
    }
}
