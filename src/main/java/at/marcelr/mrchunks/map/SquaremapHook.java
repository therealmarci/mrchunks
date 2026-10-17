package at.marcelr.mrchunks.map;

import at.marcelr.mrchunks.Loader;
import at.marcelr.mrchunks.LoaderManager;
import at.marcelr.mrchunks.MRChunks;
import at.marcelr.mrchunks.Settings;
import at.marcelr.mrchunks.Texts;
import org.bukkit.Bukkit;
import org.bukkit.World;
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

    void update() {
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
                .replace("{id}", String.valueOf(loader.id()))
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
