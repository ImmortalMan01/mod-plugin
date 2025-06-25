package me.ogulcan.chatmod;

import be.seeseemelk.mockbukkit.MockBukkit;
import me.ogulcan.chatmod.Main;
import me.ogulcan.chatmod.AddWordResult;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class AddBlockedWordTest {
    private Main plugin;

    @BeforeEach
    public void setUp() {
        MockBukkit.mock();
        plugin = MockBukkit.load(Main.class);
    }

    @AfterEach
    public void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    public void testAddBlockedWordPersistsToFile() {
        File file = plugin.getBlockedWordsFile();
        assertNotNull(file, "Blocked words file should not be null");
        assertEquals(AddWordResult.ADDED, plugin.addBlockedWord("foobar"));
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        List<String> words = cfg.getStringList("blocked-words");
        assertTrue(words.contains("foobar"));
    }
}
