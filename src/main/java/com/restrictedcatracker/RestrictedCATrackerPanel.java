package com.restrictedcatracker;

import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;

public class RestrictedCATrackerPanel extends PluginPanel
{
    private static final Color GREEN = new Color(0, 200, 83);
    private static final Color DEFAULT_TEXT = Color.WHITE;

    private final RestrictedCATrackerConfig config;
    private final List<BossEntry> entries = new ArrayList<>();
    private final JPanel listPanel = new JPanel();

    public RestrictedCATrackerPanel(RestrictedCATrackerConfig config)
    {
        super(false);
        this.config = config;

        setLayout(new BorderLayout());
        setBorder(new EmptyBorder(10, 10, 10, 10));
        setBackground(ColorScheme.DARK_GRAY_COLOR);

        JLabel title = new JLabel("Restricted CA Tracker");
        title.setForeground(Color.WHITE);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));
        title.setBorder(new EmptyBorder(0, 0, 10, 0));

        JButton addButton = new JButton("+ Add Boss");
        addButton.addActionListener(this::onAddBoss);
        addButton.setFocusPainted(false);

        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        topPanel.add(title, BorderLayout.NORTH);
        topPanel.add(addButton, BorderLayout.SOUTH);

        listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
        listPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

        JScrollPane scrollPane = new JScrollPane(listPanel);
        scrollPane.setBorder(null);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);

        add(topPanel, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);

        loadFromConfig();
    }

    private void loadFromConfig()
    {
        entries.clear();
        String raw = config.bossData();
        if (raw != null && !raw.trim().isEmpty())
        {
            for (String chunk : raw.split(";;"))
            {
                BossEntry entry = BossEntry.parse(chunk);
                if (entry != null)
                {
                    entries.add(entry);
                }
            }
        }
    }

    private void saveToConfig()
    {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < entries.size(); i++)
        {
            sb.append(entries.get(i).serialize());
            if (i < entries.size() - 1)
            {
                sb.append(";;");
            }
        }
        config.bossData(sb.toString());
    }

    /**
     * Rebuilds the visible list of boss rows from the current entries list.
     * Call this any time entries change or on initial load.
     */
    public void rebuild()
    {
        listPanel.removeAll();

        if (entries.isEmpty())
        {
            JLabel empty = new JLabel("<html><center>No bosses tracked yet.<br>Click '+ Add Boss' to start.</center></html>");
            empty.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
            empty.setHorizontalAlignment(SwingConstants.CENTER);
            empty.setBorder(new EmptyBorder(20, 0, 0, 0));
            listPanel.add(empty);
        }
        else
        {
            for (BossEntry entry : entries)
            {
                listPanel.add(buildRow(entry));
                listPanel.add(Box.createRigidArea(new Dimension(0, 4)));
            }
        }

        listPanel.revalidate();
        listPanel.repaint();
    }

    private JPanel buildRow(BossEntry entry)
    {
        boolean complete = entry.isComplete();

        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        row.setBorder(new EmptyBorder(4, 8, 4, 6));

        // Without an explicit max height, BoxLayout (Y_AXIS) on the
        // parent listPanel stretches each row to share the full
        // available vertical space equally - that's what was causing
        // the huge empty rows. Capping preferred/max height fixes it.
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        row.setPreferredSize(new Dimension(row.getPreferredSize().width, 40));

        JLabel nameLabel = new JLabel(entry.getName());
        nameLabel.setForeground(complete ? GREEN : DEFAULT_TEXT);
        nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD, 14f));

        JLabel progressLabel = new JLabel(entry.getCurrent() + "/" + entry.getMax());
        progressLabel.setForeground(complete ? GREEN : ColorScheme.LIGHT_GRAY_COLOR);
        progressLabel.setFont(progressLabel.getFont().deriveFont(Font.PLAIN, 13f));

        // Vertical box instead of a 2-row grid: grid cells stretch to
        // fill all available height, which was the main cause of the
        // oversized rows. A box only takes as much height as its content
        // actually needs.
        JPanel leftPanel = new JPanel();
        leftPanel.setLayout(new BoxLayout(leftPanel, BoxLayout.Y_AXIS));
        leftPanel.setOpaque(false);
        leftPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        nameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        progressLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        leftPanel.add(nameLabel);
        leftPanel.add(progressLabel);

        // Checkmark button — clicking toggles "mark complete" by snapping
        // current up to max (or back down to max-1 if un-checking).
        JButton checkButton = new JButton(complete ? "\u2713" : "\u25CB");
        checkButton.setForeground(complete ? GREEN : ColorScheme.LIGHT_GRAY_COLOR);
        checkButton.setFont(checkButton.getFont().deriveFont(Font.BOLD, 14f));
        checkButton.setBorderPainted(false);
        checkButton.setContentAreaFilled(false);
        checkButton.setFocusPainted(false);
        checkButton.setMargin(new Insets(0, 2, 0, 2));
        checkButton.setToolTipText(complete ? "Mark as not complete" : "Mark as complete (max achievable)");
        checkButton.addActionListener((ActionEvent e) ->
        {
            if (entry.isComplete())
            {
                entry.setCurrent(Math.max(0, entry.getMax() - 1));
            }
            else
            {
                entry.setCurrent(entry.getMax());
            }
            saveToConfig();
            rebuild();
        });

        JButton editButton = new JButton("\u270E");
        editButton.setBorderPainted(false);
        editButton.setContentAreaFilled(false);
        editButton.setFocusPainted(false);
        editButton.setMargin(new Insets(0, 2, 0, 2));
        editButton.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        editButton.setFont(editButton.getFont().deriveFont(12f));
        editButton.setToolTipText("Edit");
        editButton.addActionListener(e -> onEditBoss(entry));

        JButton removeButton = new JButton("\u2715");
        removeButton.setBorderPainted(false);
        removeButton.setContentAreaFilled(false);
        removeButton.setFocusPainted(false);
        removeButton.setMargin(new Insets(0, 2, 0, 2));
        removeButton.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        removeButton.setFont(removeButton.getFont().deriveFont(12f));
        removeButton.setToolTipText("Remove");
        removeButton.addActionListener(e -> onRemoveBoss(entry));

        // FlowLayout sized to content instead of a grid stretching to
        // fill the row - keeps the button cluster compact and centered
        // vertically rather than each button claiming a tall equal cell.
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
        buttonPanel.setOpaque(false);
        buttonPanel.add(checkButton);
        buttonPanel.add(editButton);
        buttonPanel.add(removeButton);

        row.add(leftPanel, BorderLayout.CENTER);
        row.add(buttonPanel, BorderLayout.EAST);

        return row;
    }

    private void onAddBoss(ActionEvent e)
    {
        BossEntry result = showBossDialog("Add Boss", "", 0, 1);
        if (result != null)
        {
            entries.add(result);
            saveToConfig();
            rebuild();
        }
    }

    private void onEditBoss(BossEntry entry)
    {
        BossEntry result = showBossDialog("Edit Boss", entry.getName(), entry.getCurrent(), entry.getMax());
        if (result != null)
        {
            entry.setName(result.getName());
            entry.setCurrent(result.getCurrent());
            entry.setMax(result.getMax());
            saveToConfig();
            rebuild();
        }
    }

    private void onRemoveBoss(BossEntry entry)
    {
        int confirm = JOptionPane.showConfirmDialog(
                this,
                "Remove " + entry.getName() + " from tracker?",
                "Confirm Remove",
                JOptionPane.YES_NO_OPTION
        );

        if (confirm == JOptionPane.YES_OPTION)
        {
            entries.remove(entry);
            saveToConfig();
            rebuild();
        }
    }

    /**
     * Shows a small modal dialog for entering/editing a boss's name,
     * current progress, and your manually-determined achievable max.
     * Returns null if the user cancels or input is invalid.
     */
    private BossEntry showBossDialog(String title, String initialName, int initialCurrent, int initialMax)
    {
        JTextField nameField = new JTextField(initialName);
        JTextField currentField = new JTextField(String.valueOf(initialCurrent));
        JTextField maxField = new JTextField(String.valueOf(initialMax));

        JPanel panel = new JPanel(new GridLayout(3, 2, 5, 8));
        panel.add(new JLabel("Boss name:"));
        panel.add(nameField);
        panel.add(new JLabel("Current completed:"));
        panel.add(currentField);
        panel.add(new JLabel("Max achievable:"));
        panel.add(maxField);

        int result = JOptionPane.showConfirmDialog(
                this,
                panel,
                title,
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE
        );

        if (result != JOptionPane.OK_OPTION)
        {
            return null;
        }

        String name = nameField.getText().trim();
        if (name.isEmpty())
        {
            JOptionPane.showMessageDialog(this, "Boss name cannot be empty.");
            return null;
        }

        int current;
        int max;
        try
        {
            current = Integer.parseInt(currentField.getText().trim());
            max = Integer.parseInt(maxField.getText().trim());
        }
        catch (NumberFormatException ex)
        {
            JOptionPane.showMessageDialog(this, "Current and max must be whole numbers.");
            return null;
        }

        if (max < 0)
        {
            JOptionPane.showMessageDialog(this, "Max achievable can't be negative. Use 0 if this boss is fully unobtainable on your account.");
            return null;
        }

        if (current < 0)
        {
            current = 0;
        }

        return new BossEntry(name, current, max);
    }
}