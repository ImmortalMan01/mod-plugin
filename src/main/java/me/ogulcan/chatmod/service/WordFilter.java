package me.ogulcan.chatmod.service;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public class WordFilter {

    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}");
    // Replace punctuation with spaces but keep letters, digits and whitespace
    private static final Pattern PUNCT = Pattern.compile("[^\\p{L}\\p{Nd}\\s]");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    /**
     * Normalize text by converting to lowercase, replacing common Turkish
     * characters with their ASCII equivalents, stripping any remaining
     * diacritics and converting punctuation to spaces. Multiple spaces are
     * collapsed to a single space and the result is trimmed.
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
        String withSpaces = PUNCT.matcher(withoutDiacritics).replaceAll(" ");
        return WHITESPACE.matcher(withSpaces).replaceAll(" ").trim();
    }

    public static boolean containsBlockedWord(String message, List<String> blockedWords) {
        if (message == null) return false;
        String normalizedMessage = normalize(message);
        String[] tokens = normalizedMessage.split(" ");

        // Merge consecutive single-character tokens so "s i k" becomes "sik"
        List<String> words = new java.util.ArrayList<>();
        StringBuilder sb = new StringBuilder();
        for (String t : tokens) {
            if (t.length() <= 1) {
                sb.append(t);
            } else {
                if (sb.length() > 0) {
                    words.add(sb.toString());
                    sb.setLength(0);
                }
                words.add(t);
            }
        }
        if (sb.length() > 0) words.add(sb.toString());

        for (String token : words) {
            for (String w : blockedWords) {
                if (token.contains(normalize(w))) {
                    return true;
                }
            }
        }
        return false;
    }
}
