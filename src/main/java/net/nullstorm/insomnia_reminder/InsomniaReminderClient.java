package net.nullstorm.insomnia_reminder;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.stat.Stats;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Insomnia Reminder (client-only):
 * - Night reminder (optional) encourages sleeping to avoid phantoms.
 * - Morning greeting + rooster sound at 06:00, and also when a slept night "skips" time.
 * - Overworld only (no dimension/world config).
 */
public class InsomniaReminderClient implements ClientModInitializer {

    public static InsomniaReminderConfig CONFIG;

    private static InsomniaReminderClient INSTANCE;

    /**
     * Called after the config is saved from the ModMenu screen so changes take effect immediately,
     * and per-day gating is reset in a predictable way for testing.
     */
    public static void onConfigSaved(InsomniaReminderConfig newCfg) {
        if (newCfg == null) return;

        InsomniaReminderConfig old = CONFIG;
        CONFIG = newCfg;

        // If the feature was toggled off -> on, allow it to fire again immediately (no restart needed).
        if (INSTANCE != null && old != null) {
            if (!old.morningEnabled && newCfg.morningEnabled) {
                INSTANCE.lastDayPlayedAM = -1;
            }
            if (!old.nightEnabled && newCfg.nightEnabled) {
                INSTANCE.lastDayPlayedPM = -1;
            }
        }
    }


    // 06:00 is timeOfDay == 0 in modern Minecraft (day starts at 06:00).
    private static final int AM_TICK = 0;

    // Night reminder "window" tick. Keep it late evening so it's helpful.
    // (This value came from the earlier working version; not a gameplay mechanic.)
    private static final int PM_WARN_TICK = 12492;

    // Small window so we don't spam if tick drifts.
    private static final int WINDOW_TICKS = 100;

    // When the night is slept away, client time often jumps forward and the player is ejected
    // around 06:01–06:04. We treat any morning within this grace window as "wake-up morning".
    private static final int SLEEP_WAKE_GRACE_TICKS = 2400; // ~2 minutes

    private static final Random RANDOM = new Random();

    // Default message pools (Overworld only)
    private static final String[] MORNING_OVERWORLD = {
            "Good morning! ☀️ The sun is up, and so are the creepers.",
            "Rise and shine! Another beautiful day to punch trees.",
            "Morning! The villagers are awake and already judging you.",
            "Good morning! Coffee is optional — diamonds are not.",
            "The rooster has spoken. It is officially daytime.",
            "Morning! If you hear hissing, that’s… probably fine."
    };

    private static final String[] NIGHT_OVERWORLD = {
            "Good night 🌙 Sleep now, avoid respawning later.",
            "Nighttime detected. Beds are safer than bravery.",
            "Good night! The monsters have clocked in for their shift.",
            "It’s getting dark… statistically, this is a bad idea.",
            "Time for sleep. Even the Endermen need personal space.",
            "Good night! Don’t let the phantoms win."
    };

    private static final String[] ULTRA_RARE_MORNING = {
            "You actually slept on time. I’m proud of you.",
            "Legend says this player uses beds responsibly.",
            "A rare morning indeed. Screenshot this moment."
    };

    private static final String[] ULTRA_RARE_NIGHT = {
            "Beds. Use them. This is not a suggestion.",
            "It’s late. The phantoms are sharpening their teeth."
    };

    // Per-day gating
    private long lastDayPlayedAM = -1;
    private long lastDayPlayedPM = -1;

    // Track time so we can detect time jumps from sleeping or /time set.
    private int lastTimeOfDaySeen = -1;
    private long lastAbsTimeSeen = -1;

    // Track stat to detect rest reset (sometimes helpful)
    private int lastTimeSinceRest = -1;

    // Small centered HUD text overlay (SCREEN/BOTH mode)

    @Override
    public void onInitializeClient() {
        INSTANCE = this;
        CONFIG = InsomniaReminderConfig.load(FabricLoader.getInstance().getConfigDir());

        ClientTickEvents.END_CLIENT_TICK.register(this::onEndClientTick);
    }

    private void onEndClientTick(MinecraftClient client) {

        if (client == null || client.player == null || client.world == null) return;
        if (!CONFIG.enabled) return;

        // Overworld only
        if (client.world.getRegistryKey() != World.OVERWORLD) return;

        long absTime = client.world.getTimeOfDay();
        int timeOfDay = (int) (client.world.getTimeOfDay() % 24000L);
        long dayIndex = absTime / 24000L;

        // If time moves backwards (e.g. /time set), clear per-day gates so testing doesn't require a restart.
        if (lastAbsTimeSeen != -1 && absTime < lastAbsTimeSeen) {
            lastDayPlayedAM = -1;
            lastDayPlayedPM = -1;
        }


        // Detect TIME_SINCE_REST (0 == just slept) as a backup.
        int timeSinceRest = client.player.getStatHandler()
                .getStat(Stats.CUSTOM.getOrCreateStat(Stats.TIME_SINCE_REST));
        boolean restJustReset = (lastTimeSinceRest != -1 && timeSinceRest < lastTimeSinceRest);
        lastTimeSinceRest = timeSinceRest;

        // Detect a big time jump forward (sleep skipping the night).
        // Normal tick delta is +1; sleep causes a large jump.
        boolean bigForwardJump = false;
        if (lastAbsTimeSeen != -1) {
            long delta = absTime - lastAbsTimeSeen;
            if (delta > 200) bigForwardJump = true;
        }
        lastAbsTimeSeen = absTime;

        boolean jumpedFromNightToMorning = false;
        if (bigForwardJump && lastTimeOfDaySeen != -1) {
            boolean wasNight = lastTimeOfDaySeen >= 12000;
            boolean isMorning = timeOfDay >= AM_TICK && timeOfDay <= AM_TICK + SLEEP_WAKE_GRACE_TICKS;
            jumpedFromNightToMorning = wasNight && isMorning;
			boolean sleptThisTick = jumpedFromNightToMorning || restJustReset;
        if (sleptThisTick) {
        // Force-reset insomnia state; client stat sync can lag behind actual sleep
        timeSinceRest = 0;
        lastTimeSinceRest = 0;
        }
        }
        lastTimeOfDaySeen = timeOfDay;

        // ----- Morning (rooster + greeting) -----
        if (CONFIG.morningEnabled) {
            boolean inExactWindow = inWindow(timeOfDay, AM_TICK, WINDOW_TICKS);
            boolean inWakeGrace = timeOfDay >= AM_TICK && timeOfDay <= AM_TICK + SLEEP_WAKE_GRACE_TICKS;

            // Fire once per day. If time jumps to 06:03, still count for that day.
            if (lastDayPlayedAM != dayIndex) {
                if (inExactWindow) {
                    playMorning(client, dayIndex);
                } else if (inWakeGrace && (jumpedFromNightToMorning || restJustReset)) {
                    playMorning(client, dayIndex);
                }
            }
        }

        // ----- Night reminder (wolf + message) -----
        if (CONFIG.nightEnabled) {
            if (lastDayPlayedPM != dayIndex && inWindow(timeOfDay, PM_WARN_TICK, WINDOW_TICKS)) {
                boolean isInsomniac = timeSinceRest >= 72000; // phantom threshold
                if (CONFIG.nightAlwaysPlays || isInsomniac) {
                    playNight(client, dayIndex);
                }
            }
        }
    }

    private void playMorning(MinecraftClient client, long dayIndex) {
        lastDayPlayedAM = dayIndex;

        // rooster sound
        playSound(client, "rooster"); // see sounds.json mapping
        String msg = chooseMessage(true);
        if (msg != null) displayText(client, msg);
    }

    private void playNight(MinecraftClient client, long dayIndex) {
        lastDayPlayedPM = dayIndex;

        // wolf howl sound
        playSound(client, "wolf");
        String msg = chooseMessage(false);
        if (msg != null) displayText(client, msg);
    }

    private static boolean inWindow(int timeOfDay, int target, int windowTicks) {
        return timeOfDay >= target && timeOfDay <= target + windowTicks;
    }

    private void playSound(MinecraftClient client, String vanillaId) {
        if (client == null || client.player == null) return;

        float vol = Math.max(0f, Math.min(1f, CONFIG.volumePercent / 100f));
        if (vol <= 0f) return;

        Identifier id = Identifier.of("insomnia_reminder", vanillaId);
        SoundEvent ev = SoundEvent.of(id);

        // Use player-relative category
        client.getSoundManager().play(PositionedSoundInstance.master(ev, vol, 1.0f));
    }

    private String chooseMessage(boolean morning) {
        // ultra-rare
        if (CONFIG.ultraRareMessages && CONFIG.ultraRareChancePercent > 0) {
            if (RANDOM.nextInt(100) < CONFIG.ultraRareChancePercent) {
                String[] pool = morning ? ULTRA_RARE_MORNING : ULTRA_RARE_NIGHT;
                return pool[RANDOM.nextInt(pool.length)];
            }
        }

        // weighted custom vs defaults
        List<WeightedMsg> combined = new ArrayList<>();
        for (String s : getDefaultMessagePool(morning)) combined.add(new WeightedMsg(s, 10));

        List<WeightedMsg> custom = getCustomMessages(morning);
        if (CONFIG.customMessagesEnabled && !custom.isEmpty()) {
            if (!CONFIG.includeDefaultMessagesWhenCustomPresent) combined.clear();
            combined.addAll(custom);
        }

        return chooseWeighted(combined);
    }

    private static void displayText(MinecraftClient client, String msg) {
        if (client == null || client.player == null || msg == null || msg.isBlank()) return;

        InsomniaReminderConfig.MessageDisplayMode mode = CONFIG.messageDisplayMode;
        if (mode == null) mode = InsomniaReminderConfig.MessageDisplayMode.BOTH;

        if (mode == InsomniaReminderConfig.MessageDisplayMode.CHAT || mode == InsomniaReminderConfig.MessageDisplayMode.BOTH) {
            client.player.sendMessage(Text.literal(msg), false);
        }

        if (mode == InsomniaReminderConfig.MessageDisplayMode.SCREEN || mode == InsomniaReminderConfig.MessageDisplayMode.BOTH) {
            showTitle(client, msg);
        }
    }

    // Uses the vanilla title system, but automatically fits the text so it doesn't blast off-screen.
    private static void showTitle(MinecraftClient client, String msg) {
        // Subtitle-size center-ish popup:
        // Minecraft titles have a fixed large font; using subtitle gives us a smaller, more readable popup
        // that still appears near the center. We keep the SCREEN line empty and put the whole message
        // into the subtitle, truncating with an ellipsis if needed.
        if (client == null || client.inGameHud == null || client.textRenderer == null) return;
        if (msg == null) return;

        String raw = msg.trim();
        if (raw.isEmpty()) return;

        TextRenderer tr = client.textRenderer;

        int maxWidthPx = (int) (client.getWindow().getScaledWidth() * 0.85);
        if (maxWidthPx < 180) maxWidthPx = 180;

        String fitted = (tr.getWidth(raw) <= maxWidthPx) ? raw : truncateToWidth(tr, raw, maxWidthPx);

        client.inGameHud.setTitle(Text.empty());
        client.inGameHud.setSubtitle(Text.literal(fitted));
        client.inGameHud.setTitleTicks(5, 45, 10); // fadeIn, stay, fadeOut
    }

private static String truncateToWidth(TextRenderer tr, String s, int maxWidthPx) {
        if (s == null) return "";
        String t = s;
        if (tr.getWidth(t) <= maxWidthPx) return t;

        String ell = "…";
        int ellW = tr.getWidth(ell);
        if (ellW >= maxWidthPx) return "";

        int lo = 0, hi = t.length();
        while (lo < hi) {
            int mid = (lo + hi + 1) / 2;
            String sub = t.substring(0, mid);
            if (tr.getWidth(sub) + ellW <= maxWidthPx) lo = mid;
            else hi = mid - 1;
        }
        if (lo <= 0) return "";
        return t.substring(0, lo) + ell;
    }

    private static String appendEllipsisIfNeeded(TextRenderer tr, String s, int maxWidthPx) {
        String ell = "…";
        if (tr.getWidth(s) <= maxWidthPx) return s;
        return truncateToWidth(tr, s, maxWidthPx);
    }


    private static String[] getDefaultMessagePool(boolean morning) {
        return morning ? MORNING_OVERWORLD : NIGHT_OVERWORLD;
    }

    private static List<WeightedMsg> getCustomMessages(boolean morning) {
        List<WeightedMsg> msgs = new ArrayList<>();
        if (morning) {
            addIfNotBlankWeighted(msgs, CONFIG.customMorning1, CONFIG.customMorning1Weight);
            addIfNotBlankWeighted(msgs, CONFIG.customMorning2, CONFIG.customMorning2Weight);
            addIfNotBlankWeighted(msgs, CONFIG.customMorning3, CONFIG.customMorning3Weight);
            addIfNotBlankWeighted(msgs, CONFIG.customMorning4, CONFIG.customMorning4Weight);
            addIfNotBlankWeighted(msgs, CONFIG.customMorning5, CONFIG.customMorning5Weight);
        } else {
            addIfNotBlankWeighted(msgs, CONFIG.customNight1, CONFIG.customNight1Weight);
            addIfNotBlankWeighted(msgs, CONFIG.customNight2, CONFIG.customNight2Weight);
            addIfNotBlankWeighted(msgs, CONFIG.customNight3, CONFIG.customNight3Weight);
            addIfNotBlankWeighted(msgs, CONFIG.customNight4, CONFIG.customNight4Weight);
            addIfNotBlankWeighted(msgs, CONFIG.customNight5, CONFIG.customNight5Weight);
        }
        return msgs;
    }

    private static void addIfNotBlankWeighted(List<WeightedMsg> list, String s, int weight) {
        if (s == null) return;
        String v = s.trim();
        if (v.isEmpty()) return;
        int w = Math.max(0, weight);
        if (w <= 0) return;
        list.add(new WeightedMsg(v, w));
    }

    private static String chooseWeighted(List<WeightedMsg> msgs) {
        if (msgs == null || msgs.isEmpty()) return null;
        int total = 0;
        for (WeightedMsg m : msgs) total += Math.max(0, m.weight);
        if (total <= 0) return null;

        int r = RANDOM.nextInt(total);
        int acc = 0;
        for (WeightedMsg m : msgs) {
            acc += Math.max(0, m.weight);
            if (r < acc) return m.msg;
        }
        return msgs.get(msgs.size() - 1).msg;
    }

    private static class WeightedMsg {
        final String msg;
        final int weight;
        WeightedMsg(String msg, int weight) {
            this.msg = msg;
            this.weight = weight;
        }
    }
}
