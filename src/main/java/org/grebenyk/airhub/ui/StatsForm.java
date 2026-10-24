package org.grebenyk.airhub.ui;

import org.grebenyk.airhub.db.Db;

import javax.swing.*;
import javax.swing.table.TableModel;
import java.awt.*;
import java.sql.SQLException;

/** Flight statistics: counts grouped by the selected field, shown as a table and a bar chart. */
public class StatsForm extends JInternalFrame {
    // {label, SQL expression for GROUP BY}
    private static final String[][] GROUPS = {
            {"Статус", "f.status"}, {"Тип", "f.type"}, {"Авіакомпанія", "a.name"}, {"Місто", "f.city"},
            {"Дата", "CAST(f.sched_time AS DATE)"}};
    private static final int LABEL = 0, SQL = 1;
    private static final int WINDOW_WIDTH = 950, WINDOW_HEIGHT = 450, DIVIDER = 300;

    private final JTable grid = new JTable();
    private final Chart chart = new Chart();

    StatsForm() {
        super("Статистика рейсів", true, true, true, true);
        JComboBox<String> groupBy = new JComboBox<>();
        for (String[] g : GROUPS) groupBy.addItem(g[LABEL]);
        groupBy.addActionListener(e -> load(groupBy.getSelectedIndex()));

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.add(new JLabel("Групувати за:"));
        top.add(groupBy);
        add(top, BorderLayout.NORTH);
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, new JScrollPane(grid), chart);
        split.setDividerLocation(DIVIDER);
        add(split);
        setSize(WINDOW_WIDTH, WINDOW_HEIGHT);
        load(0);
    }

    private void load(int g) {
        String expr = GROUPS[g][SQL];
        try {
            grid.setModel(Db.query("SELECT " + expr + " AS \"" + GROUPS[g][LABEL] + "\", COUNT(*) AS \"Кількість\""
                    + " FROM flights f JOIN airlines a ON a.id = f.airline_id GROUP BY " + expr + " ORDER BY 2 DESC"));
            chart.repaint();
            Db.log("СТАТИСТИКА", GROUPS[g][LABEL]);
        } catch (SQLException e) {
            Ui.showError(this, e);
        }
    }

    /** Bar chart drawn from the grid data: column 0 - label, column 1 - count. */
    private class Chart extends JPanel {
        private static final int NAME_COL = 0, COUNT_COL = 1;
        private static final int PADDING = 10, BAR_GAP = 5, AXIS_HEIGHT = 30, TOP_MARGIN = 30, LABEL_OFFSET = 15;
        private static final Color BAR_COLOR = new Color(66, 133, 244);

        Chart() {
            setBackground(Color.WHITE);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            TableModel model = grid.getModel();
            int n = model.getRowCount();
            if (n == 0) return;
            long max = 1;
            for (int r = 0; r < n; r++) max = Math.max(max, count(model, r));
            // base is the line bars grow up from (the Swing Y axis points down)
            int base = getHeight() - AXIS_HEIGHT, barWidth = (getWidth() - 2 * PADDING) / n;
            ((Graphics2D) g).setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            for (int r = 0; r < n; r++) {
                long v = count(model, r);
                int h = (int) (v * (base - TOP_MARGIN) / max), x = PADDING + r * barWidth;
                g.setColor(BAR_COLOR);
                g.fillRect(x + BAR_GAP, base - h, barWidth - 2 * BAR_GAP, h);
                g.setColor(Color.DARK_GRAY);
                g.drawString(String.valueOf(v), x + BAR_GAP, base - h - BAR_GAP);
                g.drawString(String.valueOf(model.getValueAt(r, NAME_COL)), x + BAR_GAP, base + LABEL_OFFSET);
            }
        }

        private static long count(TableModel model, int row) {
            return ((Number) model.getValueAt(row, COUNT_COL)).longValue();
        }
    }
}
