package at.marcelr.mrchunks;

import at.marcelr.mrchunks.command.ChunkloaderCommand;
import at.marcelr.mrchunks.command.MRChunksCommand;
import at.marcelr.mrchunks.gui.MenuListener;
import at.marcelr.mrchunks.map.MapHooks;
import at.marcelr.mrchunks.web.WebMap;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.Objects;

public final class MRChunks extends JavaPlugin {

    private FileConfiguration config;
    private Settings settings;
    private final Messages messages = new Messages();
    private final DiscordWebhook discord = new DiscordWebhook(this);
    private final AdminActions adminActions = new AdminActions(this);
    private LoaderManager loaders;
    private LoaderItem loaderItem;
    private BorderDisplay border;
    private UpdateChecker updateChecker;
    private MenuListener menuListener;
    private WebMap webMap;
    private MapHooks maps;
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

        updateChecker = new UpdateChecker(this);
        updateChecker.start();
        menuListener = new MenuListener(this);
        menuListener.start();
        webMap = new WebMap(this);
        webMap.start();
        maps.start();

        getServer().getPluginManager().registerEvents(new LoaderListener(this), this);
        getServer().getPluginManager().registerEvents(menuListener, this);
        register("chunkloader", new ChunkloaderCommand(this));
        register("mrchunks", new MRChunksCommand(this));

        getLogger().info(loaders.all().size() + " Chunkloader geladen, " + loaders.loadedChunkCount() + " Chunks werden geladen gehalten.");
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
        if (webMap != null) {
            webMap.stop();
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
        if (loaders != null) {
            loaders.releaseAll();
            loaders.save();
        }
        discord.shutdown();
    }

    /** Reloads config.yml; tickets are re-created because radius or load method may have changed. */
    public void reload() {
        ConfigUpdater.update(this);
        loaders.releaseAll();
        loadConfiguration();
        loaders.applyAll();
        startValidation();
        updateChecker.start();
        menuListener.start();
        webMap.start();
        maps.start();
    }

    private void loadConfiguration() {
        config = ConfigUpdater.load(this);
        settings = Settings.from(config, getLogger());
        messages.load(config);
        discord.load(config.getConfigurationSection("discord"));
    }

    /** Periodically makes sure every loader still has its block (restores or deletes, see config). */
    private void startValidation() {
        if (validationTask != null) {
            validationTask.cancel();
        }
        long interval = settings.loaders().checkIntervalSeconds() * 20L;
        validationTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Map.Entry<Loader, Boolean> entry : loaders.validate().entrySet()) {
                Loader loader = entry.getKey();
                if (entry.getValue()) {
                    getLogger().info("Block von Chunkloader #" + loader.id() + " fehlte und wurde wiederhergestellt.");
                    discord.log("loader-restored", adminActions.discordTags(loader));
                } else {
                    getLogger().info("Chunkloader #" + loader.id() + " wurde gelöscht, weil sein Block fehlte.");
                    discord.log("loader-removed-missing", adminActions.discordTags(loader));
                }
            }
        }, interval, interval);
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

    public WebMap webMap() {
        return webMap;
    }

    public MapHooks maps() {
        return maps;
    }
}
