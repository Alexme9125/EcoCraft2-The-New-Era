package com.ecocraft2.gas;

import java.util.function.Consumer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.fluids.FluidType;

/** Forge 1.19.2 {@link FluidType} with custom still/flowing textures and ARGB tint. */
public final class Ecc2FluidType extends FluidType {

    private final ResourceLocation stillTexture;
    private final ResourceLocation flowingTexture;
    private final int tintColor;

    public Ecc2FluidType(
            Properties properties,
            ResourceLocation stillTexture,
            ResourceLocation flowingTexture,
            int tintColor) {
        super(properties);
        this.stillTexture = stillTexture;
        this.flowingTexture = flowingTexture;
        this.tintColor = tintColor;
    }

    @Override
    public void initializeClient(Consumer<IClientFluidTypeExtensions> consumer) {
        consumer.accept(
                new IClientFluidTypeExtensions() {
                    @Override
                    public ResourceLocation getStillTexture() {
                        return stillTexture;
                    }

                    @Override
                    public ResourceLocation getFlowingTexture() {
                        return flowingTexture;
                    }

                    @Override
                    public int getTintColor() {
                        return tintColor;
                    }
                });
    }
}
