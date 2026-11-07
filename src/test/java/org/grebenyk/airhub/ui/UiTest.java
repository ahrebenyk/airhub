package org.grebenyk.airhub.ui;

import org.grebenyk.airhub.db.Db;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class UiTest {

    @Test
    void databaseErrorsAreExplainedInUkrainian() throws SQLException {
        Db.init("jdbc:h2:mem:" + UUID.randomUUID());
        SQLException e = assertThrows(SQLException.class, () -> Db.update("DELETE FROM airlines WHERE id = 1"));
        assertTrue(Ui.message(e).startsWith("Запис використовується в інших таблицях"));
        e = assertThrows(SQLException.class, () -> Db.update("UPDATE flights SET sched_time = 'abc' WHERE id = 1"));
        assertEquals("Невірний формат дати або часу (РРРР-ММ-ДД ГГ:ХХ)", Ui.message(e));
    }

    @Test
    void serverMessagesAreShownWithoutSqlText() throws SQLException {
        Db.init("jdbc:h2:mem:" + UUID.randomUUID());
        SQLException e = assertThrows(SQLException.class, () -> Db.update("DELETE FROM users WHERE login = 'admin'"));
        String message = Ui.message(e);
        assertTrue(message.contains("останнього адміністратора"), message);
        assertFalse(message.contains("SQL statement"), message);
        assertFalse(message.contains("DELETE"), message);
    }

    @Test
    void unknownErrorKeepsItsMessage() {
        assertEquals("boom", Ui.message(new IllegalStateException("boom")));
        assertEquals("java.lang.IllegalStateException", Ui.message(new IllegalStateException()));
    }

    @Test
    void titleFallsBackWhenAirportNameIsMissing() {
        assertEquals("AirHub — Аеропорт Ніжин", MainFrame.title("Аеропорт Ніжин"));
        assertEquals("AirHub", MainFrame.title(null));
        assertEquals("AirHub", MainFrame.title("  "));
    }
}
