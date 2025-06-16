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
    private static final String CHAT_URL = "https://api.openai.com/v1/chat/completions";
    private static final String DEFAULT_MODEL = "omni-moderation-latest";
    public static final String DEFAULT_SYSTEM_PROMPT =
            "You are a Minecraft chat moderator. Evaluate one incoming message for inappropriate content: if it contains strong profanity, insults, hate speech, sexual slurs, religious or racial degradation, or threats, reply exactly \u201cvar\u201d; otherwise reply exactly \u201cyok\u201d. Players may hide profanity with symbols, leetspeak (a\u21924, e\u21923, i\u21921, etc.), or by merging words\u2014try to detect these. Ignore mild slang such as \u201clan\u201d, \u201coha\u201d, \u201csalak\u201d, \u201cnoob\u201d, player nicknames, and Minecraft commands. Output must be all lowercase and contain nothing except either \u201cvar\u201d or \u201cyok\u201d.";
    private final OkHttpClient client = new OkHttpClient();
    private final Gson gson = new Gson();
    private final String apiKey;
    private final double threshold;
    private final int rateLimit;
    private final Logger logger;
    private final String model;
    private final boolean chatModel;
    private final boolean reasoningModel;
    private final String reasoningEffort;
    private final boolean enabled;
    private final boolean debug;
    private final String systemPrompt;
    private Instant window = Instant.now();
    private int count = 0;

    protected String getUrl() {
        return URL;
    }

    protected String getChatUrl() {
        return CHAT_URL;
    }

    public ModerationService(String apiKey, String model, double threshold, int rateLimit,
                             Logger logger, boolean debug, String systemPrompt, String reasoningEffort) {
        this.apiKey = apiKey;
        this.model = (model == null || model.isBlank()) ? DEFAULT_MODEL : model;
        this.chatModel = "gpt-4.1-mini".equalsIgnoreCase(this.model) ||
                "gpt-4.1".equalsIgnoreCase(this.model) ||
                "o3".equalsIgnoreCase(this.model) ||
                "o4-mini".equalsIgnoreCase(this.model);
        this.reasoningModel = "o3".equalsIgnoreCase(this.model) ||
                "o4-mini".equalsIgnoreCase(this.model);
        this.reasoningEffort = reasoningEffort == null ? "medium" : reasoningEffort;
        this.systemPrompt = (systemPrompt == null || systemPrompt.isBlank()) ? DEFAULT_SYSTEM_PROMPT : systemPrompt;
        this.threshold = threshold;
        this.rateLimit = rateLimit;
        this.logger = logger;
        this.debug = debug;
        this.enabled = apiKey != null && !apiKey.isBlank() && !"REPLACE_ME".equals(apiKey);
        if (!enabled) {
            logger.warning("OpenAI API key missing or not set. Moderation requests will be skipped.");
        }
        if (debug) {
            if (chatModel) {
                logger.info("Using chat model: " + this.model);
            } else {
                logger.info("Using moderation model: " + this.model);
            }
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
        if (chatModel) {
            client.dispatcher().executorService().execute(() -> sendChatRequest(message, future, 0));
        } else {
            client.dispatcher().executorService().execute(() -> sendRequest(message, future, 0));
        }
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
                if (debug) {
                    logger.log(Level.WARNING, "Moderation request failed", e);
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
                        if (debug) {
                            logger.warning("OpenAI error: " + response.code());
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
                    boolean trigger = r.blocked;
                    for (double score : scores.values()) {
                        if (score >= threshold) {
                            trigger = true;
                            break;
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

    private void sendChatRequest(String message, CompletableFuture<Result> future, int attempt) {
        RequestBody body = RequestBody.create(gson.toJson(new ChatPayload(message)), MediaType.parse("application/json"));
        Request request = new Request.Builder()
                .url(getChatUrl())
                .post(body)
                .header("Authorization", "Bearer " + apiKey)
                .build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                if (debug) {
                    logger.log(Level.WARNING, "Chat request failed", e);
                    logger.info("Retrying in debug mode, attempt " + attempt);
                }
                if (attempt < 2) {
                    CompletableFuture.delayedExecutor(1L << attempt, java.util.concurrent.TimeUnit.SECONDS, client.dispatcher().executorService())
                            .execute(() -> sendChatRequest(message, future, attempt + 1));
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
                                    .execute(() -> sendChatRequest(message, future, attempt + 1));
                            return;
                        }
                        if (debug) {
                            logger.warning("OpenAI error: " + response.code());
                            logger.info("Response body: " + (rb != null ? rb.string() : "null"));
                        }
                        future.complete(new Result(false, false, new HashMap<>()));
                        return;
                    }
                    String json = rb.string();
                    if (debug) {
                        logger.info("OpenAI response: " + json);
                    }
                    ChatResponse cr = gson.fromJson(json, ChatResponse.class);
                    if (cr.choices == null || cr.choices.length == 0 || cr.choices[0].message == null) {
                        future.complete(new Result(false, false, new HashMap<>()));
                        return;
                    }
                    String content = cr.choices[0].message.content == null ? "" : cr.choices[0].message.content.trim().toLowerCase();
                    boolean trigger = content.startsWith("var");
                    future.complete(new Result(trigger, trigger, new HashMap<>()));
                }
            }
        });
    }

    private class Payload {
        final String model = ModerationService.this.model;
        final String input;
        Payload(String input) { this.input = input; }
    }

    private class ChatPayload {
        final String model = ModerationService.this.model;
        final Message[] messages;
        final double temperature = reasoningModel ? 1 : 0;
        final Integer max_tokens;
        @SerializedName("max_completion_tokens")
        final Integer maxCompletionTokens;
        @SerializedName("reasoning_effort")
        final String effort;

        ChatPayload(String input) {
            this.messages = new Message[]{
                    new Message("system", systemPrompt),
                    new Message("user", input)
            };
            if (reasoningModel) {
                this.max_tokens = null;
                // remove completion limit for reasoning models
                this.maxCompletionTokens = null;
                this.effort = reasoningEffort;
            } else {
                // gpt-4.1 and similar models may require more than one token
                // for these short replies
                this.max_tokens = 5;
                this.maxCompletionTokens = null;
                this.effort = null;
            }
        }
    }

    private static class Message {
        final String role;
        final String content;
        Message(String role, String content) {
            this.role = role;
            this.content = content;
        }
    }

    private static class ChatResponse {
        Choice[] choices;
        static class Choice {
            Message message;
        }
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
