package at.marcelr.mrchunks;

import at.marcelr.mrchunks.api.MRadminLink;
import at.marcelr.mrchunks.command.ChunkloaderCommand;
import at.marcelr.mrchunks.command.MRChunksCommand;
import at.marcelr.mrchunks.dummy.Dummies;
import at.marcelr.mrchunks.gui.MenuListener;
import at.marcelr.mrchunks.lag.LagMonitor;
import at.marcelr.mrchunks.lag.ServerPause;
import at.marcelr.mrchunks.link.LinkRequests;
import at.marcelr.mrchunks.map.MapHooks;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class MRChunks extends JavaPlugin {

    private FileConfiguration config;
    private Settings settings;
    /** lang-version 2: version display with MC version; 3: loaders by name/number; 4: squaremap.address; 5: merged admin commands; 6: shorter window titles; 7: lag protection (admin usage); 8: /chunkloader is only the menu (invite notice); 9: get/return as two buttons; 11: dummies and linked accounts (admin usage). */
    private final Messages messages = new Messages(15, Map.ofEntries(
            Map.entry(2, Set.of("version", "gui-info-name")),
            Map.entry(3, Set.of("discord.loader-placed", "discord.loader-deleted", "discord.loader-toggled",
                    "discord.loader-restored", "discord.loader-removed-missing", "discord.admin-delete",
                    "map-hover-tooltip", "map-click-tooltip", "loader-not-found", "usage", "placed", "placed-inactive",
                    "toggled", "deleted", "delete-confirm", "owner-notify-toggled", "owner-notify-deleted",
                    "border-shown", "teleported", "admin-usage", "list-entry", "gui-loader-name", "gui-loader-lore",
                    "gui-title-loader")),
            Map.entry(4, Set.of("map-url-unknown")),
            Map.entry(5, Set.of("admin-usage", "gui-invite-lore")),
            Map.entry(6, Set.of("gui-title-loader", "gui-title-mine", "gui-title-player")),
            Map.entry(7, Set.of("admin-usage")),
            Map.entry(8, Set.of("invite-notify")),
            Map.entry(9, Set.of("gui-items-lore", "gui-mine-empty-lore")),
            // The ticket shows the location itself - no second "Location" line in the lock details.
            Map.entry(10, Set.of("lock-details")),
            Map.entry(11, Set.of("admin-usage")),
            // 12: "Website | GitHub" in /mrchunks version.
            Map.entry(12, Set.of("version-links")),
            // 13: chunk budget and loader sizes (width x length instead of size x size, budget instead of active limit).
            Map.entry(13, Set.of("item-lore", "map-click-tooltip", "placed", "placed-inactive", "admin-usage",
                    "gui-loader-lore", "gui-toggle-on-lore", "gui-player-lore", "gui-limit-lore")),
            // 14: how the team unlocks, in the ticket MRadmin opens for a lag lock.
            Map.entry(14, Set.of("lock-details")),
            // 15: web map with size, budget, status reason and dummy in the tooltip.
            Map.entry(15, Set.of("map-click-tooltip"))));
    private final DiscordWebhook discord = new DiscordWebhook(this);
    private final AdminActions adminActions = new AdminActions(this);
    private LoaderManager loaders;
    private LoaderItem loaderItem;
    private BorderDisplay border;
    private UpdateChecker updateChecker;
    private MenuListener menuListener;
    private MapHooks maps;
    private LagMonitor lag;
    private ServerPause pause;
    private MRadminLink mradmin;
    private Dummies dummies;
    private LinkRequests linkRequests;
    private BukkitTask validationTask;

    @Override
    public void onEnable() {
        ConfigUpdater.update(this);
        loadConfiguration();

        loaderItem = new LoaderItem(this);
        maps = new MapHooks(this);
        border = new BorderDisplay(this);
        loaders = new LoaderManager(this);
        loaders.load();
        loaders.applyAll();
        startValidation();
        dummies = new Dummies(this);
        dummies.checkAvailability();
        dummies.start();
        // Dummies log in like players - not while the plugin is still enabling.
        Bukkit.getScheduler().runTask(this, dummies::syncAll);
        linkRequests = new LinkRequests(this);
        lag = new LagMonitor(this);
        pause = new ServerPause(this);
        lag.start();
        pause.start();

        updateChecker = new UpdateChecker(this);
        updateChecker.start();
        menuListener = new MenuListener(this);
        menuListener.start();
        maps.start();

        getServer().getPluginManager().registerEvents(new LoaderListener(this), this);
        getServer().getPluginManager().registerEvents(border, this);
        getServer().getPluginManager().registerEvents(menuListener, this);
        getServer().getPluginManager().registerEvents(dummies, this);
        register("chunkloader", new ChunkloaderCommand(this));
        register("mrchunks", new MRChunksCommand(this));
        // Optional: MRadmin (module API) - dashboard, player menu, server health, tickets, web interface.
        mradmin = new MRadminLink(this);
        getServer().getPluginManager().registerEvents(mradmin, this);
        mradmin.connect();

        getLogger().info(loaders.all().size() + " chunk loader(s) loaded, " + loaders.loadedChunkCount() + " chunks are kept loaded.");
    }

    @Override
    public void onDisable() {
        if (menuListener != null) {
            menuListener.stop();
            menuListener.closeAll();
        }
        if (border != null) {
            border.stopAll();
        }
        if (maps != null) {
            maps.stop();
        }
        if (updateChecker != null) {
            updateChecker.stop();
        }
        if (validationTask != null) {
            validationTask.cancel();
        }
        if (dummies != null) {
            dummies.shutdown();
        }
        if (loaders != null) {
            loaders.releaseAll();
            loaders.save();
        }
        if (lag != null) {
            lag.stop();
        }
        if (pause != null) {
            pause.stop();
        }
        discord.shutdown();
    }

    /** Reloads config.yml; tickets are re-created because radius or load method may have changed. */
    public void reload() {
        ConfigUpdater.update(this);
        loaders.releaseAll();
        loadConfiguration();
        dummies.checkAvailability();
        loaders.applyAll();
        startValidation();
        lag.start();
        pause.start();
        updateChecker.start();
        menuListener.start();
        maps.start();
        // Permissions may have changed (e.g. mrchunks.exempt given via LuckPerms).
        Bukkit.getOnlinePlayers().forEach(adminActions::enforceExempt);
    }

    private void loadConfiguration() {
        config = ConfigUpdater.load(this);
        messages.load(this, config.getString("language", "auto"), config.getString("fallback-language", "en"),
                config.getString("server-language", "en"));
        settings = Settings.from(config, messages, getLogger());
        discord.load(config.getConfigurationSection("discord"));
    }

    /** Periodically makes sure every loader still has its block (restores or deletes, see config). */
    private void startValidation() {
        if (validationTask != null) {
            validationTask.cancel();
        }
        long interval = settings.loaders().checkIntervalSeconds() * 20L;
        validationTask = Bukkit.getScheduler().runTaskTimer(this, () -> reportValidation(loaders.validate()),
                interval, interval);
    }

    /** Logs and reports the result of {@link LoaderManager#validate}. */
    public void reportValidation(Map<Loader, Boolean> result) {
        for (Map.Entry<Loader, Boolean> entry : result.entrySet()) {
            Loader loader = entry.getKey();
            if (entry.getValue()) {
                getLogger().info("Block of chunk loader " + Texts.consoleName(this, loader) + " was missing and has been restored.");
                discord.log("loader-restored", adminActions.discordTags(loader));
            } else {
                getLogger().info("Chunk loader " + Texts.consoleName(this, loader) + " was deleted because its block was missing.");
                discord.log("loader-removed-missing", adminActions.discordTags(loader));
            }
        }
    }

    @Override
    public FileConfiguration getConfig() {
        if (config == null) {
            config = ConfigUpdater.load(this);
        }
        return config;
    }

    @Override
    public void reloadConfig() {
        config = ConfigUpdater.load(this);
    }

    private void register(String name, TabExecutor executor) {
        PluginCommand command = Objects.requireNonNull(getCommand(name), name);
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }

    public Settings settings() {
        return settings;
    }

    public Messages messages() {
        return messages;
    }

    public DiscordWebhook discord() {
        return discord;
    }

    public AdminActions adminActions() {
        return adminActions;
    }

    public LoaderManager loaders() {
        return loaders;
    }

    public LoaderItem loaderItem() {
        return loaderItem;
    }

    public BorderDisplay border() {
        return border;
    }

    public UpdateChecker updateChecker() {
        return updateChecker;
    }

    public LagMonitor lag() {
        return lag;
    }

    public ServerPause pause() {
        return pause;
    }

    public MapHooks maps() {
        return maps;
    }

    /** The optional connection to MRadmin. */
    public MRadminLink mradmin() {
        return mradmin;
    }

    /** Dummy players; null only while the plugin is starting. */
    public Dummies dummies() {
        return dummies;
    }

    public LinkRequests linkRequests() {
        return linkRequests;
    }
}
