package me.ogulcan.chatmod.storage;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class LogStoreTest {
    @Test
    public void testSaveLoad() throws Exception {
        File tmp = File.createTempFile("logs", ".json");
        tmp.deleteOnExit();
        LogStore store = new LogStore(tmp, 10);
        store.addEntry(new LogEntry(UUID.randomUUID(), "Steve", "hello", 1));
        store.addEntry(new LogEntry(UUID.randomUUID(), "Alex", "hi", 2));

        store = new LogStore(tmp, 10);
        List<LogEntry> list = store.getEntries();
        assertEquals(2, list.size());
        assertEquals("Steve", list.get(0).name);
        assertEquals("hello", list.get(0).message);
    }

    @Test
    public void testBounded() throws Exception {
        File tmp = File.createTempFile("logs", ".json");
        tmp.deleteOnExit();
        LogStore store = new LogStore(tmp, 3);
        for (int i = 0; i < 5; i++) {
            store.addEntry(new LogEntry(UUID.randomUUID(), "p"+i, "m"+i, i));
        }
        List<LogEntry> list = store.getEntries();
        assertEquals(3, list.size());
        assertEquals("p2", list.get(0).name);
        assertEquals("p4", list.get(2).name);
    }
}
