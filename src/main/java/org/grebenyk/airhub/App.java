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
        // FlatLaf draws the title bar itself and by default shows the program icon only in frames:
        // the first key enables it in dialogs, the second in message and confirmation dialogs (JOptionPane)
        UIManager.put("TitlePane.showIconInDialogs", true);
        UIManager.put("OptionPane.showIcon", true);
        // forms inside the main window (JInternalFrame) take this icon instead of the Java one
        UIManager.put("InternalFrame.icon", Ui.formIcon());
        Ui.localize();
        try {
            Db.init();
        } catch (SQLException e) {
            Ui.showError(null, e);
            return;
        }
        SwingUtilities.invokeLater(() -> {
            // the login dialog has no owner window and uses the shared JOptionPane frame for its icon
            JOptionPane.getRootFrame().setIconImage(Ui.appIcon());
            if (Ui.login()) {
                new MainFrame().setVisible(true);
            } else {
                System.exit(0);
            }
        });
    }
}
