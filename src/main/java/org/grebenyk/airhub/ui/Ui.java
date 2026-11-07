package org.grebenyk.airhub.ui;

import org.grebenyk.airhub.db.Db;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.sql.SQLException;
import java.util.Map;

/** Login form and shared UI helper methods. */
public class Ui {
    private static final int LOGIN_FIELD_COLUMNS = 15;
    // after this many failed logins the program exits, which slows down password guessing
    static final int MAX_LOGIN_ATTEMPTS = 3;
    // H2 error codes (org.h2.api.ErrorCode) -> explanation for the user
    private static final Map<Integer, String> SQL_ERRORS = Map.of(
            23503, "Запис використовується в інших таблицях (наприклад, авіакомпанія має рейси), тому його не можна видалити",
            23506, "Вказане значення відсутнє в довіднику",
            23505, "Запис з таким значенням уже існує",
            23502, "Не заповнено обов'язкове поле",
            23513, "Недопустиме значення поля",
            22001, "Значення задовге для цього поля",
            22007, "Невірний формат дати або часу (РРРР-ММ-ДД ГГ:ХХ)",
            22018, "Невірний формат даних (число або дата)",
            90020, "База даних уже відкрита іншим екземпляром програми");

    /** Login form: repeats until a successful login, cancel, or MAX_LOGIN_ATTEMPTS failures. */
    public static boolean login() {
        JTextField login = new JTextField(LOGIN_FIELD_COLUMNS);
        JPasswordField password = new JPasswordField(LOGIN_FIELD_COLUMNS);
        Object[] form = {"Логін:", login, "Пароль:", password};
        for (int attempt = 1; attempt <= MAX_LOGIN_ATTEMPTS; attempt++) {
            int answer = JOptionPane.showConfirmDialog(null, form, "AirHub — авторизація",
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE, icon("FileView.computerIcon"));
            if (answer != JOptionPane.OK_OPTION) return false;
            try {
                if (Db.login(login.getText().trim(), new String(password.getPassword())) != null) {
                    Db.log("ВХІД", "");
                    return true;
                }
            } catch (SQLException e) {
                showError(null, e);
                return false;
            }
            Db.log("НЕВДАЛИЙ ВХІД", "логін: " + login.getText());
            password.setText("");
            JOptionPane.showMessageDialog(null, "Невірний логін або пароль", "Помилка", JOptionPane.ERROR_MESSAGE);
        }
        JOptionPane.showMessageDialog(null, "Перевищено кількість спроб входу", "Помилка", JOptionPane.ERROR_MESSAGE);
        return false;
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
        JOptionPane.showMessageDialog(parent, message(e), "Помилка", JOptionPane.ERROR_MESSAGE);
    }

    /** Error text for the user: known database errors are explained, the rest keep their own message. */
    static String message(Exception e) {
        if (e instanceof SQLException s && SQL_ERRORS.containsKey(s.getErrorCode())) return SQL_ERRORS.get(s.getErrorCode());
        String message = e.getMessage() != null ? e.getMessage() : e.toString();
        // H2 appends "; SQL statement: ..." and the error code; the user needs only the first line
        return message.lines().findFirst().orElse(message).replaceFirst(";\s*SQL statement:.*$", "");
    }
}
