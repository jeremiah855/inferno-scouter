package com.infernoscouter;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.util.List;
import java.awt.event.ActionListener;
import java.awt.Component;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import net.runelite.client.ui.PluginPanel;

public class InfernoScouterPanel extends PluginPanel
{
    private final JTextField codeField = new JTextField();
    private final JLabel waveLabel = new JLabel();
    private final InfernoSpawnImage spawnImage = new InfernoSpawnImage();
    private final JTextArea legendArea = new JTextArea();
    private final ColorSwatch batSwatch = new ColorSwatch();
    private final ColorSwatch blobSwatch = new ColorSwatch();
    private final ColorSwatch meleeSwatch = new ColorSwatch();
    private final ColorSwatch rangerSwatch = new ColorSwatch();
    private final ColorSwatch magerSwatch = new ColorSwatch();
    private final CompassToggle compassToggle = new CompassToggle();
    private final JButton pasteStartButton = new JButton("Paste tilemarker");
    private final JButton resetStartButton = new JButton("Reset");
    private final JTextField startTileField = new JTextField();
    private boolean southUp = true;

    public InfernoScouterPanel()
    {
        setLayout(new BorderLayout(0, 10));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setOpaque(false);

        JLabel title = new JLabel("Inferno Scouter");
        title.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));
        header.add(title);

        waveLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        waveLabel.setText("Wave ?");
        waveLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));

        JPanel waveRow = new JPanel(new BorderLayout());
        waveRow.setOpaque(false);
        waveRow.setBorder(BorderFactory.createEmptyBorder(0, 0, 6, 0));
        waveRow.add(waveLabel, BorderLayout.WEST);
        waveRow.add(compassToggle, BorderLayout.EAST);
        header.add(waveRow);

        codeField.setEditable(false);
        codeField.setFont(new Font(Font.MONOSPACED, Font.BOLD, 18));
        codeField.setText("[?????????]");
        codeField.setPreferredSize(new Dimension(10, 42));

        JButton copy = new JButton("Copy code");
        copy.addActionListener(e -> copyText(codeField.getText()));

        JPanel codeRow = new JPanel(new BorderLayout(8, 0));
        codeRow.setOpaque(false);
        codeRow.add(codeField, BorderLayout.CENTER);
        codeRow.add(copy, BorderLayout.EAST);
        header.add(codeRow);

        add(header, BorderLayout.NORTH);
        spawnImage.setPreferredSize(new Dimension(300, 310));
        spawnImage.setAlignmentX(Component.LEFT_ALIGNMENT);

        startTileField.setEditable(false);
        startTileField.setFocusable(false);
        startTileField.setHorizontalAlignment(SwingConstants.CENTER);
        startTileField.setText("");
        startTileField.setPreferredSize(new Dimension(90, 26));
        startTileField.setMaximumSize(new Dimension(110, 26));

        JPanel startRow = new JPanel();
        startRow.setLayout(new BoxLayout(startRow, BoxLayout.X_AXIS));
        startRow.setOpaque(false);
        startRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        startRow.add(pasteStartButton);
        startRow.add(Box.createHorizontalStrut(6));
        startRow.add(startTileField);
        startRow.add(Box.createHorizontalGlue());

        JPanel resetRow = new JPanel();
        resetRow.setLayout(new BoxLayout(resetRow, BoxLayout.X_AXIS));
        resetRow.setOpaque(false);
        resetRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        resetRow.add(resetStartButton);
        resetRow.add(Box.createHorizontalGlue());

        JPanel centerPanel = new JPanel();
        centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));
        centerPanel.setOpaque(false);
        centerPanel.add(spawnImage);
        centerPanel.add(Box.createVerticalStrut(6));
        centerPanel.add(startRow);
        centerPanel.add(Box.createVerticalStrut(4));
        centerPanel.add(resetRow);
        add(centerPanel, BorderLayout.CENTER);

        JPanel legendContainer = new JPanel();
        legendContainer.setLayout(new BoxLayout(legendContainer, BoxLayout.Y_AXIS));
        legendContainer.setOpaque(false);
        legendContainer.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));

        legendArea.setEditable(false);
        legendArea.setFocusable(false);
        legendArea.setOpaque(false);
        legendArea.setLineWrap(true);
        legendArea.setWrapStyleWord(true);
        legendArea.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        legendArea.setText(
                "The 9 characters of the code represents the 9 spawn positions as they appear in reading order, " +
                        "and what spawns on each one for the scouted wave:"
        );

        legendContainer.add(legendArea);
        legendContainer.add(Box.createVerticalStrut(6));
        legendContainer.add(makeLegendRow(magerSwatch, "M = Mager"));
        legendContainer.add(makeLegendRow(rangerSwatch, "R = Ranger"));
        legendContainer.add(makeLegendRow(meleeSwatch, "X = Melee"));
        legendContainer.add(makeLegendRow(blobSwatch, "B = Blob"));
        legendContainer.add(makeLegendRow(batSwatch, "Y = Bat"));

        add(legendContainer, BorderLayout.SOUTH);
        setSouthUp(true);
    }

    public void setCode(String code)
    {
        if (code == null || code.isEmpty())
        {
            return;
        }
        codeField.setText(code);
    }

    public void setWaveNumber(int waveNumber)
    {
        if (waveNumber > 0)
        {
            waveLabel.setText("Wave " + waveNumber);
        }
        else
        {
            waveLabel.setText("Wave ?");
        }
    }

    public void setSpawns(List<InfernoSpawnImage.Spawn> spawns)
    {
        spawnImage.setSpawns(spawns);
    }

    public void setLegendColors(Color bat, Color blob, Color melee, Color ranger, Color mager)
    {
        batSwatch.setColor(bat);
        blobSwatch.setColor(blob);
        meleeSwatch.setColor(melee);
        rangerSwatch.setColor(ranger);
        magerSwatch.setColor(mager);
    }

    public void setStartTile(int x, int y, Color color)
    {
        spawnImage.setStartTile(x, y, color);
    }

    public void clearStartTile()
    {
        spawnImage.clearStartTile();
    }

    public void setStartTileDisplay(String text)
    {
        startTileField.setText(text == null ? "" : text);
    }

    public void setStartTileActions(Runnable pasteAction, Runnable resetAction)
    {
        setButtonAction(pasteStartButton, pasteAction);
        setButtonAction(resetStartButton, resetAction);
    }

    public void setSouthUp(boolean southUp)
    {
        this.southUp = southUp;
        spawnImage.setSouthUp(southUp);
        compassToggle.setSouthUp(southUp);
    }

    private static void copyText(String text)
    {
        if (text == null || text.isEmpty())
        {
            return;
        }
        String out = text;
        if (out.startsWith("[") && out.endsWith("]") && out.length() >= 2)
        {
            out = out.substring(1, out.length() - 1);
        }
        try
        {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(out), null);
        }
        catch (Exception ignored)
        {
            // User can still select and Ctrl+C.
        }
    }

    private JPanel makeLegendRow(ColorSwatch swatch, String text)
    {
        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.setOpaque(false);

        JLabel label = new JLabel(text);
        label.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));

        row.add(swatch, BorderLayout.WEST);
        row.add(label, BorderLayout.CENTER);
        return row;
    }

    private static void setButtonAction(JButton button, Runnable action)
    {
        for (ActionListener listener : button.getActionListeners())
        {
            button.removeActionListener(listener);
        }
        if (action != null)
        {
            button.addActionListener(e -> action.run());
        }
    }

    private static final class ColorSwatch extends JPanel
    {
        private ColorSwatch()
        {
            setPreferredSize(new Dimension(12, 12));
            setMinimumSize(new Dimension(12, 12));
            setMaximumSize(new Dimension(12, 12));
            setBorder(BorderFactory.createLineBorder(Color.DARK_GRAY));
            setOpaque(true);
        }

        private void setColor(Color color)
        {
            if (color != null)
            {
                setBackground(color);
            }
        }
    }

    private final class CompassToggle extends JPanel
    {
        private boolean southUp = true;

        private CompassToggle()
        {
            setPreferredSize(new Dimension(26, 26));
            setMinimumSize(new Dimension(26, 26));
            setMaximumSize(new Dimension(26, 26));
            setOpaque(false);
            setToolTipText("Toggle map orientation");
            setCursor(new Cursor(Cursor.HAND_CURSOR));
            addMouseListener(new java.awt.event.MouseAdapter()
            {
                @Override
                public void mouseClicked(java.awt.event.MouseEvent e)
                {
                    InfernoScouterPanel.this.setSouthUp(!southUp);
                }
            });
        }

        private void setSouthUp(boolean southUp)
        {
            this.southUp = southUp;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g)
        {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            try
            {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int size = Math.min(getWidth(), getHeight()) - 2;
                int x = (getWidth() - size) / 2;
                int y = (getHeight() - size) / 2;

                g2.setColor(new Color(40, 40, 40));
                g2.fillOval(x, y, size, size);
                g2.setColor(new Color(120, 120, 120));
                g2.drawOval(x, y, size, size);

                String letter = southUp ? "S" : "N";
                Font font = g2.getFont().deriveFont(Font.BOLD, 12f);
                g2.setFont(font);
                FontMetrics fm = g2.getFontMetrics(font);
                int textWidth = fm.stringWidth(letter);
                int textX = x + (size - textWidth) / 2;
                int textY = y + (size + fm.getAscent() - fm.getDescent()) / 2;
                g2.setColor(Color.WHITE);
                g2.drawString(letter, textX, textY);
            }
            finally
            {
                g2.dispose();
            }
        }
    }
}
