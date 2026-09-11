package dev.by1337.web.util;

public class Base62Converter {

    private static final String ALPHABET =
            "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final int BASE = ALPHABET.length();

    public static String encode(long value) {
        if (value == 0) {
            return "0";
        }

        StringBuilder sb = new StringBuilder(11);

        while (value != 0) {
            int remainder = (int) Long.remainderUnsigned(value, BASE);
            sb.append(ALPHABET.charAt(remainder));
            value = Long.divideUnsigned(value, BASE);
        }

        return sb.reverse().toString();
    }
}