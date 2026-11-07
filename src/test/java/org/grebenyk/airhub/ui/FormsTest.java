package org.grebenyk.airhub.ui;

import org.grebenyk.airhub.db.Db;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.awt.*;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/** Forms are created without being shown; components are found by their visible text. */
class FormsTest {

    @BeforeEach
    void setUp() throws SQLException {
        // JFrame cannot be created without a display (for example on a CI server)
        assumeFalse(GraphicsEnvironment.isHeadless());
        Db.init("jdbc:h2:mem:" + UUID.randomUUID());
    }

    private static <T extends Component> List<T> find(Container root, Class<T> type) {
        List<T> found = new ArrayList<>();
        for (Component c : root.getComponents()) {
            if (type.isInstance(c)) found.add(type.cast(c));
            if (c instanceof Container child) found.addAll(find(child, type));
        }
        return found;
    }

    private static JButton button(Container root, String text) {
        return find(root, JButton.class).stream().filter(b -> text.equals(b.getText())).findFirst().orElse(null);
    }

    private static List<String> menus(MainFrame frame) {
        JMenuBar bar = frame.getJMenuBar();
        List<String> names = new ArrayList<>();
        for (int i = 0; i < bar.getMenuCount(); i++) names.add(bar.getMenu(i).getText());
        return names;
    }

    private static List<String> items(MainFrame frame, String menu) {
        JMenuBar bar = frame.getJMenuBar();
        for (int i = 0; i < bar.getMenuCount(); i++) {
            if (!bar.getMenu(i).getText().equals(menu)) continue;
            return Arrays.stream(bar.getMenu(i).getMenuComponents())
                    .filter(JMenuItem.class::isInstance).map(c -> ((JMenuItem) c).getText()).toList();
        }
        return List.of();
    }

    private static TableForm flights() {
        return new TableForm("Рейси", "flights", true, "sched_time", Map.of("type", MainFrame.TYPES, "status", MainFrame.STATUSES),
                Map.of("airline_id", "SELECT id, name FROM airlines ORDER BY name"));
    }

    private static JTable grid(Container form) {
        return find(form, JTable.class).getFirst();
    }

    @Test
    void adminSeesAllMenusInStandardOrder() throws SQLException {
        Db.login("admin", "admin");
        MainFrame frame = new MainFrame();
        assertEquals(List.of("Файл", "Таблиці", "Аналіз", "Адміністрування", "Довідка"), menus(frame));
        assertEquals(List.of("Користувачі", "Журнал системи", "Параметри системи", "Очистити старий журнал"),
                items(frame, "Адміністрування"));
        assertEquals("AirHub — Аеропорт Ніжин", frame.getTitle());
        assertTrue(find(frame.getContentPane(), JLabel.class).stream()
                .anyMatch(l -> l.getText().contains("Користувач: admin (ADMIN)")));
        frame.dispose();
    }

    @Test
    void guestDoesNotSeeReportsAnalysisAndAdministration() throws SQLException {
        Db.login("guest", "guest");
        MainFrame frame = new MainFrame();
        assertEquals(List.of("Файл", "Таблиці", "Довідка"), menus(frame));
        assertEquals(List.of("Вихід"), items(frame, "Файл"));
        frame.dispose();
    }

    @Test
    void operatorSeesReportsButNotAdministration() throws SQLException {
        Db.login("operator", "1111");
        MainFrame frame = new MainFrame();
        assertEquals(List.of("Файл", "Таблиці", "Аналіз", "Довідка"), menus(frame));
        assertEquals(List.of("Звіт: розклад рейсів", "Вихід"), items(frame, "Файл"));
        frame.dispose();
    }

    @Test
    void editButtonsDependOnEditRight() throws SQLException {
        Db.login("operator", "1111");
        TableForm form = flights();
        for (String text : List.of("Знайти", "Додати", "Змінити", "Видалити")) assertNotNull(button(form, text), text);

        Db.login("guest", "guest");
        form = flights();
        assertNotNull(button(form, "Знайти"));
        assertNull(button(form, "Додати"));
        assertNull(button(form, "Видалити"));
    }

    @Test
    void logFormIsReadOnlyEvenForAdmin() throws SQLException {
        Db.login("admin", "admin");
        TableForm form = new TableForm("Журнал системи", "user_log", false, "ts", Map.of("action", MainFrame.ACTIONS));
        assertNull(button(form, "Додати"));
        assertNull(button(form, "Змінити"));
    }

    @Test
    void flightsShowUkrainianHeadersAndAirlineNames() throws SQLException {
        Db.login("guest", "guest");
        JTable grid = grid(flights());
        assertEquals(8, grid.getRowCount());
        List<String> headers = new ArrayList<>();
        for (int i = 0; i < grid.getColumnCount(); i++) headers.add(grid.getColumnModel().getColumn(i).getHeaderValue().toString());
        assertEquals(List.of("ID", "Рейс", "Авіакомпанія", "Тип", "Місто", "Час за розкладом", "Гейт", "Статус"), headers);
        assertEquals("МАУ", grid.getValueAt(0, 2));
        assertEquals("2026-10-24 08:30", grid.getValueAt(0, 5));
    }

    @Test
    void filterListAppliesImmediately() throws SQLException {
        Db.login("guest", "guest");
        TableForm form = flights();
        JComboBox<?> status = find(form, JComboBox.class).stream()
                .filter(cb -> "(усі)".equals(cb.getItemAt(0)) && cb.getItemCount() == MainFrame.STATUSES.length + 1)
                .findFirst().orElseThrow();
        status.setSelectedItem("Скасовано");
        assertEquals(1, grid(form).getRowCount());
        status.setSelectedIndex(0);
        assertEquals(8, grid(form).getRowCount());
    }

    @Test
    void searchIsCaseInsensitiveForCyrillic() throws SQLException {
        Db.login("guest", "guest");
        TableForm form = flights();
        JTextField search = find(form, JTextField.class).stream().filter(f -> f.getColumns() == 12).findFirst().orElseThrow();
        search.setText("ВАРШАВА");
        button(form, "Знайти").doClick();
        assertEquals(2, grid(form).getRowCount());
        search.setText("ps1");
        button(form, "Знайти").doClick();
        assertEquals(1, grid(form).getRowCount());
    }

    @Test
    void dateRangeFilterKeepsOnlyFlightsOfThePeriod() throws SQLException {
        Db.login("guest", "guest");
        TableForm form = flights();
        List<JTextField> dates = find(form, JTextField.class).stream().filter(f -> f.getColumns() == 8).toList();
        dates.get(0).setText("2026-10-25");
        dates.get(1).setText("2026-10-25");
        button(form, "Знайти").doClick();
        assertEquals(3, grid(form).getRowCount());
    }

    @Test
    void openingTableIsLogged() throws SQLException {
        Db.login("operator", "1111");
        flights();
        assertEquals("flights", Db.scalar("SELECT details FROM user_log WHERE action = 'ВІДКРИТТЯ' AND login = 'operator'"));
    }

    @Test
    void statisticsCountsAllFlights() throws SQLException {
        Db.login("operator", "1111");
        JTable grid = grid(new StatsForm());
        assertEquals(6, grid.getRowCount());
        long total = 0;
        for (int r = 0; r < grid.getRowCount(); r++) total += ((Number) grid.getValueAt(r, 1)).longValue();
        assertEquals(8, total);
        assertEquals("За розкладом", grid.getValueAt(0, 0));
    }

    @Test
    void scheduleReportIsOrderedByTime() throws SQLException {
        Db.login("operator", "1111");
        JTable grid = grid(new ReportForm("Звіт", MainFrame.SCHEDULE_SQL));
        assertEquals(7, grid.getColumnCount());
        assertEquals(8, grid.getRowCount());
        assertEquals("PS101", grid.getValueAt(0, 1));
        assertEquals("W61235", grid.getValueAt(7, 1));
    }
}
