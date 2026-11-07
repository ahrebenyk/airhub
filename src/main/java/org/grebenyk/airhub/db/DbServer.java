package org.grebenyk.airhub.db;

import org.h2.api.Trigger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;

/**
 * Server side: code executed by the H2 database itself. Registered in schema.sql
 * (CREATE ALIAS, CREATE TRIGGER). H2 passes the Connection parameter automatically; it is omitted in SQL.
 */
public class DbServer {

    /** Function GET_SETTING(name): value of a system setting, or null. */
    public static String getSetting(Connection c, String name) throws SQLException {
        try (PreparedStatement st = c.prepareStatement("SELECT val FROM settings WHERE name = ?")) {
            st.setString(1, name);
            try (ResultSet rs = st.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    /** Procedure CLEAN_LOG(days): deletes log records older than days days; returns the number deleted. */
    public static int cleanLog(Connection c, Integer days) throws SQLException {
        // days < 1 would move the boundary to now or into the future and delete the whole log
        if (days == null || days < 1) throw new SQLException("Кількість днів зберігання журналу має бути цілим числом від 1");
        // DATEADD with a negative number of days gives the moment "days days ago"
        try (PreparedStatement st = c.prepareStatement("DELETE FROM user_log WHERE ts < DATEADD('DAY', ?, CURRENT_TIMESTAMP)")) {
            st.setInt(1, -days);
            return st.executeUpdate();
        }
    }

    /**
     * BEFORE INSERT/UPDATE trigger on flights: converts the flight number to upper case
     * and writes status changes to the log on behalf of SERVER.
     */
    public static class FlightTrigger implements Trigger {
        // row array indexes match the column order of the flights table in schema.sql
        private static final int FLIGHT_NO = 1, STATUS = 7;
        private static final String LOG_STATUS = "INSERT INTO user_log (login, action, details) VALUES ('SERVER', 'СТАТУС', ?)";

        @Override
        public void init(Connection c, String schema, String trigger, String table, boolean before, int type) {
        }

        // oldRow is null on INSERT; in a BEFORE trigger, changes to newRow are written to the table
        @Override
        public void fire(Connection c, Object[] oldRow, Object[] newRow) throws SQLException {
            if (newRow[FLIGHT_NO] != null) newRow[FLIGHT_NO] = newRow[FLIGHT_NO].toString().trim().toUpperCase();
            if (oldRow != null && !Objects.equals(oldRow[STATUS], newRow[STATUS])) {
                try (PreparedStatement st = c.prepareStatement(LOG_STATUS)) {
                    st.setString(1, newRow[FLIGHT_NO] + ": " + oldRow[STATUS] + " -> " + newRow[STATUS]);
                    st.executeUpdate();
                }
            }
        }
    }

    /**
     * BEFORE INSERT/UPDATE/DELETE trigger on users: stores passwords only as hashes
     * and keeps at least one administrator so the system cannot be locked out.
     */
    public static class UserTrigger implements Trigger {
        // row array indexes match the column order of the users table in schema.sql
        private static final int PASSWORD = 2, ROLE = 3;
        private static final String ADMIN = "ADMIN";

        @Override
        public void init(Connection c, String schema, String trigger, String table, boolean before, int type) {
        }

        // newRow is null on DELETE; a password that is already a hash (unchanged on edit) is kept as is
        @Override
        public void fire(Connection c, Object[] oldRow, Object[] newRow) throws SQLException {
            if (newRow != null && newRow[PASSWORD] != null && !Passwords.isHashed(newRow[PASSWORD].toString())) {
                newRow[PASSWORD] = Passwords.hash(newRow[PASSWORD].toString());
            }
            boolean losesAdmin = oldRow != null && ADMIN.equals(oldRow[ROLE]) && (newRow == null || !ADMIN.equals(newRow[ROLE]));
            if (losesAdmin && adminCount(c) <= 1) {
                throw new SQLException("Не можна видалити останнього адміністратора або змінити його роль");
            }
        }

        private static long adminCount(Connection c) throws SQLException {
            try (PreparedStatement st = c.prepareStatement("SELECT COUNT(*) FROM users WHERE role = ?")) {
                st.setString(1, ADMIN);
                try (ResultSet rs = st.executeQuery()) {
                    rs.next();
                    return rs.getLong(1);
                }
            }
        }
    }
}
