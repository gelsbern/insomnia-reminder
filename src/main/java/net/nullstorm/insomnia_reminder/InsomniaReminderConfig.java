package net.nullstorm.insomnia_reminder;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Simple Gson-backed config for Insomnia Reminder.
 * Unknown fields in older config files are ignored by Gson, so upgrades are safe.
 */
public class InsomniaReminderConfig {
    private static final String FILE_NAME = "insomnia_reminder.json";
    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(MessageDisplayMode.class, (JsonDeserializer<MessageDisplayMode>) (JsonElement json, java.lang.reflect.Type typeOfT, com.google.gson.JsonDeserializationContext context) -> {
                try {
                    String s = json != null ? json.getAsString() : "";
                    if ("TITLE".equalsIgnoreCase(s)) return MessageDisplayMode.SCREEN; // backward compat
                    return MessageDisplayMode.valueOf(s.toUpperCase());
                } catch (Exception e) {
                    return MessageDisplayMode.SCREEN;
                }
            })
            .setPrettyPrinting()
            .create();

    // ---- Core toggles ----
    public boolean enabled = true;
    public boolean morningEnabled = true;
    public boolean nightEnabled = true;

    /** Sound volume for rooster/wolf (0-100). */
    public int volumePercent = 100;

    /** If true, the night reminder triggers even when the player is not yet phantom-susceptible. */
    public boolean nightAlwaysPlays = false;

    // Where to display reminder text when it triggers.
    public MessageDisplayMode messageDisplayMode = MessageDisplayMode.SCREEN;

    // ---- Custom messages + weighting ----
    public boolean customMessagesEnabled = false;
    public boolean includeDefaultMessagesWhenCustomPresent = true;

    public String customMorning1 = ""; public int customMorning1Weight = 10;
    public String customMorning2 = ""; public int customMorning2Weight = 10;
    public String customMorning3 = ""; public int customMorning3Weight = 10;
    public String customMorning4 = ""; public int customMorning4Weight = 10;
    public String customMorning5 = ""; public int customMorning5Weight = 10;

    public String customNight1 = ""; public int customNight1Weight = 10;
    public String customNight2 = ""; public int customNight2Weight = 10;
    public String customNight3 = ""; public int customNight3Weight = 10;
    public String customNight4 = ""; public int customNight4Weight = 10;
    public String customNight5 = ""; public int customNight5Weight = 10;

    // ---- Ultra-rare funnies ----
    public boolean ultraRareMessages = true;
    public int ultraRareChancePercent = 1;

    public enum MessageDisplayMode {
        CHAT,
        SCREEN,
        BOTH
    }

    public static InsomniaReminderConfig load(Path configDir) {
        try {
            Files.createDirectories(configDir);
            Path p = configDir.resolve(FILE_NAME);
            if (!Files.exists(p)) {
                InsomniaReminderConfig cfg = new InsomniaReminderConfig();
                cfg.save(configDir);
                return cfg;
            }
            String json = Files.readString(p, StandardCharsets.UTF_8);
            InsomniaReminderConfig cfg = GSON.fromJson(json, InsomniaReminderConfig.class);
            if (cfg == null) cfg = new InsomniaReminderConfig();
            cfg.sanitize();
            return cfg;
        } catch (Exception ignored) {
            // If config is malformed, fall back to defaults.
            return new InsomniaReminderConfig();
        }
    }

    public void save(Path configDir) {
        try {
            Files.createDirectories(configDir);
            sanitize();
            Path p = configDir.resolve(FILE_NAME);
            Files.writeString(p, GSON.toJson(this), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }

    private void sanitize() {
        ultraRareChancePercent = clampInt(ultraRareChancePercent, 0, 100);

        volumePercent = clampInt(volumePercent, 0, 100);

        customMorning1Weight = clampInt(customMorning1Weight, 0, 100);
        customMorning2Weight = clampInt(customMorning2Weight, 0, 100);
        customMorning3Weight = clampInt(customMorning3Weight, 0, 100);
        customMorning4Weight = clampInt(customMorning4Weight, 0, 100);
        customMorning5Weight = clampInt(customMorning5Weight, 0, 100);

        customNight1Weight = clampInt(customNight1Weight, 0, 100);
        customNight2Weight = clampInt(customNight2Weight, 0, 100);
        customNight3Weight = clampInt(customNight3Weight, 0, 100);
        customNight4Weight = clampInt(customNight4Weight, 0, 100);
        customNight5Weight = clampInt(customNight5Weight, 0, 100);

        if (messageDisplayMode == null) messageDisplayMode = MessageDisplayMode.BOTH;
    }

    private static int clampInt(int v, int min, int max) {
        return Math.min(max, Math.max(min, v));
    }
}
