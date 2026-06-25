package com.example.notebot.mixin;

import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Accessor к полям yaw/pitch у ClientPlayerEntity.
 * В Yarn 1.21+ у Entity нет публичного сеттера pitch, поэтому
 * используем accessor — это самый чистый путь.
 *
 * <p>{@code prevYaw}/{@code prevPitch} не аксессорятся намеренно —
 * эти поля не лежат на самом ClientPlayerEntity, и значения
 * выровняются сами на следующем тике рендера.</p>
 */
@Mixin(ClientPlayerEntity.class)
public interface ClientPlayerEntityAccessor {
    @Accessor("yaw")
    void notebot$setYaw(float yaw);

    @Accessor("pitch")
    void notebot$setPitch(float pitch);
}