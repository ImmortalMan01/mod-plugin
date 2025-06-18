package me.ogulcan.chatmod.service;

import java.text.Normalizer;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.Locale;
import java.util.regex.Pattern;

public class WordFilter {

    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}");
    // Replace punctuation with spaces but keep letters, digits and whitespace
    private static final Pattern PUNCT = Pattern.compile("[^\\p{L}\\p{Nd}\\s]");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private static final java.util.Map<Character, Character> DIGIT_MAP =
            java.util.Map.of('0', 'o', '1', 'i', '3', 'e', '4', 'a', '5', 's',
                              '7', 't', '8', 'b');

    /**
     * Normalize text by converting to lowercase, replacing common Turkish
     * characters with their ASCII equivalents, stripping any remaining
     * diacritics and converting punctuation to spaces. Multiple spaces are
     * collapsed to a single space and the result is trimmed.
     */
    public static String normalize(String text) {
        return normalize(text, false);
    }

    /**
     * @param canonical if true digits are mapped to letters and repeated
     *                  characters are collapsed
     */
    public static String normalize(String text, boolean canonical) {
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
        String base = WHITESPACE.matcher(withSpaces).replaceAll(" ").trim();
        if (!canonical) return base;

        StringBuilder mapped = new StringBuilder();
        for (char c : base.toCharArray()) {
            if (Character.isDigit(c) && DIGIT_MAP.containsKey(c)) {
                mapped.append(DIGIT_MAP.get(c));
            } else {
                mapped.append(c);
            }
        }

        StringBuilder collapsed = new StringBuilder();
        char prev = 0;
        for (int i = 0; i < mapped.length(); i++) {
            char c = mapped.charAt(i);
            if (c != prev || c == ' ') {
                collapsed.append(c);
                prev = c;
            }
        }
        return collapsed.toString();
    }

    public static String canonicalize(String text) {
        return normalize(text, true);
    }

    public static boolean containsBlockedWord(String message, List<String> blockedWords) {
        if (message == null) return false;
        Set<String> normalized = new HashSet<>();
        for (String w : blockedWords) {
            normalized.add(canonicalize(w));
        }
        return containsBlockedWord(message, normalized, true);
    }

    /**
     * Variant of {@link #containsBlockedWord(String, List)} where the block list
     * is already normalized. This avoids normalizing each word repeatedly.
     */
    public static boolean containsBlockedWord(String message, Set<String> normalizedWords, boolean wordsNormalized) {
        if (!wordsNormalized) {
            // Convert and delegate
            return containsBlockedWord(message, new java.util.ArrayList<>(normalizedWords));
        }
        if (message == null) return false;
        String normalizedMessage = canonicalize(message);
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
            if (normalizedWords.stream().anyMatch(token::contains)) {
                return true;
            }
        }
        return false;
    }

}
