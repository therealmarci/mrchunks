package at.marcelr.mrchunks.api;

import at.marcelr.mradmin.api.MapLayer;
import at.marcelr.mradmin.api.MapMarker;
import at.marcelr.mrchunks.Loader;
import at.marcelr.mrchunks.LoaderManager;
import at.marcelr.mrchunks.MRChunks;
import at.marcelr.mrchunks.Settings;
import at.marcelr.mrchunks.Texts;
import at.marcelr.mrchunks.lag.Activity;
import at.marcelr.mrchunks.lag.LagLock;
import at.marcelr.mrchunks.lag.LagMonitor;
import at.marcelr.mrchunks.lag.Metric;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The chunk loaders on the web map, which MRadmin draws (module API 7): every loader's area as a rectangle plus an
 * optional icon (a dummy icon where a dummy sits) - one layer for all, or (map-layer.split-layers) one each for
 * active, switched-off and locked loaders, coloured by status (map-layer.status-colors). Texts in the server
 * language. <b>Only loaded while MRadmin runs</b>, like {@link MRadminModule}.
 */
final class LoaderMap {

    private static final String ICON = "loader";
    private static final String DUMMY_ICON = "dummy";
    private static final int ICON_SIZE = 16;

    private LoaderMap() {
    }

    static List<MapLayer> layers(MRChunks plugin) {
        Settings.MapLayerSettings settings = plugin.settings().mapLayer();
        if (!settings.enabled()) {
            return List.of();
        }
        return plugin.messages().server(() -> build(plugin, settings));
    }

    private static List<MapLayer> build(MRChunks plugin, Settings.MapLayerSettings settings) {
        boolean split = settings.status().splitLayers();
        List<MapMarker> active = new ArrayList<>();
        List<MapMarker> inactive = split ? new ArrayList<>() : active;
        List<MapMarker> locked = split ? new ArrayList<>() : active;
        for (Loader loader : plugin.loaders().all()) {
            // Locked loaders are always shown - they are what admins look for.
            boolean isLocked = plugin.loaders().lagLocked(loader);
            if (!loader.active() && !isLocked && !settings.showInactive()) {
                continue;
            }
            addMarkers(plugin, isLocked ? locked : loader.active() ? active : inactive, loader, settings, isLocked);
        }
        if (!split) {
            return List.of(new MapLayer("loaders", settings.layerName(), settings.defaultHidden(), 0, active));
        }
        return List.of(
                new MapLayer("active", settings.layerName(), settings.defaultHidden(), 0, active),
                new MapLayer("inactive", settings.status().inactiveLayerName(), settings.defaultHidden(), 1, inactive),
                new MapLayer("locked", settings.status().lockedLayerName(), settings.defaultHidden(), 2, locked));
    }

    private static void addMarkers(MRChunks plugin, List<MapMarker> markers, Loader loader,
                                   Settings.MapLayerSettings settings, boolean locked) {
        // The area covers whole chunks; the map uses block coordinates.
        int minX = loader.minChunkX() << 4;
        int minZ = loader.minChunkZ() << 4;
        int maxX = (loader.maxChunkX() + 1) << 4;
        int maxZ = (loader.maxChunkZ() + 1) << 4;
        int color = colorOf(plugin, loader, settings, locked).getRGB() & 0xFFFFFF;
        boolean strong = loader.active() || locked;
        String hover = fill(plugin, settings.hoverTooltip(), loader);
        String click = fill(plugin, settings.clickTooltip(), loader);
        MapMarker.Style style = new MapMarker.Style(color, strong ? 0.9 : 0.6, 2, color,
                strong ? settings.fillOpacity() : settings.fillOpacity() / 2);
        markers.add(new MapMarker.Area("loader_" + loader.id(), loader.worldId(), minX, minZ, maxX, maxZ, style, hover, click));
        // A sitting dummy gets its own icon instead of the loader icon.
        boolean dummy = settings.status().dummyIcon() && plugin.dummies().dummyOf(loader) != null;
        if (dummy || settings.showIcon()) {
            markers.add(new MapMarker.Icon("loader_icon_" + loader.id(), loader.worldId(), loader.x() + 0.5,
                    loader.z() + 0.5, dummy ? DUMMY_ICON : ICON, ICON_SIZE, hover, click));
        }
    }

    static Map<String, BufferedImage> icons() {
        return Map.of(ICON, icon(), DUMMY_ICON, dummyIcon());
    }

    /**
     * Locked (red), switched off (grey), paused, disabled by lag and conspicuous (yellow), otherwise map-layer.color
     * (status = green). Without status-colors locked and conspicuous loaders look like the others.
     */
    private static Color colorOf(MRChunks plugin, Loader loader, Settings.MapLayerSettings settings, boolean locked) {
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
        if (settings.color() != null) {
            return settings.color();
        }
        // Same idea as on the built-in page: a stable hue per owner.
        float hue = (LoaderManager.name(loader.owner()).hashCode() & 0x7fffffff) % 360 / 360f;
        return Color.getHSBColor(hue, 0.65f, 0.80f);
    }

    private static String fill(MRChunks plugin, String template, Loader loader) {
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
                .replace("{state}", html(state(plugin, loader)))
                .replace("{status}", status(plugin, loader))
                .replace("{dummy}", plugin.dummies().dummyOf(loader) != null
                        ? plugin.settings().mapLayer().status().tooltipDummy() : "");
    }

    private static String state(MRChunks plugin, Loader loader) {
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
    private static String status(MRChunks plugin, Loader loader) {
        Settings.StatusSettings status = plugin.settings().mapLayer().status();
        LagLock lock = plugin.loaders().lagLock(loader);
        if (lock != null) {
            return reason(plugin, status.tooltipLocked(), lock.metric(), lock.value(), lock.limit());
        }
        if (loader.active() && plugin.settings().lag().enabled() && plugin.lag().conspicuous(loader)) {
            Activity a = plugin.lag().activity(loader);
            Metric metric = a.worst();
            return reason(plugin, status.tooltipWarn(), metric, a.value(metric),
                    plugin.settings().lag().limitsFor(loader.area()).warn(metric));
        }
        return html(state(plugin, loader));
    }

    private static String reason(MRChunks plugin, String template, Metric metric, double value, double limit) {
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
