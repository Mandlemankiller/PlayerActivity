package cz.jeme.programu.playeractivity;

import org.bukkit.Bukkit;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.text.ParseException;
import java.util.logging.Level;

public class PlayerActivity extends JavaPlugin {
    private Database database;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        try {
            database = new Database(getDataFolder());
        } catch (ClassNotFoundException | SQLException e) {
            serverLog(Level.SEVERE, "Couldn't establish connection with database!");
            e.printStackTrace();
        }

        PluginManager pluginManager = Bukkit.getPluginManager();
        pluginManager.registerEvents(new EventListener(database), this);
    }

    @Override
    public void onDisable() {
        try {
            serverLog(Level.INFO, "Closing all sessions...");
            database.closeAllSessions();
            serverLog(Level.INFO, "Closing connection with database...");
            database.closeConnection();
        } catch (SQLException | ParseException e) {
            serverLog(Level.SEVERE, "Couldn't close sessions and close connection with database!");
            e.printStackTrace();
        }
    }

    public static void serverLog(Level lvl, String msg) {
        if (msg == null) {
            throw new NullPointerException("Message is null!");
        }
        Bukkit.getServer().getLogger().log(lvl, Messages.strip(Messages.PREFIX) + msg);
    }
}
