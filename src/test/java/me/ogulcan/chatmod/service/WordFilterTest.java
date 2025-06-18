package me.ogulcan.chatmod.service;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class WordFilterTest {
    @Test
    public void testDetectsBlockedWord() {
        List<String> words = List.of("orospu", "piç");
        assertTrue(WordFilter.containsBlockedWord("Sen bir orospu çocuğusun", words));
    }

    @Test
    public void testIgnoresCleanMessage() {
        List<String> words = List.of("orospu", "piç");
        assertFalse(WordFilter.containsBlockedWord("Merhaba nasılsın", words));
    }

    @Test
    public void testNormalizationDetectsVariant() {
        List<String> words = List.of("sikeyim");
        assertTrue(WordFilter.containsBlockedWord("s\u0131key\u0131m", words));
    }

    @Test
    public void testDetectsWordWithSpaces() {
        List<String> words = List.of("sikeyim");
        assertTrue(WordFilter.containsBlockedWord("s i k e y i m", words));
    }

    @Test
    public void testDetectsWordWithSpecialChars() {
        List<String> words = List.of("sikeyim");
        assertTrue(WordFilter.containsBlockedWord("s*i+k(e)y!i%m", words));
    }

    @Test
    public void testCanonicalDetection() {
        List<String> words = List.of("sik");
        assertTrue(WordFilter.containsBlockedWord("siiiik", words));
        assertTrue(WordFilter.containsBlockedWord("s1k", words));
        assertTrue(WordFilter.containsBlockedWord("\u015F\u00EFkk", words));
    }

    @Test
    public void testDigitSubstitutionS2k() {
        List<String> words = List.of("szk");
        assertTrue(WordFilter.containsBlockedWord("s2k", words));
    }

    @Test
    public void testDigitSubstitutionG6k() {
        List<String> words = List.of("ggk");
        assertTrue(WordFilter.containsBlockedWord("g6k", words));
    }

    @Test
    public void testConfusableCharacters() {
        List<String> words = List.of("sik");
        assertTrue(WordFilter.containsBlockedWord("\u0455ik", words));
    }

    @Test
    public void testZeroWidthCharacters() {
        List<String> words = List.of("sik");
        assertTrue(WordFilter.containsBlockedWord("s\u200Bi\u200Bk", words));
    }

    @Test
    public void testNormalizedWordList() {
        Set<String> words = List.of("orospu", "piç").stream()
                .map(WordFilter::canonicalize)
                .collect(java.util.stream.Collectors.toSet());
        assertTrue(WordFilter.containsBlockedWord("Sen bir orospu çocuğusun", words, true));
    }

    @Test
    public void testNormalizedWordListClean() {
        Set<String> words = List.of("orospu", "piç").stream()
                .map(WordFilter::canonicalize)
                .collect(java.util.stream.Collectors.toSet());
        assertFalse(WordFilter.containsBlockedWord("Merhaba nasılsın", words, true));
    }

    @Test
    public void testRegexMatch() {
        List<String> words = List.of("/bad(word)?/");
        assertTrue(WordFilter.containsBlockedWord("such a badword indeed", words));
    }

    @Test
    public void testRegexPrecompiledList() {
        Set<String> words = Set.of("foo");
        List<java.util.regex.Pattern> patterns = List.of(java.util.regex.Pattern.compile("bad(word)?"));
        assertTrue(WordFilter.containsBlockedWord("another badword", words, patterns, true));
    }

    @Test
    public void testLevenshteinMissingChar() {
        List<String> words = List.of("sik");
        assertTrue(WordFilter.containsBlockedWord("s*k", words, 1));
    }

    @Test
    public void testLevenshteinSubstitution() {
        List<String> words = List.of("sik");
        assertTrue(WordFilter.containsBlockedWord("slk", words, 1));
    }

    @Test
    public void testFuzzyThreshold() {
        List<String> words = List.of("sik");
        assertTrue(WordFilter.containsBlockedWord("skik", words, 0, false, false, 60));
    }

    @Test
    public void testFuzzyThresholdSingleCharIgnored() {
        List<String> words = List.of("sik");
        assertFalse(WordFilter.containsBlockedWord("s", words, 0, false, false, 60));
    }

    @Test
    public void testStemmingMatches() {
        WordFilter.setLanguage("en");
        Set<String> words = Set.of(WordFilter.canonicalize("run"));
        assertTrue(WordFilter.containsBlockedWord("running", words, java.util.Collections.<java.util.regex.Pattern>emptyList(), true, 0, true, false, 0));
    }

    @Test
    public void testZemberekLemmas() {
        WordFilter.setLanguage("tr");
        Set<String> words = Set.of(WordFilter.canonicalize("sik"));
        assertTrue(WordFilter.containsBlockedWord("sikleri", words, java.util.Collections.<java.util.regex.Pattern>emptyList(), true, 0, false, true, 0));
    }

}
