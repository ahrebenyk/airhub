package org.grebenyk.airhub.ui;

import org.grebenyk.airhub.db.Db;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.sql.SQLException;

/** Login form and shared UI helper methods. */
public class Ui {
    private static final int LOGIN_FIELD_COLUMNS = 15;

    /** Login form: repeats until a successful login or cancel. */
    public static boolean login() {
        JTextField login = new JTextField(LOGIN_FIELD_COLUMNS);
        JPasswordField password = new JPasswordField(LOGIN_FIELD_COLUMNS);
        Object[] form = {"Логін:", login, "Пароль:", password};
        while (true) {
            int answer = JOptionPane.showConfirmDialog(null, form, "AirHub — авторизація",
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE, icon("FileView.computerIcon"));
            if (answer != JOptionPane.OK_OPTION) return false;
            try {
                Db.user = Db.login(login.getText().trim(), new String(password.getPassword()));
            } catch (SQLException e) {
                showError(null, e);
                return false;
            }
            if (Db.user != null) {
                Db.log("ВХІД", "");
                return true;
            }
            Db.log("НЕВДАЛИЙ ВХІД", "логін: " + login.getText());
            JOptionPane.showMessageDialog(null, "Невірний логін або пароль", "Помилка", JOptionPane.ERROR_MESSAGE);
        }
    }

    /** Built-in Swing/FlatLaf icon by UIManager key, e.g. "FileView.fileIcon". */
    static Icon icon(String key) {
        return UIManager.getIcon(key);
    }

    static JButton button(String text, String iconKey, ActionListener action) {
        JButton b = new JButton(text, iconKey == null ? null : icon(iconKey));
        b.addActionListener(action);
        return b;
    }

    public static void showError(Component parent, Exception e) {
        String message = e.getMessage() != null ? e.getMessage() : e.toString();
        JOptionPane.showMessageDialog(parent, message, "Помилка", JOptionPane.ERROR_MESSAGE);
    }
}
