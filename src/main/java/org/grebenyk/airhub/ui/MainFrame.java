package org.grebenyk.airhub.ui;

import org.grebenyk.airhub.db.Db;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.sql.SQLException;
import java.util.Map;

/** Main window: menu items are shown according to the user's role and permissions. */
public class MainFrame extends JFrame {
    static final String[] TYPES = {"Виліт", "Приліт"};
    static final String[] STATUSES = {"За розкладом", "Посадка", "Затримано", "Відбув", "Прибув", "Скасовано"};
    static final String[] ROLES = {"ADMIN", "USER"};
    // BOOLEAN column values in the form the table displays them
    static final String[] BOOLEANS = {"true", "false"};
    static final String[] ACTIONS = {"ВХІД", "НЕВДАЛИЙ ВХІД", "ВИХІД", "ВІДКРИТТЯ", "ДОДАВАННЯ", "ЗМІНА", "ВИДАЛЕННЯ",
            "ЗВІТ", "СТАТИСТИКА", "СТАТУС", "ОЧИЩЕННЯ"};
    private static final int WINDOW_WIDTH = 1200, WINDOW_HEIGHT = 750;
    private static final int CASCADE_STEP = 20;
    static final String SCHEDULE_SQL = """
            SELECT f.sched_time AS "Час", f.flight_no AS "Рейс", a.name AS "Авіакомпанія", f.type AS "Тип",
                   f.city AS "Місто", f.gate AS "Гейт", f.status AS "Статус"
            FROM flights f JOIN airlines a ON a.id = f.airline_id
            ORDER BY f.sched_time""";

    private final JDesktopPane desktop = new JDesktopPane();

    public MainFrame() {
        super(title(Db.setting("airport_name")));
        // closing is handled manually so that the logout is written to the log
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                exit();
            }
        });
        setSize(WINDOW_WIDTH, WINDOW_HEIGHT);
        setLocationRelativeTo(null);
        add(desktop);
        Db.User user = Db.user();
        add(new JLabel(" Користувач: " + user.login() + " (" + user.role() + ")"), BorderLayout.SOUTH);

        JMenuBar bar = new JMenuBar();
        JMenu file = new JMenu("Файл");
        if (user.canReports()) addItem(file, "Звіт: розклад рейсів", "FileView.fileIcon", this::showReport);
        addItem(file, "Вихід", null, this::exit);
        bar.add(file);

        JMenu tables = new JMenu("Таблиці");
        addItem(tables, "Рейси", "FileChooser.detailsViewIcon", this::openFlights);
        addItem(tables, "Авіакомпанії", "FileView.directoryIcon",
                () -> open(new TableForm("Авіакомпанії", "airlines", true, null, Map.of())));
        bar.add(tables);

        if (user.canReports()) {
            JMenu analysis = new JMenu("Аналіз");
            addItem(analysis, "Статистика рейсів", "FileChooser.listViewIcon", () -> open(new StatsForm()));
            bar.add(analysis);
        }

        if (user.isAdmin()) {
            JMenu admin = new JMenu("Адміністрування");
            addItem(admin, "Користувачі", "FileView.computerIcon", () -> open(new TableForm("Користувачі", "users", true, null,
                    Map.of("role", ROLES, "can_edit", BOOLEANS, "can_reports", BOOLEANS))));
            addItem(admin, "Журнал системи", "FileView.hardDriveIcon",
                    () -> open(new TableForm("Журнал системи", "user_log", false, "ts", Map.of("action", ACTIONS))));
            addItem(admin, "Параметри системи", "FileView.floppyDriveIcon",
                    () -> open(new TableForm("Параметри системи", "settings", true, null, Map.of())));
            admin.addSeparator();
            addItem(admin, "Очистити старий журнал", null, this::cleanLog);
            bar.add(admin);
        }

        JMenu help = new JMenu("Довідка");
        addItem(help, "Допомога", null, this::showHelp);
        addItem(help, "Про програму", null, this::showAbout);
        bar.add(help);
        setJMenuBar(bar);
    }

    // the airport name is an admin setting and may be deleted or left empty
    static String title(String airportName) {
        return airportName == null || airportName.isBlank() ? "AirHub" : "AirHub — " + airportName;
    }

    private void addItem(JMenu menu, String text, String iconKey, Runnable action) {
        JMenuItem item = new JMenuItem(text, iconKey == null ? null : Ui.icon(iconKey));
        item.addActionListener(e -> action.run());
        menu.add(item);
    }

    private void open(JInternalFrame f) {
        desktop.add(f);
        // each new window is offset so it does not cover the previous ones
        int offset = CASCADE_STEP * desktop.getAllFrames().length;
        f.setLocation(offset, offset);
        f.setVisible(true);
    }

    private void openFlights() {
        // airline_id is shown and edited as the airline name from the airlines table
        open(new TableForm("Рейси", "flights", true, "sched_time", Map.of("type", TYPES, "status", STATUSES),
                Map.of("airline_id", "SELECT id, name FROM airlines ORDER BY name")));
    }

    private void showReport() {
        open(new ReportForm("Звіт: розклад рейсів", SCHEDULE_SQL));
        Db.log("ЗВІТ", "Розклад рейсів");
    }

    private void cleanLog() {
        String days = Db.setting("log_keep_days");
        int answer = JOptionPane.showConfirmDialog(this, "Видалити записи журналу, старші за " + days + " днів?", "Журнал",
                JOptionPane.YES_NO_OPTION);
        if (answer != JOptionPane.YES_OPTION) return;
        try {
            // both the number of days and the deletion are evaluated on the database server
            Object n = Db.scalar("SELECT CLEAN_LOG(CAST(GET_SETTING('log_keep_days') AS INT))");
            Db.log("ОЧИЩЕННЯ", "Видалено записів: " + n);
            JOptionPane.showMessageDialog(this, "Видалено записів: " + n);
        } catch (SQLException e) {
            Ui.showError(this, e);
        }
    }

    // text is wrapped in a JLabel: JOptionPane splits a string on "\n" and the HTML is not recognized
    private void showHelp() {
        JOptionPane.showMessageDialog(this, new JLabel("""
                <html><b>AirHub — система управління аеропортом</b><br><br>
                <b>Таблиці</b> — перегляд і редагування рейсів та авіакомпаній.<br>
                &nbsp;• клік по заголовку колонки — сортування;<br>
                &nbsp;• Пошук — оберіть поле і введіть текст;<br>
                &nbsp;• Фільтр — період (РРРР-ММ-ДД), тип, статус;<br>
                &nbsp;• Додати / Змінити / Видалити — для користувачів з правом редагування.<br>
                <b>Файл → Звіт</b> — розклад рейсів.<br>
                <b>Аналіз</b> — статистика рейсів (таблиця і діаграма).<br>
                <b>Адміністрування</b> — користувачі, журнал, параметри (тільки ADMIN).</html>"""),
                "Допомога", JOptionPane.INFORMATION_MESSAGE);
    }

    private void showAbout() {
        JOptionPane.showMessageDialog(this, new JLabel("""
                <html><b>AirHub</b> v%s<br>Система управління аеропортом<br><br>
                Java + Swing (FlatLaf) + H2<br>Автор: Гребеник Артем</html>""".formatted(version())),
                "Про програму", JOptionPane.INFORMATION_MESSAGE);
    }

    // Implementation-Version is written to the jar manifest from pom.xml; it is absent when run from the IDE
    private static String version() {
        String version = MainFrame.class.getPackage().getImplementationVersion();
        return version != null ? version : "dev";
    }

    private void exit() {
        Db.log("ВИХІД", "");
        System.exit(0);
    }
}
