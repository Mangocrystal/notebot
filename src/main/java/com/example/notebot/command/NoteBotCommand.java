package com.example.notebot.command;

import com.example.notebot.NoteBotMod;
import com.example.notebot.playback.NotePlaybackManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.text.Text;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

/**
 * Команда /notebot с подкомандами:
 *   play   — начать воспроизведение (с паузы продолжает)
 *   pause  — пауза
 *   resume — продолжить (алиас play, если на паузе)
 *   stop   — остановить и сбросить позицию
 *   reload — перечитать конфиг и MIDI
 *   status — текущее состояние
 */
public final class NoteBotCommand {

    private NoteBotCommand() {}

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(
                literal("notebot")
                        .then(literal("play")
                                .executes(NoteBotCommand::cmdPlay))
                        .then(literal("pause")
                                .executes(NoteBotCommand::cmdPause))
                        .then(literal("resume")
                                .executes(NoteBotCommand::cmdResume))
                        .then(literal("stop")
                                .executes(NoteBotCommand::cmdStop))
                        .then(literal("reload")
                                .executes(NoteBotCommand::cmdReload))
                        .then(literal("status")
                                .executes(NoteBotCommand::cmdStatus))
        );
    }

    private static int cmdPlay(CommandContext<FabricClientCommandSource> ctx) {
        NoteBotMod.reloadConfig();
        NotePlaybackManager.get().play(NoteBotMod.config());
        ctx.getSource().sendFeedback(Text.literal(
                "[notebot] " + NotePlaybackManager.get().status()));
        return 1;
    }

    private static int cmdPause(CommandContext<FabricClientCommandSource> ctx) {
        NotePlaybackManager.get().pause();
        ctx.getSource().sendFeedback(Text.literal(
                "[notebot] " + NotePlaybackManager.get().status()));
        return 1;
    }

    private static int cmdResume(CommandContext<FabricClientCommandSource> ctx) {
        NotePlaybackManager pm = NotePlaybackManager.get();
        if (pm.state() == NotePlaybackManager.State.PAUSED) {
            pm.play(null);
        } else {
            ctx.getSource().sendFeedback(Text.literal("[notebot] nothing to resume"));
            return 0;
        }
        ctx.getSource().sendFeedback(Text.literal(
                "[notebot] " + pm.status()));
        return 1;
    }

    private static int cmdStop(CommandContext<FabricClientCommandSource> ctx) {
        NotePlaybackManager.get().stop();
        ctx.getSource().sendFeedback(Text.literal("[notebot] stopped"));
        return 1;
    }

    private static int cmdReload(CommandContext<FabricClientCommandSource> ctx) {
        NoteBotMod.reloadConfig();
        NotePlaybackManager pm = NotePlaybackManager.get();
        ctx.getSource().sendFeedback(Text.literal(
                "[notebot] reloaded — " + pm.total() + " notes queued"));
        return 1;
    }

    private static int cmdStatus(CommandContext<FabricClientCommandSource> ctx) {
        NotePlaybackManager pm = NotePlaybackManager.get();
        ctx.getSource().sendFeedback(Text.literal(
                "[notebot] state=" + pm.state()
                        + " notes=" + pm.remaining() + "/" + pm.total()
                        + " — " + pm.status()));
        return 1;
    }
}