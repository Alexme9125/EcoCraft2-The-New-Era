package com.ecocraft2.giant;

import net.minecraft.world.item.ItemStack;

/** Result of filling or draining a single fluid container item (cell, bucket, or modded tank). */
public record Ecc2FluidContainerResult(ItemStack resultingItem, String fluidId, int amountMb) {}
