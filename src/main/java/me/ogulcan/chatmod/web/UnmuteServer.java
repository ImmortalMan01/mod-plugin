package me.ogulcan.chatmod.web;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import me.ogulcan.chatmod.Main;
import org.bukkit.Bukkit;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;

public class UnmuteServer {
    private final HttpServer server;
    private final Gson gson = new Gson();
    private final Main plugin;

    public UnmuteServer(Main plugin, int port) throws IOException {
        this.plugin = plugin;
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/unmute", new UnmuteHandler());
        server.setExecutor(Executors.newSingleThreadExecutor());
        server.start();
    }

    public void stop() {
        server.stop(0);
    }

    private class UnmuteHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            String body;
            try (InputStream in = exchange.getRequestBody()) {
                body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            Map<?, ?> data = gson.fromJson(body, Map.class);
            String player = data == null ? null : (String) data.get("player");
            if (player == null || player.isBlank()) {
                byte[] resp = "{\"error\":\"Missing player\"}".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(400, resp.length);
                exchange.getResponseBody().write(resp);
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                UUID uuid = Bukkit.getOfflinePlayer(player).getUniqueId();
                plugin.getStore().unmute(uuid);
                plugin.cancelUnmute(uuid);
            });
            byte[] resp = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, resp.length);
            exchange.getResponseBody().write(resp);
        }
    }
}
