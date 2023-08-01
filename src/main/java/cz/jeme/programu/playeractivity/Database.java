package cz.jeme.programu.playeractivity;

import java.io.File;
import java.sql.*;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Database {
    private final String databasePath;
    private static final String DATABASE_FILE_NAME = "sessions.db";
    private static final String SQLITE_JDBC_PREFIX = "jdbc:sqlite:";
    private static final String TABLE_NAME = "Sessions";
    private final Connection connection;
    public Database(File dataFolder) throws ClassNotFoundException, SQLException {
        if (dataFolder == null) {
            throw new NullPointerException("Data folder is null!");
        }

        // Load sqlite JDBC driver
        Class.forName("org.sqlite.JDBC");

        databasePath = dataFolder + File.separator + DATABASE_FILE_NAME;
        connection = openConnection();
        createTable();
    }

    private Connection openConnection() throws SQLException {
        return DriverManager.getConnection(SQLITE_JDBC_PREFIX + databasePath);
    }

    public void closeConnection() throws SQLException {
        connection.close();
    }

    private void createTable() throws SQLException {
        String statementStr = "CREATE TABLE IF NOT EXISTS " + TABLE_NAME + "("
                + "id INTEGER PRIMARY KEY, "
                + "uuid TEXT NOT NULL, "
                + "start_stamp TEXT NOT NULL, "
                + "end_stamp TEXT"
                + ");";
        PreparedStatement statement = connection.prepareStatement(statementStr);
        statement.execute();
        statement.close();
    }
    public void createSession(UUID uuid) throws SQLException {
        Session session = new Session(uuid);
        String statementStr = "INSERT INTO " + TABLE_NAME
                + "(uuid, start_stamp) VALUES(?, ?);";

        PreparedStatement statement = connection.prepareStatement(statementStr);
        statement.setString(1, uuid.toString());
        statement.setString(2, Session.formatDate(session.startStamp));

        statement.execute();
        statement.close();
    }
    public void closeSession(Session session) throws SQLException {
        session.end();
        String statementStr = "UPDATE " + TABLE_NAME + " SET "
                + "end_stamp = ?"
                + " WHERE uuid = ? AND start_stamp = ?;";

        PreparedStatement statement = connection.prepareStatement(statementStr);
        statement.setString(1, Session.formatDate(session.endStamp));
        statement.setString(2, session.uuid.toString());
        statement.setString(3, Session.formatDate(session.startStamp));

        statement.execute();
        statement.close();
    }
    public void closeSessions(List<Session> sessions) throws SQLException {
        for (Session session : sessions) {
            closeSession(session);
        }
    }
    public List<Session> getOpenSessions(UUID uuid) throws SQLException, ParseException {
        String statementStr = "SELECT * FROM " + TABLE_NAME
                + " WHERE uuid = ? AND end_stamp IS NULL;";

        PreparedStatement statement = connection.prepareStatement(statementStr);
        statement.setString(1, uuid.toString());

        ResultSet result = statement.executeQuery();
        List<Session> sessions = new ArrayList<>();
        while (result.next()) {
            sessions.add(new Session(uuid, result.getString(3)));
        }
        statement.close();
        return sessions;
    }

    public List<Session> getAllOpenSessions() throws SQLException, ParseException {
        String statementStr = "SELECT * FROM " + TABLE_NAME
                + " WHERE end_stamp IS NULL;";

        PreparedStatement statement = connection.prepareStatement(statementStr);
        ResultSet result = statement.executeQuery();
        List<Session> sessions = new ArrayList<>();
        while (result.next()) {
            sessions.add(new Session(UUID.fromString(result.getString(1)), result.getString(2)));
        }
        statement.close();
        return sessions;
    }

    public void closeAllSessions() throws SQLException, ParseException {
        closeSessions(getAllOpenSessions());
    }
}
