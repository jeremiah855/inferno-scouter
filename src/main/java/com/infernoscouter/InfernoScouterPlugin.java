package com.infernoscouter;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.inject.Provides;
import java.awt.Color;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.NPC;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.NpcSpawned;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.Text;

@PluginDescriptor(
        name = "Inferno Scouter",
        description = "Scouts Inferno wave spawns and outputs a 9-tile code.",
        tags = {"inferno", "scout", "waves"}
)
public class InfernoScouterPlugin extends Plugin
{
    private static final int INFERNO_REGION_ID = 9043;
    private static final int REGION_X_OFFSET = 17;
    private static final int REGION_Y_OFFSET = 46;
    private static final int GRID_WIDTH = 29;
    private static final int GRID_HEIGHT = 30;
    private static final Pattern WAVE_MESSAGE = Pattern.compile("Wave: (\\d+)");
    private static final Color PLACEHOLDER_COLOR = new Color(210, 210, 210);
    private static final Color START_TILE_COLOR = new Color(0xFF51B4BA, true);
    private static final String START_TILE_LABEL = "Start";
    private static final Gson GSON = new Gson();

    private static final Set<Integer> ALLOWED_NPC_IDS = Set.of(
            7692, // Jal-MejRah (bat)
            7693, // Jal-Ak (blob)
            7697, // Jal-ImKot (melee)
            7698, // Jal-Xil (ranger)
            7702, // Jal-Xil (alt)
            7699, // Jal-Zek (mager)
            7703  // Jal-Zek (alt)
    );

    private static final List<P> REGION_SPAWNS = buildRegionSpawns();
    private static final Map<Long, Integer> REGION_INDEX = buildRegionIndex(REGION_SPAWNS);

    @Inject private Client client;
    @Inject private ClientToolbar clientToolbar;
    @Inject private OverlayManager overlayManager;
    @Inject private InfernoScouterConfig config;
    @Inject private InfernoStartTileOverlay startTileOverlay;

    private InfernoScouterPanel panel;
    private NavigationButton navButton;

    private String lastCode = "[?????????]";
    private int currentWaveNumber = -1;
    private int pendingWaveNumber = -1;
    private int pendingWaveStartTick = -1;
    private StartTile startTile = null;
    private String startTileDisplayText = "";

    private final List<SpawnSnapshot> currentWaveSpawns = new ArrayList<>();

    private int batchTick = -1;
    private int quietClientTicks = 0;
    private boolean batchOpen = false;
    private int batchWaveNumber = -1;
    private final List<SpawnSnapshot> batch = new ArrayList<>();

    @Provides
    InfernoScouterConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(InfernoScouterConfig.class);
    }

    @Override
    protected void startUp()
    {
        panel = new InfernoScouterPanel();
        panel.setCode(lastCode);
        panel.setWaveNumber(currentWaveNumber);
        panel.setSpawns(buildInitialSpawns());
        panel.setStartTileActions(this::handlePasteStartTile, this::handleResetStartTile);
        panel.setStartTileDisplay(startTileDisplayText);
        applyStartTileToPanel();
        panel.setLegendColors(
                config.batColor(),
                config.blobColor(),
                config.meleeColor(),
                config.rangerColor(),
                config.magerColor()
        );

        BufferedImage icon;
        try
        {
            icon = ImageUtil.loadImageResource(getClass(), "icon.png");
            if (icon == null)
            {
                icon = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
            }
        }
        catch (Exception e)
        {
            icon = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        }

        navButton = NavigationButton.builder()
                .tooltip("Inferno Scouter")
                .icon(icon)
                .panel(panel)
                .priority(6)
                .build();

        clientToolbar.addNavigation(navButton);
        overlayManager.add(startTileOverlay);
    }

    @Override
    protected void shutDown()
    {
        if (navButton != null)
        {
            clientToolbar.removeNavigation(navButton);
            navButton = null;
        }
        if (startTileOverlay != null)
        {
            overlayManager.remove(startTileOverlay);
        }
        panel = null;
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged e)
    {
        if (batchOpen && e.getGameState() != GameState.LOGGED_IN)
        {
            finalizeBatch();
            clearBatch();
        }

        if (e.getGameState() != GameState.LOGGED_IN)
        {
            pendingWaveNumber = -1;
            pendingWaveStartTick = -1;
        }
    }

    @Subscribe
    public void onChatMessage(ChatMessage event)
    {
        if (client.getGameState() != GameState.LOGGED_IN)
        {
            return;
        }

        if (event.getType() != ChatMessageType.SPAM && event.getType() != ChatMessageType.GAMEMESSAGE)
        {
            return;
        }

        String message = Text.removeTags(event.getMessage());
        Matcher matcher = WAVE_MESSAGE.matcher(message);
        if (matcher.find())
        {
            pendingWaveNumber = Integer.parseInt(matcher.group(1));
            pendingWaveStartTick = client.getTickCount();
            if (batchOpen && batchTick == pendingWaveStartTick && batchWaveNumber <= 0)
            {
                batchWaveNumber = pendingWaveNumber;
            }
        }
    }

    @Subscribe
    public void onNpcSpawned(NpcSpawned event)
    {
        if (!isInInferno())
        {
            return;
        }

        NPC npc = event.getNpc();
        if (npc == null || !ALLOWED_NPC_IDS.contains(npc.getId()))
        {
            return;
        }

        int tick = client.getTickCount();
        if (pendingWaveStartTick >= 0 && (tick - pendingWaveStartTick) > 2)
        {
            return;
        }

        WorldPoint wp = npc.getWorldLocation();
        if (wp == null)
        {
            return;
        }

        if (!batchOpen || tick != batchTick)
        {
            batchOpen = true;
            batchTick = tick;
            batchWaveNumber = pendingWaveNumber;
            batch.clear();
            quietClientTicks = 0;
        }

        quietClientTicks = 0;

        MobType type = typeFor(npc);
        if (type == null)
        {
            return;
        }

        batch.add(new SpawnSnapshot(type, wp.getRegionX(), wp.getRegionY()));
    }

    @Subscribe
    public void onClientTick(ClientTick tick)
    {
        if (!batchOpen)
        {
            return;
        }

        int now = client.getTickCount();
        if (now > batchTick)
        {
            finalizeBatch();
            clearBatch();
            return;
        }

        quietClientTicks++;
        if (quietClientTicks >= 2)
        {
            finalizeBatch();
            clearBatch();
        }
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event)
    {
        if (!"infernoscout".equals(event.getGroup()))
        {
            return;
        }

        if (panel != null)
        {
            panel.setLegendColors(
                    config.batColor(),
                    config.blobColor(),
                    config.meleeColor(),
                    config.rangerColor(),
                    config.magerColor()
            );
            panel.setSpawns(buildInitialSpawns());
        }
    }

    private void finalizeBatch()
    {
        if (batch.isEmpty())
        {
            return;
        }

        if (batchWaveNumber <= 0)
        {
            return;
        }

        lastCode = buildCode(batch);
        currentWaveSpawns.clear();
        currentWaveSpawns.addAll(batch);

        if (batchWaveNumber > 0)
        {
            currentWaveNumber = batchWaveNumber;
        }

        pendingWaveNumber = -1;
        pendingWaveStartTick = -1;

        updatePanel();
    }

    private void updatePanel()
    {
        if (panel == null)
        {
            return;
        }

        panel.setCode(lastCode);
        panel.setWaveNumber(currentWaveNumber);
        panel.setSpawns(buildInitialSpawns());
        applyStartTileToPanel();
    }

    private void handlePasteStartTile()
    {
        String text = readClipboardText();
        StartTile parsed = parseStartTile(text);
        if (parsed == null)
        {
            startTile = null;
            startTileDisplayText = "X";
        }
        else
        {
            startTile = parsed;
            startTileDisplayText = "(" + parsed.gridX + ", " + parsed.gridY + ")";
        }

        applyStartTileToPanel();
    }

    private void handleResetStartTile()
    {
        startTile = null;
        startTileDisplayText = "";
        applyStartTileToPanel();
    }

    private void applyStartTileToPanel()
    {
        if (panel == null)
        {
            return;
        }

        if (startTile != null)
        {
            panel.setStartTile(startTile.gridX, startTile.gridY, startTile.color);
        }
        else
        {
            panel.clearStartTile();
        }
        panel.setStartTileDisplay(startTileDisplayText);
    }

    private static String readClipboardText()
    {
        try
        {
            Object data = Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.stringFlavor);
            if (data instanceof String)
            {
                return (String) data;
            }
        }
        catch (UnsupportedFlavorException | IOException | IllegalStateException ignored)
        {
            // Clipboard unavailable
        }
        return null;
    }

    private StartTile parseStartTile(String text)
    {
        if (text == null)
        {
            return null;
        }

        String trimmed = text.trim();
        if (trimmed.isEmpty())
        {
            return null;
        }

        TileMarkerData[] markers;
        try
        {
            markers = GSON.fromJson(trimmed, TileMarkerData[].class);
        }
        catch (JsonSyntaxException ex)
        {
            return null;
        }

        if (markers == null || markers.length != 1 || markers[0] == null)
        {
            return null;
        }

        TileMarkerData marker = markers[0];
        if (marker.regionId != INFERNO_REGION_ID)
        {
            return null;
        }

        if (marker.z != 0)
        {
            return null;
        }

        int gridX = marker.regionX - REGION_X_OFFSET;
        int gridY = REGION_Y_OFFSET - marker.regionY;
        if (gridX < 0 || gridX >= GRID_WIDTH || gridY < 0 || gridY >= GRID_HEIGHT)
        {
            return null;
        }

        WorldPoint wp = WorldPoint.fromRegion(marker.regionId, marker.regionX, marker.regionY, marker.z);
        return new StartTile(marker.regionX, marker.regionY, gridX, gridY, wp, START_TILE_COLOR, START_TILE_LABEL);
    }

    private String buildCode(List<SpawnSnapshot> wave)
    {
        char[] out = new char[9];
        Arrays.fill(out, 'o');

        for (SpawnSnapshot s : wave)
        {
            int idx = indexForRegion(s.regionX, s.regionY);
            if (idx >= 0 && idx < out.length)
            {
                out[idx] = letterFor(s.type);
            }
        }

        return "[" + new String(out) + "]";
    }

    private List<InfernoSpawnImage.Spawn> buildRenderSpawns(List<SpawnSnapshot> wave)
    {
        List<InfernoSpawnImage.Spawn> spawns = new ArrayList<>();
        for (SpawnSnapshot s : wave)
        {
            int x = s.regionX - REGION_X_OFFSET;
            int y = REGION_Y_OFFSET - s.regionY;
            if (x < 0 || y < 0)
            {
                continue;
            }

            spawns.add(new InfernoSpawnImage.Spawn(x, y, sizeFor(s.type), colorFor(s.type), letterFor(s.type)));
        }
        return spawns;
    }

    private List<InfernoSpawnImage.Spawn> buildInitialSpawns()
    {
        if (!currentWaveSpawns.isEmpty())
        {
            return buildRenderSpawns(currentWaveSpawns);
        }

        List<InfernoSpawnImage.Spawn> spawns = new ArrayList<>();
        for (int i = 0; i < REGION_SPAWNS.size(); i++)
        {
            P p = REGION_SPAWNS.get(i);
            int x = p.x - REGION_X_OFFSET;
            int y = REGION_Y_OFFSET - p.y;
            if (x < 0 || y < 0)
            {
                continue;
            }

            char number = (char) ('1' + i);
            spawns.add(new InfernoSpawnImage.Spawn(x, y, 1, PLACEHOLDER_COLOR, number));
        }
        return spawns;
    }

    private void clearBatch()
    {
        batchOpen = false;
        batchTick = -1;
        batchWaveNumber = -1;
        quietClientTicks = 0;
        batch.clear();
    }

    boolean isInInferno()
    {
        if (client.getGameState() != GameState.LOGGED_IN)
        {
            return false;
        }
        int[] regions = client.getMapRegions();
        if (regions == null)
        {
            return false;
        }
        for (int r : regions)
        {
            if (r == INFERNO_REGION_ID)
            {
                return true;
            }
        }
        return false;
    }

    StartTile getStartTile()
    {
        return startTile;
    }

    private static int indexForRegion(int x, int y)
    {
        Integer idx = REGION_INDEX.get(key(x, y));
        return idx == null ? -1 : idx;
    }

    private static char letterFor(MobType type)
    {
        switch (type)
        {
            case BAT: return 'Y';
            case BLOB: return 'B';
            case MELEE: return 'X';
            case RANGER: return 'R';
            case MAGER: return 'M';
            default: return '?';
        }
    }

    private static int sizeFor(MobType type)
    {
        switch (type)
        {
            case BAT: return 2;
            case BLOB: return 3;
            case MELEE: return 4;
            case RANGER: return 3;
            case MAGER: return 4;
            default: return 2;
        }
    }

    private Color colorFor(MobType type)
    {
        switch (type)
        {
            case BAT: return config.batColor();
            case BLOB: return config.blobColor();
            case MELEE: return config.meleeColor();
            case RANGER: return config.rangerColor();
            case MAGER: return config.magerColor();
            default: return Color.WHITE;
        }
    }

    private static MobType typeFor(NPC npc)
    {
        String name = npc.getName();
        if ("Jal-MejRah".equals(name)) return MobType.BAT;
        if ("Jal-Ak".equals(name)) return MobType.BLOB;
        if ("Jal-ImKot".equals(name)) return MobType.MELEE;
        if ("Jal-Xil".equals(name)) return MobType.RANGER;
        if ("Jal-Zek".equals(name)) return MobType.MAGER;

        switch (npc.getId())
        {
            case 7692: return MobType.BAT;
            case 7693: return MobType.BLOB;
            case 7697: return MobType.MELEE;
            case 7698:
            case 7702: return MobType.RANGER;
            case 7699:
            case 7703: return MobType.MAGER;
            default: return null;
        }
    }

    private static List<P> buildRegionSpawns()
    {
        List<P> pts = new ArrayList<>();
        pts.add(new P(18, 41));
        pts.add(new P(39, 41));
        pts.add(new P(20, 35));
        pts.add(new P(40, 34));
        pts.add(new P(33, 29));
        pts.add(new P(22, 23));
        pts.add(new P(40, 21));
        pts.add(new P(18, 18));
        pts.add(new P(32, 18));

        pts.sort((a, b) ->
        {
            if (a.y != b.y) return Integer.compare(b.y, a.y);
            return Integer.compare(a.x, b.x);
        });

        return Collections.unmodifiableList(pts);
    }

    private static Map<Long, Integer> buildRegionIndex(List<P> pts)
    {
        Map<Long, Integer> map = new HashMap<>();
        for (int i = 0; i < pts.size(); i++)
        {
            P p = pts.get(i);
            map.put(key(p.x, p.y), i);
        }
        return Collections.unmodifiableMap(map);
    }

    private static final class P
    {
        final int x;
        final int y;
        P(int x, int y)
        {
            this.x = x;
            this.y = y;
        }
    }

    private enum MobType
    {
        BAT,
        BLOB,
        MELEE,
        RANGER,
        MAGER
    }

    private static final class SpawnSnapshot
    {
        final MobType type;
        final int regionX;
        final int regionY;

        SpawnSnapshot(MobType type, int regionX, int regionY)
        {
            this.type = type;
            this.regionX = regionX;
            this.regionY = regionY;
        }
    }

    static final class StartTile
    {
        final int regionX;
        final int regionY;
        final int gridX;
        final int gridY;
        final WorldPoint worldPoint;
        final Color color;
        final String label;

        StartTile(int regionX, int regionY, int gridX, int gridY, WorldPoint worldPoint, Color color, String label)
        {
            this.regionX = regionX;
            this.regionY = regionY;
            this.gridX = gridX;
            this.gridY = gridY;
            this.worldPoint = worldPoint;
            this.color = color;
            this.label = label;
        }
    }

    private static final class TileMarkerData
    {
        int regionId;
        int regionX;
        int regionY;
        int z;
        String color;
        String label;
    }

    private static long key(int x, int y)
    {
        return (((long) x) << 32) ^ (y & 0xffffffffL);
    }
}
