package cz.jeme.programu.playeractivity;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class EventListener implements Listener {

    private final Database database;

    public EventListener(Database database) {
        this.database = database;
    }

    @EventHandler
    private void onPlayerLogin(PlayerLoginEvent event) {
        Player player = event.getPlayer();
        database.updateName(player);
        database.createSession(player);
    }

    @EventHandler
    private void onPlayerQuit(PlayerQuitEvent event) {
        database.closeSessions(database.getOpenSessions(event.getPlayer().getUniqueId()));
    }
}
