package at.marcelr.mrchunks.web;

import at.marcelr.mrchunks.Loader;
import at.marcelr.mrchunks.LoaderManager;
import at.marcelr.mrchunks.MRChunks;
import at.marcelr.mrchunks.Settings;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitTask;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;

/**
 * Built-in web page with all chunk loaders: {@code GET /} serves the page, {@code GET /api/loaders} the data.
 * <p>
 * The HTTP threads never touch the Bukkit API: the main thread builds a JSON snapshot every
 * {@code web.refresh-seconds} and the handlers only hand out that string.
 */
public final class WebMap {

    private final MRChunks plugin;
    private HttpServer server;
    private ExecutorService executor;
    private BukkitTask snapshotTask;
    private volatile String snapshot = "{}";
    private volatile String page = "";
    private volatile String error;

    public WebMap(MRChunks plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        Settings.WebSettings web = plugin.settings().web();
        error = null;
        if (!web.enabled()) {
            return;
        }
        page = loadPage(web.title());
        try {
            server = HttpServer.create(new InetSocketAddress(web.bind(), web.port()), 0);
        } catch (IOException | IllegalArgumentException e) {
            error = "Port " + web.port() + " auf " + web.bind() + " nicht verfügbar (" + e.getMessage() + ")";
            plugin.getLogger().warning("Webkarte: " + error);
            server = null;
            return;
        }
        executor = Executors.newFixedThreadPool(2, r -> {
            Thread thread = new Thread(r, "MRchunks-Web");
            thread.setDaemon(true);
            return thread;
        });
        server.setExecutor(executor);
        server.createContext("/", this::handle);
        server.start();

        snapshotTask = Bukkit.getScheduler().runTaskTimer(plugin, this::refresh, 1L, web.refreshSeconds() * 20L);
        plugin.getLogger().info("Webkarte läuft auf http://" + displayHost(web) + ":" + web.port()
                + (web.accessKey().isEmpty() ? " (ohne Zugriffsschlüssel)" : " (mit Zugriffsschlüssel)"));
    }

    public void stop() {
        if (snapshotTask != null) {
            snapshotTask.cancel();
            snapshotTask = null;
        }
        if (server != null) {
            server.stop(0);
            server = null;
        }
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }

    public boolean running() {
        return server != null;
    }

    /** Why the map is not running although enabled, or null. */
    public String error() {
        return error;
    }

    /** Link for admins, including the access key. */
    public String url() {
        Settings.WebSettings web = plugin.settings().web();
        String base = "http://" + displayHost(web) + ":" + web.port() + "/";
        return web.accessKey().isEmpty() ? base : base + "?key=" + web.accessKey();
    }

    private static String displayHost(Settings.WebSettings web) {
        if (!web.bind().equals("0.0.0.0")) {
            return web.bind();
        }
        String ip = Bukkit.getIp();
        return ip == null || ip.isBlank() ? "<server-ip>" : ip;
    }

    // ------------------------------------------------------------ snapshot

    /** Main thread: builds the JSON the page shows. */
    private void refresh() {
        Settings settings = plugin.settings();
        Settings.WebSettings web = settings.web();
        JsonObject root = new JsonObject();
        root.addProperty("title", web.title());
        root.addProperty("generated", System.currentTimeMillis());
        root.addProperty("radius", settings.loaders().radius());
        root.addProperty("refreshSeconds", web.refreshSeconds());
        root.addProperty("showOwners", web.showOwners());
        root.addProperty("showCoordinates", web.showCoordinates());

        Map<String, JsonArray> perWorld = new LinkedHashMap<>();
        Map<String, String> environments = new LinkedHashMap<>();
        for (World world : Bukkit.getWorlds()) {
            perWorld.put(world.getName(), new JsonArray());
            environments.put(world.getName(), world.getEnvironment().name());
        }
        for (Loader loader : plugin.loaders().all()) {
            JsonObject entry = new JsonObject();
            entry.addProperty("id", loader.id());
            entry.addProperty("cx", loader.chunkX());
            entry.addProperty("cz", loader.chunkZ());
            entry.addProperty("active", loader.active());
            if (web.showOwners()) {
                entry.addProperty("owner", LoaderManager.name(loader.owner()));
            }
            if (web.showCoordinates()) {
                entry.addProperty("x", loader.x());
                entry.addProperty("y", loader.y());
                entry.addProperty("z", loader.z());
            }
            perWorld.computeIfAbsent(loader.worldName(), k -> new JsonArray()).add(entry);
        }
        JsonArray worlds = new JsonArray();
        perWorld.forEach((name, loaders) -> {
            JsonObject world = new JsonObject();
            world.addProperty("name", name);
            world.addProperty("environment", environments.getOrDefault(name, "NORMAL"));
            world.add("loaders", loaders);
            worlds.add(world);
        });
        root.add("worlds", worlds);
        snapshot = root.toString();
    }

    // -------------------------------------------------------------- http

    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            if (!"GET".equals(exchange.getRequestMethod())) {
                send(exchange, 405, "text/plain", "Method Not Allowed");
                return;
            }
            String path = exchange.getRequestURI().getPath();
            if (!authorized(exchange)) {
                send(exchange, 401, "text/html", "<!doctype html><meta charset=utf-8><title>MRchunks</title>"
                        + "<p style=\"font-family:sans-serif\">Zugriffsschlüssel fehlt oder ist falsch. "
                        + "Den vollständigen Link bekommst du per <code>/mrchunks web</code>.</p>");
                return;
            }
            switch (path) {
                case "/", "/index.html" -> send(exchange, 200, "text/html", page);
                case "/api/loaders" -> send(exchange, 200, "application/json", snapshot);
                default -> send(exchange, 404, "text/plain", "Not Found");
            }
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.FINE, "Webkarte: Fehler bei einer Anfrage", e);
        }
    }

    private boolean authorized(HttpExchange exchange) {
        String expected = plugin.settings().web().accessKey();
        if (expected.isEmpty()) {
            return true;
        }
        String given = exchange.getRequestHeaders().getFirst("X-Access-Key");
        if (given == null) {
            given = queryParam(exchange.getRequestURI().getRawQuery(), "key");
        }
        return given != null && MessageDigest.isEqual(
                given.getBytes(StandardCharsets.UTF_8), expected.getBytes(StandardCharsets.UTF_8));
    }

    private static String queryParam(String query, String name) {
        if (query == null) {
            return null;
        }
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0 && pair.substring(0, eq).equals(name)) {
                return URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    private static void send(HttpExchange exchange, int status, String type, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", type + "; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.getResponseHeaders().set("Referrer-Policy", "no-referrer");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private String loadPage(String title) {
        try (InputStream in = plugin.getResource("web/index.html")) {
            if (in == null) {
                return "<!doctype html><title>MRchunks</title><p>web/index.html fehlt im Plugin.</p>";
            }
            String html = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return html.replace("{{TITLE}}", escapeHtml(title));
        } catch (IOException e) {
            return "<!doctype html><title>MRchunks</title><p>Seite konnte nicht geladen werden.</p>";
        }
    }

    private static String escapeHtml(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
