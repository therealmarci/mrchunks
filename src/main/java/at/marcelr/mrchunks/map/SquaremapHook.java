package at.marcelr.mrchunks.map;

import at.marcelr.mrchunks.Loader;
import at.marcelr.mrchunks.LoaderManager;
import at.marcelr.mrchunks.MRChunks;
import at.marcelr.mrchunks.Settings;
import at.marcelr.mrchunks.Texts;
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
import java.util.Map;
import java.util.UUID;

/**
 * Draws every loader's area as a rectangle (plus an optional icon) on a "Chunkloader" layer of squaremap.
 * Only loaded by {@link MapHooks} when squaremap is installed. Markers are rebuilt from scratch on every
 * update - with a few hundred loaders that is cheap and avoids any drift.
 */
final class SquaremapHook {

    private static final Key LAYER = Key.of("mrchunks_loaders");
    private static final Key ICON = Key.of("mrchunks_loader");
    private static final int ICON_SIZE = 16;

    private final MRChunks plugin;
    private final Squaremap api;
    /** world -> our layer in that world (re-registered if squaremap was reloaded) */
    private final Map<UUID, SimpleLayerProvider> layers = new HashMap<>();

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
        Settings.SquaremapSettings settings = plugin.settings().squaremap();
        int radius = plugin.settings().loaders().radius();
        for (World world : Bukkit.getWorlds()) {
            MapWorld mapWorld = api.getWorldIfEnabled(BukkitAdapter.worldIdentifier(world)).orElse(null);
            if (mapWorld == null) {
                continue;
            }
            SimpleLayerProvider layer = layers.get(world.getUID());
            if (layer == null || !mapWorld.layerRegistry().hasEntry(LAYER)) {
                layer = SimpleLayerProvider.builder(settings.layerName())
                        .showControls(true)
                        .defaultHidden(settings.defaultHidden())
                        .layerPriority(5)
                        .zIndex(250)
                        .build();
                if (mapWorld.layerRegistry().hasEntry(LAYER)) {
                    mapWorld.layerRegistry().unregister(LAYER);
                }
                mapWorld.layerRegistry().register(LAYER, layer);
                layers.put(world.getUID(), layer);
            }
            layer.clearMarkers();
            for (Loader loader : plugin.loaders().all()) {
                if (!loader.worldId().equals(world.getUID()) || (!loader.active() && !settings.showInactive())) {
                    continue;
                }
                addMarkers(layer, loader, radius, settings);
            }
        }
    }

    private void addMarkers(SimpleLayerProvider layer, Loader loader, int radius, Settings.SquaremapSettings settings) {
        // The area covers whole chunks; squaremap uses block coordinates.
        int minX = (loader.chunkX() - radius) << 4;
        int minZ = (loader.chunkZ() - radius) << 4;
        int maxX = (loader.chunkX() + radius + 1) << 4;
        int maxZ = (loader.chunkZ() + radius + 1) << 4;
        Color color = loader.active() ? colorOf(loader, settings) : settings.inactiveColor();
        MarkerOptions options = MarkerOptions.builder()
                .strokeColor(color)
                .strokeWeight(2)
                .strokeOpacity(loader.active() ? 0.9 : 0.6)
                .fillColor(color)
                .fillOpacity(loader.active() ? settings.fillOpacity() : settings.fillOpacity() / 2)
                .hoverTooltip(fill(settings.hoverTooltip(), loader))
                .clickTooltip(fill(settings.clickTooltip(), loader))
                .build();
        layer.addMarker(Key.of("loader_" + loader.id()),
                Marker.rectangle(Point.of(minX, minZ), Point.of(maxX, maxZ)).markerOptions(options));
        if (settings.showIcon()) {
            layer.addMarker(Key.of("loader_icon_" + loader.id()),
                    Marker.icon(Point.of(loader.x() + 0.5, loader.z() + 0.5), ICON, ICON_SIZE)
                            .markerOptions(MarkerOptions.builder()
                                    .hoverTooltip(fill(settings.hoverTooltip(), loader))
                                    .clickTooltip(fill(settings.clickTooltip(), loader))));
        }
    }

    /** Removes our layers and icon (on disable / before re-applying with new settings). */
    void remove() {
        for (World world : Bukkit.getWorlds()) {
            api.getWorldIfEnabled(BukkitAdapter.worldIdentifier(world)).ifPresent(mapWorld -> {
                if (mapWorld.layerRegistry().hasEntry(LAYER)) {
                    mapWorld.layerRegistry().unregister(LAYER);
                }
            });
        }
        layers.clear();
        if (api.iconRegistry().hasEntry(ICON)) {
            api.iconRegistry().unregister(ICON);
        }
        try {
            removeGrid();
        } catch (IOException e) {
            plugin.getLogger().warning("squaremap: could not remove the chunk grid: " + e.getMessage());
        }
    }

    /** Address of the map: squaremap.public-url, otherwise squaremap's own web-address setting. */
    String publicUrl() {
        String configured = plugin.settings().squaremap().publicUrl();
        if (!configured.isEmpty()) {
            return configured;
        }
        Path config = api.webDir().getParent().resolve("config.yml");
        if (Files.exists(config)) {
            String address = YamlConfiguration.loadConfiguration(config.toFile()).getString("settings.web-address", "");
            if (!address.isBlank()) {
                return address;
            }
        }
        return "";
    }

    private Color colorOf(Loader loader, Settings.SquaremapSettings settings) {
        if (settings.color() != null) {
            return settings.color();
        }
        // Same idea as on the built-in page: a stable hue per owner.
        float hue = (LoaderManager.name(loader.owner()).hashCode() & 0x7fffffff) % 360 / 360f;
        return Color.getHSBColor(hue, 0.65f, 0.80f);
    }

    private String fill(String template, Loader loader) {
        Settings.LoaderSettings loaders = plugin.settings().loaders();
        return template
                .replace("{name}", html(Texts.name(plugin, loader)))
                .replace("{number}", String.valueOf(loader.number()))
                .replace("{id}", String.valueOf(loader.number()))
                .replace("{player}", html(LoaderManager.name(loader.owner())))
                .replace("{world}", html(loader.worldName()))
                .replace("{x}", String.valueOf(loader.x()))
                .replace("{y}", String.valueOf(loader.y()))
                .replace("{z}", String.valueOf(loader.z()))
                .replace("{size}", String.valueOf(loaders.size()))
                .replace("{chunks}", String.valueOf(loaders.chunkCount()))
                .replace("{state}", html(Texts.state(plugin, loader.active())));
    }

    private static String html(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
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
