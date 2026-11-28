package com.ecocraft2.gas;

import com.ecocraft2.bridge.Ecc2CellHelper;
import com.ecocraft2.registry.Ecc2Registries;
import com.industrialage.ia2.logistics.CellFamily;
import com.industrialage.ia2.logistics.CellItem;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.templates.FluidHandlerItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Full 1000 mB named EcoCraft 2 fluid cell item ({@code water_gas_cell}, {@code natural_gas_cell},
 * {@code culture_medium_cell}, {@code basic_culture_medium_cell}). Biogas reuses {@code
 * ic2:biogas_cell} natively.
 *
 * <p>Attaches a Forge {@code IFluidHandlerItem} capability delegating through {@link Ecc2CellHelper}
 * so emptying automatically converts into {@code ic2:fluid_cell} (or {@code ic2:empty_cell} for
 * {@code basic_culture_medium_cell}) and interacts natively with IC2NE tanks, pipes, and machines.
 */
public final class Ecc2NamedCellItem extends Item {

    private final String fluidTranslationKey;

    public Ecc2NamedCellItem(String fluidTranslationKey) {
        super(Ecc2Registries.tabItemProperties().stacksTo(64));
        this.fluidTranslationKey = fluidTranslationKey;
    }

    @Override
    public ICapabilityProvider initCapabilities(ItemStack stack, @Nullable CompoundTag nbt) {
        return new FluidHandlerItemStack(stack, CellFamily.CAPACITY) {
            @Override
            public @NotNull FluidStack getFluid() {
                return Ecc2CellHelper.readContents(container);
            }

            @Override
            public boolean canFillFluidType(FluidStack fluid) {
                return Ecc2CellHelper.cellAdmits(container, fluid);
            }

            @Override
            protected void setFluid(FluidStack fluid) {
                container = Ecc2CellHelper.withContents(container, fluid);
            }

            @Override
            protected void setContainerToEmpty() {
                setFluid(FluidStack.EMPTY);
            }
        };
    }

    @Override
    public int getMaxStackSize(ItemStack stack) {
        return stack.getTagElement("Fluid") != null && !Ecc2CellHelper.readContents(stack).isEmpty()
                ? 1
                : super.getMaxStackSize(stack);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Player player = ctx.getPlayer();
        Level level = ctx.getLevel();
        if (player == null) {
            return InteractionResult.PASS;
        }
        if (level instanceof ServerLevel serverLevel) {
            return CellItem.interactWithBlock(
                            serverLevel,
                            ctx.getClickedPos(),
                            ctx.getClickedFace(),
                            player,
                            ctx.getHand())
                    ? InteractionResult.CONSUME
                    : InteractionResult.PASS;
        }
        return FluidUtil.getFluidHandler(level, ctx.getClickedPos(), ctx.getClickedFace())
                        .isPresent()
                ? InteractionResult.SUCCESS
                : InteractionResult.PASS;
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            @Nullable Level level,
            List<Component> tooltip,
            TooltipFlag flag) {
        FluidStack fs = Ecc2CellHelper.readContents(stack);
        if (!fs.isEmpty()) {
            tooltip.add(
                    Component.translatable(
                                    "tooltip.ecera.cell.contents",
                                    Component.translatable(fluidTranslationKey))
                            .withStyle(ChatFormatting.AQUA));
        }
    }
}
