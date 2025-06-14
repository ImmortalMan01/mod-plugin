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
}
