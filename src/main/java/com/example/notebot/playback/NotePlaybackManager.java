package com.example.notebot.playback;

import com.example.notebot.NoteBotMod;
import com.example.notebot.config.NoteBotConfig;
import com.example.notebot.midi.MidiNoteEvent;
import com.example.notebot.midi.MidiParser;
import com.example.notebot.mixin.ClientPlayerEntityAccessor;
import com.example.notebot.mixin.MinecraftClientAccessor;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Планировщик воспроизведения MIDI. Каждый клиентский тик проверяет,
 * какие ноты пора играть, разворачивает камеру к нужному нотному блоку
 * и эмулирует ЛКМ через {@link MinecraftClientAccessor}.
 *
 * <p>Состояния:</p>
 * <ul>
 *     <li>{@code IDLE}    — ничего не делает</li>
 *     <li>{@code PLAYING} — воспроизводит</li>
 *     <li>{@code PAUSED}  — на паузе, время заморожено</li>
 * </ul>
 *
 * <p>Клик происходит максимум раз в тик (≈50 мс) — анти-чит и сервер
 * не любят всплески C2S-пакетов чаще этого. Для очень коротких нот
 * (< clickDelayMs) можно увеличить лимит, но для обычной музыки 50 мс
 * достаточно.</p>
 */
public final class NotePlaybackManager {

    public enum State { IDLE, PLAYING, PAUSED }

    private static final NotePlaybackManager INSTANCE = new NotePlaybackManager();

    public static NotePlaybackManager get() { return INSTANCE; }

    private State state = State.IDLE;
    private List<MidiNoteEvent> schedule = List.of();
    private int nextIndex = 0;
    private long startTimeMs = 0L;
    private long pausedElapsedMs = 0L;
    private long lastClickMs = 0L;

    private NoteBotConfig config;
    private String status = "Idle";

    private NotePlaybackManager() {}

    public State state()       { return state; }
    public String status()     { return status; }
    public int remaining()     { return Math.max(0, schedule.size() - nextIndex); }
    public int total()         { return schedule.size(); }

    /** Перезагрузить расписание из MIDI-файла конфига. */
    public boolean reload(NoteBotConfig config) {
        this.config = config;
        Path midi = config.midiPath();
        List<MidiNoteEvent> events = MidiParser.parseFile(midi);
        schedule = filterMappable(events, config);
        nextIndex = 0;
        state = State.IDLE;
        status = "Loaded " + schedule.size() + " mappable notes from " + midi.getFileName();
        NoteBotMod.log(status);
        return !schedule.isEmpty();
    }

    /** Начать воспроизведение (или продолжить с паузы). */
    public void play(NoteBotConfig config) {
        if (config != null) reload(config);
        if (schedule.isEmpty()) {
            status = "No schedule loaded — check MIDI file and noteMap";
            NoteBotMod.log(status);
            return;
        }
        if (state == State.PAUSED) {
            startTimeMs = System.currentTimeMillis() - pausedElapsedMs;
            state = State.PLAYING;
        } else {
            startTimeMs = System.currentTimeMillis();
            nextIndex = 0;
            lastClickMs = 0L;
            state = State.PLAYING;
        }
        status = "Playing " + remaining() + " notes";
        NoteBotMod.log(status);
    }

    /** Пауза — замораживает таймер. */
    public void pause() {
        if (state != State.PLAYING) return;
        pausedElapsedMs = System.currentTimeMillis() - startTimeMs;
        state = State.PAUSED;
        status = "Paused at " + pausedElapsedMs + " ms";
        NoteBotMod.log(status);
    }

    /** Полная остановка. */
    public void stop() {
        state = State.IDLE;
        nextIndex = 0;
        status = "Stopped";
        NoteBotMod.log(status);
    }

    /** Тик от NoteBotMod. Выполняет максимум один клик в тик. */
    public void onClientTick(MinecraftClient client) {
        if (state != State.PLAYING) return;
        if (client.player == null || client.world == null) return;

        long now = System.currentTimeMillis();
        long elapsed = now - startTimeMs;

        // Защита от слишком частых кликов: минимум clickDelayMs между кликами.
        if (now - lastClickMs < Math.max(20, config.clickDelayMs)) {
            return;
        }

        // Пропустить все ноты, время которых уже наступило; играем ровно одну за тик.
        while (nextIndex < schedule.size()) {
            MidiNoteEvent ev = schedule.get(nextIndex);
            if (ev.timeMs() > elapsed) break;
            if (playOne(client, ev)) {
                nextIndex++;
                lastClickMs = now;
                break;
            }
            // Не смогли сыграть — пропускаем.
            nextIndex++;
        }

        if (nextIndex >= schedule.size()) {
            state = State.IDLE;
            status = "Finished";
            NoteBotMod.log(status);
        }
    }

    /** Сыграть одну ноту. Возвращает true если клик был выполнен. */
    private boolean playOne(MinecraftClient client, MidiNoteEvent ev) {
        // Найти имя блока по MIDI-номеру.
        String key = String.valueOf(ev.midiNote());
        String blockName = config.noteMap.get(key);
        if (blockName == null) {
            // fallback: по имени ноты (без учёта октавы)
            blockName = config.noteMap.get(
                    MidiParser.noteName(ev.midiNote()).toUpperCase(Locale.ROOT));
        }
        if (blockName == null) {
            NoteBotMod.log("Skip unmapped note " + ev.midiNote());
            return false;
        }
        int[] pos = config.blocks.get(blockName);
        if (pos == null || pos.length != 3) {
            NoteBotMod.log("Skip note " + blockName + " — no block coords");
            return false;
        }

        BlockPos target = new BlockPos(pos[0], pos[1], pos[2]);
        World world = client.world;
        if (world.getBlockState(target).getBlock() != Blocks.NOTE_BLOCK) {
            NoteBotMod.log("Skip " + blockName + " — block at " + target
                    + " is not NOTE_BLOCK ("
                    + world.getBlockState(target).getBlock() + ")");
            return false;
        }

        ClientPlayerEntity player = client.player;
        Vec3d eye = player.getEyePos();
        Vec3d blockCenter = Vec3d.ofCenter(target);
        double dist = eye.distanceTo(blockCenter);
        if (dist > config.maxReach) {
            NoteBotMod.log("Skip " + blockName + " — too far ("
                    + String.format("%.2f", dist) + " m)");
            return false;
        }

        // Вычислить yaw/pitch от глаз игрока к центру блока.
        Vec3d diff = blockCenter.subtract(eye);
        double horiz = Math.sqrt(diff.x * diff.x + diff.z * diff.z);
        float targetYaw   = (float) Math.toDegrees(Math.atan2(-diff.x, diff.z));
        float targetPitch = (float) Math.toDegrees(-Math.atan2(diff.y, horiz));

        ClientPlayerEntityAccessor acc = (ClientPlayerEntityAccessor) player;
        acc.notebot$setYaw(targetYaw);
        acc.notebot$setPitch(targetPitch);
        player.setHeadYaw(targetYaw);

        // doAttack сам определит, попадает ли raycast в блок, и выполнит
        // attackBlock с правильным направлением от лица игрока.
        ((MinecraftClientAccessor) client).notebot$doAttack();
        return true;
    }

    /** Оставить только ноты, для которых есть маппинг и координаты. */
    private static List<MidiNoteEvent> filterMappable(List<MidiNoteEvent> events,
                                                      NoteBotConfig config) {
        Map<String, String> map = config.noteMap;
        Map<String, int[]> blocks = config.blocks;
        List<MidiNoteEvent> out = new ArrayList<>(events.size());
        for (MidiNoteEvent e : events) {
            String key = String.valueOf(e.midiNote());
            String name = map.get(key);
            if (name == null) {
                name = map.get(MidiParser.noteName(e.midiNote()).toUpperCase(Locale.ROOT));
            }
            if (name == null) continue;
            int[] pos = blocks.get(name);
            if (pos == null || pos.length != 3) continue;
            out.add(e);
        }
        return out;
    }
}