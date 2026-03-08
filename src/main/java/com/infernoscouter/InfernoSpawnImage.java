package com.infernoscouter;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JComponent;
import net.runelite.client.util.ImageUtil;

public class InfernoSpawnImage extends JComponent
{
    private static final int BASE_WIDTH_TILES = 29;
    private static final int BASE_HEIGHT_TILES = 30;
    private static final Color GRID_COLOR = new Color(255, 255, 255, 40);
    private static final Color FALLBACK_BG = new Color(30, 30, 30);

    private final BufferedImage baseImage;
    private final List<Spawn> spawns = new ArrayList<>();
    private boolean southUp = true;

    public InfernoSpawnImage()
    {
        baseImage = ImageUtil.loadImageResource(getClass(), "/inferno-base.png");
        setOpaque(false);
    }

    public void setSpawns(List<Spawn> newSpawns)
    {
        spawns.clear();
        if (newSpawns != null)
        {
            spawns.addAll(newSpawns);
        }
        repaint();
    }

    public void setSouthUp(boolean southUp)
    {
        if (this.southUp == southUp)
        {
            return;
        }
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
            int width = getWidth();
            int height = getHeight();
            if (width <= 0 || height <= 0)
            {
                return;
            }

            int tileSize = Math.max(1, Math.min(width / BASE_WIDTH_TILES, height / BASE_HEIGHT_TILES));
            int imageWidth = BASE_WIDTH_TILES * tileSize;
            int imageHeight = BASE_HEIGHT_TILES * tileSize;
            int xOffset = (width - imageWidth) / 2;
            int yOffset = (height - imageHeight) / 2;

            if (baseImage != null)
            {
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                if (southUp)
                {
                    g2.drawImage(baseImage, xOffset + imageWidth, yOffset + imageHeight, -imageWidth, -imageHeight, null);
                }
                else
                {
                    g2.drawImage(baseImage, xOffset, yOffset, imageWidth, imageHeight, null);
                }
            }
            else
            {
                g2.setColor(FALLBACK_BG);
                g2.fillRect(xOffset, yOffset, imageWidth, imageHeight);
            }

            g2.setColor(GRID_COLOR);
            for (int x = 0; x <= BASE_WIDTH_TILES; x++)
            {
                int px = xOffset + (southUp ? (BASE_WIDTH_TILES - x) * tileSize : x * tileSize);
                g2.drawLine(px, yOffset, px, yOffset + imageHeight);
            }
            for (int y = 0; y <= BASE_HEIGHT_TILES; y++)
            {
                int py = yOffset + (southUp ? (BASE_HEIGHT_TILES - y) * tileSize : y * tileSize);
                g2.drawLine(xOffset, py, xOffset + imageWidth, py);
            }

            for (Spawn spawn : spawns)
            {
                int drawX = spawn.x;
                int drawY = spawn.y - (spawn.size - 1);
                if (southUp)
                {
                    drawX = BASE_WIDTH_TILES - drawX - spawn.size;
                    drawY = BASE_HEIGHT_TILES - drawY - spawn.size;
                }

                int px = xOffset + drawX * tileSize;
                int py = yOffset + drawY * tileSize;
                int sizePx = spawn.size * tileSize;

                g2.setColor(spawn.color);
                g2.fillRect(px, py, sizePx, sizePx);

                g2.setColor(darken(spawn.color, 0.7f));
                g2.drawRect(px, py, Math.max(1, sizePx - 1), Math.max(1, sizePx - 1));

                String letter = String.valueOf(spawn.letter);
                int fontSize = Math.max(10, (sizePx * 3) / 5);
                Font font = g2.getFont().deriveFont(Font.BOLD, (float) fontSize);
                g2.setFont(font);
                FontMetrics fm = g2.getFontMetrics(font);
                int textWidth = fm.stringWidth(letter);
                int textX = px + (sizePx - textWidth) / 2;
                int textY = py + (sizePx + fm.getAscent() - fm.getDescent()) / 2;

                g2.setColor(textColorFor(spawn.color));
                g2.drawString(letter, textX, textY);
            }
        }
        finally
        {
            g2.dispose();
        }
    }

    private static Color textColorFor(Color color)
    {
        double lum = 0.2126 * color.getRed()
                + 0.7152 * color.getGreen()
                + 0.0722 * color.getBlue();
        return lum < 140 ? Color.WHITE : Color.BLACK;
    }

    private static Color darken(Color color, float factor)
    {
        int r = Math.max(0, Math.min(255, Math.round(color.getRed() * factor)));
        int g = Math.max(0, Math.min(255, Math.round(color.getGreen() * factor)));
        int b = Math.max(0, Math.min(255, Math.round(color.getBlue() * factor)));
        return new Color(r, g, b, color.getAlpha());
    }

    public static final class Spawn
    {
        private final int x;
        private final int y;
        private final int size;
        private final Color color;
        private final char letter;

        public Spawn(int x, int y, int size, Color color, char letter)
        {
            this.x = x;
            this.y = y;
            this.size = size;
            this.color = color;
            this.letter = letter;
        }
    }
}
