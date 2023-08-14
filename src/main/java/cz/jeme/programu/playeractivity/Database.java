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
    private static final String TABLE_NAME = "sessions";
    private String tableNamePrefixed = null;
    public Connection connection = null;
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
        tableNamePrefixed = tablePrefix + TABLE_NAME;

        List<UUID> onlineUuids = Bukkit.getOnlinePlayers().stream()
                .map(Player::getUniqueId)
                .toList();

        List<Session> removedSessions = new ArrayList<>();

        if (!onlineUuids.isEmpty()) {
            PlayerActivity.serverLog(Level.WARNING, "Performing a plugin reload while players online is not recommended!");
            if (connection == null) {
                PlayerActivity.serverLog(Level.WARNING, "Reopening new sessions for all online players!");
            } else {
                PlayerActivity.serverLog(Level.WARNING, "Closing last sessions for online players and opening new ones.");
                Date currentDate = new Date();
                for (UUID uuid : onlineUuids) {
                    List<Session> sessions = getOpenSessions(uuid);
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
        }

        closeConnection(connection);
        connection = openConnection();
        createTable();

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

        for (UUID uuid : onlineUuids) {
            createSession(uuid);
        }
    }

    private Connection openConnection() {
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
            return null;
        }

        if (PlayerActivity.config.getBoolean("logging.log-connection")) {
            PlayerActivity.serverLog(Level.INFO, "Opened connection with database");
        }
        if (PlayerActivity.config.getBoolean("logging.log-login")) {
            PlayerActivity.serverLog(Level.INFO, "Logged in as " + user + "@" + server + ":" + port);
        }
        if (PlayerActivity.config.getBoolean("logging.log-driver")) {
            PlayerActivity.serverLog(Level.INFO, "Using " + driver);
        }
        if (PlayerActivity.config.getBoolean("logging.log-jdbc")) {
            PlayerActivity.serverLog(Level.INFO, "Using JDBC " + jdbcVersion);
        }
        return conn;
    }

    public void closeConnection(Connection conn) {
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

    private void createTable() {
        String statementStr = "CREATE TABLE IF NOT EXISTS " + tableNamePrefixed + " ("
                + "id INT AUTO_INCREMENT PRIMARY KEY, "
                + "uuid UUID NOT NULL, "
                + "start_stamp DATETIME NOT NULL, "
                + "end_stamp DATETIME, "
                + "play_time INT"
                + ");";
        PreparedStatement statement;
        try {
            statement = connection.prepareStatement(statementStr);
            statement.execute();
            statement.close();
        } catch (SQLException e) {
            PlayerActivity.serverLog("Couldn't create table!", e);
        }
    }

    public void createSession(UUID uuid) {
        List<Session> currentSessions = getOpenSessions(uuid);
        if (!currentSessions.isEmpty()) {
            Player player = Bukkit.getPlayer(uuid);
            String name = "<unknown name>";
            if (player != null) name = player.getName();

            PlayerActivity.serverLog(Level.SEVERE, "Player " + name
                    + " (" + uuid + ") had " + currentSessions.size()
                    + " open sessions while trying to create a new session! Did the plugin crash recently?");
            closeSessions(currentSessions);
        }
        Session session = new Session(uuid);
        String statementStr = "INSERT INTO " + tableNamePrefixed
                + "(uuid, start_stamp) VALUES(?, ?);";

        PreparedStatement statement;
        try {
            statement = connection.prepareStatement(statementStr);
            statement.setString(1, uuid.toString());
            statement.setTimestamp(2, new Timestamp(session.startDate.getTime()));

            statement.execute();
            statement.close();
            if (PlayerActivity.config.getBoolean("logging.log-sessions")) {
                Player player = Bukkit.getPlayer(uuid);
                String name = "<unknown name>";
                if (player != null) {
                    name = player.getName();
                }
                PlayerActivity.serverLog(Level.INFO, "Created session for player " + name
                        + " (" + uuid + ")");
            }
        } catch (SQLException e) {
            PlayerActivity.serverLog("Couldn't create session!", e);
        }
    }

    public void closeSession(Session session, Date endDate) {
        session.close(endDate);
        String statementStr = "UPDATE " + tableNamePrefixed + " SET "
                + "end_stamp = ?, play_time = ?"
                + " WHERE uuid = ? AND start_stamp = ?;";

        try {
            PreparedStatement statement = connection.prepareStatement(statementStr);
            statement.setTimestamp(1, new Timestamp(session.endDate.getTime()));
            statement.setInt(2, session.playTime);
            statement.setString(3, session.uuid.toString());
            statement.setTimestamp(4, new Timestamp(session.startDate.getTime()));

            statement.execute();
            statement.close();
            if (PlayerActivity.config.getBoolean("logging.log-sessions")) {
                Player player = Bukkit.getPlayer(session.uuid);
                String name = "<unknown name>";
                if (player != null) {
                    name = player.getName();
                }
                PlayerActivity.serverLog(Level.INFO, "Closed session for player " + name
                        + " (" + session.uuid + ") after " + Session.translatePlayTime(session.playTime));
            }
        } catch (SQLException e) {
            PlayerActivity.serverLog("Couldn't close session!", e);
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

    public List<Session> getOpenSessions(UUID uuid) {
        String statementStr = "SELECT * FROM " + tableNamePrefixed
                + " WHERE uuid = ? AND end_stamp IS NULL;";

        PreparedStatement statement;
        try {
            statement = connection.prepareStatement(statementStr);
            statement.setString(1, uuid.toString());
        } catch (SQLException e) {
            PlayerActivity.serverLog("Couldn't prepare get-sessions statement for uuid \"" + uuid.toString() + "\"", e);
            return Collections.emptyList();
        }
        return getOpenSessions(statement);
    }

    private List<Session> getOpenSessions(PreparedStatement statement) {
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
        }
        return sessions;
    }

    public List<Session> getAllOpenSessions() {
        String statementStr = "SELECT * FROM " + tableNamePrefixed
                + " WHERE end_stamp IS NULL;";

        PreparedStatement statement;
        try {
            statement = connection.prepareStatement(statementStr);
        } catch (SQLException e) {
            PlayerActivity.serverLog("Couldn't prepare get-all-sessions statement!", e);
            return Collections.emptyList();
        }
        return getOpenSessions(statement);
    }

    public void closeAllSessions() {
        closeSessions(getAllOpenSessions());
    }
}
