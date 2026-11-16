package org.grebenyk.airhub.ui;

import org.grebenyk.airhub.db.Db;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.image.BaseMultiResolutionImage;
import java.net.URL;
import java.sql.SQLException;
import java.util.Map;

/** Login form and shared UI helper methods. */
public class Ui {
    private static final int LOGIN_FIELD_COLUMNS = 15;
    // the first size is the logical size of the form icon, the others are for display scaling 125-200%
    private static final int[] FORM_ICON_SIZES = {16, 20, 24, 32};
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

    /** Ukrainian button texts of standard dialogs (JOptionPane): Java has no Ukrainian Swing resources. */
    public static void localize() {
        UIManager.put("OptionPane.okButtonText", "OK");
        UIManager.put("OptionPane.cancelButtonText", "Скасувати");
        UIManager.put("OptionPane.yesButtonText", "Так");
        UIManager.put("OptionPane.noButtonText", "Ні");
        UIManager.put("OptionPane.titleText", "Виберіть варіант");
    }

    /** Program icon (resource /icon.png) for window title bars and the taskbar, or null if it is missing. */
    public static Image appIcon() {
        URL url = Ui.class.getResource("/icon.png");
        return url == null ? null : new ImageIcon(url).getImage();
    }

    /**
     * Small program icon for the title bars of forms inside the main window (instead of the Java icon), or null.
     * It holds several sizes so it stays sharp with Windows display scaling.
     */
    public static Icon formIcon() {
        Image icon = appIcon();
        if (icon == null) return null;
        Image[] sizes = new Image[FORM_ICON_SIZES.length];
        for (int i = 0; i < sizes.length; i++) {
            sizes[i] = new ImageIcon(icon.getScaledInstance(FORM_ICON_SIZES[i], FORM_ICON_SIZES[i], Image.SCALE_SMOOTH)).getImage();
        }
        return new ImageIcon(new BaseMultiResolutionImage(sizes));
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
