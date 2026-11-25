package com.ecocraft2.gas;

import java.util.function.Supplier;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidType;

/**
 * Shared metadata record describing a registered EcoCraft 2 fluid for cross-version client model /
 * extension registration.
 */
public record Ecc2FluidSpec(
        String path,
        String stillTexturePath,
        String flowTexturePath,
        int density,
        int viscosity,
        int temperature,
        int lightLevel,
        int tintArgb,
        Supplier<? extends FluidType> fluidType,
        Supplier<? extends Fluid> sourceFluid,
        Supplier<? extends Fluid> flowingFluid) {}
