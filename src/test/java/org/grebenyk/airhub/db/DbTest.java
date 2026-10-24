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
        Db.user = null;
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
        Db.user = Db.login("operator", "1111");
        Db.log("ВХІД", "");
        assertEquals("-", Db.scalar("SELECT login FROM user_log WHERE action = 'НЕВДАЛИЙ ВХІД'"));
        assertEquals("operator", Db.scalar("SELECT login FROM user_log WHERE action = 'ВХІД'"));
    }

    @Test
    void queryReturnsReadOnlyModelWithFormattedTime() throws SQLException {
        DefaultTableModel model = Db.query("SELECT flight_no, sched_time FROM flights WHERE flight_no = ?", "PS101");
        assertEquals(1, model.getRowCount());
        assertEquals("2026-10-24 08:30", model.getValueAt(0, 1));
        assertFalse(model.isCellEditable(0, 0));
    }
}
