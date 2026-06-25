package com.example.notebot.mixin;

import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Invoker к private {@code MinecraftClient.doAttack()},
 * чтобы можно было программно выполнить ЛКМ клиента.
 *
 * <p>В Yarn 1.21+ метод приватный — Invoker даёт прямой вызов
 * без reflection и без обращения к хоткеям.</p>
 */
@Mixin(MinecraftClient.class)
public interface MinecraftClientAccessor {
    @Invoker("doAttack")
    boolean notebot$doAttack();
}