package com.ecocraft2.giant;

import com.ecocraft2.common.config.Ecc2Ids;
import com.ecocraft2.common.giant.GiantMachineKind;
import com.ecocraft2.registry.Ecc2Registries;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.RegistryObject;
import java.util.List;
import org.jetbrains.annotations.Nullable;

/** Forge 1.19.2 registrations for the Megastructure Component and 5 Giant Machine unit blocks. */
public final class GiantRegistry {

    public static final RegistryObject<Item> MEGASTRUCTURE_COMPONENT =
            Ecc2Registries.ITEMS.register(
                    Ecc2Ids.MEGASTRUCTURE_COMPONENT,
                    () ->
                            new Item(Ecc2Registries.tabItemProperties()) {
                                @Override
                                public void appendHoverText(
                                        ItemStack stack,
                                        @Nullable Level level,
                                        List<Component> tooltip,
                                        TooltipFlag flag) {
                                    tooltip.add(
                                            Component.translatable(
                                                            "tooltip.ecera.megastructure_component")
                                                    .withStyle(ChatFormatting.GRAY));
                                }
                            });

    public static final RegistryObject<Block> GIANT_MACERATOR_UNIT =
            registerUnit(Ecc2Ids.GIANT_MACERATOR_UNIT, GiantMachineKind.MACERATOR);
    public static final RegistryObject<Block> GIANT_COMPRESSOR_UNIT =
            registerUnit(Ecc2Ids.GIANT_COMPRESSOR_UNIT, GiantMachineKind.COMPRESSOR);
    public static final RegistryObject<Block> GIANT_RECYCLER_UNIT =
            registerUnit(Ecc2Ids.GIANT_RECYCLER_UNIT, GiantMachineKind.RECYCLER);
    public static final RegistryObject<Block> GIANT_EXTRACTOR_UNIT =
            registerUnit(Ecc2Ids.GIANT_EXTRACTOR_UNIT, GiantMachineKind.EXTRACTOR);
    public static final RegistryObject<Block> GIANT_ORE_WASHER_UNIT =
            registerUnit(Ecc2Ids.GIANT_ORE_WASHER_UNIT, GiantMachineKind.ORE_WASHER);

    public static final RegistryObject<BlockEntityType<GiantMachineBlockEntity>> GIANT_MACHINE_BE =
            Ecc2Registries.BLOCK_ENTITIES.register(
                    "giant_machine",
                    () ->
                            BlockEntityType.Builder.of(
                                            GiantMachineBlockEntity::new,
                                            GIANT_MACERATOR_UNIT.get(),
                                            GIANT_COMPRESSOR_UNIT.get(),
                                            GIANT_RECYCLER_UNIT.get(),
                                            GIANT_EXTRACTOR_UNIT.get(),
                                            GIANT_ORE_WASHER_UNIT.get())
                                    .build(null));

    public static final RegistryObject<MenuType<GiantMachineMenu>> GIANT_MACHINE_MENU =
            Ecc2Registries.MENUS.register(
                    "giant_machine", () -> IForgeMenuType.create(GiantMachineMenu::new));

    private GiantRegistry() {}

    public static void bootstrap() {}

    private static RegistryObject<Block> registerUnit(String name, GiantMachineKind kind) {
        RegistryObject<Block> block =
                Ecc2Registries.BLOCKS.register(name, () -> new GiantMachineBlock(kind));
        Ecc2Registries.ITEMS.register(
                name, () -> new BlockItem(block.get(), Ecc2Registries.tabItemProperties()));
        return block;
    }
}
