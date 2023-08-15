package cz.jeme.programu.playeractivity;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.logging.Level;

public class PlayerActivity extends JavaPlugin {
    public static FileConfiguration config;
    private Database database = null;
    private RecoveryTimestampRunnable recoveryRunnable;

    private boolean configured;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        recoveryRunnable = new RecoveryTimestampRunnable(getDataFolder());

        reload();

        String user = config.getString("mariadb.user");
        String databaseName = config.getString("mariadb.database-name");

        configured = user != null && !user.equals("<user_name>") || databaseName != null && !databaseName.equals("<database_name>");

        if (!configured) {
            serverLog(Level.WARNING, "The plugin config has not yet been set!");
            serverLog(Level.WARNING, "Please configure the plugin and restart your server");
            serverLog(Level.WARNING, "The plugin will now be disabled");
            return;
        }

        database = new Database(recoveryRunnable);

        recoveryRunnable.start();

        new PlayerActivityCommand(this);

        PluginManager pluginManager = Bukkit.getPluginManager();
        pluginManager.registerEvents(new EventListener(database), this);
    }

    public void reload() {
        reloadConfig();
        config = getConfig();
        if (database != null) database.reload();
    }

    @Override
    public void onDisable() {
        if (!configured) return;
        database.closeAllSessions();
        recoveryRunnable.cancel();
    }

    public static void serverLog(Level level, String message) {
        if (message == null) {
            throw new NullPointerException("Message is null!");
        }
        Bukkit.getLogger().log(level, Messages.strip(Messages.PREFIX) + message);
    }

    public static void serverLog(Level level, String message, Exception exception) {
        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);
        exception.printStackTrace(printWriter);
        String stackTraceStr = stringWriter.toString();
        serverLog(level, message + "\n" + stackTraceStr);
    }

    public static void serverLog(String message, Exception exception) {
        serverLog(Level.SEVERE, message, exception);
    }
}
