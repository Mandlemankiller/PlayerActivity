package cz.jeme.programu.playeractivity;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class EventListener implements Listener {

    private final Database database;

    public EventListener(Database database) {
        this.database = database;
    }

    @EventHandler
    private void onPlayerLogin(PlayerLoginEvent event) {
        database.updatePlayerName(event.getPlayer());
    }

    @EventHandler
    private void onPlayerJoin(PlayerJoinEvent event) {
        database.createSession(event.getPlayer());
    }

    @EventHandler
    private void onPlayerQuit(PlayerQuitEvent event) {
        database.closeSessions(database.getOpenSessions(event.getPlayer().getUniqueId()));
    }
}
