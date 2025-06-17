package me.ogulcan.chatmod.storage;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.MockPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class LogStoreTest {
    private MockPlugin plugin;
    private File tempDir;

    @BeforeEach
    public void setUp() throws Exception {
        MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        tempDir = Files.createTempDirectory("logs").toFile();
    }

    @AfterEach
    public void tearDown() {
        MockBukkit.unmock();
        if (tempDir != null) {
            for (File f : tempDir.listFiles()) {
                f.delete();
            }
            tempDir.delete();
        }
    }

    @Test
    public void testDeferredSave() throws Exception {
        File file = new File(tempDir, "logs.json");
        LogStore store = new LogStore(plugin, file);
        store.add(UUID.randomUUID(), "Bob", "hello");
        assertFalse(file.exists() && file.length() > 0);
        MockBukkit.getMock().getScheduler().performTicks(80L);
        MockBukkit.getMock().getScheduler().waitAsyncTasksFinished();
        assertFalse(file.exists() && file.length() > 0);
        store.close();
        assertTrue(file.exists() && file.length() > 0);
    }
}
