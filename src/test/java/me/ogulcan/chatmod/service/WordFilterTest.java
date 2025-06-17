package me.ogulcan.chatmod.service;

import org.junit.jupiter.api.Test;
import java.util.List;

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
    public void testNormalizedWordList() {
        List<String> words = List.of("orospu", "piç").stream()
                .map(WordFilter::normalize)
                .toList();
        assertTrue(WordFilter.containsBlockedWord("Sen bir orospu çocuğusun", words, true));
    }

    @Test
    public void testNormalizedWordListClean() {
        List<String> words = List.of("orospu", "piç").stream()
                .map(WordFilter::normalize)
                .toList();
        assertFalse(WordFilter.containsBlockedWord("Merhaba nasılsın", words, true));
    }

}
