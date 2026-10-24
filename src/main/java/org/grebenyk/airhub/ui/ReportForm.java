package org.grebenyk.airhub.ui;

import org.grebenyk.airhub.db.Db;

import javax.swing.*;
import java.sql.SQLException;

/** Report: result of an SQL query. */
public class ReportForm extends JInternalFrame {
    private static final int WINDOW_WIDTH = 900, WINDOW_HEIGHT = 450;

    ReportForm(String title, String sql) {
        super(title, true, true, true, true);
        JTable grid = new JTable();
        grid.setAutoCreateRowSorter(true);
        try {
            grid.setModel(Db.query(sql));
        } catch (SQLException e) {
            Ui.showError(this, e);
        }
        add(new JScrollPane(grid));
        setSize(WINDOW_WIDTH, WINDOW_HEIGHT);
    }
}
