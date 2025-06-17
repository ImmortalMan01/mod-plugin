package me.ogulcan.chatmod.web;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import me.ogulcan.chatmod.Main;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

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

    public UnmuteServer(Main plugin, int port, int threads) throws IOException {
        this.plugin = plugin;
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/unmute", new UnmuteHandler());
        server.createContext("/mute", new MuteHandler());
        server.createContext("/status", new StatusHandler());
        server.createContext("/reload", new ReloadHandler());
        server.createContext("/clearlogs", new ClearLogsHandler());
        server.createContext("/logs", new LogsHandler());
        server.createContext("/command", new CommandHandler());
        // Use a fixed thread pool to handle concurrent requests
        server.setExecutor(Executors.newFixedThreadPool(threads));
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

    private class MuteHandler implements HttpHandler {
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
            Number minutes = data == null ? null : (Number) data.get("minutes");
            if (player == null || minutes == null) {
                byte[] resp = "{\"error\":\"Missing fields\"}".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(400, resp.length);
                exchange.getResponseBody().write(resp);
                return;
            }
            long min = minutes.longValue();
            Bukkit.getScheduler().runTask(plugin, () -> {
                OfflinePlayer off = Bukkit.getOfflinePlayer(player);
                plugin.getStore().mute(off.getUniqueId(), min);
                plugin.scheduleUnmute(off.getUniqueId(), min * 60L * 20L);
            });
            byte[] resp = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, resp.length);
            exchange.getResponseBody().write(resp);
        }
    }

    private class StatusHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String player = null;
            if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                String query = exchange.getRequestURI().getQuery();
                if (query != null && query.startsWith("player=")) {
                    player = query.substring(7);
                }
            } else if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                String body;
                try (InputStream in = exchange.getRequestBody()) {
                    body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                }
                Map<?, ?> data = gson.fromJson(body, Map.class);
                player = data == null ? null : (String) data.get("player");
            } else {
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            if (player == null || player.isBlank()) {
                byte[] resp = "{\"error\":\"Missing player\"}".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(400, resp.length);
                exchange.getResponseBody().write(resp);
                return;
            }
            OfflinePlayer off = Bukkit.getOfflinePlayer(player);
            boolean muted = plugin.getStore().isMuted(off.getUniqueId());
            long remaining = muted ? plugin.getStore().remaining(off.getUniqueId()) / 60000 : 0;
            byte[] resp = gson.toJson(Map.of("muted", muted, "remaining", remaining)).getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, resp.length);
            exchange.getResponseBody().write(resp);
        }
    }

    private class ReloadHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            Bukkit.getScheduler().runTask(plugin, plugin::reloadAll);
            byte[] resp = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, resp.length);
            exchange.getResponseBody().write(resp);
        }
    }

    private class ClearLogsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> plugin.getLogStore().clear());
            byte[] resp = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, resp.length);
            exchange.getResponseBody().write(resp);
        }
    }

    private class LogsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            int count = 5;
            String query = exchange.getRequestURI().getQuery();
            if (query != null && query.startsWith("count=")) {
                try {
                    count = Integer.parseInt(query.substring(6));
                } catch (NumberFormatException ignored) {}
            }
            var logs = plugin.getLogStore().getLogs();
            int from = Math.max(0, logs.size() - count);
            var slice = logs.subList(from, logs.size());
            byte[] resp = gson.toJson(slice).getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, resp.length);
            exchange.getResponseBody().write(resp);
        }
    }

    private class CommandHandler implements HttpHandler {
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
            String cmd = data == null ? null : (String) data.get("command");
            if (cmd == null || cmd.isBlank()) {
                byte[] resp = "{\"error\":\"Missing command\"}".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(400, resp.length);
                exchange.getResponseBody().write(resp);
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd));
            byte[] resp = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, resp.length);
            exchange.getResponseBody().write(resp);
        }
    }
}
