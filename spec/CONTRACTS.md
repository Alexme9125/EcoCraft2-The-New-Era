# EcoCraft 2: The New Era (`ecc2`) — Canonical IDs, Packages & Inter-Module Contracts

> **Single Source of Truth for ALL workers (`core-logic`, `art-assets`, `ic2-bridge`, `solar-kinetic`, `giant-gas`, `compat-mod`, `forge-1192`, `qa-verifier`).**
> Do NOT invent different IDs, package names, or method signatures. Follow this contract verbatim.

---

## 1. Identity & Clean-Room Isolation

- **Mod ID**: `ecc2`
- **Root Package**: `com.ecocraft2`
- **Repository Root**: `/Users/alex/Tools/EcC2/ecocraft2`
- **IC2NE 1.0.6 External JAR Dir (READ-ONLY, NEVER COPY INTO REPO)**: `/Users/alex/Tools/EcC2/.ic2-libs/`
  - `ic2-neoforge-1.21.1-1.0.6.jar`
  - `ic2-neoforge-26.1.2-1.0.6.jar`
  - `ic2-neoforge-26.2-1.0.6.jar`
  - `ic2-forge-1.19.2-1.0.6.jar`
- **NEVER read or copy from `/Users/alex/Tools/EcC2/asp-research/` or `Advanced Solar Panels-4.3.0.jar`!**
- **NEVER modify any file inside `/Users/alex/Tools/IC2/rebuild/`!**

---

## 2. Complete Registry ID Table (`ecc2:*`)

### 2.1 Blocks (with matching BlockItems unless fluid block) — 23 Placeable Blocks + 3 Fluid Blocks

| Category | Registry Path | Block Class / Role | Facing / States |
| --- | --- | --- | --- |
| ASP Solar | `advanced_solar_panel` | Solar Panel Tier 1 (8/1 EU/t, 32k EU) | `facing` (H4) |
| ASP Solar | `hybrid_solar_panel` | Solar Panel Tier 2 (64/8 EU/t, 100k EU) | `facing` (H4) |
| ASP Solar | `ultimate_hybrid_solar_panel` | Solar Panel Tier 3 (512/64 EU/t, 1M EU) | `facing` (H4) |
| ASP Solar | `quantum_solar_panel` | Solar Panel Tier 5 (4096/2048 EU/t, 10M EU) | `facing` (H4) |
| ASP Creative | `quantum_generator` | Creative Multi-Packet Generator (no recipe) | `facing` (H4), `active` |
| ASP Machine | `molecular_transformer` | Molecular Transformer (3D BER + plasma core) | `facing` (H4), `active` (light 12) |
| Adv Wind | `durable_wind_kinetic_generator` | Wind Kinetic (0.80× KU, 0.50× wear) | `facing` (H4), `active` |
| Adv Wind | `overclocked_wind_kinetic_generator` | Wind Kinetic (2.20× KU, 2.00× wear) | `facing` (H4), `active` |
| Adv Wind | `precision_wind_kinetic_generator` | Wind Kinetic (2.40× KU, 0.50× wear) | `facing` (H4), `active` |
| Adv Water | `durable_water_kinetic_generator` | Water Kinetic (0.80× KU, 0.50× wear) | `facing` (6-dir), `active` |
| Adv Water | `overclocked_water_kinetic_generator` | Water Kinetic (2.20× KU, 2.00× wear) | `facing` (6-dir), `active` |
| Adv Water | `precision_water_kinetic_generator` | Water Kinetic (2.40× KU, 0.50× wear) | `facing` (6-dir), `active` |
| Giant Machine | `giant_macerator_unit` | 3×3×3 Giant Macerator Unit | `facing` (H4), `formed`, `active`, `role` (0..26 or enum `UNFORMED/CORE/CORNER/SHELL`) |
| Giant Machine | `giant_compressor_unit` | 3×3×3 Giant Compressor Unit | same |
| Giant Machine | `giant_recycler_unit` | 3×3×3 Giant Recycler Unit | same |
| Giant Machine | `giant_extractor_unit` | 3×3×3 Giant Extractor Unit | same |
| Giant Machine | `giant_ore_washer_unit` | 3×3×3 Giant Ore Washing Plant Unit | same |
| Water Gas | `water_gas_machine` | Water Gas Machine (solar 2 EU/t + LV) | `facing` (H4), `active` |
| Gas Industry | `gas_furnace` | Gas Furnace (50 mB gas/item, 100t) | `facing` (H4), `active` |
| Gas Industry | `gas_generator` | Gas Generator (MV Tier 2, 16k EU/gas) | `facing` (H4), `active` |
| Natural Gas | `methane_ice_cluster` | Methane Ice Cluster (seafloor ore, iron pickaxe+, light 4) | default |
| Biogas | `culture_incubator` | Culture Incubator (bonemeal+100mB water→100mB culture_medium) | `facing` (H4), `active` |
| Biogas | `organic_fermenter` | Organic Fermenter (50mB culture_medium+food→20mB×H ic2:biogas) | `facing` (H4), `active` |
| Fluid Blocks | `water_gas`, `natural_gas`, `culture_medium` | LiquidBlock (no BlockItem) | fluid level |

### 2.2 Standalone Items — 26 Items

| Category | Registry Path | Notes |
| --- | --- | --- |
| ASP Crafting (14) | `sunnarium`, `sunnarium_part`, `sunnarium_alloy`, `irradiant_uranium`, `enriched_sunnarium`, `enriched_sunnarium_alloy`, `irradiant_glass_pane`, `iridium_iron_plate`, `reinforced_iridium_iron_plate`, `irradiant_reinforced_plate`, `iridium_ingot`, `uranium_ingot`, `mt_core`, `quantum_core` | Independent items; `iridium_ingot` in `#c:ingots/iridium` / `#forge:ingots/iridium`, `uranium_ingot` in `#c:ingots/uranium` / `#forge:ingots/uranium` |
| ASP Helmets (3) | `advanced_solar_helmet`, `hybrid_solar_helmet`, `ultimate_hybrid_solar_helmet` | Electric helmets; hybrid & ultimate dyeable (vanilla `dyed_color` / `display.color` + upgrade color preservation) + full quantum helmet abilities |
| Giant Component (1) | `megastructure_component` | Mid-tier crafting item (`orig machine + megastructure_component -> giant_*_unit`) |
| Named Cells (5) | `water_gas_cell`, `natural_gas_cell`, `culture_medium_cell`, `basic_culture_medium_cell`, `biogas_cell` | Full 1000 mB fluid cells; emptying returns `ic2:fluid_cell` (or `ic2:empty_cell` for `basic_culture_medium_cell`) |
| Buckets (3) | `water_gas_bucket`, `natural_gas_bucket`, `culture_medium_bucket` | Standard fluid buckets (`ic2:biogas_bucket` already exists in IC2NE!) |

### 2.3 Fluids — 3 New Fluids + 1 Reused IC2NE Fluid

| Fluid ID | Flowing ID | Density | Viscosity | Temp | Light | ARGB Tint | Tags |
| --- | --- | ---: | ---: | ---: | ---: | --- | --- |
| `ecc2:water_gas` | `ecc2:flowing_water_gas` | -400 | 400 | 310 | 2 | `0xD89BD0D6` | `c:water_gas`, `ecc2:gas_fuel` |
| `ecc2:natural_gas` | `ecc2:flowing_natural_gas` | -600 | 350 | 290 | 0 | `0xC888D4FF` | `c:natural_gas`, `ecc2:gas_fuel` |
| `ecc2:culture_medium` | `ecc2:flowing_culture_medium` | 1020 | 1800 | 300 | 1 | `0xE84CC77A` | `c:culture_medium` |
| `ic2:biogas` (REUSED) | `ic2:flowing_biogas` | (IC2NE) | (IC2NE) | (IC2NE) | (IC2NE) | (IC2NE) | `c:biogas`, `ecc2:gas_fuel` |

---

## 3. Core Pure-Java Domain API (`src/common`, Java 17)

Package root: `com.ecocraft2.common` (MUST compile with Java 17 and have ZERO Minecraft / NeoForge / Forge / IC2 imports!).

1. `com.ecocraft2.common.solar`:
   - `SolarTier` (enum: `ADVANCED(8,1,32_000,1)`, `HYBRID(64,8,100_000,2)`, `ULTIMATE_HYBRID(512,64,1_000_000,3)`, `QUANTUM(4_096,2_048,10_000_000,5)`)
   - `SkyCondition` (enum: `NONE`, `NIGHT`, `RAIN`, `DAY`) — note `RAIN` generates `nightPower` and displays dedicated rain icon!
   - `SolarPanelEngine` — tick(hasSky, seesSky, isDay, isRaining, canRain), `checkSkyInterval = 128`, non-negative clamped `storage`, `offeredEnergy() = min(storage, packetSize(tier))`, `drawEnergy(double)`, charging priority before grid draw.
   - `QuantumGeneratorEngine` — `production >= 0` (clamped), `tier` clamped `1..6` (`6 = MAX = 32_768`), `active` (default true on load/init if redstone == 0; redstone > 0 stops), `packetCount() = production > packetSize(tier) ? ceil(production / packetSize(tier)) : 1`, `offeredPerPacket() = active ? production / packetCount() : 0`, `handleButtonEvent(int event)` (0..5: -100/-10/-1/+1/+10/+100; 10..15: -500/-50/-5/+5/+50/+500; 20..25: tier 1..6; rejects all other IDs!).
   - `MolecularTransformerEngine` — fixes ALL ASP bugs: `demanded() = max(0, totalEu - energyUsed - energyIn)`, `inject(amount)` accepts `min(amount, demanded)` and returns `amount - accepted`, clears `energyIn` on recipe completion, pauses completion if output cannot fit (zero item loss!), `int idleTicks` capped at `40` (no byte overflow!).
   - `SolarHelmetSpec` (enum: `ADVANCED`, `HYBRID`, `ULTIMATE_HYBRID`) + `SolarHelmetEngine` (per-stack/per-player state, charging order: feet → legs → chest → offhand → main inventory → self).
2. `com.ecocraft2.common.kinetic`:
   - `RotorSpec` (enum: `WOOD("ic2:wooden_rotor", 10_800, 60, 70, 5, 4, false)`, `BRONZE("ic2:bronze_rotor", 86_400, 75, 100, 7, 4, true)`, `IRON("ic2:iron_rotor", 86_400, 75, 100, 7, 4, true)`, `STEEL("ic2:steel_rotor", 172_800, 90, 120, 9, 6, true)`, `CARBON("ic2:carbon_rotor", 604_800, 110, 150, 11, 8, true)`).
   - `KineticMachineTier` (enum: `STANDARD(1.0, 1.0)`, `DURABLE(0.80, 0.50)`, `OVERCLOCKED(2.20, 2.00)`, `PRECISION(2.40, 0.50)`).
   - `RotorWearAccumulator` — fractional wear accumulator; every 20 active ticks adds `(overWind ? 4.0 : 1.0) * tier.wearMultiplier()`, returns whole durability points to deduct (`int`).
3. `com.ecocraft2.common.giant`:
   - `GiantMachineKind` (enum/registry extensible via `GiantMachineSpec`: `MACERATOR`, `COMPRESSOR`, `RECYCLER`, `EXTRACTOR`, `ORE_WASHER`).
   - `GiantCornerPort` (enum of 8 horizontal lower-corner ports: `FRONT_LEFT`, `FRONT_RIGHT`, `BACK_LEFT`, `BACK_RIGHT`, `LEFT_FRONT`, `LEFT_BACK`, `RIGHT_FRONT`, `RIGHT_BACK` — mapped cleanly from structure facing + corner block offset `(dx, dy=0, dz)` + world `Direction`!).
   - `PortAccessMode` (enum: `FULL(0)`, `INPUT(1)`, `OUTPUT(2)`, `BLOCKED(3)`) + `GiantPortPermissions` (16-bit packed int, 2 bits per port, default all `FULL`).
   - `GiantMachineEngine` — 9 independent lanes (`0..8`), each lane processes up to **3 operations per batch** (`batch = min(3, availableInput / recipeInputCount, outputSpaceForBatch, fluidSpaceForBatch)`), energy cost per tick = `sum(activeLaneBatch[i]) * singleUpgradedEuPerTick`, base Tier 3 (HV, 512 EU/t), base capacity `max(32_000, 27 * baseReserve) + 10_000 * storageUpgrades`, supports IC2 `ProcessingUpgrades` (`overclockers`, `transformers`, `storage`).
   - `MultiblockGeometry` — pure coordinate math finding 3×3×3 bounding box & bottom-center core position from any unit block.
4. `com.ecocraft2.common.gas`:
   - `GasFuelSpec` (enum: `WATER_GAS("ecc2:water_gas", 10_000, 32)`, `NATURAL_GAS("ecc2:natural_gas", 10_000, 64)`, `BIOGAS("ic2:biogas", 16_000, 32)`).
   - `WaterGasEngine` — solar 2 EU/t daytime (when sky visible & not raining) + LV grid input; recipe: `100 mB water + 1 coal_dust + 1000 EU / 200t -> 1000 mB water_gas`; tank capacities 8,000 mB each.
   - `GasFurnaceEngine` — `50 mB` of any `GasFuelSpec` per smelted item; base `100 tick/item`; overclocker accelerates ticks while preserving exact `50 mB/item` total gas cost.
   - `GasGeneratorEngine` — burns `GasFuelSpec` from 16,000 mB tank into 16,000 EU buffer (Tier 2 MV, 128 EU/t output packet limit), exact fractional EU/mB accounting (10 EU/mB for water_gas & natural_gas, 16 EU/mB for biogas).
   - `CultureIncubatorEngine` — `1 bonemeal + 100 mB water + 200 EU / 100t -> 100 mB culture_medium` (LV Tier 1, 8,000 mB water & culture tanks).
   - `OrganicFermenterEngine` — `50 mB culture_medium + any food with nutrition H > 0 + 200 EU / 100t -> 20 * H mB ic2:biogas` (LV Tier 1, 8,000 mB culture tank, 16,000 mB biogas tank).
5. `com.ecocraft2.common.config`:
   - `Ecc2ConfigDefaults` — all 31+ ASP config defaults + EcoCraft 2 kinetic/giant/gas balance defaults.

---

## 4. IC2NE 1.0.6 Bridge Interfaces (`com.ecocraft2.bridge`)

Shared between `ic2-bridge` (which implements Mixins calling these interfaces) and `solar-kinetic` / `giant-gas` / `forge-1192` (which implement these interfaces on their Blocks/BEs/Items):

1. `Ecc2PowerConnectable` (implemented by Blocks):
   - `default boolean ecc2AcceptsCable(BlockState state, Direction side) { return true; }`
2. `Ecc2KineticSource` (implemented by BlockEntities):
   - `int ecc2DrawKinetic(ServerLevel level, Direction fromSide, int maxRequestedKu);`
3. `Ecc2ElectricItem` (implemented by Items):
   - `double ecc2Capacity(ItemStack stack);`
   - `double ecc2TransferLimit(ItemStack stack);`
   - `int ecc2Tier(ItemStack stack);`
   - `boolean ecc2CanProvide(ItemStack stack);`
   - `double ecc2Stored(ItemStack stack);`
   - `void ecc2SetStored(ItemStack stack, double eu);`
   - `double ecc2Charge(ItemStack stack, double offer, int chargerTier, boolean ignoreLimit, boolean simulate);`
   - `double ecc2Discharge(ItemStack stack, double requested, int chargerTier, boolean ignoreLimit, boolean simulate);`
4. `Ecc2ArmorSuitBridge` (implemented by Armor Items):
   - `enum SuitClass { NONE, NANO, QUANTUM }`
   - `SuitClass ecc2SuitClass(ItemStack stack);`
5. `Ecc2ExternalRecipes` (static registry populated at mod init & read by Mixins):
   - `registerCompressor(String inputId, int inputCount, String outputId, int outputCount)`
   - `registerExtruder(String inputId, int inputCount, String outputId, int outputCount)`
   - `registerCanningBottle(String containerId, int containerCount, String ingredientId, int ingredientCount, String outputId, int outputCount)`
   - `registerNamedCell(boolean basicFamily, String cellItemId, String fluidId)`

---

## 5. Texture & GUI Asset Paths (`assets/ecc2/...`)

All paths strictly lowercase `[a-z0-9/._-]`:
- Block textures: `assets/ecc2/textures/block/<name>.png` (with `_top`, `_bottom`, `_side`, `_front`, `_front_active`, `_port_full`, `_port_in`, `_port_out`, `_port_blocked`, etc.)
- Item textures: `assets/ecc2/textures/item/<name>.png` (hybrid/ultimate helmets also have `_overlay.png` for dyeing layer1)
- Armor textures: `assets/ecc2/textures/models/armor/advanced_solar_layer_1.png`, `hybrid_solar_layer_1.png`, `hybrid_solar_layer_1_overlay.png`, `ultimate_hybrid_solar_layer_1.png`, `ultimate_hybrid_solar_layer_1_overlay.png`
- Fluid textures: `assets/ecc2/textures/block/fluid/<fluid>_still.png` & `<fluid>_flow.png` (with `.mcmeta` animation)
- 3D MT model textures: `assets/ecc2/textures/entity/molecular_transformer.png`, `plasma.png` (4×4 grid), `particles.png` (8×8 grid)
- GUI textures:
  - `assets/ecc2/textures/gui/solar_panel.png` (194×168, includes sun/moon/rain icons + energy bar)
  - `assets/ecc2/textures/gui/quantum_generator.png` (176×193)
  - `assets/ecc2/textures/gui/molecular_transformer.png` (220×193)
  - `assets/ecc2/textures/gui/kinetic_generator.png` (176×166, LCD + rotor durability bar + tier badge)
  - `assets/ecc2/textures/gui/giant_machine.png` (256×236, redesigned IC2NE blue-slate sci-fi layout: 3×3 input with 9 micro-progress bars, central EU core + optional 64k mB water column, 3×3 output, 4 upgrade slots, battery slot, side access gear button + 8-corner port matrix overlay!)
  - `assets/ecc2/textures/gui/water_gas_machine.png` (176×180, solar status + water/gas dual tanks + coal dust + cell slots + upgrades)
  - `assets/ecc2/textures/gui/gas_furnace.png` (176×166, gas tank + blue flame gauge + input/output/cell + upgrades)
  - `assets/ecc2/textures/gui/gas_generator.png` (176×166, gas tank + EU bar + charge slot + cell slots)
  - `assets/ecc2/textures/gui/culture_incubator.png` (176×180, water/culture tanks + bonemeal + cell slots + upgrades)
  - `assets/ecc2/textures/gui/organic_fermenter.png` (176×180, culture/biogas tanks + food + container return + cell slots + upgrades)
- Lang: `assets/ecc2/lang/en_us.json`, `assets/ecc2/lang/zh_cn.json`
