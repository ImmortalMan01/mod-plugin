package me.ogulcan.chatmod.service;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import okhttp3.*;
import org.bukkit.Bukkit;

import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ModerationService {
    private static final String URL = "https://api.openai.com/v1/moderations";
    private static final String DEFAULT_MODEL = "omni-moderation-latest";
    private final OkHttpClient client = new OkHttpClient();
    private final Gson gson = new Gson();
    private final String apiKey;
    private final double threshold;
    private final int rateLimit;
    private final Logger logger;
    private final String model;
    private final boolean enabled;
    private final boolean debug;
    private Instant window = Instant.now();
    private int count = 0;

    protected String getUrl() {
        return URL;
    }

    public ModerationService(String apiKey, String model, double threshold, int rateLimit, Logger logger, boolean debug) {
        this.apiKey = apiKey;
        this.model = (model == null || model.isBlank()) ? DEFAULT_MODEL : model;
        this.threshold = threshold;
        this.rateLimit = rateLimit;
        this.logger = logger;
        this.debug = debug;
        this.enabled = apiKey != null && !apiKey.isBlank() && !"REPLACE_ME".equals(apiKey);
        if (!enabled) {
            logger.warning("OpenAI API key missing or not set. Moderation requests will be skipped.");
        }
        if (debug) {
            logger.info("Using moderation model: " + this.model);
        }
    }

    private synchronized boolean incrementAndCheckRate() {
        Instant now = Instant.now();
        if (now.isAfter(window.plusSeconds(60))) {
            window = now;
            count = 0;
        }
        if (count >= rateLimit) return false;
        count++;
        return true;
    }

    public CompletableFuture<Result> moderate(String message) {
        CompletableFuture<Result> future = new CompletableFuture<>();
        if (!enabled) {
            future.complete(new Result(false, false, new HashMap<>()));
            return future;
        }
        if (!incrementAndCheckRate()) {
            future.complete(new Result(false, false, new HashMap<>()));
            return future;
        }
        if (debug) {
            logger.info("Moderating message: " + message);
        }
        client.dispatcher().executorService().execute(() -> sendRequest(message, future, 0));
        return future;
    }

    private void sendRequest(String message, CompletableFuture<Result> future, int attempt) {
        RequestBody body = RequestBody.create(gson.toJson(new Payload(message)), MediaType.parse("application/json"));
        Request request = new Request.Builder()
                .url(getUrl())
                .post(body)
                .header("Authorization", "Bearer " + apiKey)
                .build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                logger.log(Level.WARNING, "Moderation request failed", e);
                if (debug) {
                    logger.info("Retrying in debug mode, attempt " + attempt);
                }
                if (attempt < 2) {
                    CompletableFuture.delayedExecutor(1L << attempt, java.util.concurrent.TimeUnit.SECONDS, client.dispatcher().executorService())
                            .execute(() -> sendRequest(message, future, attempt + 1));
                } else {
                    future.complete(new Result(false, false, new HashMap<>()));
                }
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try (ResponseBody rb = response.body()) {
                    if (!response.isSuccessful()) {
                        if ((response.code() == 429 || response.code() >= 500) && attempt < 2) {
                            CompletableFuture.delayedExecutor(1L << attempt, java.util.concurrent.TimeUnit.SECONDS, client.dispatcher().executorService())
                                    .execute(() -> sendRequest(message, future, attempt + 1));
                            return;
                        }
                        logger.warning("OpenAI error: " + response.code());
                        if (debug) {
                            logger.info("Response body: " + (rb != null ? rb.string() : "null"));
                        }
                        future.complete(new Result(false, false, new HashMap<>()));
                        return;
                    }
                    String json = rb.string();
                    if (debug) {
                        logger.info("OpenAI response: " + json);
                    }
                    ModerationResponse mr = gson.fromJson(json, ModerationResponse.class);
                    if (mr.results == null || mr.results.length == 0) {
                        future.complete(new Result(false, false, new HashMap<>()));
                        return;
                    }
                    ModerationResponse.Result r = mr.results[0];
                    Map<String, Double> scores = r.categoryScores;
                    boolean trigger = r.flagged;
                    for (String cat : scores.keySet()) {
                        if (scores.get(cat) >= threshold) {
                            trigger = true;
                        }
                    }
                    if (debug) {
                        logger.info("Trigger: " + trigger + ", blocked: " + r.blocked + ", scores: " + scores);
                    }
                    future.complete(new Result(trigger, r.blocked, scores));
                }
            }
        });
    }

    private class Payload {
        final String model = ModerationService.this.model;
        final String input;
        Payload(String input) { this.input = input; }
    }

    private static class ModerationResponse {
        Result[] results;
        static class Result {
            boolean flagged;
            @SerializedName("blocked")
            boolean blocked;
            @SerializedName("category_scores")
            Map<String, Double> categoryScores = new HashMap<>();
        }
    }

    public static class Result {
        public final boolean triggered;
        public final boolean blocked;
        public final Map<String, Double> scores;
        public Result(boolean triggered, boolean blocked, Map<String, Double> scores) {
            this.triggered = triggered;
            this.blocked = blocked;
            this.scores = scores;
        }
    }
}
