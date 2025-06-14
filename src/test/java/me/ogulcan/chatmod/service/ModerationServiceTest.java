package me.ogulcan.chatmod.service;

import com.google.gson.Gson;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;

public class ModerationServiceTest {
    private MockWebServer server;
    private ModerationService service;

    @BeforeEach
    public void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        service = new ModerationService("test", "omni-moderation-latest", 0.5, 60, java.util.logging.Logger.getAnonymousLogger(), false) {
            @Override
            protected String getUrl() { return server.url("/v1/moderations").toString(); }
        };
    }

    @AfterEach
    public void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    public void testTriggered() throws Exception {
        server.enqueue(new MockResponse().setBody(response(true, false, 0.7)));
        ModerationService.Result r = service.moderate("bad").get();
        assertTrue(r.triggered);
    }

    @Test
    public void testNotTriggered() throws Exception {
        server.enqueue(new MockResponse().setBody(response(false, false, 0.1)));
        ModerationService.Result r = service.moderate("ok").get();
        assertFalse(r.triggered);
    }

    @Test
    public void testFlaggedBelowThreshold() throws Exception {
        server.enqueue(new MockResponse().setBody(response(true, false, 0.3)));
        ModerationService.Result r = service.moderate("hmm").get();
        assertFalse(r.triggered);
    }

    @Test
    public void testBlocked() throws Exception {
        server.enqueue(new MockResponse().setBody(blocked()));
        ModerationService.Result r = service.moderate("bad").get();
        assertTrue(r.blocked);
    }

    @Test
    public void testRateLimit() throws Exception {
        service = new ModerationService("test", "omni-moderation-latest", 0.5, 1, java.util.logging.Logger.getAnonymousLogger(), false) {
            @Override
            protected String getUrl() { return server.url("/v1/moderations").toString(); }
        };
        server.enqueue(new MockResponse().setBody(response(true, false, 0.7)));
        server.enqueue(new MockResponse().setBody(response(true, false, 0.7)));
        service.moderate("one").get();
        ModerationService.Result r = service.moderate("two").get();
        assertFalse(r.triggered);
    }

    @Test
    public void testFallbackOnServerError() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(500));
        ModerationService.Result r = service.moderate("hi").get();
        assertFalse(r.triggered);
    }

    @Test
    public void testDisabledWhenNoApiKey() throws Exception {
        ModerationService disabled = new ModerationService("", "omni-moderation-latest", 0.5, 60, java.util.logging.Logger.getAnonymousLogger(), false);
        ModerationService.Result r = disabled.moderate("whatever").get();
        assertFalse(r.triggered);
    }

    private String response(boolean flagged, boolean blocked, double score) {
        return new Gson().toJson(Map.of("results", new Object[]{
                Map.of(
                        "flagged", flagged,
                        "blocked", blocked,
                        "category_scores", Map.of("harassment", score)
                )
        }));
    }

    private String blocked() {
        return new Gson().toJson(Map.of("results", new Object[]{
                Map.of(
                        "flagged", true,
                        "blocked", true,
                        "category_scores", Map.of("harassment", 0.6)
                )
        }));
    }
}
