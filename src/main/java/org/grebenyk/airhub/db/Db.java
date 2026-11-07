package org.grebenyk.airhub.db;

import javax.swing.table.DefaultTableModel;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Vector;

/** H2 database access: connection, schema creation, query execution, user action log. */
public class Db {
    private static final String URL = "jdbc:h2:./data/airhub";
    private static final String DB_USER = "sa", DB_PASSWORD = "";
    private static final String ANONYMOUS = "-";
    private static final DateTimeFormatter TO_MINUTES = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter TO_SECONDS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public record User(int id, String login, String role, boolean canEdit, boolean canReports) {
        public boolean isAdmin() {
            return "ADMIN".equals(role);
        }
    }

    private static User user;
    private static Connection connection;

    public static void init() throws SQLException {
        init(URL);
    }

    /** Opens the database at url (tests use an in-memory database); the session starts without a user. */
    public static void init(String url) throws SQLException {
        connection = DriverManager.getConnection(url, DB_USER, DB_PASSWORD);
        user = null;
        update("RUNSCRIPT FROM 'classpath:/schema.sql' CHARSET 'UTF-8'");
        // initial data is loaded only into a new (empty) database; existing data is never overwritten
        if (((Number) scalar("SELECT COUNT(*) FROM users")).intValue() == 0) {
            update("RUNSCRIPT FROM 'classpath:/init.sql' CHARSET 'UTF-8'");
        }
    }

    private static PreparedStatement prepare(String sql, Object... params) throws SQLException {
        PreparedStatement st = connection.prepareStatement(sql);
        // JDBC "?" parameters are numbered from 1
        for (int i = 0; i < params.length; i++) st.setObject(i + 1, params[i]);
        return st;
    }

    public static int update(String sql, Object... params) throws SQLException {
        try (PreparedStatement st = prepare(sql, params)) {
            st.execute();
            return st.getUpdateCount();
        }
    }

    /** First column of the first result row, or null if there are no rows. */
    public static Object scalar(String sql, Object... params) throws SQLException {
        try (PreparedStatement st = prepare(sql, params); ResultSet rs = st.executeQuery()) {
            return rs.next() ? rs.getObject(1) : null;
        }
    }

    /** SELECT result as a read-only JTable model; date-time values are converted to text. */
    public static DefaultTableModel query(String sql, Object... params) throws SQLException {
        try (PreparedStatement st = prepare(sql, params); ResultSet rs = st.executeQuery()) {
            ResultSetMetaData md = rs.getMetaData();
            int n = md.getColumnCount();
            Vector<String> cols = new Vector<>();
            for (int i = 1; i <= n; i++) cols.add(md.getColumnLabel(i));
            Vector<Vector<Object>> rows = new Vector<>();
            while (rs.next()) {
                Vector<Object> row = new Vector<>();
                for (int i = 1; i <= n; i++) {
                    Object value = rs.getObject(i);
                    row.add(value instanceof Timestamp t ? format(t) : value);
                }
                rows.add(row);
            }
            return new DefaultTableModel(rows, cols) {
                @Override
                public boolean isCellEditable(int row, int col) {
                    return false;
                }

                // column type is taken from the first non-null value so numbers sort as numbers, not text
                @Override
                public Class<?> getColumnClass(int col) {
                    for (int r = 0; r < getRowCount(); r++) {
                        if (getValueAt(r, col) != null) return getValueAt(r, col).getClass();
                    }
                    return Object.class;
                }
            };
        }
    }

    // seconds are shown only when non-zero (log); flight times are set to the minute
    private static String format(Timestamp t) {
        LocalDateTime d = t.toLocalDateTime();
        return d.format(d.getSecond() == 0 ? TO_MINUTES : TO_SECONDS);
    }

    /** Signed-in user, or null before a successful login. */
    public static User user() {
        return user;
    }

    /** Checks the password against the stored hash; on success the user becomes the current one, otherwise returns null. */
    public static User login(String login, String password) throws SQLException {
        String sql = "SELECT id, login, role, can_edit, can_reports, password FROM users WHERE login = ?";
        try (PreparedStatement st = prepare(sql, login); ResultSet rs = st.executeQuery()) {
            if (!rs.next() || !Passwords.verify(password, rs.getString(6))) return null;
            user = new User(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getBoolean(4), rs.getBoolean(5));
            return user;
        }
    }

    public static void log(String action, String details) {
        String login = user == null ? ANONYMOUS : user.login();
        try {
            update("INSERT INTO user_log (login, action, details) VALUES (?, ?, ?)", login, action, details);
        } catch (SQLException e) {
            // a failure to write the log must not interrupt the user's work
            e.printStackTrace();
        }
    }

    /** System setting from the settings table (via the server function GET_SETTING). */
    public static String setting(String name) {
        try {
            return (String) scalar("SELECT GET_SETTING(?)", name);
        } catch (SQLException e) {
            return null;
        }
    }
}
