package com.example.notebot;

import com.example.notebot.command.NoteBotCommand;
import com.example.notebot.config.NoteBotConfig;
import com.example.notebot.playback.NotePlaybackManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

public final class NoteBotMod implements ClientModInitializer {

    public static final String MOD_ID = "notebot";

    private static NoteBotConfig config;

    @Override
    public void onInitializeClient() {
        log("NoteBot initializing…");
        config = NoteBotConfig.load();
        NotePlaybackManager.get().reload(config);

        // Тики — воспроизведение.
        ClientTickEvents.END_CLIENT_TICK.register(client ->
                NotePlaybackManager.get().onClientTick(client));

        // Регистрация клиентской команды.
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                NoteBotCommand.register(dispatcher));

        log("NoteBot ready. Use /notebot play | stop | pause | resume | reload | status");
    }

    public static NoteBotConfig config() {
        return config;
    }

    public static void reloadConfig() {
        config = NoteBotConfig.load();
        NotePlaybackManager.get().reload(config);
    }

    public static void log(String msg) {
        System.out.println("[notebot] " + msg);
    }
}