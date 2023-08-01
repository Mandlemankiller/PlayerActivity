package cz.jeme.programu.playeractivity;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.sql.SQLException;
import java.text.ParseException;

public class EventListener implements Listener {
    private final Database database;

    public EventListener(Database database) {
        this.database = database;
    }

    @EventHandler
    private void onPlayerJoin(PlayerJoinEvent event) throws SQLException {
        database.createSession(event.getPlayer().getUniqueId());
    }

    @EventHandler
    private void onPlayerQuit(PlayerQuitEvent event) throws SQLException, ParseException {
        database.closeSessions(database.getOpenSessions(event.getPlayer().getUniqueId()));
    }
}
