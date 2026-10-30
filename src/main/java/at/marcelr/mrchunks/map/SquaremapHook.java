package at.marcelr.mrchunks.map;

import at.marcelr.mrchunks.Loader;
import at.marcelr.mrchunks.LoaderManager;
import at.marcelr.mrchunks.MRChunks;
import at.marcelr.mrchunks.Settings;
import at.marcelr.mrchunks.Texts;
import at.marcelr.mrchunks.lag.Activity;
import at.marcelr.mrchunks.lag.LagLock;
import at.marcelr.mrchunks.lag.LagMonitor;
import at.marcelr.mrchunks.lag.Metric;
import at.marcelr.mrchunks.util.MapAddress;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import com.google.gson.JsonObject;
import xyz.jpenilla.squaremap.api.BukkitAdapter;
import xyz.jpenilla.squaremap.api.Key;
import xyz.jpenilla.squaremap.api.MapWorld;
import xyz.jpenilla.squaremap.api.Point;
import xyz.jpenilla.squaremap.api.SimpleLayerProvider;
import xyz.jpenilla.squaremap.api.Squaremap;
import xyz.jpenilla.squaremap.api.SquaremapProvider;
import xyz.jpenilla.squaremap.api.marker.Marker;
import xyz.jpenilla.squaremap.api.marker.MarkerOptions;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Draws every loader's area as a rectangle (plus an optional icon, a dummy icon where a dummy sits) on squaremap -
 * one layer for all, or (squaremap.split-layers) one each for active, switched-off and locked loaders, coloured by
 * status (squaremap.status-colors).
 * Only loaded by {@link MapHooks} when squaremap is installed. Markers are rebuilt from scratch on every
 * update - with a few hundred loaders that is cheap and avoids any drift.
 */
final class SquaremapHook {

    private static final Key LAYER = Key.of("mrchunks_loaders");
    private static final Key LAYER_INACTIVE = Key.of("mrchunks_loaders_inactive");
    private static final Key LAYER_LOCKED = Key.of("mrchunks_loaders_locked");
    private static final Key ICON = Key.of("mrchunks_loader");
    private static final Key DUMMY_ICON = Key.of("mrchunks_dummy");
    private static final int ICON_SIZE = 16;

    private final MRChunks plugin;
    private final Squaremap api;
    /** world -> our layers in that world (re-registered if squaremap was reloaded) */
    private final Map<UUID, Map<Key, SimpleLayerProvider>> layers = new HashMap<>();

    SquaremapHook(MRChunks plugin) {
        this.plugin = plugin;
        this.api = SquaremapProvider.get();
        if (!api.iconRegistry().hasEntry(ICON)) {
            api.iconRegistry().register(ICON, icon());
        }
    }

    // ------------------------------------------------------------ chunk grid

    private static final String GRID_FILE = "mrchunks-grid.js";
    private static final String GRID_MARKER = "<!-- mrchunks-grid -->";

    /**
     * Copies the grid script into squaremap's web folder and includes it in index.html, before squaremap's own
     * module script (the grid script must hook into Leaflet before squaremap creates the map). Idempotent; called
     * on every update because squaremap rewrites its web files on (re)load.
     */
    void installGrid() {
        Settings.GridSettings grid = plugin.settings().squaremap().grid();
        Path web = api.webDir();
        Path index = web.resolve("index.html");
        try {
            if (!grid.enabled() && !plugin.settings().squaremap().hidePlayers()) {
                removeGrid(); // nothing for the script to do
                return;
            }
            String script = gridScript(grid);
            Path target = web.resolve(GRID_FILE);
            if (!Files.exists(target) || !Files.readString(target, StandardCharsets.UTF_8).equals(script)) {
                Files.writeString(target, script, StandardCharsets.UTF_8);
            }
            if (!Files.exists(index)) {
                return;
            }
            String html = Files.readString(index, StandardCharsets.UTF_8);
            // Version parameter: browsers fetch the script again whenever the grid settings change.
            String tag = GRID_MARKER + "<script src=\"" + GRID_FILE + "?v=" + Integer.toHexString(script.hashCode()) + "\"></script>";
            String cleaned = html.replaceAll("(?m)^.*" + GRID_MARKER + ".*\\R?", "");
            int module = cleaned.indexOf("<script type=\"module\"");
            if (module < 0) {
                return; // unknown squaremap layout - leave it alone
            }
            String patched = cleaned.substring(0, module) + tag + "\n      " + cleaned.substring(module);
            if (!patched.equals(html)) {
                Files.writeString(index, patched, StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            plugin.getLogger().warning("squaremap: could not inject the chunk grid: " + e.getMessage());
        }
    }

    private void removeGrid() throws IOException {
        Path web = api.webDir();
        Path index = web.resolve("index.html");
        if (Files.exists(index)) {
            String html = Files.readString(index, StandardCharsets.UTF_8);
            String cleaned = html.replaceAll("(?m)^.*" + GRID_MARKER + ".*\\R?", "");
            if (!cleaned.equals(html)) {
                Files.writeString(index, cleaned, StandardCharsets.UTF_8);
            }
        }
        Files.deleteIfExists(web.resolve(GRID_FILE));
    }

    private String gridScript(Settings.GridSettings grid) throws IOException {
        JsonObject config = new JsonObject();
        config.addProperty("gridEnabled", grid.enabled());
        config.addProperty("defaultVisible", grid.defaultVisible());
        config.addProperty("color", grid.color());
        config.addProperty("opacity", grid.opacity());
        config.addProperty("minChunkPixels", grid.minChunkPixels());
        config.addProperty("regionLines", grid.regionLines());
        config.addProperty("regionColor", grid.regionColor());
        config.addProperty("labels", grid.labels());
        config.addProperty("label", grid.label());
        config.addProperty("hidePlayerList", plugin.settings().squaremap().hidePlayers());
        try (InputStream in = plugin.getResource("web/squaremap-grid.js")) {
            if (in == null) {
                throw new IOException("web/squaremap-grid.js is missing in the plugin");
            }
            // Gson output is valid JavaScript and escapes "<" etc., so config values cannot break out.
            return new String(in.readAllBytes(), StandardCharsets.UTF_8)
                    .replace("__MRCHUNKS_CONFIG__", config.toString());
        }
    }

    // ------------------------------------------------------------- markers

    /** Hides (or shows again) all online players on the map; not persistent, applied again on every join. */
    void hidePlayers(boolean hide) {
        for (org.bukkit.entity.Player player : Bukkit.getOnlinePlayers()) {
            api.playerManager().hidden(player.getUniqueId(), hide, false);
        }
    }

    void hide(UUID player) {
        api.playerManager().hide(player, false);
    }

    void update() {
        installGrid();
        if (!api.iconRegistry().hasEntry(ICON)) {
            api.iconRegistry().register(ICON, icon()); // lost on /squaremap reload
        }
        if (!api.iconRegistry().hasEntry(DUMMY_ICON)) {
            api.iconRegistry().register(DUMMY_ICON, dummyIcon());
        }
        Settings.SquaremapSettings settings = plugin.settings().squaremap();
        Settings.StatusSettings status = settings.status();
        for (World world : Bukkit.getWorlds()) {
            MapWorld mapWorld = api.getWorldIfEnabled(BukkitAdapter.worldIdentifier(world)).orElse(null);
            if (mapWorld == null) {
                continue;
            }
            Map<Key, SimpleLayerProvider> own = layers.computeIfAbsent(world.getUID(), k -> new HashMap<>());
            SimpleLayerProvider active = layer(mapWorld, own, LAYER, settings.layerName(), 0);
            SimpleLayerProvider inactive = active;
            SimpleLayerProvider locked = active;
            if (status.splitLayers()) {
                inactive = layer(mapWorld, own, LAYER_INACTIVE, status.inactiveLayerName(), 1);
                locked = layer(mapWorld, own, LAYER_LOCKED, status.lockedLayerName(), 2);
            } else {
                for (Key key : List.of(LAYER_INACTIVE, LAYER_LOCKED)) {
                    own.remove(key);
                    if (mapWorld.layerRegistry().hasEntry(key)) {
                        mapWorld.layerRegistry().unregister(key);
                    }
                }
            }
            active.clearMarkers();
            inactive.clearMarkers();
            locked.clearMarkers();
            for (Loader loader : plugin.loaders().all()) {
                if (!loader.worldId().equals(world.getUID())) {
                    continue;
                }
                // Locked loaders are always shown - they are what admins look for.
                boolean isLocked = plugin.loaders().lagLocked(loader);
                if (!loader.active() && !isLocked && !settings.showInactive()) {
                    continue;
                }
                addMarkers(isLocked ? locked : loader.active() ? active : inactive, loader, settings);
            }
        }
    }

    /** Our layer {@code key} in this world, registered again if squaremap lost it (e.g. /squaremap reload). */
    private SimpleLayerProvider layer(MapWorld mapWorld, Map<Key, SimpleLayerProvider> own, Key key, String name, int order) {
        SimpleLayerProvider layer = own.get(key);
        if (layer == null || !mapWorld.layerRegistry().hasEntry(key)) {
            layer = SimpleLayerProvider.builder(name)
                    .showControls(true)
                    .defaultHidden(plugin.settings().squaremap().defaultHidden())
                    .layerPriority(5 + order)
                    .zIndex(250 - order)
                    .build();
            if (mapWorld.layerRegistry().hasEntry(key)) {
                mapWorld.layerRegistry().unregister(key);
            }
            mapWorld.layerRegistry().register(key, layer);
            own.put(key, layer);
        }
        return layer;
    }

    private void addMarkers(SimpleLayerProvider layer, Loader loader, Settings.SquaremapSettings settings) {
        // The area covers whole chunks; squaremap uses block coordinates.
        int minX = loader.minChunkX() << 4;
        int minZ = loader.minChunkZ() << 4;
        int maxX = (loader.maxChunkX() + 1) << 4;
        int maxZ = (loader.maxChunkZ() + 1) << 4;
        boolean locked = plugin.loaders().lagLocked(loader);
        Color color = colorOf(loader, settings, locked);
        boolean strong = loader.active() || locked;
        String hover = fill(settings.hoverTooltip(), loader);
        String click = fill(settings.clickTooltip(), loader);
        MarkerOptions options = MarkerOptions.builder()
                .strokeColor(color)
                .strokeWeight(2)
                .strokeOpacity(strong ? 0.9 : 0.6)
                .fillColor(color)
                .fillOpacity(strong ? settings.fillOpacity() : settings.fillOpacity() / 2)
                .hoverTooltip(hover)
                .clickTooltip(click)
                .build();
        layer.addMarker(Key.of("loader_" + loader.id()),
                Marker.rectangle(Point.of(minX, minZ), Point.of(maxX, maxZ)).markerOptions(options));
        // A sitting dummy gets its own icon instead of the loader icon.
        boolean dummy = settings.status().dummyIcon() && plugin.dummies().dummyOf(loader) != null;
        if (dummy || settings.showIcon()) {
            layer.addMarker(Key.of("loader_icon_" + loader.id()),
                    Marker.icon(Point.of(loader.x() + 0.5, loader.z() + 0.5), dummy ? DUMMY_ICON : ICON, ICON_SIZE)
                            .markerOptions(MarkerOptions.builder().hoverTooltip(hover).clickTooltip(click)));
        }
    }

    /** Removes our layers and icons (on disable / before re-applying with new settings). */
    void remove() {
        for (World world : Bukkit.getWorlds()) {
            api.getWorldIfEnabled(BukkitAdapter.worldIdentifier(world)).ifPresent(mapWorld -> {
                for (Key key : List.of(LAYER, LAYER_INACTIVE, LAYER_LOCKED)) {
                    if (mapWorld.layerRegistry().hasEntry(key)) {
                        mapWorld.layerRegistry().unregister(key);
                    }
                }
            });
        }
        layers.clear();
        for (Key key : List.of(ICON, DUMMY_ICON)) {
            if (api.iconRegistry().hasEntry(key)) {
                api.iconRegistry().unregister(key);
            }
        }
        try {
            removeGrid();
        } catch (IOException e) {
            plugin.getLogger().warning("squaremap: could not remove the chunk grid: " + e.getMessage());
        }
    }

    /** Address of the map for players, see {@link MapAddress}. */
    String publicUrl(String publicIp) {
        Settings.SquaremapSettings settings = plugin.settings().squaremap();
        String webAddress = "";
        int port = 8080;
        Path config = api.webDir().getParent().resolve("config.yml");
        if (Files.exists(config)) {
            YamlConfiguration squaremapConfig = YamlConfiguration.loadConfiguration(config.toFile());
            webAddress = squaremapConfig.getString("settings.web-address", "");
            port = squaremapConfig.getInt("settings.internal-webserver.port", 8080);
        }
        return MapAddress.build(settings.address(), settings.reverseProxy(), webAddress, Bukkit.getIp(), publicIp, port);
    }

    /**
     * Locked (red), switched off (grey), paused, disabled by lag and conspicuous (yellow), otherwise squaremap.color (status = green).
     * Without status-colors locked and conspicuous loaders look like the others.
     */
    private Color colorOf(Loader loader, Settings.SquaremapSettings settings, boolean locked) {
        Settings.StatusSettings status = settings.status();
        if (locked && status.colors()) {
            return status.lockedColor();
        }
        if (!loader.active()) {
            return settings.inactiveColor();
        }
        if (plugin.loaders().paused() || plugin.loaders().shed(loader)) {
            return settings.pausedColor();
        }
        if (status.colors() && plugin.settings().lag().enabled() && plugin.lag().conspicuous(loader)) {
            return status.warnColor();
        }
        return ownerColor(loader, settings);
    }

    private Color ownerColor(Loader loader, Settings.SquaremapSettings settings) {
        if (settings.color() != null) {
            return settings.color();
        }
        // Same idea as on the built-in page: a stable hue per owner.
        float hue = (LoaderManager.name(loader.owner()).hashCode() & 0x7fffffff) % 360 / 360f;
        return Color.getHSBColor(hue, 0.65f, 0.80f);
    }

    private String fill(String template, Loader loader) {
        return template
                .replace("{name}", html(Texts.name(plugin, loader)))
                .replace("{number}", String.valueOf(loader.number()))
                .replace("{id}", String.valueOf(loader.number()))
                .replace("{player}", html(LoaderManager.name(loader.owner())))
                .replace("{world}", html(loader.worldName()))
                .replace("{x}", String.valueOf(loader.x()))
                .replace("{y}", String.valueOf(loader.y()))
                .replace("{z}", String.valueOf(loader.z()))
                .replace("{width}", String.valueOf(loader.width()))
                .replace("{length}", String.valueOf(loader.length()))
                .replace("{size}", String.valueOf(loader.width()))
                .replace("{chunks}", String.valueOf(loader.area()))
                .replace("{state}", html(state(loader)))
                .replace("{status}", status(loader))
                .replace("{dummy}", plugin.dummies().dummyOf(loader) != null ? plugin.settings().squaremap().status().tooltipDummy() : "");
    }

    private String state(Loader loader) {
        if (loader.active() && plugin.loaders().shed(loader)) {
            return plugin.messages().plain("state-shed");
        }
        if (loader.active() && plugin.loaders().adminPause() != null) {
            return plugin.messages().plain("state-admin-paused");
        }
        return loader.active() && plugin.loaders().paused()
                ? plugin.messages().plain("state-paused") : Texts.state(plugin, loader.active());
    }

    /** {state}, for locked and conspicuous loaders with the reason (metric, value, limit). HTML-safe. */
    private String status(Loader loader) {
        Settings.StatusSettings status = plugin.settings().squaremap().status();
        LagLock lock = plugin.loaders().lagLock(loader);
        if (lock != null) {
            return reason(status.tooltipLocked(), lock.metric(), lock.value(), lock.limit());
        }
        if (loader.active() && plugin.settings().lag().enabled() && plugin.lag().conspicuous(loader)) {
            Activity a = plugin.lag().activity(loader);
            Metric metric = a.worst();
            return reason(status.tooltipWarn(), metric, a.value(metric),
                    plugin.settings().lag().limitsFor(loader.area()).warn(metric));
        }
        return html(state(loader));
    }

    private String reason(String template, Metric metric, double value, double limit) {
        return template.replace("{metric}", html(plugin.messages().plain("lag-metric-" + metric.key())))
                .replace("{value}", LagMonitor.format(value))
                .replace("{limit}", LagMonitor.format(limit));
    }

    private static String html(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }


    /** A small figure (head, shirt, trousers) for loaders with a dummy, drawn in code like the loader icon. */
    private static BufferedImage dummyIcon() {
        BufferedImage image = new BufferedImage(ICON_SIZE, ICON_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g.setColor(new Color(0x1B1B1B));
        g.fillRect(4, 0, 8, 7);
        g.fillRect(2, 7, 12, 5);
        g.fillRect(4, 12, 8, 4);
        g.setColor(new Color(0xC69C6D));
        g.fillRect(5, 1, 6, 5);
        g.fillRect(3, 8, 2, 3);
        g.fillRect(11, 8, 2, 3);
        g.setColor(new Color(0x00AFAF));
        g.fillRect(5, 7, 6, 5);
        g.setColor(new Color(0x3C3CA0));
        g.fillRect(5, 12, 6, 3);
        g.setColor(new Color(0x2B1E16));
        g.fillRect(5, 1, 6, 2);
        g.dispose();
        return image;
    }
    /** A small emerald-like block icon, drawn in code so no image file is needed. */
    private static BufferedImage icon() {
        BufferedImage image = new BufferedImage(ICON_SIZE, ICON_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g.setColor(new Color(0x0B3D22));
        g.fillRect(0, 0, 16, 16);
        g.setColor(new Color(0x17DD62));
        g.fillRect(1, 1, 14, 14);
        g.setColor(new Color(0x41F384));
        g.fillRect(3, 3, 10, 10);
        g.setColor(new Color(0x0FA14A));
        g.fillRect(5, 5, 6, 6);
        g.setColor(new Color(0xD9FFE8));
        g.fillRect(6, 6, 2, 2);
        g.dispose();
        return image;
    }
}
