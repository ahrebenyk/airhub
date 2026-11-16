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
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/** Help sections, context help (F1) and the "About" window. */
class HelpAboutTest {
    private static final Pattern LINK = Pattern.compile("href=\"help:([a-z]+)\"");

    @BeforeEach
    void setUp() throws SQLException {
        Db.init("jdbc:h2:mem:" + UUID.randomUUID());
    }

    @Test
    void everyTopicHasPageWithItsTitle() {
        for (var topic : HelpForm.TOPICS.entrySet()) {
            String text = HelpForm.text(topic.getKey());
            assertTrue(text.contains("<h2>" + topic.getValue().replace(" (адміністратор)", "")),
                    "section " + topic.getKey() + " must start with its title");
        }
    }

    @Test
    void linksBetweenSectionsPointToExistingTopics() {
        int links = 0;
        for (String topic : HelpForm.topics()) {
            Matcher m = LINK.matcher(HelpForm.text(topic));
            while (m.find()) {
                links++;
                assertTrue(HelpForm.TOPICS.containsKey(m.group(1)), topic + " -> " + m.group(1));
            }
        }
        assertTrue(links > 0);
    }

    @Test
    void unknownTopicIsReportedInsteadOfException() {
        assertTrue(HelpForm.text("missing").contains("Розділ довідки не знайдено"));
    }

    @Test
    void helpMenuHasHelpWithF1AndAbout() throws SQLException {
        assumeFalse(GraphicsEnvironment.isHeadless());
        Db.login("guest", "guest");
        MainFrame frame = new MainFrame();
        JMenu help = frame.getJMenuBar().getMenu(frame.getJMenuBar().getMenuCount() - 1);
        List<JMenuItem> items = Arrays.stream(help.getMenuComponents())
                .filter(JMenuItem.class::isInstance).map(JMenuItem.class::cast).toList();
        assertEquals(List.of("Допомога", "Про програму"), items.stream().map(JMenuItem::getText).toList());
        assertEquals(KeyStroke.getKeyStroke("F1"), items.getFirst().getAccelerator());
        frame.dispose();
    }

    @Test
    void contextHelpOpensSectionOfActiveForm() throws SQLException {
        assumeFalse(GraphicsEnvironment.isHeadless());
        Db.login("admin", "admin");
        MainFrame frame = new MainFrame();
        assertEquals(HelpForm.MAIN, frame.activeTopic());
        click(frame, "Таблиці", "Рейси");
        assertEquals(HelpForm.FLIGHTS, frame.activeTopic());
        click(frame, "Адміністрування", "Журнал системи");
        assertEquals(HelpForm.LOG, frame.activeTopic());

        HelpForm help = frame.showHelp(frame.activeTopic());
        assertEquals(HelpForm.LOG, help.topic());
        assertTrue(help.pageText().contains("Очищення журналу"));
        // the second call reuses the open help window
        assertSame(help, frame.showHelp(HelpForm.STATS));
        assertEquals(HelpForm.STATS, help.topic());
        help.dispose();
        frame.dispose();
    }

    private static void click(MainFrame frame, String menu, String item) {
        JMenuBar bar = frame.getJMenuBar();
        for (int i = 0; i < bar.getMenuCount(); i++) {
            if (!bar.getMenu(i).getText().equals(menu)) continue;
            for (Component c : bar.getMenu(i).getMenuComponents()) {
                if (c instanceof JMenuItem mi && mi.getText().equals(item)) mi.doClick();
            }
        }
    }

    @Test
    void aboutShowsNameVersionAuthorsAndSupport() {
        String[][] rows = AboutForm.rows();
        List<String> labels = Arrays.stream(rows).map(r -> r[0]).toList();
        assertEquals(List.of("Програма", "Версія", "Автори", "Технічна підтримка", "Середовище", "Файл бази даних",
                "Авторські права"), labels);
        // run from target/classes, not from the jar, so the manifest version is absent
        assertEquals("dev", rows[1][1]);
        assertEquals(AboutForm.SUPPORT_EMAIL, rows[3][1]);
        assertTrue(rows[4][1].contains("H2 2."), rows[4][1]);
        assertTrue(rows[5][1].endsWith("airhub.mv.db"));
    }

    @Test
    void formIconIsSmallProgramIcon() {
        Icon icon = Ui.formIcon();
        assertNotNull(icon);
        assertEquals(16, icon.getIconWidth());
        assertEquals(16, icon.getIconHeight());
    }

    @Test
    void aboutWindowShowsAllRows() {
        assumeFalse(GraphicsEnvironment.isHeadless());
        AboutForm about = new AboutForm(null);
        String text = String.join("\n", texts(about.getContentPane()));
        for (String[] row : AboutForm.rows()) assertTrue(text.contains(row[1]), row[0]);
        about.dispose();
    }

    private static List<String> texts(Container root) {
        List<String> texts = new ArrayList<>();
        for (Component c : root.getComponents()) {
            if (c instanceof JLabel l) texts.add(l.getText());
            if (c instanceof Container child) texts.addAll(texts(child));
        }
        return texts;
    }
}
