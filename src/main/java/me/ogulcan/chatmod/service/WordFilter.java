package me.ogulcan.chatmod.service;

import java.util.List;

public class WordFilter {
    public static boolean containsBlockedWord(String message, List<String> blockedWords) {
        if (message == null) return false;
        String lower = message.toLowerCase();
        for (String w : blockedWords) {
            if (lower.contains(w.toLowerCase())) {
                return true;
            }
        }
        return false;
    }
}
