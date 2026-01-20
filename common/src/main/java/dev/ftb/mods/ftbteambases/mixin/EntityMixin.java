package dev.ftb.mods.ftbteambases.mixin;

import dev.ftb.mods.ftbteambases.block.BasesPortalBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class EntityMixin {
    @Inject(method="move", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;setPos(DDD)V", ordinal = 0, shift = At.Shift.AFTER))
    public void onMove(MoverType moverType, Vec3 vec3, CallbackInfo ci) {
        BasesPortalBlock.checkForSpectator((Entity) (Object) this);
    }
}
