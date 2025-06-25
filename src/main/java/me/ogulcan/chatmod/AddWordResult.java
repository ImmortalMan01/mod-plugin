package me.ogulcan.chatmod;

/**
 * Result of attempting to add a blocked word.
 */
public enum AddWordResult {
    /** Word added successfully. */
    ADDED,
    /** Word already exists in the list. */
    EXISTS,
    /** Failed to save to file. */
    ERROR
}
