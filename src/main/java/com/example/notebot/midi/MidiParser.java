package com.example.notebot.midi;

import javax.sound.midi.*;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Парсер MIDI-файла в список {@link MidiNoteEvent}.
 *
 * <p>Корректно учитывает tempo-change события (meta type 0x51),
 * чтобы время нот было точным независимо от смен темпа в треке.</p>
 *
 * <p>Использует стандартный {@code javax.sound.midi}, который входит
 * в Java SE — внешних зависимостей не требуется.</p>
 */
public final class MidiParser {

    private MidiParser() {}

    /** Распарсить файл. Возвращает пустой список при ошибке. */
    public static List<MidiNoteEvent> parseFile(Path file) {
        if (!Files.exists(file)) {
            System.err.println("[notebot] MIDI file not found: " + file);
            return Collections.emptyList();
        }
        try (InputStream in = Files.newInputStream(file)) {
            return parseStream(in);
        } catch (IOException | InvalidMidiDataException e) {
            System.err.println("[notebot] MIDI parse error: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    /** Распарсить поток. */
    public static List<MidiNoteEvent> parseStream(InputStream in)
            throws IOException, InvalidMidiDataException {
        Sequence sequence = MidiSystem.getSequence(in);
        return extractNotes(sequence);
    }

    /**
     * Извлечь все ноты из последовательности, вычислив абсолютное время
     * начала каждой ноты в миллисекундах с учётом tempo change events.
     */
    private static List<MidiNoteEvent> extractNotes(Sequence sequence) {
        List<MidiNoteEvent> events = new ArrayList<>();
        float divisionType = sequence.getDivisionType();
        float ppq;
        if (divisionType == Sequence.PPQ) {
            ppq = sequence.getResolution();
        } else {
            System.err.println("[notebot] unsupported MIDI division type: " + divisionType
                    + " (only PPQ is supported)");
            return events;
        }
        if (ppq <= 0) {
            System.err.println("[notebot] invalid PPQ resolution: " + ppq);
            return events;
        }

        for (Track track : sequence.getTracks()) {
            // Начальный темп — 500000 мкс / quarter (= 120 BPM).
            long microsPerQuarter = 500_000L;
            long absoluteTick = 0;

            // Карта "время в миллисекундах для тика N" строится через темп.
            // Чтобы не пересчитывать сложно, идём по событиям и поддерживаем
            // running counter абсолютного времени в мс.
            long absoluteMs = 0;
            long lastTick = 0;

            for (int i = 0; i < track.size(); i++) {
                MidiEvent ev = track.get(i);
                long tick = ev.getTick();
                long deltaTicks = tick - lastTick;
                if (deltaTicks > 0) {
                    absoluteMs += (deltaTicks * microsPerQuarter) / 1000L / (long) ppq;
                }
                lastTick = tick;
                absoluteTick = tick;

                MidiMessage msg = ev.getMessage();
                if (msg instanceof MetaMessage meta) {
                    if (meta.getType() == 0x51 && meta.getData().length >= 3) {
                        byte[] data = meta.getData();
                        microsPerQuarter =
                                ((long)(data[0] & 0xFF) << 16) |
                                ((long)(data[1] & 0xFF) << 8)  |
                                ((long)(data[2] & 0xFF));
                    }
                    continue;
                }
                if (!(msg instanceof ShortMessage sm)) continue;
                int cmd = sm.getCommand();
                if (cmd != ShortMessage.NOTE_ON) continue;
                int note = sm.getData1();
                int vel  = sm.getData2();
                int ch   = sm.getChannel();
                if (vel == 0) continue; // NOTE_ON с velocity 0 == NOTE_OFF

                // Длительность: ищем следующий NOTE_OFF по тому же каналу и ноте.
                long durationMs = 0;
                long searchMs = absoluteMs;
                long microsPerQuarterSearch = microsPerQuarter;
                long lastTickSearch = tick;
                for (int j = i + 1; j < track.size(); j++) {
                    MidiMessage m2 = track.get(j).getMessage();
                    if (m2 instanceof MetaMessage mm && mm.getType() == 0x51
                            && mm.getData().length >= 3) {
                        byte[] d = mm.getData();
                        microsPerQuarterSearch =
                                ((long)(d[0] & 0xFF) << 16) |
                                ((long)(d[1] & 0xFF) << 8)  |
                                ((long)(d[2] & 0xFF));
                    }
                    if (!(m2 instanceof ShortMessage ss)) continue;
                    if (ss.getCommand() == ShortMessage.NOTE_OFF
                            && ss.getData1() == note
                            && ss.getChannel() == ch) {
                        long dt = track.get(j).getTick() - lastTickSearch;
                        searchMs += (dt * microsPerQuarterSearch) / 1000L / (long) ppq;
                        durationMs = searchMs - absoluteMs;
                        break;
                    }
                    long dt = track.get(j).getTick() - lastTickSearch;
                    searchMs += (dt * microsPerQuarterSearch) / 1000L / (long) ppq;
                    lastTickSearch = track.get(j).getTick();
                }

                events.add(new MidiNoteEvent(absoluteMs, note, ch, durationMs));
            }
        }

        // Сортируем по времени и убираем слишком короткие (<5 мс).
        events.removeIf(e -> e.durationMs() > 0 && e.durationMs() < 5);
        events.sort((a, b) -> Long.compare(a.timeMs(), b.timeMs()));
        return events;
    }

    /** Преобразовать MIDI-номер в имя ноты (C, C#, D, ... B). */
    public static String noteName(int midi) {
        String[] names = {"C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"};
        return names[midi % 12];
    }
}