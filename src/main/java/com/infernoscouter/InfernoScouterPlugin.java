package com.infernoscouter;

import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.NPC;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.NpcSpawned;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;

@PluginDescriptor(
        name = "Inferno Scouter",
        description = "Scouts Inferno wave spawns and outputs a 9-tile code.",
        tags = {"inferno", "scout", "waves"}
)
public class InfernoScouterPlugin extends Plugin
{
    // TEMP: debugging — remove later
    private static final boolean DEBUG_COPY_TO_CLIPBOARD = false;

    @Inject private Client client;
    @Inject private ClientToolbar clientToolbar;

    private InfernoScouterPanel panel;
    private NavigationButton navButton;

    // Inferno region gate
    private static final int INFERNO_REGION_ID = 9043;

    // Only the 5 Inferno enemies you care about
    private static final Set<Integer> ALLOWED_NPC_IDS = Set.of(
            7692, // Jal-MejRah (bat)
            7693, // Jal-Ak (blob)
            7697, // Jal-ImKot (melee)
            7698, // Jal-Xil (ranger)
            7702, // Jal-Xil (alt)
            7699, // Jal-Zek (mager)
            7703  // Jal-Zek (alt)
    );

    // ===== Canonical 9-tile SHAPE (from one session) =====
    // We do NOT assume these absolute coords are stable.
    // We only use them as a reference shape for calibration (translation + rotation).
    private static final List<P> CANON = buildCanonReadingOrder();
    private static final Set<Long> CANON_SET = new HashSet<>();
    private static final long[] CANON_KEYS; // in reading order
    static
    {
        for (P p : CANON)
        {
            CANON_SET.add(key(p.x, p.y));
        }
        CANON_KEYS = new long[CANON.size()];
        for (int i = 0; i < CANON.size(); i++)
        {
            CANON_KEYS[i] = key(CANON.get(i).x, CANON.get(i).y);
        }
    }

    private static List<P> buildCanonReadingOrder()
    {
        List<P> pts = new ArrayList<>();
        pts.add(new P(12050, 1641));
        pts.add(new P(12071, 1641));
        pts.add(new P(12052, 1635));
        pts.add(new P(12072, 1634));
        pts.add(new P(12065, 1629));
        pts.add(new P(12054, 1623));
        pts.add(new P(12072, 1621));
        pts.add(new P(12050, 1618));
        pts.add(new P(12064, 1618));

        // Reading order: north->south (y desc), west->east (x asc)
        pts.sort((a, b) ->
        {
            if (a.y != b.y) return Integer.compare(b.y, a.y);
            return Integer.compare(a.x, b.x);
        });
        return pts;
    }

    private static final class P
    {
        final int x, y;
        P(int x, int y) { this.x = x; this.y = y; }
    }

    private enum Rot
    {
        R0, R90, R180, R270;

        P rot(P p)
        {
            switch (this)
            {
                case R0:   return new P(p.x, p.y);
                case R90:  return new P(p.y, -p.x);
                case R180: return new P(-p.x, -p.y);
                case R270: return new P(-p.y, p.x);
                default:   return new P(p.x, p.y);
            }
        }

        P inv(int x, int y)
        {
            switch (this)
            {
                case R0:   return new P(x, y);
                case R90:  return new P(-y, x);
                case R180: return new P(-x, -y);
                case R270: return new P(y, -x);
                default:   return new P(x, y);
            }
        }
    }

    private static final class Cand
    {
        final Rot r;
        final int dx, dy; // observed = r(canon) + (dx,dy)
        Cand(Rot r, int dx, int dy) { this.r = r; this.dx = dx; this.dy = dy; }
    }

    // ===== Runtime state =====
    private String lastCode = "[?????????]";
    private boolean calibrated = false;

    private final Set<Long> seenPoints = new HashSet<>();
    private final List<Cand> candidates = new ArrayList<>();

    // batch capture (same game tick)
    private int batchTick = -1;
    private int quietClientTicks = 0;
    private boolean batchOpen = false;
    private final List<Snap> batch = new ArrayList<>();

    private static final class Snap
    {
        final String name;
        final int id;
        final int x, y, plane;
        final char letter;

        Snap(String name, int id, int x, int y, int plane, char letter)
        {
            this.name = name;
            this.id = id;
            this.x = x;
            this.y = y;
            this.plane = plane;
            this.letter = letter;
        }
    }

    @Override
    protected void startUp()
    {
        panel = new InfernoScouterPanel();
        panel.setCode(lastCode);
        panel.setCalibrated(calibrated);

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
    }

    @Override
    protected void shutDown()
    {
        if (navButton != null)
        {
            clientToolbar.removeNavigation(navButton);
            navButton = null;
        }
        panel = null;
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged e)
    {
        // IMPORTANT: do NOT clear the displayed code/status on logout
        // (scouting requires logging out right after the wave spawns)

        // If we were mid-batch and the user logs out, finalize now.
        if (batchOpen && e.getGameState() != GameState.LOGGED_IN)
        {
            finalizeBatch("logout");
            clearBatch();
        }

        // If we logged in, reset calibration because instance coords can change.
        // (but keep lastCode visible)
        if (e.getGameState() == GameState.LOGGED_IN)
        {
            resetCalibration();
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

        WorldPoint wp = npc.getWorldLocation();
        if (wp == null)
        {
            return;
        }

        int tick = client.getTickCount();

        if (!batchOpen || tick != batchTick)
        {
            // start new batch (new wave spawn tick)
            batchOpen = true;
            batchTick = tick;
            batch.clear();
            quietClientTicks = 0;
        }

        quietClientTicks = 0;

        String name = npc.getName() == null ? "npc" : npc.getName();
        batch.add(new Snap(name, npc.getId(), wp.getX(), wp.getY(), wp.getPlane(), letterFor(npc)));
    }

    @Subscribe
    public void onClientTick(ClientTick tick)
    {
        if (!batchOpen)
        {
            return;
        }

        // If game tick advanced, finalize (fallback)
        int now = client.getTickCount();
        if (now > batchTick)
        {
            finalizeBatch("tick-advanced");
            clearBatch();
            return;
        }

        // Same game tick: finalize after a couple quiet client ticks
        quietClientTicks++;
        if (quietClientTicks >= 2)
        {
            finalizeBatch("quiet-clientticks");
            clearBatch();
        }
    }

    private void finalizeBatch(String reason)
    {
        if (batch.isEmpty())
        {
            return;
        }

        // Feed observed points into calibration
        for (Snap s : batch)
        {
            learnPoint(s.x, s.y);
        }

        calibrated = (candidates.size() == 1);
        if (panel != null)
        {
            panel.setCalibrated(calibrated);
        }

        // IMPORTANT CHANGE:
        // Always overwrite the code on each captured wave.
        // If not calibrated yet, show [?????????] so you can tell it captured a new wave.
        final String codeOut;
        if (calibrated)
        {
            codeOut = buildCode(batch, candidates.get(0));
        }
        else
        {
            codeOut = "[?????????]";
        }

        lastCode = codeOut;
        if (panel != null)
        {
            panel.setCode(lastCode);
        }

        // TEMP DEBUG: copy to clipboard every finalize
        if (DEBUG_COPY_TO_CLIPBOARD)
        {
            debugCopy(buildDebugString(reason, codeOut));
        }
    }

    private String buildDebugString(String reason, String codeOut)
    {
        StringBuilder sb = new StringBuilder(512);
        sb.append("InfernoScouter DEBUG\n");
        sb.append("finalizeReason=").append(reason).append("\n");
        sb.append("gameTick=").append(batchTick).append("\n");
        sb.append("code=").append(codeOut).append("\n");
        sb.append("calibrated=").append(calibrated).append("\n");
        sb.append("seenPoints=").append(seenPoints.size()).append("\n");
        sb.append("candidates=").append(candidates.size()).append("\n");

        if (!candidates.isEmpty())
        {
            sb.append("candidateList=");
            for (int i = 0; i < candidates.size(); i++)
            {
                Cand c = candidates.get(i);
                sb.append(c.r).append("(").append(c.dx).append(",").append(c.dy).append(")");
                if (i + 1 < candidates.size()) sb.append(" ");
            }
            sb.append("\n");
        }

        sb.append("batch=[");
        for (int i = 0; i < batch.size(); i++)
        {
            Snap s = batch.get(i);
            sb.append(s.name)
                    .append("#").append(s.id)
                    .append("@").append(s.x).append(",").append(s.y).append(",").append(s.plane);
            if (i + 1 < batch.size()) sb.append("; ");
        }
        sb.append("]\n");

        return sb.toString();
    }

    private static void debugCopy(String text)
    {
        try
        {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
        }
        catch (Exception ignored)
        {
        }
    }

    private void resetCalibration()
    {
        seenPoints.clear();
        candidates.clear();
        calibrated = false;
        if (panel != null)
        {
            panel.setCalibrated(false);
        }
    }

    private void learnPoint(int x, int y)
    {
        long k = key(x, y);
        if (!seenPoints.add(k))
        {
            return;
        }

        recomputeCandidates();
        if (candidates.isEmpty())
        {
            // likely instance changed — restart calibration from this point only
            seenPoints.clear();
            seenPoints.add(k);
            recomputeCandidates();
        }
    }

    private void recomputeCandidates()
    {
        candidates.clear();
        if (seenPoints.isEmpty())
        {
            return;
        }

        long first = seenPoints.iterator().next();
        int ox0 = xOf(first);
        int oy0 = yOf(first);

        List<Long> seen = new ArrayList<>(seenPoints);

        for (Rot r : Rot.values())
        {
            for (P p0 : CANON)
            {
                P rp0 = r.rot(p0);
                int dx = ox0 - rp0.x;
                int dy = oy0 - rp0.y;

                Cand cand = new Cand(r, dx, dy);
                if (candValid(cand, seen))
                {
                    candidates.add(cand);
                }
            }
        }
    }

    private boolean candValid(Cand c, List<Long> seen)
    {
        Set<Long> mappedCanon = new HashSet<>();
        for (long k : seen)
        {
            int ox = xOf(k);
            int oy = yOf(k);

            int rx = ox - c.dx;
            int ry = oy - c.dy;
            P canonP = c.r.inv(rx, ry);

            long ck = key(canonP.x, canonP.y);
            if (!CANON_SET.contains(ck))
            {
                return false;
            }
            if (!mappedCanon.add(ck))
            {
                return false;
            }
        }
        return true;
    }

    private String buildCode(List<Snap> wave, Cand c)
    {
        char[] out = new char[9];
        Arrays.fill(out, 'o');

        for (Snap s : wave)
        {
            int rx = s.x - c.dx;
            int ry = s.y - c.dy;
            P canonP = c.r.inv(rx, ry);

            int idx = canonIndex(canonP.x, canonP.y);
            if (idx >= 0 && idx < out.length)
            {
                out[idx] = s.letter;
            }
        }

        return "[" + new String(out) + "]";
    }

    private int canonIndex(int x, int y)
    {
        long k = key(x, y);
        for (int i = 0; i < CANON_KEYS.length; i++)
        {
            if (CANON_KEYS[i] == k)
            {
                return i;
            }
        }
        return -1;
    }

    private void clearBatch()
    {
        batchOpen = false;
        batchTick = -1;
        quietClientTicks = 0;
        batch.clear();
    }

    private boolean isInInferno()
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

    private static char letterFor(NPC npc)
    {
        String name = npc.getName();
        if ("Jal-MejRah".equals(name)) return 'Y';
        if ("Jal-Ak".equals(name))     return 'B';
        if ("Jal-ImKot".equals(name))  return 'X';
        if ("Jal-Xil".equals(name))    return 'R';
        if ("Jal-Zek".equals(name))    return 'M';

        switch (npc.getId())
        {
            case 7692: return 'Y';
            case 7693: return 'B';
            case 7697: return 'X';
            case 7698:
            case 7702: return 'R';
            case 7699:
            case 7703: return 'M';
            default:   return '?';
        }
    }

    private static long key(int x, int y)
    {
        return (((long) x) << 32) ^ (y & 0xffffffffL);
    }
    private static int xOf(long k) { return (int) (k >> 32); }
    private static int yOf(long k) { return (int) k; }
}
