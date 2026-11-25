package com.ecocraft2.mixin;

import com.ecocraft2.bridge.Ecc2ExternalRecipes;
import com.industrialage.ia2.content.ProcessingMachine;
import java.util.function.Predicate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Injects EcoCraft 2's registered processing recipes (ASP compressor recipes for Iridium and
 * Uranium ingots) into IC2NE's {@code ProcessingRecipes#find}.
 */
@Mixin(targets = "com.industrialage.ia2.content.ProcessingRecipes", remap = false)
public final class ProcessingRecipesMixin {

    @Inject(method = "find", at = @At("RETURN"), cancellable = true)
    private void ecc2$findExternalProcessingRecipe(
            String machine,
            String input,
            Predicate<String> matchesTag,
            CallbackInfoReturnable<ProcessingMachine.Recipe> cir) {
        if (cir.getReturnValue() != null) {
            return;
        }
        var entry = Ecc2ExternalRecipes.findProcessing(machine, input, matchesTag);
        if (entry != null) {
            cir.setReturnValue(
                    new ProcessingMachine.Recipe(
                            input, entry.inputCount(), entry.outputId(), entry.outputCount()));
        }
    }
}
