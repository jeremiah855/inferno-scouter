package com.infernoscouter;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.util.Collection;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;
import net.runelite.client.ui.overlay.OverlayUtil;

@Singleton
public class InfernoStartTileOverlay extends Overlay
{
    private final Client client;
    private final InfernoScouterPlugin plugin;

    @Inject
    public InfernoStartTileOverlay(Client client, InfernoScouterPlugin plugin)
    {
        this.client = client;
        this.plugin = plugin;
        setLayer(OverlayLayer.ABOVE_SCENE);
        setPosition(OverlayPosition.DYNAMIC);
        setPriority(OverlayPriority.LOW);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        InfernoScouterPlugin.StartTile tile = plugin.getStartTile();
        if (tile == null || !plugin.isInInferno())
        {
            return null;
        }

        Collection<WorldPoint> candidates = WorldPoint.toLocalInstance(client, tile.worldPoint);
        if (candidates == null || candidates.isEmpty())
        {
            candidates = java.util.List.of(tile.worldPoint);
        }

        Color color = tile.color;
        String label = tile.label;

        for (WorldPoint worldPoint : candidates)
        {
            if (client.getPlane() != worldPoint.getPlane())
            {
                continue;
            }

            LocalPoint localPoint = LocalPoint.fromWorld(client, worldPoint);
            if (localPoint == null)
            {
                continue;
            }

            Polygon poly = Perspective.getCanvasTilePoly(client, localPoint);
            if (poly == null)
            {
                continue;
            }

            Color fill = new Color(0, 0, 0, 50);
            OverlayUtil.renderPolygon(graphics, poly, color, fill, new BasicStroke(2));

            if (label != null && !label.isEmpty())
            {
                Point textLocation = Perspective.getCanvasTextLocation(client, graphics, localPoint, label, 0);
                if (textLocation != null)
                {
                    OverlayUtil.renderTextLocation(graphics, textLocation, label, color);
                }
            }
        }

        return null;
    }
}
