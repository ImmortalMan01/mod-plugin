package me.ogulcan.chatmod.service;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public class WordFilter {

    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}");
    private static final Pattern NON_ALNUM = Pattern.compile("[^\\p{L}\\p{Nd}]");

    /**
     * Normalize text by converting to lowercase, replacing common Turkish
     * characters with their ASCII equivalents, stripping any remaining
     * diacritics and removing whitespace or punctuation.
     */
    private static String normalize(String text) {
        if (text == null) return "";
        String lower = text.toLowerCase(Locale.ROOT)
                .replace('ğ', 'g')
                .replace('ş', 's')
                .replace('ö', 'o')
                .replace('ü', 'u')
                .replace('ç', 'c')
                .replace('ı', 'i');
        String nfd = Normalizer.normalize(lower, Normalizer.Form.NFD);
        String withoutDiacritics = DIACRITICS.matcher(nfd).replaceAll("");
        return NON_ALNUM.matcher(withoutDiacritics).replaceAll("");
    }

    public static boolean containsBlockedWord(String message, List<String> blockedWords) {
        if (message == null) return false;
        String normalizedMessage = normalize(message);
        for (String w : blockedWords) {
            if (normalizedMessage.contains(normalize(w))) {
                return true;
            }
        }
        return false;
    }
}
