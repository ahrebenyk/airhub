package org.grebenyk.airhub.ui;

import org.grebenyk.airhub.db.Db;

import javax.swing.*;
import java.awt.*;
import java.sql.SQLException;

/** "About" window: program name, version, authors, technical support contacts, environment. */
public class AboutForm extends JDialog {
    static final String PROGRAM = "AirHub";
    static final String DESCRIPTION = "Система управління аеропортом";
    static final String AUTHORS = "Гребеник Артем";
    static final String SUPPORT_EMAIL = "artem.grebenyk@gmail.com";
    static final String COPYRIGHT = "© 2026 Гребеник Артем";
    private static final int GAP = 12, TITLE_FONT_SCALE = 2;

    AboutForm(Window owner) {
        super(owner, "Про програму", ModalityType.APPLICATION_MODAL);
        Image icon = Ui.appIcon();
        JLabel title = new JLabel(PROGRAM, icon != null ? new ImageIcon(icon) : Ui.icon("OptionPane.informationIcon"), SwingConstants.LEFT);
        title.setFont(title.getFont().deriveFont(Font.BOLD, title.getFont().getSize2D() * TITLE_FONT_SCALE));

        JPanel info = new JPanel(new GridBagLayout());
        GridBagConstraints label = new GridBagConstraints(), field = new GridBagConstraints();
        label.anchor = GridBagConstraints.WEST;
        label.insets = new Insets(GAP / 4, 0, GAP / 4, GAP);
        field.gridwidth = GridBagConstraints.REMAINDER;
        field.fill = GridBagConstraints.HORIZONTAL;
        field.weightx = 1;
        for (String[] row : rows()) {
            info.add(new JLabel(row[0] + ":"), label);
            JLabel value = new JLabel(row[1]);
            info.add(value, field);
        }

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton close = Ui.button("Закрити", null, e -> dispose());
        buttons.add(close);

        JPanel content = new JPanel(new BorderLayout(GAP, GAP));
        content.setBorder(BorderFactory.createEmptyBorder(GAP, GAP, GAP, GAP));
        content.add(title, BorderLayout.NORTH);
        content.add(info);
        content.add(buttons, BorderLayout.SOUTH);
        setContentPane(content);
        getRootPane().setDefaultButton(close);
        getRootPane().registerKeyboardAction(e -> dispose(), KeyStroke.getKeyStroke("ESCAPE"), JComponent.WHEN_IN_FOCUSED_WINDOW);
        // setResizable changes the window insets, so it is called before pack()
        setResizable(false);
        pack();
        setLocationRelativeTo(owner);
    }

    /** {label, value} rows of the window. */
    static String[][] rows() {
        return new String[][]{
                {"Програма", PROGRAM + " — " + DESCRIPTION},
                {"Версія", version()},
                {"Автори", AUTHORS},
                {"Технічна підтримка", SUPPORT_EMAIL},
                {"Середовище", "Java " + Runtime.version() + ", H2 " + h2Version()},
                {"Файл бази даних", Db.file() + ".mv.db"},
                {"Авторські права", COPYRIGHT}};
    }

    // Implementation-Version is written to the jar manifest from pom.xml; it is absent when run from the IDE
    static String version() {
        String version = AboutForm.class.getPackage().getImplementationVersion();
        return version != null ? version : "dev";
    }

    private static String h2Version() {
        try {
            return String.valueOf(Db.scalar("SELECT H2VERSION()"));
        } catch (SQLException e) {
            return "?";
        }
    }
}
