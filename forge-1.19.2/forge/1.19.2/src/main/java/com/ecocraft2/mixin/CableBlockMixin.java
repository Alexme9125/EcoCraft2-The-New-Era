package com.ecocraft2.mixin;

import com.ecocraft2.bridge.Ecc2PowerConnectable;
import com.industrialage.ia2.forge.content.CableBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Connects IC2NE Forge 1.19.2 cables visually and physically to EcoCraft 2 power blocks. */
@Mixin(value = CableBlock.class, remap = false)
public abstract class CableBlockMixin {

    @Inject(method = "connects", at = @At("RETURN"), cancellable = true)
    private void ecc2$connectPowerBlock(
            BlockState state, BlockState neighbor, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ()
                && neighbor.getBlock() instanceof Ecc2PowerConnectable connectable
                && connectable.ecc2AcceptsCable(neighbor, null)) {
            cir.setReturnValue(true);
        }
    }
}
