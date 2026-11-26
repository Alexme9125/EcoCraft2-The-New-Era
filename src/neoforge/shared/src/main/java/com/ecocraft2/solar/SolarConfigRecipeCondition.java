package com.ecocraft2.solar;

import com.ecocraft2.config.Ecc2Config;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Locale;
import net.neoforged.neoforge.common.conditions.ICondition;

/**
 * Optional NeoForge datapack recipe condition ({@code ecc2:config_flag}) and runtime recipe toggle
 * evaluator driven by {@link Ecc2Config}.
 */
public record SolarConfigRecipeCondition(String flag, boolean expected) implements ICondition {

    public static final MapCodec<SolarConfigRecipeCondition> CODEC =
            RecordCodecBuilder.mapCodec(
                    instance ->
                            instance.group(
                                            Codec.STRING
                                                    .fieldOf("flag")
                                                    .forGetter(SolarConfigRecipeCondition::flag),
                                            Codec.BOOL
                                                    .optionalFieldOf("expected", true)
                                                    .forGetter(
                                                            SolarConfigRecipeCondition::expected))
                                    .apply(instance, SolarConfigRecipeCondition::new));

    @Override
    public boolean test(IContext context) {
        return evaluateFlag(flag) == expected;
    }

    @Override
    public MapCodec<? extends ICondition> codec() {
        return CODEC;
    }

    /** Evaluates a named {@link Ecc2Config} recipe toggle flag. */
    public static boolean evaluateFlag(String flag) {
        if (flag == null || flag.isEmpty()) {
            return true;
        }
        return switch (flag.toLowerCase(Locale.ROOT)) {
            case "hard_recipes", "hardrecipes" -> Ecc2Config.hardRecipes();
            case "normal_recipes" -> !Ecc2Config.hardRecipes();
            case "easy_asp_recipe", "easyasprecipe" -> Ecc2Config.easyASPRecipe();
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

    /**
     * Returns {@code true} if an {@code ecc2} crafting recipe path is disabled by current
     * {@link Ecc2Config} recipe flags.
     */
    public static boolean isRecipeDisabledByConfig(String recipeId) {
        if (recipeId == null || !recipeId.startsWith("ecc2:")) {
            return false;
        }
        String path = recipeId.substring("ecc2:".length());
        return switch (path) {
            case "advanced_solar_panel" ->
                    Ecc2Config.disableAdvancedSolarRecipe() || Ecc2Config.easyASPRecipe();
            case "advanced_solar_panel_easy" ->
                    Ecc2Config.disableAdvancedSolarRecipe() || !Ecc2Config.easyASPRecipe();
            case "hybrid_solar_panel" ->
                    Ecc2Config.disableHybridSolarRecipe() || !Ecc2Config.hardRecipes();
            case "hybrid_solar_panel_normal" ->
                    Ecc2Config.disableHybridSolarRecipe() || Ecc2Config.hardRecipes();
            case "hybrid_solar_panel_split" -> Ecc2Config.disableHybridSolarRecipe();
            case "ultimate_hybrid_solar_panel" ->
                    Ecc2Config.disableUltimateSolarRecipe() || !Ecc2Config.hardRecipes();
            case "ultimate_hybrid_solar_panel_normal" ->
                    Ecc2Config.disableUltimateSolarRecipe() || Ecc2Config.hardRecipes();
            case "ultimate_hybrid_solar_panel_8x" -> Ecc2Config.disableUltimateSolarRecipe();
            case "quantum_solar_panel" -> Ecc2Config.disableQuantumSolarRecipe();
            case "advanced_solar_helmet" -> Ecc2Config.disableAdvancedHelmetRecipe();
            case "hybrid_solar_helmet" -> Ecc2Config.disableHybridHelmetRecipe();
            case "ultimate_hybrid_solar_helmet", "ultimate_hybrid_solar_helmet_upgrade" ->
                    Ecc2Config.disableUltimateHelmetRecipe();
            case "molecular_transformer" -> Ecc2Config.disableMolecularTransformerRecipe();
            default -> false;
        };
    }
}
