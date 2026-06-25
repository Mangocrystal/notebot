package com.example.notebot.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Конфиг мода. Хранится в {@code config/notebot/notebot.json}.
 *
 * <p>Структура:</p>
 * <pre>
 * {
 *   "midiFile": "song.mid",                 // MIDI-файл (относительно config/notebot/)
 *   "blocks":   { "C": [x,y,z], ... },      // координаты нотных блоков по именам нот
 *   "noteMap":  { "60": "C", ... },         // маппинг MIDI-номера (или имени) → имя блока
 *   "clickDelayMs": 80                      // задержка между ЛКМ и следующей нотой (мс)
 * }
 * </pre>
 */
public final class NoteBotConfig {
    public static final String CONFIG_DIR  = "notebot";
    public static final String CONFIG_FILE = "notebot.json";

    /** MIDI-файл относительно {@code config/notebot/}. */
    public String midiFile = "song.mid";

    /** Имя ноты → координаты блока. Допустимо любое строковое имя. */
    public Map<String, int[]> blocks = new LinkedHashMap<>();

    /** MIDI-номер (как строка) или имя ноты → имя блока, на котором её играть. */
    public Map<String, String> noteMap = new LinkedHashMap<>();

    /** Задержка после клика перед поворотом к следующей ноте (мс). */
    public int clickDelayMs = 80;

    /** Максимальная дистанция до блока, иначе нота пропускается. */
    public double maxReach = 4.5;

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    private static final Type TYPE = new TypeToken<NoteBotConfig>() {}.getType();

    public static Path configDir() {
        return FabricLoader.getInstance().getConfigDir().resolve(CONFIG_DIR);
    }

    public static Path configFile() {
        return configDir().resolve(CONFIG_FILE);
    }

    /** Загрузить конфиг с диска или создать дефолтный. */
    public static NoteBotConfig load() {
        try {
            Files.createDirectories(configDir());
            Path file = configFile();
            if (Files.exists(file)) {
                try (var reader = Files.newBufferedReader(file)) {
                    NoteBotConfig cfg = GSON.fromJson(reader, TYPE);
                    if (cfg != null) {
                        return cfg;
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("[notebot] failed to read config: " + e.getMessage());
        }
        NoteBotConfig def = createDefault();
        def.save();
        return def;
    }

    /** Сохранить конфиг на диск. */
    public void save() {
        try {
            Files.createDirectories(configDir());
            try (var writer = Files.newBufferedWriter(configFile())) {
                GSON.toJson(this, TYPE, writer);
            }
        } catch (IOException e) {
            System.err.println("[notebot] failed to save config: " + e.getMessage());
        }
    }

    /** Полный путь до MIDI-файла. */
    public Path midiPath() {
        return configDir().resolve(midiFile);
    }

    /** Дефолтный конфиг: 12 нот хроматической гаммы (C..B), MIDI-номера октавы 4. */
    public static NoteBotConfig createDefault() {
        NoteBotConfig cfg = new NoteBotConfig();

        // 12 нотных блоков в ряд. Поменяй координаты под свою постройку.
        cfg.blocks.put("C",  new int[]{0, 64, 0});
        cfg.blocks.put("C#", new int[]{1, 64, 0});
        cfg.blocks.put("D",  new int[]{2, 64, 0});
        cfg.blocks.put("D#", new int[]{3, 64, 0});
        cfg.blocks.put("E",  new int[]{4, 64, 0});
        cfg.blocks.put("F",  new int[]{5, 64, 0});
        cfg.blocks.put("F#", new int[]{6, 64, 0});
        cfg.blocks.put("G",  new int[]{7, 64, 0});
        cfg.blocks.put("G#", new int[]{8, 64, 0});
        cfg.blocks.put("A",  new int[]{9, 64, 0});
        cfg.blocks.put("A#", new int[]{10, 64, 0});
        cfg.blocks.put("B",  new int[]{11, 64, 0});

        // Прямой маппинг MIDI → ноты. 4-я октава = C4 = 60.
        cfg.noteMap.put("60", "C");
        cfg.noteMap.put("61", "C#");
        cfg.noteMap.put("62", "D");
        cfg.noteMap.put("63", "D#");
        cfg.noteMap.put("64", "E");
        cfg.noteMap.put("65", "F");
        cfg.noteMap.put("66", "F#");
        cfg.noteMap.put("67", "G");
        cfg.noteMap.put("68", "G#");
        cfg.noteMap.put("69", "A");
        cfg.noteMap.put("70", "A#");
        cfg.noteMap.put("71", "B");

        cfg.clickDelayMs = 80;
        cfg.maxReach = 4.5;
        cfg.midiFile = "song.mid";

        return cfg;
    }
}