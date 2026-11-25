package com.ecocraft2.mixin;

import com.ecocraft2.bridge.Ecc2PowerConnectable;
import com.industrialage.ia2.gameplay.CableBlock;
import java.util.Map;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Connects IC2NE cables visually and physically to EcoCraft 2 power-connectable blocks. */
@Mixin(value = CableBlock.class, remap = false)
public abstract class CableBlockMixin {

    @Shadow
    @Final
    private static Map<Direction, BooleanProperty> CONNECTIONS;

    @Inject(method = "updateConnection", at = @At("RETURN"), cancellable = true)
    private void ecc2$connectPowerBlock(
            BlockState state,
            Direction side,
            BlockState neighbor,
            CallbackInfoReturnable<BlockState> cir) {
        if (neighbor.getBlock() instanceof Ecc2PowerConnectable connectable
                && connectable.ecc2AcceptsCable(neighbor, side.getOpposite())) {
            cir.setReturnValue(cir.getReturnValue().setValue(CONNECTIONS.get(side), true));
        }
    }
}
