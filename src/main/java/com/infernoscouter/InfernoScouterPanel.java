package com.infernoscouter;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import net.runelite.client.ui.PluginPanel;

public class InfernoScouterPanel extends PluginPanel
{
    private final JTextField codeField = new JTextField();
    private final JLabel statusLabel = new JLabel();
    private final JTextArea legendArea = new JTextArea();

    public InfernoScouterPanel()
    {
        setLayout(new BorderLayout(0, 10));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JLabel title = new JLabel("Inferno Scouter");
        title.setBorder(BorderFactory.createEmptyBorder(0, 0, 6, 0));
        add(title, BorderLayout.NORTH);

        JPanel center = new JPanel(new BorderLayout(0, 10));

        // Code row + copy button
        codeField.setEditable(false);
        codeField.setFont(new Font(Font.MONOSPACED, Font.BOLD, 18));
        codeField.setText("[?????????]");
        codeField.setPreferredSize(new Dimension(10, 42));

        JButton copy = new JButton("Copy code");
        copy.addActionListener(e -> copyText(codeField.getText()));

        JPanel topRow = new JPanel(new BorderLayout(8, 0));
        topRow.add(codeField, BorderLayout.CENTER);
        topRow.add(copy, BorderLayout.EAST);
        center.add(topRow, BorderLayout.NORTH);

        // Explainer text (as requested)
        legendArea.setEditable(false);
        legendArea.setFocusable(false);
        legendArea.setOpaque(false);
        legendArea.setLineWrap(true);
        legendArea.setWrapStyleWord(true);
        legendArea.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        legendArea.setText(
                "The 9 characters of the code represents the 9 spawn positions as they appear in reading order, " +
                        "and what spawns on each one for the scouted wave:\n" +
                        "M = Mager\n" +
                        "R = Ranger\n" +
                        "X = Melee\n" +
                        "B = Blob\n" +
                        "Y = Bat"
        );
        center.add(legendArea, BorderLayout.CENTER);

        // Status line (calibrating / complete)
        statusLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        setCalibrated(false); // default
        center.add(statusLabel, BorderLayout.SOUTH);

        add(center, BorderLayout.CENTER);
    }

    public void setCode(String code)
    {
        if (code == null || code.isEmpty())
        {
            return;
        }
        codeField.setText(code);
    }

    public void setCalibrated(boolean calibrated)
    {
        if (calibrated)
        {
            statusLabel.setText("Calibration complete");
            statusLabel.setForeground(new Color(0, 150, 0)); // green
        }
        else
        {
            statusLabel.setText("Calibrating...");
            statusLabel.setForeground(Color.RED);
        }
    }

    private static void copyText(String text)
    {
        if (text == null || text.isEmpty())
        {
            return;
        }
        try
        {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
        }
        catch (Exception ignored)
        {
            // User can still select and Ctrl+C.
        }
    }
}
