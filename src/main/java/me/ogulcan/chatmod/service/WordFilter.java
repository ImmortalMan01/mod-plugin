package me.ogulcan.chatmod.service;

import java.text.Normalizer;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.tartarus.snowball.ext.englishStemmer;
import org.tartarus.snowball.ext.turkishStemmer;

public class WordFilter {

    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}");
    // Replace punctuation with spaces but keep letters, digits and whitespace
    private static final Pattern PUNCT = Pattern.compile("[^\\p{L}\\p{Nd}\\s]");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    // Strip zero width characters which can split words
    private static final Pattern ZERO_WIDTH = Pattern.compile("[\\u200B\\u200C\\u200D\\uFEFF]");

    // Map common confusable characters (e.g. Cyrillic letters) to ASCII
    private static final Map<Character, Character> CONFUSABLE_MAP = Map.ofEntries(
            Map.entry('а', 'a'), // Cyrillic a
            Map.entry('е', 'e'), // Cyrillic e
            Map.entry('ѕ', 's'), // Cyrillic dze
            Map.entry('о', 'o'), // Cyrillic o
            Map.entry('р', 'p'), // Cyrillic er
            Map.entry('і', 'i')  // Cyrillic i
    );

    private static java.util.Map<Character, Character> CHAR_MAP =
            java.util.Map.of('0', 'o', '1', 'i', '2', 'z', '3', 'e', '4', 'a',
                              '5', 's', '6', 'g', '7', 't', '8', 'b', '9', 'g');

    private static final englishStemmer ENGLISH = new englishStemmer();
    private static final turkishStemmer TURKISH = new turkishStemmer();
    private static String LANGUAGE = "en";

    public static void setCharacterMap(java.util.Map<Character, Character> map) {
        if (map != null && !map.isEmpty()) {
            CHAR_MAP = java.util.Map.copyOf(map);
        }
    }

    /** Set the stemming language, either "en" or "tr". */
    public static void setLanguage(String lang) {
        LANGUAGE = "tr".equalsIgnoreCase(lang) ? "tr" : "en";
    }

    /**
     * Stem a single word using the configured language.
     */
    public static String stem(String word) {
        if (word == null || word.isEmpty()) return "";
        if ("tr".equals(LANGUAGE)) {
            TURKISH.setCurrent(word);
            TURKISH.stem();
            return TURKISH.getCurrent();
        }
        ENGLISH.setCurrent(word);
        ENGLISH.stem();
        return ENGLISH.getCurrent();
    }

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
        String noZeroWidth = ZERO_WIDTH.matcher(withoutDiacritics).replaceAll("");
        String withSpaces = PUNCT.matcher(noZeroWidth).replaceAll(" ");
        String base = WHITESPACE.matcher(withSpaces).replaceAll(" ").trim();

        StringBuilder replaced = new StringBuilder(base.length());
        for (char c : base.toCharArray()) {
            replaced.append(CONFUSABLE_MAP.getOrDefault(c, c));
        }
        String normalized = replaced.toString();
        if (!canonical) return normalized;

        StringBuilder mapped = new StringBuilder();
        for (char c : normalized.toCharArray()) {
            mapped.append(CHAR_MAP.getOrDefault(c, c));
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

    private static int levenshtein(String s1, String s2) {
        int m = s1.length();
        int n = s2.length();
        int[] prev = new int[n + 1];
        int[] curr = new int[n + 1];
        for (int j = 0; j <= n; j++) {
            prev[j] = j;
        }
        for (int i = 1; i <= m; i++) {
            curr[0] = i;
            char c1 = s1.charAt(i - 1);
            for (int j = 1; j <= n; j++) {
                int cost = c1 == s2.charAt(j - 1) ? 0 : 1;
                curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] tmp = prev;
            prev = curr;
            curr = tmp;
        }
        return prev[n];
    }

    public static boolean containsBlockedWord(String message, List<String> blockedWords) {
        return containsBlockedWord(message, blockedWords, 0, false, false);
    }

    public static boolean containsBlockedWord(String message, List<String> blockedWords, int maxDistance) {
        return containsBlockedWord(message, blockedWords, maxDistance, false, false);
    }

    public static boolean containsBlockedWord(String message, List<String> blockedWords, int maxDistance, boolean useStemming) {
        return containsBlockedWord(message, blockedWords, maxDistance, useStemming, useStemming && "tr".equals(LANGUAGE));
    }

    public static boolean containsBlockedWord(String message, List<String> blockedWords, int maxDistance, boolean useStemming, boolean useZemberek) {
        if (message == null) return false;
        Set<String> normalized = new HashSet<>();
        List<Pattern> patterns = new java.util.ArrayList<>();
        for (String w : blockedWords) {
            if (w.startsWith("/") && w.endsWith("/") && w.length() > 1) {
                patterns.add(Pattern.compile(w.substring(1, w.length() - 1)));
            } else {
                normalized.add(canonicalize(w));
            }
        }
        return containsBlockedWord(message, normalized, patterns, true, maxDistance, useStemming, useZemberek);
    }

    /**
     * Variant of {@link #containsBlockedWord(String, List)} where the block list
     * is already normalized. This avoids normalizing each word repeatedly.
     */
    public static boolean containsBlockedWord(String message, Set<String> normalizedWords, boolean wordsNormalized) {
        return containsBlockedWord(message, normalizedWords, java.util.Collections.emptyList(), wordsNormalized, 0, false);
    }

    public static boolean containsBlockedWord(String message, Set<String> normalizedWords, boolean wordsNormalized, int maxDistance) {
        return containsBlockedWord(message, normalizedWords, java.util.Collections.emptyList(), wordsNormalized, maxDistance, false);
    }

    public static boolean containsBlockedWord(String message, Set<String> normalizedWords, List<Pattern> regexPatterns, boolean wordsNormalized) {
        return containsBlockedWord(message, normalizedWords, regexPatterns, wordsNormalized, 0, false);
    }

    public static boolean containsBlockedWord(String message, Set<String> normalizedWords, List<Pattern> regexPatterns, boolean wordsNormalized, int maxDistance) {
        return containsBlockedWord(message, normalizedWords, regexPatterns, wordsNormalized, maxDistance, false, false);
    }

    public static boolean containsBlockedWord(String message, Set<String> normalizedWords, List<Pattern> regexPatterns, boolean wordsNormalized, int maxDistance, boolean useStemming) {
        return containsBlockedWord(message, normalizedWords, regexPatterns, wordsNormalized, maxDistance, useStemming, useStemming && "tr".equals(LANGUAGE));
    }

    public static boolean containsBlockedWord(String message, Set<String> normalizedWords, List<Pattern> regexPatterns, boolean wordsNormalized, int maxDistance, boolean useStemming, boolean useZemberek) {
        if (!wordsNormalized) {
            // Normalize words and patterns
            Set<String> normalized = new HashSet<>();
            List<Pattern> patterns = new java.util.ArrayList<>(regexPatterns);
            for (String w : normalizedWords) {
                if (w.startsWith("/") && w.endsWith("/") && w.length() > 1) {
                    patterns.add(Pattern.compile(w.substring(1, w.length() - 1)));
                } else {
                    normalized.add(canonicalize(w));
                }
            }
            return containsBlockedWord(message, normalized, patterns, true, maxDistance, useStemming, useZemberek);
        }
        if (message == null) return false;
        String normalizedMessage = canonicalize(message);
        for (Pattern p : regexPatterns) {
            if (p.matcher(normalizedMessage).find()) {
                return true;
            }
        }
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

        Set<String> stemmedWords = null;
        if (useStemming) {
            stemmedWords = new HashSet<>();
            for (String w : normalizedWords) {
                stemmedWords.add(stem(w));
            }
        }
        Set<String> lemmaWords = null;
        if (useZemberek && "tr".equals(LANGUAGE)) {
            lemmaWords = new HashSet<>();
            for (String w : normalizedWords) {
                lemmaWords.add(ZemberekStemmer.lemma(w));
            }
        }

        for (String token : words) {
            if (normalizedWords.stream().anyMatch(token::contains)) {
                return true;
            }
            if (useZemberek && lemmaWords != null) {
                String lt = ZemberekStemmer.lemma(token);
                if (lemmaWords.contains(lt)) {
                    return true;
                }
            }
            if (useStemming) {
                String ts = stem(token);
                if (stemmedWords.contains(ts)) {
                    return true;
                }
            }
        }
        if (maxDistance > 0) {
            for (String token : words) {
                for (String w : normalizedWords) {
                    if (Math.abs(token.length() - w.length()) > maxDistance) continue;
                    if (levenshtein(token, w) <= maxDistance) return true;
                }
                if (useZemberek && lemmaWords != null) {
                    String lt = ZemberekStemmer.lemma(token);
                    for (String lw : lemmaWords) {
                        if (Math.abs(lt.length() - lw.length()) > maxDistance) continue;
                        if (levenshtein(lt, lw) <= maxDistance) return true;
                    }
                }
                if (useStemming) {
                    String ts = stem(token);
                    for (String sw : stemmedWords) {
                        if (Math.abs(ts.length() - sw.length()) > maxDistance) continue;
                        if (levenshtein(ts, sw) <= maxDistance) return true;
                    }
                }
            }
        }
        return false;
    }

}
