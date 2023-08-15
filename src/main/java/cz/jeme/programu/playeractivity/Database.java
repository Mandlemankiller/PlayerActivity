package cz.jeme.programu.playeractivity;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.sql.*;
import java.util.Date;
import java.util.*;
import java.util.logging.Level;

public final class Database {
    private static final String JDBC_PREFIX = "jdbc:mariadb";
    private static final String JDBC_DRIVER = "org.mariadb.jdbc.Driver";
    private static final String SESSIONS_TABLE_NAME = "sessions";
    private static final String NAMES_TABLE_NAME = "player_names";
    private String sessionsTableNamePrefixed = null;
    private String namesTableNamePrefixed = null;
    private final RecoveryTimestampRunnable recoveryRunnable;

    static {
        try {
            Class.forName(JDBC_DRIVER);
        } catch (ClassNotFoundException e) {
            PlayerActivity.serverLog("Couldn't find driver \"" + JDBC_DRIVER + "\"", e);
        }
    }

    public Database(RecoveryTimestampRunnable recoveryRunnable) {
        this.recoveryRunnable = recoveryRunnable;
        reload();
    }

    public void reload() {
        String tablePrefix = PlayerActivity.config.getString("mariadb.table-prefix");
        sessionsTableNamePrefixed = tablePrefix + SESSIONS_TABLE_NAME;
        namesTableNamePrefixed = tablePrefix + NAMES_TABLE_NAME;

        List<Player> onlinePlayers = new ArrayList<>(Bukkit.getOnlinePlayers());

        List<Session> removedSessions = new ArrayList<>();

        if (!onlinePlayers.isEmpty()) {
            PlayerActivity.serverLog(Level.WARNING, "Performing a plugin reload with players online is not recommended!");
            PlayerActivity.serverLog(Level.WARNING, "Closing last sessions for online players and opening new ones.");
            Date currentDate = new Date();
            for (Player player : onlinePlayers) {
                List<Session> sessions = getOpenSessions(player.getUniqueId());
                Optional<Session> closestSession = sessions.stream()
                        .min(Comparator.comparing(session -> Math.abs(session.startDate.getTime() - currentDate.getTime())));

                if (closestSession.isPresent()) {
                    Session session = closestSession.get();
                    removedSessions.add(session);
                    closeSession(session);
                } else {
                    PlayerActivity.serverLog(Level.SEVERE, "Player had no sessions open, but he was online when reloading the plugin!");
                }
            }
        }

        createTables(tablePrefix);

        List<Session> openSessions = getAllOpenSessions().stream()
                .filter(session -> !removedSessions.contains(session))
                .toList();

        if (!openSessions.isEmpty()) {
            PlayerActivity.serverLog(Level.WARNING, "There are active open sessions! Did the plugin crash recently?");
            PlayerActivity.serverLog(Level.WARNING, "Using recovery timestamp to close these sessions.");
            for (Session session : openSessions) {
                closeSession(session, recoveryRunnable.getRecoveryDate());
            }
        }

        onlinePlayers.forEach(this::createSession);
    }

    private Connection openConnection() throws SQLException {
        String server = PlayerActivity.config.getString("mariadb.server");
        String port = PlayerActivity.config.getString("mariadb.port");
        String databaseName = PlayerActivity.config.getString("mariadb.database-name");
        String user = PlayerActivity.config.getString("mariadb.user");
        String password = PlayerActivity.config.getString("mariadb.password");
        Connection conn;
        DatabaseMetaData meta;
        String driver;
        String jdbcVersion;
        try {
            conn = DriverManager.getConnection(String.format("%s://%s:%s/%s", JDBC_PREFIX, server, port, databaseName), user, password);
            meta = conn.getMetaData();
            driver = meta.getDriverName() + " (" + meta.getDriverVersion() + ")";
            jdbcVersion = "(" + meta.getJDBCMajorVersion() + "." + meta.getJDBCMinorVersion() + ")";
        } catch (SQLException e) {
            PlayerActivity.serverLog("Couldn't open connection with database!", e);
            throw e;
        }

        if (PlayerActivity.config.getBoolean("logging.log-connection")) {
            PlayerActivity.serverLog(Level.INFO, "Opened connection with database (" + user + "@" + server + ":" + port + ")");
        }
        if (PlayerActivity.config.getBoolean("logging.log-driver")) {
            PlayerActivity.serverLog(Level.INFO, "Using " + driver);
        }
        if (PlayerActivity.config.getBoolean("logging.log-jdbc")) {
            PlayerActivity.serverLog(Level.INFO, "Using JDBC " + jdbcVersion);
        }
        return conn;
    }

    private void closeConnection(Connection conn) {
        if (conn == null) return;
        try {
            conn.close();
        } catch (SQLException e) {
            PlayerActivity.serverLog("Couldn't close connection with database!", e);
            return;
        }
        if (PlayerActivity.config.getBoolean("logging.log-connection")) {
            PlayerActivity.serverLog(Level.INFO, "Closed connection with database");
        }
    }

    private void createTables(String tablePrefix) {
        String namesStatementStr = "CREATE TABLE IF NOT EXISTS " + namesTableNamePrefixed + " ("
                + "id INT AUTO_INCREMENT PRIMARY KEY, "
                + "uuid UUID UNIQUE NOT NULL, "
                + "name VARCHAR(20) NOT NULL"
                + ");";
        String sessionsStatementStr = "CREATE TABLE IF NOT EXISTS " + sessionsTableNamePrefixed + " ("
                + "id INT AUTO_INCREMENT PRIMARY KEY, "
                + "player_uuid UUID NOT NULL , "
                + "start_time DATETIME NOT NULL, "
                + "end_time DATETIME, "
                + "play_time INT, "
                + "CONSTRAINT fk_" + tablePrefix + "player_uuid "
                + "FOREIGN KEY (player_uuid) REFERENCES " + namesTableNamePrefixed + " (uuid)"
                + ");";
        PreparedStatement namesStatement;
        PreparedStatement sessionsStatement;
        Connection connection = null;
        try {
            connection = openConnection();

            namesStatement = connection.prepareStatement(namesStatementStr);
            namesStatement.execute();
            namesStatement.close();

            sessionsStatement = connection.prepareStatement(sessionsStatementStr);
            sessionsStatement.execute();
            sessionsStatement.close();
        } catch (SQLException e) {
            PlayerActivity.serverLog("Couldn't create table!", e);
        } finally {
            closeConnection(connection);
        }
    }

    public void createSession(UUID playerUuid, String playerName) {
        List<Session> currentSessions = getOpenSessions(playerUuid);
        if (!currentSessions.isEmpty()) {
            PlayerActivity.serverLog(Level.SEVERE, "Player " + playerName
                    + " (" + playerUuid + ") had " + currentSessions.size()
                    + " open sessions while trying to create a new session! Did the plugin crash recently?");
            closeSessions(currentSessions);
        }
        Session session = new Session(playerUuid);
        String statementStr = "INSERT INTO " + sessionsTableNamePrefixed
                + "(player_uuid, start_time) VALUES(?, ?);";

        PreparedStatement statement;
        Connection connection = null;
        try {
            connection = openConnection();

            statement = connection.prepareStatement(statementStr);
            statement.setString(1, playerUuid.toString());
            statement.setTimestamp(2, new Timestamp(session.startDate.getTime()));

            statement.execute();
            statement.close();
            if (PlayerActivity.config.getBoolean("logging.log-sessions")) {
                PlayerActivity.serverLog(Level.INFO, "Created session for player " + playerName
                        + " (" + playerUuid + ")");
            }
        } catch (SQLException e) {
            PlayerActivity.serverLog("Couldn't create session!", e);
        } finally {
            closeConnection(connection);
        }
    }

    public void createSession(Player player) {
        createSession(player.getUniqueId(), player.getName());
    }

    public void closeSession(Session session, Date endDate) {
        session.close(endDate);
        String statementStr = "UPDATE " + sessionsTableNamePrefixed + " SET "
                + "end_time = ?, play_time = ?"
                + " WHERE player_uuid = ? AND start_time = ?;";
        Connection connection = null;
        try {
            connection = openConnection();

            PreparedStatement statement = connection.prepareStatement(statementStr);
            statement.setTimestamp(1, new Timestamp(session.endDate.getTime()));
            statement.setInt(2, session.playTime);
            statement.setString(3, session.playerUuid.toString());
            statement.setTimestamp(4, new Timestamp(session.startDate.getTime()));

            statement.execute();
            statement.close();
            if (PlayerActivity.config.getBoolean("logging.log-sessions")) {
                Player player = Bukkit.getPlayer(session.playerUuid);
                String name = "<unknown name>";
                if (player != null) {
                    name = player.getName();
                }
                PlayerActivity.serverLog(Level.INFO, "Closed session for player " + name
                        + " (" + session.playerUuid + ") after " + Session.translatePlayTime(session.playTime));
            }
        } catch (SQLException e) {
            PlayerActivity.serverLog("Couldn't close session!", e);
        } finally {
            closeConnection(connection);
        }
    }

    public void closeSession(Session session) {
        closeSession(session, new Date());
    }

    public void closeSessions(List<Session> sessions) {
        for (Session session : sessions) {
            closeSession(session);
        }
    }

    public List<Session> getOpenSessions(UUID playerUuid) {
        String statementStr = "SELECT * FROM " + sessionsTableNamePrefixed
                + " WHERE player_uuid = ? AND end_time IS NULL;";

        PreparedStatement statement;
        Connection connection = null;
        try {
            connection = openConnection();
            statement = connection.prepareStatement(statementStr);
            statement.setString(1, playerUuid.toString());
        } catch (SQLException e) {
            PlayerActivity.serverLog("Couldn't prepare get-sessions statement for uuid \"" + playerUuid.toString() + "\"", e);
            closeConnection(connection);
            return Collections.emptyList();
        }
        return getOpenSessions(statement, connection);
    }

    private List<Session> getOpenSessions(PreparedStatement statement, Connection connection) {
        List<Session> sessions = new ArrayList<>();
        try {
            ResultSet result = statement.executeQuery();
            while (result.next()) {
                sessions.add(new Session(
                        UUID.fromString(result.getString(2)),
                        new Date(result.getTimestamp(3).getTime())
                ));
            }
            statement.close();
        } catch (SQLException e) {
            PlayerActivity.serverLog("Couldn't get open sessions!", e);
        } finally {
            closeConnection(connection);
        }
        return sessions;
    }

    public List<Session> getAllOpenSessions() {
        String statementStr = "SELECT * FROM " + sessionsTableNamePrefixed
                + " WHERE end_time IS NULL;";

        PreparedStatement statement;
        Connection connection = null;
        try {
            connection = openConnection();
            statement = connection.prepareStatement(statementStr);
        } catch (SQLException e) {
            PlayerActivity.serverLog("Couldn't prepare get-all-sessions statement!", e);
            closeConnection(connection);
            return Collections.emptyList();
        }
        return getOpenSessions(statement, connection);
    }

    public void updatePlayerName(Player player) {
        if (createPlayerName(player)) return;
        String name = player.getName();
        UUID uuid = player.getUniqueId();
        String statementStr = "UPDATE " + namesTableNamePrefixed + " SET "
                + "name = ? WHERE uuid = ?;";
        PreparedStatement statement;
        Connection connection = null;
        try {
            connection = openConnection();
            statement = connection.prepareStatement(statementStr);
            statement.setString(1, name);
            statement.setString(2, uuid.toString());
            statement.execute();
        } catch (SQLException e) {
            PlayerActivity.serverLog("Couldn't update name for player " + name + " (" + uuid + ")!", e);
        } finally {
            closeConnection(connection);
        }
    }

    private boolean createPlayerName(Player player) {
        // Return true when the name was created successfully or when it crashes
        // Returns false when the player already has the name in the database
        String name = player.getName();
        UUID uuid = player.getUniqueId();
        String statementStr = "SELECT 1 FROM " + namesTableNamePrefixed
                + " WHERE uuid = ?;";

        PreparedStatement checkStatement;
        Connection connection = null;
        try {
            connection = openConnection();
            checkStatement = connection.prepareStatement(statementStr);
            checkStatement.setString(1, uuid.toString());
            ResultSet result = checkStatement.executeQuery();
            if (!result.next()) {
                String createNameStatementStr = "INSERT INTO " + namesTableNamePrefixed
                        + " (uuid, name) VALUES(?, ?);";
                PreparedStatement createNameStatement = connection.prepareStatement(createNameStatementStr);
                createNameStatement.setString(1, uuid.toString());
                createNameStatement.setString(2, name);
                createNameStatement.execute();
                createNameStatement.close();
                return true;
            }
            return false;
        } catch (SQLException e) {
            PlayerActivity.serverLog("Couldn't create name record for player " + name + " (" + uuid + ")!", e);
            return true;
        } finally {
            closeConnection(connection);
        }
    }

    public void closeAllSessions() {
        closeSessions(getAllOpenSessions());
    }
}
