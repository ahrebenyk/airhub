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
    private HelpForm helpForm;

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
        setIconImage(Ui.appIcon());
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
                () -> open(new TableForm("Авіакомпанії", "airlines", true, null, Map.of()), HelpForm.AIRLINES));
        bar.add(tables);

        if (user.canReports()) {
            JMenu analysis = new JMenu("Аналіз");
            addItem(analysis, "Статистика рейсів", "FileChooser.listViewIcon", () -> open(new StatsForm(), HelpForm.STATS));
            bar.add(analysis);
        }

        if (user.isAdmin()) {
            JMenu admin = new JMenu("Адміністрування");
            addItem(admin, "Користувачі", "FileView.computerIcon", () -> open(new TableForm("Користувачі", "users", true, null,
                    Map.of("role", ROLES, "can_edit", BOOLEANS, "can_reports", BOOLEANS)), HelpForm.USERS));
            addItem(admin, "Журнал системи", "FileView.hardDriveIcon",
                    () -> open(new TableForm("Журнал системи", "user_log", false, "ts", Map.of("action", ACTIONS)), HelpForm.LOG));
            addItem(admin, "Параметри системи", "FileView.floppyDriveIcon",
                    () -> open(new TableForm("Параметри системи", "settings", true, null, Map.of()), HelpForm.SETTINGS));
            admin.addSeparator();
            addItem(admin, "Очистити старий журнал", null, this::cleanLog);
            bar.add(admin);
        }

        JMenu help = new JMenu("Довідка");
        // F1 opens the help section of the active form (context help)
        addItem(help, "Допомога", null, () -> showHelp(activeTopic()))
                .setAccelerator(KeyStroke.getKeyStroke("F1"));
        help.addSeparator();
        addItem(help, "Про програму", null, () -> new AboutForm(this).setVisible(true));
        bar.add(help);
        setJMenuBar(bar);
    }

    // the airport name is an admin setting and may be deleted or left empty
    static String title(String airportName) {
        return airportName == null || airportName.isBlank() ? "AirHub" : "AirHub — " + airportName;
    }

    private JMenuItem addItem(JMenu menu, String text, String iconKey, Runnable action) {
        JMenuItem item = new JMenuItem(text, iconKey == null ? null : Ui.icon(iconKey));
        item.addActionListener(e -> action.run());
        menu.add(item);
        return item;
    }

    /** Opens a form inside the main window; helpTopic is the help section shown by F1 while the form is active. */
    private void open(JInternalFrame f, String helpTopic) {
        f.putClientProperty(HelpForm.TOPIC_PROPERTY, helpTopic);
        desktop.add(f);
        // each new window is offset so it does not cover the previous ones
        int offset = CASCADE_STEP * desktop.getAllFrames().length;
        f.setLocation(offset, offset);
        f.setVisible(true);
    }

    private void openFlights() {
        // airline_id is shown and edited as the airline name from the airlines table
        open(new TableForm("Рейси", "flights", true, "sched_time", Map.of("type", TYPES, "status", STATUSES),
                Map.of("airline_id", "SELECT id, name FROM airlines ORDER BY name")), HelpForm.FLIGHTS);
    }

    private void showReport() {
        open(new ReportForm("Звіт: розклад рейсів", SCHEDULE_SQL), HelpForm.REPORT);
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

    /** Help topic of the active form, or the main window section when no form is open. */
    String activeTopic() {
        JInternalFrame f = desktop.getSelectedFrame();
        // no form is selected while the main window is not shown or after a click on the empty desktop:
        // the front form is used (index 0 of the layered pane is the top one)
        if (f == null && desktop.getAllFrames().length > 0) f = desktop.getAllFrames()[0];
        Object topic = f == null ? null : f.getClientProperty(HelpForm.TOPIC_PROPERTY);
        return topic != null ? topic.toString() : HelpForm.MAIN;
    }

    /** Opens the help window (a single one for the main window) on the section of the topic. */
    HelpForm showHelp(String topic) {
        if (helpForm == null) helpForm = new HelpForm(this);
        helpForm.show(topic);
        helpForm.setVisible(true);
        helpForm.toFront();
        return helpForm;
    }

    private void exit() {
        Db.log("ВИХІД", "");
        System.exit(0);
    }
}
