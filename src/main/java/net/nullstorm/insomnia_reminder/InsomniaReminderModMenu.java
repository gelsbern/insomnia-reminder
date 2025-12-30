package net.nullstorm.insomnia_reminder;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.text.Text;

import java.nio.file.Path;

/**
 * ModMenu + Cloth Config screen for Insomnia Reminder.
 * No world/dimension settings: the mod is Overworld-only by design.
 */
public class InsomniaReminderModMenu implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> {
            Path configDir = FabricLoader.getInstance().getConfigDir();
            InsomniaReminderConfig cfg = InsomniaReminderConfig.load(configDir);

            ConfigBuilder builder = ConfigBuilder.create()
                    .setParentScreen(parent)
                    .setTitle(Text.literal("Insomnia Reminder"));

            builder.setSavingRunnable(() -> { cfg.save(configDir); InsomniaReminderClient.onConfigSaved(cfg); });

            ConfigEntryBuilder eb = builder.entryBuilder();

            // -------- General --------
            ConfigCategory general = builder.getOrCreateCategory(Text.literal("General"));

            general.addEntry(eb.startBooleanToggle(Text.literal("Enabled"), cfg.enabled)
                    .setDefaultValue(true)
                    .setSaveConsumer(v -> cfg.enabled = v)
                    .build());

            general.addEntry(eb.startBooleanToggle(Text.literal("Morning message"), cfg.morningEnabled)
                    .setDefaultValue(true)
                    .setSaveConsumer(v -> cfg.morningEnabled = v)
                    .build());

            general.addEntry(eb.startBooleanToggle(Text.literal("Night reminder"), cfg.nightEnabled)
                    .setDefaultValue(true)
                    .setSaveConsumer(v -> cfg.nightEnabled = v)
                    .build());

            general.addEntry(eb.startBooleanToggle(Text.literal("Always play night reminder (ignore insomnia)"), cfg.nightAlwaysPlays)
                    .setDefaultValue(false)
                    .setSaveConsumer(v -> cfg.nightAlwaysPlays = v)
                    .build());

            general.addEntry(eb.startIntSlider(Text.literal("Sound volume (%)"), cfg.volumePercent, 0, 100)
                    .setDefaultValue(100)
                    .setSaveConsumer(v -> cfg.volumePercent = v)
                    .build());

            general.addEntry(eb.startEnumSelector(Text.literal("Message display"), InsomniaReminderConfig.MessageDisplayMode.class, cfg.messageDisplayMode)
                    .setEnumNameProvider(v -> {
                        InsomniaReminderConfig.MessageDisplayMode mode =
                                (InsomniaReminderConfig.MessageDisplayMode) v;

                        return switch (mode) {
                            case CHAT -> Text.literal("Chat");
                            case SCREEN -> Text.literal("Screen");
                            case BOTH -> Text.literal("Both");
                        };
                    })
                    .setTooltip(Text.literal("Where reminders appear:\n" + "• Screen: centered on-screen popup (subtitle-sized)\n" + "• Chat: normal chat message\n" + "• Both: show in screen + chat"))
                    .setSaveConsumer(v -> cfg.messageDisplayMode = v)
                    .build());

            // -------- Custom messages --------
            ConfigCategory custom = builder.getOrCreateCategory(Text.literal("Custom Messages"));

            custom.addEntry(eb.startBooleanToggle(Text.literal("Enable custom messages"), cfg.customMessagesEnabled)
                    .setDefaultValue(false)
                    .setSaveConsumer(v -> cfg.customMessagesEnabled = v)
                    .build());

            custom.addEntry(eb.startBooleanToggle(Text.literal("Include default messages when custom exists"), cfg.includeDefaultMessagesWhenCustomPresent)
                    .setDefaultValue(true)
                    .setSaveConsumer(v -> cfg.includeDefaultMessagesWhenCustomPresent = v)
                    .build());

            // Morning
            addWeighted(custom, eb, "Morning #1", () -> cfg.customMorning1, v -> cfg.customMorning1 = v, () -> cfg.customMorning1Weight, v -> cfg.customMorning1Weight = v);
            addWeighted(custom, eb, "Morning #2", () -> cfg.customMorning2, v -> cfg.customMorning2 = v, () -> cfg.customMorning2Weight, v -> cfg.customMorning2Weight = v);
            addWeighted(custom, eb, "Morning #3", () -> cfg.customMorning3, v -> cfg.customMorning3 = v, () -> cfg.customMorning3Weight, v -> cfg.customMorning3Weight = v);
            addWeighted(custom, eb, "Morning #4", () -> cfg.customMorning4, v -> cfg.customMorning4 = v, () -> cfg.customMorning4Weight, v -> cfg.customMorning4Weight = v);
            addWeighted(custom, eb, "Morning #5", () -> cfg.customMorning5, v -> cfg.customMorning5 = v, () -> cfg.customMorning5Weight, v -> cfg.customMorning5Weight = v);

            // Night
            addWeighted(custom, eb, "Night #1", () -> cfg.customNight1, v -> cfg.customNight1 = v, () -> cfg.customNight1Weight, v -> cfg.customNight1Weight = v);
            addWeighted(custom, eb, "Night #2", () -> cfg.customNight2, v -> cfg.customNight2 = v, () -> cfg.customNight2Weight, v -> cfg.customNight2Weight = v);
            addWeighted(custom, eb, "Night #3", () -> cfg.customNight3, v -> cfg.customNight3 = v, () -> cfg.customNight3Weight, v -> cfg.customNight3Weight = v);
            addWeighted(custom, eb, "Night #4", () -> cfg.customNight4, v -> cfg.customNight4 = v, () -> cfg.customNight4Weight, v -> cfg.customNight4Weight = v);
            addWeighted(custom, eb, "Night #5", () -> cfg.customNight5, v -> cfg.customNight5 = v, () -> cfg.customNight5Weight, v -> cfg.customNight5Weight = v);

            // -------- Ultra rare --------
            ConfigCategory fun = builder.getOrCreateCategory(Text.literal("Fun"));

            fun.addEntry(eb.startBooleanToggle(Text.literal("Ultra-rare messages"), cfg.ultraRareMessages)
                    .setDefaultValue(true)
                    .setSaveConsumer(v -> cfg.ultraRareMessages = v)
                    .build());

            fun.addEntry(eb.startIntSlider(Text.literal("Ultra-rare chance (%)"), cfg.ultraRareChancePercent, 0, 100)
                    .setDefaultValue(1)
                    .setSaveConsumer(v -> cfg.ultraRareChancePercent = v)
                    .build());

            return builder.build();
        };
    }

    // --- small helpers ---

    private interface StrGetter { String get(); }
    private interface StrSetter { void set(String v); }
    private interface IntGetter { int get(); }
    private interface IntSetter { void set(int v); }

    private static void addWeighted(ConfigCategory cat,
                                    ConfigEntryBuilder eb,
                                    String label,
                                    StrGetter getMsg,
                                    StrSetter setMsg,
                                    IntGetter getWeight,
                                    IntSetter setWeight) {

        cat.addEntry(eb.startStrField(Text.literal(label + " text"), getMsg.get())
                .setDefaultValue("")
                .setSaveConsumer(setMsg::set)
                .build());

        cat.addEntry(eb.startIntSlider(Text.literal(label + " weight"), getWeight.get(), 0, 100)
                .setDefaultValue(10)
                .setSaveConsumer(setWeight::set)
                .build());
    }
}
