package com.ecocraft2.config;

import com.ecocraft2.bridge.Ecc2BridgePlatform;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraftforge.common.crafting.conditions.ICondition;
import net.minecraftforge.common.crafting.conditions.IConditionSerializer;

/**
 * Forge 1.19.2 datapack recipe condition ({@code ecc2:config_flag}) gating recipes by EcoCraft 2's
 * {@link Ecc2Config} difficulty and recipe enable/disable flags.
 */
public final class SolarConfigRecipeCondition implements ICondition {

    public static final ResourceLocation ID = Ecc2BridgePlatform.rl("config_flag");

    private final String flag;

    public SolarConfigRecipeCondition(String flag) {
        this.flag = flag;
    }

    public String flag() {
        return flag;
    }

    @Override
    public ResourceLocation getID() {
        return ID;
    }

    @Override
    public boolean test(IContext context) {
        return evaluateFlag(flag);
    }

    /** Evaluates a named {@code ecc2:config_flag} against current {@link Ecc2Config} settings. */
    public static boolean evaluateFlag(String flag) {
        return switch (flag) {
            case "hard_recipes" -> Ecc2Config.hardRecipes();
            case "normal_recipes" -> !Ecc2Config.hardRecipes();
            case "easy_asp_recipe" -> Ecc2Config.easyASPRecipe();
            case "standard_asp_recipe" -> !Ecc2Config.easyASPRecipe();
            case "advanced_solar_panel" ->
                    !Ecc2Config.disableAdvancedSolarRecipe() && !Ecc2Config.easyASPRecipe();
            case "advanced_solar_panel_easy" ->
                    !Ecc2Config.disableAdvancedSolarRecipe() && Ecc2Config.easyASPRecipe();
            case "hybrid_solar_panel" ->
                    !Ecc2Config.disableHybridSolarRecipe() && Ecc2Config.hardRecipes();
            case "hybrid_solar_panel_normal" ->
                    !Ecc2Config.disableHybridSolarRecipe() && !Ecc2Config.hardRecipes();
            case "hybrid_solar_panel_any" -> !Ecc2Config.disableHybridSolarRecipe();
            case "ultimate_hybrid_solar_panel" ->
                    !Ecc2Config.disableUltimateSolarRecipe() && Ecc2Config.hardRecipes();
            case "ultimate_hybrid_solar_panel_normal" ->
                    !Ecc2Config.disableUltimateSolarRecipe() && !Ecc2Config.hardRecipes();
            case "ultimate_hybrid_solar_panel_any" -> !Ecc2Config.disableUltimateSolarRecipe();
            case "quantum_solar_panel" -> !Ecc2Config.disableQuantumSolarRecipe();
            case "advanced_solar_helmet" -> !Ecc2Config.disableAdvancedHelmetRecipe();
            case "hybrid_solar_helmet" -> !Ecc2Config.disableHybridHelmetRecipe();
            case "ultimate_hybrid_solar_helmet" -> !Ecc2Config.disableUltimateHelmetRecipe();
            case "molecular_transformer" -> !Ecc2Config.disableMolecularTransformerRecipe();
            default -> true;
        };
    }

    /** Forge serializer registered with {@code CraftingHelper.register(...)}. */
    public static final class Serializer implements IConditionSerializer<SolarConfigRecipeCondition> {

        public static final Serializer INSTANCE = new Serializer();

        private Serializer() {}

        @Override
        public void write(JsonObject json, SolarConfigRecipeCondition value) {
            json.addProperty("flag", value.flag);
        }

        @Override
        public SolarConfigRecipeCondition read(JsonObject json) {
            return new SolarConfigRecipeCondition(GsonHelper.getAsString(json, "flag"));
        }

        @Override
        public ResourceLocation getID() {
            return ID;
        }
    }
}
