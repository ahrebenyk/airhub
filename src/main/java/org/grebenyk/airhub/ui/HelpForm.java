package org.grebenyk.airhub.ui;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.text.BadLocationException;
import java.awt.*;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Help window: the user guide split into sections (resources /help/&lt;topic&gt;.html).
 * It is not modal, so the user can read the help and work in the program at the same time.
 */
public class HelpForm extends JDialog {
    /** JInternalFrame client property with the help topic of the form (context help, F1). */
    static final String TOPIC_PROPERTY = "airhub.helpTopic";
    static final String START = "start", MAIN = "main", TABLES = "tables", FLIGHTS = "flights", AIRLINES = "airlines",
            REPORT = "report", STATS = "stats", USERS = "users", LOG = "log", SETTINGS = "settings", ERRORS = "errors",
            KEYS = "keys";
    // topic -> section title, in the order of the user guide
    static final Map<String, String> TOPICS = new LinkedHashMap<>();
    // links between sections are written as <a href="help:flights">
    private static final String LINK_PREFIX = "help:";
    private static final int WINDOW_WIDTH = 900, WINDOW_HEIGHT = 600, LIST_WIDTH = 230;

    static {
        TOPICS.put(START, "1. Початок роботи");
        TOPICS.put(MAIN, "2. Головне вікно");
        TOPICS.put(TABLES, "3. Робота з таблицями");
        TOPICS.put(FLIGHTS, "4. Рейси");
        TOPICS.put(AIRLINES, "5. Авіакомпанії");
        TOPICS.put(REPORT, "6. Звіт: розклад рейсів");
        TOPICS.put(STATS, "7. Статистика рейсів");
        TOPICS.put(USERS, "8. Користувачі (адміністратор)");
        TOPICS.put(LOG, "9. Журнал системи (адміністратор)");
        TOPICS.put(SETTINGS, "10. Параметри системи (адміністратор)");
        TOPICS.put(ERRORS, "11. Повідомлення про помилки");
        TOPICS.put(KEYS, "12. Клавіші");
    }

    private final JList<String> sections = new JList<>(TOPICS.values().toArray(String[]::new));
    private final JEditorPane page = new JEditorPane("text/html", "");

    HelpForm(Window owner) {
        super(owner, "Допомога", ModalityType.MODELESS);
        sections.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        sections.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && sections.getSelectedIndex() >= 0) showPage(topicAt(sections.getSelectedIndex()));
        });
        page.setEditable(false);
        // the page uses the theme font instead of the default serif font of HTML
        page.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true);
        page.addHyperlinkListener(e -> {
            if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED && e.getDescription().startsWith(LINK_PREFIX)) {
                show(e.getDescription().substring(LINK_PREFIX.length()));
            }
        });
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, new JScrollPane(sections), new JScrollPane(page));
        split.setDividerLocation(LIST_WIDTH);
        add(split);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(Ui.button("Закрити", null, e -> setVisible(false)));
        add(buttons, BorderLayout.SOUTH);
        getRootPane().registerKeyboardAction(e -> setVisible(false), KeyStroke.getKeyStroke("ESCAPE"),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
        setSize(WINDOW_WIDTH, WINDOW_HEIGHT);
        setLocationRelativeTo(owner);
        show(START);
    }

    /** Selects the section of the topic; an unknown topic opens the first section. */
    void show(String topic) {
        int index = Math.max(0, topics().indexOf(topic));
        sections.setSelectedIndex(index);
        sections.ensureIndexIsVisible(index);
    }

    String topic() {
        return topicAt(sections.getSelectedIndex());
    }

    /** Plain text of the shown section (getText() returns HTML with Cyrillic letters as character entities). */
    String pageText() {
        try {
            return page.getDocument().getText(0, page.getDocument().getLength());
        } catch (BadLocationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String topicAt(int index) {
        return index < 0 ? START : topics().get(index);
    }

    static List<String> topics() {
        return List.copyOf(TOPICS.keySet());
    }

    private void showPage(String topic) {
        page.setText(text(topic));
        page.setCaretPosition(0);
    }

    /** HTML text of a help section. */
    static String text(String topic) {
        try (InputStream in = HelpForm.class.getResourceAsStream("/help/" + topic + ".html")) {
            if (in == null) return "<html>Розділ довідки не знайдено: " + topic + "</html>";
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "<html>Не вдалося прочитати розділ довідки: " + e.getMessage() + "</html>";
        }
    }
}
