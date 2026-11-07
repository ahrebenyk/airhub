package org.grebenyk.airhub.db;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.table.DefaultTableModel;
import java.sql.SQLException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DbTest {

    @BeforeEach
    void setUp() throws SQLException {
        // each test gets its own in-memory database; the project database in ./data is not touched
        Db.init("jdbc:h2:mem:" + UUID.randomUUID());
    }

    @Test
    void initialDataIsLoaded() throws SQLException {
        assertEquals(3L, Db.scalar("SELECT COUNT(*) FROM users"));
        assertEquals(5L, Db.scalar("SELECT COUNT(*) FROM airlines"));
        assertEquals(8L, Db.scalar("SELECT COUNT(*) FROM flights"));
    }

    @Test
    void initialDataIsNotReloadedOnRestart() throws SQLException {
        String url = "jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
        Db.init(url);
        Db.update("DELETE FROM flights WHERE flight_no = 'PS101'");
        Db.init(url);
        assertEquals(7L, Db.scalar("SELECT COUNT(*) FROM flights"));
        assertEquals("90", Db.setting("log_keep_days"));
    }

    @Test
    void loginChecksPasswordAndRights() throws SQLException {
        Db.User admin = Db.login("admin", "admin");
        assertNotNull(admin);
        assertTrue(admin.isAdmin());
        Db.User guest = Db.login("guest", "guest");
        assertFalse(guest.isAdmin());
        assertFalse(guest.canEdit());
        assertNull(Db.login("admin", "wrong"));
    }

    @Test
    void getSettingReturnsValueOrNull() {
        assertEquals("90", Db.setting("log_keep_days"));
        assertNull(Db.setting("missing"));
    }

    @Test
    void triggerConvertsFlightNumberToUpperCase() throws SQLException {
        Db.update("INSERT INTO flights (flight_no, airline_id, type, city, sched_time, status)"
                + " VALUES (?, 1, 'Виліт', 'Київ', '2026-11-01 10:00', 'За розкладом')", " ps999 ");
        assertEquals("PS999", Db.scalar("SELECT flight_no FROM flights WHERE city = 'Київ'"));
    }

    @Test
    void triggerLogsStatusChange() throws SQLException {
        Db.update("UPDATE flights SET status = 'Скасовано' WHERE flight_no = 'PS101'");
        assertEquals("SERVER", Db.scalar("SELECT login FROM user_log WHERE action = 'СТАТУС'"));
        assertEquals("PS101: Відбув -> Скасовано", Db.scalar("SELECT details FROM user_log WHERE action = 'СТАТУС'"));
    }

    @Test
    void triggerDoesNotLogUpdateWithoutStatusChange() throws SQLException {
        Db.update("UPDATE flights SET gate = 'D1' WHERE flight_no = 'PS101'");
        assertEquals(0L, Db.scalar("SELECT COUNT(*) FROM user_log WHERE action = 'СТАТУС'"));
    }

    @Test
    void checkConstraintRejectsUnknownStatus() {
        assertThrows(SQLException.class, () -> Db.update("UPDATE flights SET status = 'Невідомо' WHERE id = 1"));
    }

    @Test
    void cleanLogDeletesOnlyOldRecords() throws SQLException {
        Db.update("INSERT INTO user_log (ts, login, action, details)"
                + " VALUES (DATEADD('DAY', -100, CURRENT_TIMESTAMP), 'admin', 'ВХІД', 'old')");
        Db.log("ВХІД", "new");
        assertEquals(1, Db.scalar("SELECT CLEAN_LOG(CAST(GET_SETTING('log_keep_days') AS INT))"));
        assertEquals("new", Db.scalar("SELECT details FROM user_log"));
    }

    @Test
    void logWritesCurrentUser() throws SQLException {
        Db.log("НЕВДАЛИЙ ВХІД", "");
        Db.login("operator", "1111");
        Db.log("ВХІД", "");
        assertEquals("-", Db.scalar("SELECT login FROM user_log WHERE action = 'НЕВДАЛИЙ ВХІД'"));
        assertEquals("operator", Db.scalar("SELECT login FROM user_log WHERE action = 'ВХІД'"));
    }

    @Test
    void loginSetsCurrentUserOnlyOnSuccess() throws SQLException {
        assertNull(Db.user());
        Db.login("admin", "wrong");
        assertNull(Db.user());
        Db.login("operator", "1111");
        assertEquals("operator", Db.user().login());
    }

    @Test
    void passwordsAreStoredAsHashes() throws SQLException {
        String stored = (String) Db.scalar("SELECT password FROM users WHERE login = 'admin'");
        assertTrue(stored.startsWith(Passwords.PREFIX));
        assertFalse(stored.contains("admin"));
        assertEquals(0L, Db.scalar("SELECT COUNT(*) FROM users WHERE password NOT LIKE 'pbkdf2$%'"));
    }

    @Test
    void changedPasswordIsHashedAndUnchangedHashIsKept() throws SQLException {
        Db.update("UPDATE users SET password = 'new-pass' WHERE login = 'guest'");
        assertNotNull(Db.login("guest", "new-pass"));
        assertNull(Db.login("guest", "guest"));
        String hash = (String) Db.scalar("SELECT password FROM users WHERE login = 'guest'");
        // editing other fields in the form sends the stored hash back unchanged
        Db.update("UPDATE users SET can_reports = TRUE, password = ? WHERE login = 'guest'", hash);
        assertNotNull(Db.login("guest", "new-pass"));
    }

    @Test
    void plainPasswordsOfOldDatabaseAreMigrated() throws SQLException {
        String url = "jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
        Db.init(url);
        // simulate a database of version 1.0.0: no trigger, plain password
        Db.update("DROP TRIGGER users_trg");
        Db.update("UPDATE users SET password = 'plain' WHERE login = 'operator'");
        Db.init(url);
        assertNotNull(Db.login("operator", "plain"));
        assertEquals(0L, Db.scalar("SELECT COUNT(*) FROM users WHERE password NOT LIKE 'pbkdf2$%'"));
    }

    @Test
    void lastAdminCannotBeDeletedOrDemoted() {
        assertThrows(SQLException.class, () -> Db.update("DELETE FROM users WHERE login = 'admin'"));
        assertThrows(SQLException.class, () -> Db.update("UPDATE users SET role = 'USER' WHERE login = 'admin'"));
    }

    @Test
    void adminCanBeDeletedWhenAnotherAdminExists() throws SQLException {
        Db.update("UPDATE users SET role = 'ADMIN' WHERE login = 'operator'");
        assertEquals(1, Db.update("DELETE FROM users WHERE login = 'admin'"));
        assertThrows(SQLException.class, () -> Db.update("DELETE FROM users WHERE login = 'operator'"));
    }

    @Test
    void cleanLogRejectsNonPositiveDays() {
        Db.log("ВХІД", "new");
        assertThrows(SQLException.class, () -> Db.scalar("SELECT CLEAN_LOG(0)"));
        assertThrows(SQLException.class, () -> Db.scalar("SELECT CLEAN_LOG(-5)"));
        assertThrows(SQLException.class, () -> Db.scalar("SELECT CLEAN_LOG(NULL)"));
    }

    @Test
    void cleanLogRejectsNonNumericSetting() throws SQLException {
        Db.update("UPDATE settings SET val = 'abc' WHERE name = 'log_keep_days'");
        SQLException e = assertThrows(SQLException.class, () -> Db.scalar("SELECT CLEAN_LOG(CAST(GET_SETTING('log_keep_days') AS INT))"));
        assertEquals(22018, e.getErrorCode());
    }

    @Test
    void airlineWithFlightsCannotBeDeleted() throws SQLException {
        SQLException e = assertThrows(SQLException.class, () -> Db.update("DELETE FROM airlines WHERE name = 'МАУ'"));
        assertEquals(23503, e.getErrorCode());
        Db.update("INSERT INTO airlines (name, country) VALUES ('Ryanair', 'Ірландія')");
        assertEquals(1, Db.update("DELETE FROM airlines WHERE name = 'Ryanair'"));
    }

    @Test
    void duplicateLoginAndAirlineAreRejected() {
        SQLException e = assertThrows(SQLException.class,
                () -> Db.update("INSERT INTO users (login, password, role) VALUES ('admin', 'x', 'USER')"));
        assertEquals(23505, e.getErrorCode());
        assertThrows(SQLException.class, () -> Db.update("INSERT INTO airlines (name) VALUES ('LOT')"));
    }

    @Test
    void flightRequiresExistingAirlineAndValidType() {
        String sql = "INSERT INTO flights (flight_no, airline_id, type, city, sched_time, status)"
                + " VALUES ('X1', ?, ?, 'Київ', '2026-11-01 10:00', 'За розкладом')";
        assertEquals(23506, assertThrows(SQLException.class, () -> Db.update(sql, 99, "Виліт")).getErrorCode());
        assertEquals(23513, assertThrows(SQLException.class, () -> Db.update(sql, 1, "Транзит")).getErrorCode());
    }

    @Test
    void invalidFlightTimeIsRejected() {
        SQLException e = assertThrows(SQLException.class, () -> Db.update("UPDATE flights SET sched_time = '24.10.2026' WHERE id = 1"));
        assertEquals(22007, e.getErrorCode());
    }

    @Test
    void queryReturnsReadOnlyModelWithFormattedTime() throws SQLException {
        DefaultTableModel model = Db.query("SELECT flight_no, sched_time FROM flights WHERE flight_no = ?", "PS101");
        assertEquals(1, model.getRowCount());
        assertEquals("2026-10-24 08:30", model.getValueAt(0, 1));
        assertFalse(model.isCellEditable(0, 0));
    }
}
