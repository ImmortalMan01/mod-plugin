package me.ogulcan.chatmod.service;

import com.google.gson.Gson;
import okhttp3.*;

import java.io.IOException;
import java.util.Map;

/**
 * Simple helper to notify an external Discord bot via HTTP.
 */
public class DiscordNotifier {
    private final String url;
    private final OkHttpClient client;
    private final Gson gson = new Gson();

    public DiscordNotifier(String url, OkHttpClient client) {
        this.url = url == null ? "" : url.trim();
        this.client = client;
    }

    /**
     * Send mute information to the Discord bot.
     * @param player muted player's name
     * @param reason muted message
     * @param remaining remaining minutes
     */
    public void notifyMute(String player, String reason, long remaining, String actor, String type, long timestamp) {
        if (url.isEmpty()) return;
        RequestBody body = RequestBody.create(
                gson.toJson(Map.of(
                        "player", player,
                        "reason", reason,
                        "remaining", remaining,
                        "actor", actor,
                        "type", type,
                        "timestamp", timestamp
                )),
                MediaType.parse("application/json")
        );
        Request request = new Request.Builder()
                .url(url + "/mute")
                .post(body)
                .build();
        client.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) { /* ignore */ }
            @Override public void onResponse(Call call, Response response) throws IOException {
                response.close();
            }
        });
    }
}
