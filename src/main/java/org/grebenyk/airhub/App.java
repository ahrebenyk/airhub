package org.grebenyk.airhub;

import com.formdev.flatlaf.FlatLightLaf;
import org.grebenyk.airhub.db.Db;
import org.grebenyk.airhub.ui.MainFrame;
import org.grebenyk.airhub.ui.Ui;

import javax.swing.*;
import java.sql.SQLException;

/** Entry point: database initialization, login, main window. */
public class App {

    static void main() {
        FlatLightLaf.setup();
        try {
            Db.init();
        } catch (SQLException e) {
            Ui.showError(null, e);
            return;
        }
        SwingUtilities.invokeLater(() -> {
            if (Ui.login()) {
                new MainFrame().setVisible(true);
            } else {
                System.exit(0);
            }
        });
    }
}
