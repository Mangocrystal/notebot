package com.example.notebot.midi;

/** Событие одной ноты из MIDI-файла. */
public record MidiNoteEvent(
        long timeMs,
        int  midiNote,
        int  channel,
        long durationMs
) {}