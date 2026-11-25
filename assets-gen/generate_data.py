#!/usr/bin/env python3
"""Multi-version resource & data pack generator for EcoCraft 2: The New Era (ecc2).

Generates clean, version-accurate assets (blockstates, block/item models, 26.x item
definitions & armor equipment JSONs) and data (crafting recipes, 22 Molecular
Transformer recipes, loot tables, tags, and seafloor methane_ice_cluster worldgen)
for all four target platforms:
  - neoforge-1.21.1
  - neoforge-26.1.2
  - neoforge-26.2
  - forge-1.19.2
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Any, Dict, List, Optional, Tuple

TARGETS = ("neoforge-1.21.1", "neoforge-26.1.2", "neoforge-26.2", "forge-1.19.2")
MOD_ID = "ecc2"

SOLAR_PANELS = [
    "advanced_solar_panel",
    "hybrid_solar_panel",
    "ultimate_hybrid_solar_panel",
    "quantum_solar_panel",
]

WIND_KINETIC_GENERATORS = [
    "durable_wind_kinetic_generator",
    "overclocked_wind_kinetic_generator",
    "precision_wind_kinetic_generator",
]

WATER_KINETIC_GENERATORS = [
    "durable_water_kinetic_generator",
    "overclocked_water_kinetic_generator",
    "precision_water_kinetic_generator",
]

GIANT_KINDS = [
    "macerator",
    "compressor",
    "recycler",
    "extractor",
    "ore_washer",
]

GIANT_UNIT_BLOCKS = [f"giant_{k}_unit" for k in GIANT_KINDS]

GAS_BIO_MACHINES = [
    "water_gas_machine",
    "gas_furnace",
    "gas_generator",
    "culture_incubator",
    "organic_fermenter",
]

ALL_PLACEABLE_BLOCKS = (
    SOLAR_PANELS
    + ["quantum_generator", "molecular_transformer"]
    + WIND_KINETIC_GENERATORS
    + WATER_KINETIC_GENERATORS
    + GIANT_UNIT_BLOCKS
    + GAS_BIO_MACHINES
    + ["methane_ice_cluster"]
)

FLUID_BLOCKS = ["water_gas", "natural_gas", "culture_medium"]

ASP_CRAFTING_ITEMS = [
    "sunnarium",
    "sunnarium_part",
    "sunnarium_alloy",
    "irradiant_uranium",
    "enriched_sunnarium",
    "enriched_sunnarium_alloy",
    "irradiant_glass_pane",
    "iridium_iron_plate",
    "reinforced_iridium_iron_plate",
    "irradiant_reinforced_plate",
    "iridium_ingot",
    "uranium_ingot",
    "mt_core",
    "quantum_core",
]

SOLAR_HELMETS = [
    "advanced_solar_helmet",
    "hybrid_solar_helmet",
    "ultimate_hybrid_solar_helmet",
]

DYEABLE_HELMETS = {
    "hybrid_solar_helmet",
    "ultimate_hybrid_solar_helmet",
}

NAMED_CELLS = [
    "water_gas_cell",
    "natural_gas_cell",
    "culture_medium_cell",
    "basic_culture_medium_cell",
    "biogas_cell",
]

BUCKETS = [
    "water_gas_bucket",
    "natural_gas_bucket",
    "culture_medium_bucket",
]

STANDALONE_ITEMS = (
    ASP_CRAFTING_ITEMS
    + SOLAR_HELMETS
    + ["megastructure_component"]
    + NAMED_CELLS
    + BUCKETS
)


class TargetConfig:
    def __init__(self, target: str) -> None:
        if target not in TARGETS:
            raise ValueError(f"Unsupported target {target!r}; expected one of {TARGETS}")
        self.target = target
        self.is_1192 = target == "forge-1.19.2"
        self.is_1211 = target == "neoforge-1.21.1"
        self.is_26x = target in ("neoforge-26.1.2", "neoforge-26.2")

        self.recipe_dir = "recipes" if self.is_1192 else "recipe"
        self.loot_dir = "loot_tables" if self.is_1192 else "loot_table"
        self.tag_block_dir = "blocks" if self.is_1192 else "block"
        self.tag_item_dir = "items" if self.is_1192 else "item"
        self.tag_fluid_dir = "fluids" if self.is_1192 else "fluid"
        self.common_ns = "forge" if self.is_1192 else "c"
        self.biome_mod_dir = "forge/biome_modifier" if self.is_1192 else "neoforge/biome_modifier"
        self.biome_mod_type = "forge:add_features" if self.is_1192 else "neoforge:add_features"
        self.cond_key = "conditions" if self.is_1192 else "neoforge:conditions"
        self.cond_not = "forge:not" if self.is_1192 else "neoforge:not"
        self.cond_tag_empty = "forge:tag_empty" if self.is_1192 else "neoforge:tag_empty"

    def tag(self, common_path: str) -> str:
        return f"#{self.common_ns}:{common_path}"

    def ingredient(self, spec: str) -> Any:
        """Convert 'ns:id' or '#ns:tag' into version-accurate crafting ingredient JSON."""
        is_tag = spec.startswith("#")
        raw = spec[1:] if is_tag else spec
        if self.is_26x:
            return f"#{raw}" if is_tag else raw
        return {"tag": raw} if is_tag else {"item": raw}

    def result(self, item_id: str, count: int = 1) -> Dict[str, Any]:
        if self.is_1192:
            return {"item": item_id, "count": count}
        return {"id": item_id, "count": count}

    def tag_not_empty_condition(self, tag_id: str) -> Dict[str, Any]:
        clean = tag_id[1:] if tag_id.startswith("#") else tag_id
        return {
            "type": self.cond_not,
            "value": {
                "type": self.cond_tag_empty,
                "tag": clean,
            },
        }


def write_json(path: Path, data: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


# =============================================================================
# 1. ASSETS: BLOCKSTATES, BLOCK MODELS, ITEM MODELS, 26.x ITEM & EQUIPMENT DEFS
# =============================================================================

def h4_variants(model_off: str, model_on: Optional[str] = None) -> Dict[str, Any]:
    dirs = [("north", 0), ("east", 90), ("south", 180), ("west", 270)]
    out: Dict[str, Any] = {}
    if model_on is None:
        for d, rot in dirs:
            entry: Dict[str, Any] = {"model": model_off}
            if rot:
                entry["y"] = rot
            out[f"facing={d}"] = entry
    else:
        for active_val, model in [("false", model_off), ("true", model_on)]:
            for d, rot in dirs:
                entry = {"model": model}
                if rot:
                    entry["y"] = rot
                out[f"facing={d},active={active_val}"] = entry
    return {"variants": out}


def d6_variants(model_off: str, model_on: str) -> Dict[str, Any]:
    dirs = [
        ("north", 0, 0),
        ("east", 0, 90),
        ("south", 0, 180),
        ("west", 0, 270),
        ("up", 270, 0),
        ("down", 90, 0),
    ]
    out: Dict[str, Any] = {}
    for active_val, model in [("false", model_off), ("true", model_on)]:
        for d, rx, ry in dirs:
            entry: Dict[str, Any] = {"model": model}
            if rx:
                entry["x"] = rx
            if ry:
                entry["y"] = ry
            out[f"facing={d},active={active_val}"] = entry
    return {"variants": out}


def cube_model(up: str, down: str, north: str, south: str, east: str, west: str, particle: str) -> Dict[str, Any]:
    return {
        "parent": "minecraft:block/cube",
        "textures": {
            "up": up,
            "down": down,
            "north": north,
            "south": south,
            "east": east,
            "west": west,
            "particle": particle,
        },
    }


def generate_assets(out_root: Path, cfg: TargetConfig) -> int:
    count = 0
    assets = out_root / "assets" / MOD_ID

    def emit(rel: str, obj: Any) -> None:
        nonlocal count
        write_json(assets / rel, obj)
        count += 1

    # --- 1.1 Solar Panels (4) ---
    for sp in SOLAR_PANELS:
        emit(f"blockstates/{sp}.json", h4_variants(f"{MOD_ID}:block/{sp}"))
        emit(
            f"models/block/{sp}.json",
            cube_model(
                up=f"{MOD_ID}:block/{sp}_top",
                down=f"{MOD_ID}:block/{sp}_bottom",
                north=f"{MOD_ID}:block/{sp}_side",
                south=f"{MOD_ID}:block/{sp}_side",
                east=f"{MOD_ID}:block/{sp}_side",
                west=f"{MOD_ID}:block/{sp}_side",
                particle=f"{MOD_ID}:block/{sp}_side",
            ),
        )
        emit(f"models/item/{sp}.json", {"parent": f"{MOD_ID}:block/{sp}"})

    # --- 1.2 Quantum Generator ---
    emit("blockstates/quantum_generator.json", h4_variants(f"{MOD_ID}:block/quantum_generator", f"{MOD_ID}:block/quantum_generator_active"))
    emit("models/block/quantum_generator.json", {"parent": "minecraft:block/cube_all", "textures": {"all": f"{MOD_ID}:block/quantum_generator"}})
    emit("models/block/quantum_generator_active.json", {"parent": "minecraft:block/cube_all", "textures": {"all": f"{MOD_ID}:block/quantum_generator_active"}})
    emit("models/item/quantum_generator.json", {"parent": f"{MOD_ID}:block/quantum_generator"})

    # --- 1.3 Molecular Transformer (clean pedestal+pillars geometry for item & particle fallback; 3D BER animates in-world) ---
    emit("blockstates/molecular_transformer.json", h4_variants(f"{MOD_ID}:block/molecular_transformer", f"{MOD_ID}:block/molecular_transformer"))
    emit(
        "models/block/molecular_transformer.json",
        {
            "parent": "minecraft:block/block",
            "textures": {
                "particle": f"{MOD_ID}:block/molecular_transformer_particle",
                "frame": f"{MOD_ID}:block/molecular_transformer_particle",
                "core": f"{MOD_ID}:block/quantum_generator_active",
            },
            "elements": [
                {
                    "from": [1, 0, 1],
                    "to": [15, 3, 15],
                    "faces": {f: {"texture": "#frame"} for f in ("down", "up", "north", "south", "west", "east")},
                },
                {
                    "from": [2, 13, 2],
                    "to": [14, 16, 14],
                    "faces": {f: {"texture": "#frame"} for f in ("down", "up", "north", "south", "west", "east")},
                },
                {
                    "from": [2, 3, 2],
                    "to": [4, 13, 4],
                    "faces": {f: {"texture": "#frame"} for f in ("north", "south", "west", "east")},
                },
                {
                    "from": [12, 3, 2],
                    "to": [14, 13, 4],
                    "faces": {f: {"texture": "#frame"} for f in ("north", "south", "west", "east")},
                },
                {
                    "from": [7, 3, 12],
                    "to": [9, 13, 14],
                    "faces": {f: {"texture": "#frame"} for f in ("north", "south", "west", "east")},
                },
                {
                    "from": [5, 5, 5],
                    "to": [11, 11, 11],
                    "faces": {f: {"texture": "#core"} for f in ("down", "up", "north", "south", "west", "east")},
                },
            ],
        },
    )
    emit("models/item/molecular_transformer.json", {"parent": f"{MOD_ID}:block/molecular_transformer"})

    # --- 1.4 Advanced Wind & Water Kinetic Generators (6) ---
    for bid in WIND_KINETIC_GENERATORS:
        emit(f"blockstates/{bid}.json", h4_variants(f"{MOD_ID}:block/{bid}", f"{MOD_ID}:block/{bid}_active"))
    for bid in WATER_KINETIC_GENERATORS:
        emit(f"blockstates/{bid}.json", d6_variants(f"{MOD_ID}:block/{bid}", f"{MOD_ID}:block/{bid}_active"))
    for bid in WIND_KINETIC_GENERATORS + WATER_KINETIC_GENERATORS:
        emit(
            f"models/block/{bid}.json",
            cube_model(
                up=f"{MOD_ID}:block/{bid}_top",
                down=f"{MOD_ID}:block/{bid}_bottom",
                north=f"{MOD_ID}:block/{bid}_front",
                south=f"{MOD_ID}:block/{bid}_back",
                east=f"{MOD_ID}:block/{bid}_side",
                west=f"{MOD_ID}:block/{bid}_side",
                particle=f"{MOD_ID}:block/{bid}_side",
            ),
        )
        emit(
            f"models/block/{bid}_active.json",
            cube_model(
                up=f"{MOD_ID}:block/{bid}_top",
                down=f"{MOD_ID}:block/{bid}_bottom",
                north=f"{MOD_ID}:block/{bid}_front_active",
                south=f"{MOD_ID}:block/{bid}_back",
                east=f"{MOD_ID}:block/{bid}_side",
                west=f"{MOD_ID}:block/{bid}_side",
                particle=f"{MOD_ID}:block/{bid}_side",
            ),
        )
        emit(f"models/item/{bid}.json", {"parent": f"{MOD_ID}:block/{bid}"})

    # --- 1.5 Giant Machines (5 unit blocks + shared casing & 4 port block models) ---
    emit(
        "models/block/giant_casing.json",
        cube_model(
            up=f"{MOD_ID}:block/giant_casing_top",
            down=f"{MOD_ID}:block/giant_casing_bottom",
            north=f"{MOD_ID}:block/giant_casing_side",
            south=f"{MOD_ID}:block/giant_casing_side",
            east=f"{MOD_ID}:block/giant_casing_side",
            west=f"{MOD_ID}:block/giant_casing_side",
            particle=f"{MOD_ID}:block/giant_casing_side",
        ),
    )
    for mode in ("full", "input", "output", "blocked"):
        emit(
            f"models/block/giant_port_{mode}.json",
            cube_model(
                up=f"{MOD_ID}:block/giant_casing_top",
                down=f"{MOD_ID}:block/giant_casing_bottom",
                north=f"{MOD_ID}:block/giant_port_{mode}",
                south=f"{MOD_ID}:block/giant_port_{mode}",
                east=f"{MOD_ID}:block/giant_port_{mode}",
                west=f"{MOD_ID}:block/giant_port_{mode}",
                particle=f"{MOD_ID}:block/giant_port_{mode}",
            ),
        )

    for kind in GIANT_KINDS:
        unit_id = f"giant_{kind}_unit"
        emit(f"models/block/{unit_id}.json", {"parent": "minecraft:block/cube_all", "textures": {"all": f"{MOD_ID}:block/{unit_id}"}})
        emit(
            f"models/block/giant_core_{kind}.json",
            cube_model(
                up=f"{MOD_ID}:block/giant_casing_top",
                down=f"{MOD_ID}:block/giant_casing_bottom",
                north=f"{MOD_ID}:block/giant_core_front_{kind}",
                south=f"{MOD_ID}:block/giant_casing_side",
                east=f"{MOD_ID}:block/giant_casing_side",
                west=f"{MOD_ID}:block/giant_casing_side",
                particle=f"{MOD_ID}:block/giant_casing_side",
            ),
        )
        emit(
            f"models/block/giant_core_{kind}_active.json",
            cube_model(
                up=f"{MOD_ID}:block/giant_casing_top",
                down=f"{MOD_ID}:block/giant_casing_bottom",
                north=f"{MOD_ID}:block/giant_core_front_{kind}_active",
                south=f"{MOD_ID}:block/giant_casing_side",
                east=f"{MOD_ID}:block/giant_casing_side",
                west=f"{MOD_ID}:block/giant_casing_side",
                particle=f"{MOD_ID}:block/giant_casing_side",
            ),
        )
        # Complete state matrix for facing (H4) x formed x active x role (unformed/core/corner/shell)
        variants: Dict[str, Any] = {}
        for d, rot in [("north", 0), ("east", 90), ("south", 180), ("west", 270)]:
            for formed in ("false", "true"):
                for active in ("false", "true"):
                    for role in ("unformed", "core", "corner", "shell"):
                        if formed == "false" or role == "unformed":
                            m = f"{MOD_ID}:block/{unit_id}"
                        elif role == "core":
                            m = f"{MOD_ID}:block/giant_core_{kind}_active" if active == "true" else f"{MOD_ID}:block/giant_core_{kind}"
                        elif role == "corner":
                            m = f"{MOD_ID}:block/giant_port_full"
                        else:
                            m = f"{MOD_ID}:block/giant_casing"
                        entry: Dict[str, Any] = {"model": m}
                        if rot and role == "core" and formed == "true":
                            entry["y"] = rot
                        variants[f"facing={d},formed={formed},active={active},role={role}"] = entry
        emit(f"blockstates/{unit_id}.json", {"variants": variants})
        emit(f"models/item/{unit_id}.json", {"parent": f"{MOD_ID}:block/{unit_id}"})

    # --- 1.6 Gas & Bio Machines (5) ---
    for bid in GAS_BIO_MACHINES:
        emit(f"blockstates/{bid}.json", h4_variants(f"{MOD_ID}:block/{bid}", f"{MOD_ID}:block/{bid}_active"))
        emit(
            f"models/block/{bid}.json",
            cube_model(
                up=f"{MOD_ID}:block/{bid}_top",
                down=f"{MOD_ID}:block/{bid}_bottom",
                north=f"{MOD_ID}:block/{bid}_front",
                south=f"{MOD_ID}:block/{bid}_side",
                east=f"{MOD_ID}:block/{bid}_side",
                west=f"{MOD_ID}:block/{bid}_side",
                particle=f"{MOD_ID}:block/{bid}_side",
            ),
        )
        emit(
            f"models/block/{bid}_active.json",
            cube_model(
                up=f"{MOD_ID}:block/{bid}_top",
                down=f"{MOD_ID}:block/{bid}_bottom",
                north=f"{MOD_ID}:block/{bid}_front_active",
                south=f"{MOD_ID}:block/{bid}_side",
                east=f"{MOD_ID}:block/{bid}_side",
                west=f"{MOD_ID}:block/{bid}_side",
                particle=f"{MOD_ID}:block/{bid}_side",
            ),
        )
        emit(f"models/item/{bid}.json", {"parent": f"{MOD_ID}:block/{bid}"})

    # --- 1.7 Methane Ice Cluster ---
    emit("blockstates/methane_ice_cluster.json", {"variants": {"": {"model": f"{MOD_ID}:block/methane_ice_cluster"}}})
    emit(
        "models/block/methane_ice_cluster.json",
        {
            "parent": "minecraft:block/cube_all",
            "render_type": "minecraft:translucent",
            "textures": {"all": f"{MOD_ID}:block/methane_ice_cluster"},
        },
    )
    emit("models/item/methane_ice_cluster.json", {"parent": f"{MOD_ID}:block/methane_ice_cluster"})

    # --- 1.8 Fluid Blocks (3) ---
    for fl in FLUID_BLOCKS:
        emit(f"blockstates/{fl}.json", {"variants": {"": {"model": f"{MOD_ID}:block/{fl}"}}})
        emit(
            f"models/block/{fl}.json",
            {"parent": "minecraft:block/block", "textures": {"particle": f"{MOD_ID}:block/fluid/{fl}_still"}},
        )

    # --- 1.9 Standalone Item Models (26) ---
    for item_id in STANDALONE_ITEMS:
        if item_id in DYEABLE_HELMETS:
            emit(
                f"models/item/{item_id}.json",
                {
                    "parent": "minecraft:item/generated",
                    "textures": {
                        "layer0": f"{MOD_ID}:item/{item_id}",
                        "layer1": f"{MOD_ID}:item/{item_id}_overlay",
                    },
                },
            )
        else:
            emit(
                f"models/item/{item_id}.json",
                {
                    "parent": "minecraft:item/generated",
                    "textures": {"layer0": f"{MOD_ID}:item/{item_id}"},
                },
            )

    # --- 1.10 Armor Equipment JSONs (1.21.1+ & 26.x) ---
    if not cfg.is_1192:
        emit(
            "equipment/advanced_solar.json",
            {"layers": {"humanoid": [{"texture": f"{MOD_ID}:advanced_solar"}]}},
        )
        for tier in ("hybrid_solar", "ultimate_hybrid_solar"):
            emit(
                f"equipment/{tier}.json",
                {
                    "layers": {
                        "humanoid": [
                            {"texture": f"{MOD_ID}:{tier}", "dyeable": {"color_when_undyed": -1}},
                            {"texture": f"{MOD_ID}:{tier}_overlay"},
                        ]
                    }
                },
            )

    # --- 1.11 MC 1.21.4+ / 26.x Item Model Definitions (assets/ecc2/items/*.json) ---
    if cfg.is_26x:
        for all_id in ALL_PLACEABLE_BLOCKS + STANDALONE_ITEMS:
            if all_id in DYEABLE_HELMETS:
                emit(
                    f"items/{all_id}.json",
                    {
                        "model": {
                            "type": "minecraft:model",
                            "model": f"{MOD_ID}:item/{all_id}",
                            "tints": [{"type": "minecraft:dye", "default": -1}],
                        }
                    },
                )
            else:
                emit(
                    f"items/{all_id}.json",
                    {"model": {"type": "minecraft:model", "model": f"{MOD_ID}:item/{all_id}"}},
                )

    return count


# =============================================================================
# 2. DATA: CRAFTING RECIPES + 22 MOLECULAR TRANSFORMER RECIPES
# =============================================================================

def shaped(
    cfg: TargetConfig,
    pattern: List[str],
    key: Dict[str, str],
    result_id: str,
    count: int = 1,
    conditions: Optional[List[Dict[str, Any]]] = None,
) -> Dict[str, Any]:
    doc: Dict[str, Any] = {
        "type": "minecraft:crafting_shaped",
        "pattern": pattern,
        "key": {k: cfg.ingredient(v) for k, v in key.items()},
        "result": cfg.result(result_id, count),
    }
    if conditions:
        doc[cfg.cond_key] = conditions
    return doc


def shapeless(
    cfg: TargetConfig,
    ingredients: List[str],
    result_id: str,
    count: int = 1,
    conditions: Optional[List[Dict[str, Any]]] = None,
) -> Dict[str, Any]:
    doc: Dict[str, Any] = {
        "type": "minecraft:crafting_shapeless",
        "ingredients": [cfg.ingredient(i) for i in ingredients],
        "result": cfg.result(result_id, count),
    }
    if conditions:
        doc[cfg.cond_key] = conditions
    return doc


def generate_crafting_recipes(out_root: Path, cfg: TargetConfig) -> int:
    rdir = out_root / "data" / MOD_ID / cfg.recipe_dir
    count = 0

    def emit(name: str, data: Dict[str, Any]) -> None:
        nonlocal count
        write_json(rdir / f"{name}.json", data)
        count += 1

    # --- ASP Crafting Ladder (12) ---
    emit("sunnarium_from_parts", shaped(
        cfg, ["PPP", "PPP", "PPP"], {"P": "ecc2:sunnarium_part"}, "ecc2:sunnarium",
    ))
    emit("sunnarium_alloy", shaped(
        cfg, ["AAA", "ASA", "AAA"], {"A": "ic2:alloy", "S": "ecc2:sunnarium"}, "ecc2:sunnarium_alloy",
    ))
    emit("irradiant_uranium", shaped(
        cfg, ["GGG", "GUG", "GGG"], {"G": "minecraft:glowstone_dust", "U": cfg.tag("ingots/uranium")}, "ecc2:irradiant_uranium",
    ))
    emit("enriched_sunnarium", shaped(
        cfg, ["UUU", "USU", "UUU"], {"U": "ecc2:irradiant_uranium", "S": "ecc2:sunnarium"}, "ecc2:enriched_sunnarium",
    ))
    emit("enriched_sunnarium_alloy", shaped(
        cfg, [" A ", "ASA", " A "], {"A": "ecc2:sunnarium_alloy", "S": "ecc2:enriched_sunnarium"}, "ecc2:enriched_sunnarium_alloy",
    ))
    emit("irradiant_glass_pane", shaped(
        cfg, ["GGG", "DSD", "GGG"],
        {"G": "ic2:reinforced_glass", "D": "minecraft:glowstone_dust", "S": "ecc2:sunnarium_part"},
        "ecc2:irradiant_glass_pane", 6,
    ))
    emit("iridium_iron_plate", shaped(
        cfg, ["III", "IRI", "III"], {"I": "ic2:iron_plate", "R": cfg.tag("ingots/iridium")}, "ecc2:iridium_iron_plate",
    ))
    emit("reinforced_iridium_iron_plate", shaped(
        cfg, ["ACA", "CPC", "ACA"],
        {"A": "ic2:alloy", "C": "ic2:carbon_plate", "P": "ecc2:iridium_iron_plate"},
        "ecc2:reinforced_iridium_iron_plate",
    ))
    emit("irradiant_reinforced_plate", shaped(
        cfg, ["RDR", "GPG", "RMR"],
        {
            "R": "minecraft:redstone",
            "D": "ecc2:sunnarium_part",
            "G": "minecraft:glowstone_dust",
            "P": "ecc2:reinforced_iridium_iron_plate",
            "M": "ic2:industrial_diamond",
        },
        "ecc2:irradiant_reinforced_plate",
    ))
    emit("mt_core", shaped(
        cfg, ["GNG", "G G", "GNG"],
        {"G": "ecc2:irradiant_glass_pane", "N": "ic2:advanced_heat_vent"},
        "ecc2:mt_core",
    ))
    emit("quantum_core", shaped(
        cfg, ["EAE", "ANA", "EAE"],
        {"E": "ecc2:enriched_sunnarium_alloy", "A": "minecraft:ender_eye", "N": "minecraft:nether_star"},
        "ecc2:quantum_core",
    ))
    emit("molecular_transformer", shaped(
        cfg, ["MFM", "CTC", "MFM"],
        {"M": "ic2:advanced_machine", "F": "ic2:ev_transformer", "C": "ic2:advanced_circuit", "T": "ecc2:mt_core"},
        "ecc2:molecular_transformer",
    ))

    # --- ASP Solar Panels (NO recipe for creative-only quantum_generator!) ---
    emit("advanced_solar_panel", shaped(
        cfg, ["GGG", "ASA", "CMC"],
        {
            "G": "ecc2:irradiant_glass_pane",
            "A": "ic2:alloy",
            "S": "ic2:solar_generator",
            "C": "ic2:advanced_circuit",
            "M": "ic2:advanced_machine",
        },
        "ecc2:advanced_solar_panel",
    ))
    emit("hybrid_solar_panel", shaped(
        cfg, ["CLC", "PAP", "EKE"],
        {
            "C": "ic2:carbon_plate",
            "L": "minecraft:lapis_block",
            "P": "ecc2:irradiant_reinforced_plate",
            "A": "ecc2:advanced_solar_panel",
            "E": "ic2:advanced_circuit",
            "K": "ic2:lapotron_crystal",
        },
        "ecc2:hybrid_solar_panel",
    ))
    emit("ultimate_hybrid_solar_panel", shaped(
        cfg, ["GCG", "SHS", "ECE"],
        {
            "G": "minecraft:lapis_block",
            "C": "ic2:coal_chunk",
            "S": "ecc2:enriched_sunnarium_alloy",
            "H": "ecc2:hybrid_solar_panel",
            "E": "ic2:advanced_circuit",
        },
        "ecc2:ultimate_hybrid_solar_panel",
    ))
    emit("ultimate_hybrid_solar_panel_8x", shaped(
        cfg, ["HHH", "HCH", "HHH"],
        {"H": "ecc2:hybrid_solar_panel", "C": "ic2:advanced_circuit"},
        "ecc2:ultimate_hybrid_solar_panel",
    ))
    emit("hybrid_solar_panel_split", shapeless(
        cfg, ["ecc2:ultimate_hybrid_solar_panel"], "ecc2:hybrid_solar_panel", 8,
    ))
    emit("quantum_solar_panel", shaped(
        cfg, ["UUU", "UQU", "UUU"],
        {"U": "ecc2:ultimate_hybrid_solar_panel", "Q": "ecc2:quantum_core"},
        "ecc2:quantum_solar_panel",
    ))

    # --- ASP Solar Helmets (3 + shapeless color-preserving upgrade from Hybrid -> Ultimate) ---
    emit("advanced_solar_helmet", shaped(
        cfg, [" S ", "CNC", "GGG"],
        {"S": "ecc2:advanced_solar_panel", "C": "ic2:advanced_circuit", "N": "ic2:nano_helmet", "G": "ic2:glass_fibre_cable"},
        "ecc2:advanced_solar_helmet",
    ))
    emit("hybrid_solar_helmet", shaped(
        cfg, [" S ", "CQC", "GGG"],
        {"S": "ecc2:hybrid_solar_panel", "C": "ic2:advanced_circuit", "Q": "ic2:quantum_helmet", "G": "ic2:glass_fibre_cable"},
        "ecc2:hybrid_solar_helmet",
    ))
    emit("ultimate_hybrid_solar_helmet", shaped(
        cfg, [" S ", "CQC", "GGG"],
        {"S": "ecc2:ultimate_hybrid_solar_panel", "C": "ic2:advanced_circuit", "Q": "ic2:quantum_helmet", "G": "ic2:glass_fibre_cable"},
        "ecc2:ultimate_hybrid_solar_helmet",
    ))
    emit("ultimate_hybrid_solar_helmet_upgrade", shapeless(
        cfg, ["ecc2:hybrid_solar_helmet", "ecc2:ultimate_hybrid_solar_panel"],
        "ecc2:ultimate_hybrid_solar_helmet",
    ))

    # --- 6 Advanced Wind & Water Kinetic Generators (8 recipes incl. durable/overclocked -> precision) ---
    for kind, base_id in [("wind", "ic2:wind_kinetic_generator"), ("water", "ic2:water_kinetic_generator")]:
        emit(f"durable_{kind}_kinetic_generator", shaped(
            cfg, ["ASA", "RWR", "ASA"],
            {"A": "ic2:alloy", "S": "ic2:steel_plate", "R": "ic2:rubber", "W": base_id},
            f"ecc2:durable_{kind}_kinetic_generator",
        ))
        emit(f"overclocked_{kind}_kinetic_generator", shaped(
            cfg, ["OMC", "AWA", "CMO"],
            {
                "O": "ic2:overclocker_upgrade",
                "M": "ic2:electric_motor",
                "C": "ic2:coil",
                "A": "ic2:advanced_circuit",
                "W": base_id,
            },
            f"ecc2:overclocked_{kind}_kinetic_generator",
        ))
        for src_tier in ("durable", "overclocked"):
            emit(f"precision_{kind}_kinetic_generator_from_{src_tier}", shaped(
                cfg, ["ISI", "CWC", "LME"],
                {
                    "I": "ic2:iridium",
                    "S": "ecc2:sunnarium_alloy",
                    "C": "ic2:carbon_plate",
                    "W": f"ecc2:{src_tier}_{kind}_kinetic_generator",
                    "L": "ic2:lapotron_crystal",
                    "M": "ic2:advanced_machine",
                    "E": "ic2:advanced_circuit",
                },
                f"ecc2:precision_{kind}_kinetic_generator",
            ))

    # --- Giant Machines (Mid-tier Megastructure Component + 5 Giant Machine Units) ---
    emit("megastructure_component", shaped(
        cfg, ["CAC", "EME", "GAG"],
        {
            "C": "ic2:carbon_plate",
            "A": "ic2:alloy",
            "E": "ic2:circuit",
            "M": "ic2:machine",
            "G": "ic2:reinforced_glass",
        },
        "ecc2:megastructure_component", 2,
    ))
    giant_sources = [
        ("macerator", "ic2:macerator"),
        ("compressor", "ic2:compressor"),
        ("recycler", "ic2:recycler"),
        ("extractor", "ic2:extractor"),
        ("ore_washer", "ic2:ore_washing_plant"),
    ]
    for kind, src_machine in giant_sources:
        emit(f"giant_{kind}_unit", shapeless(
            cfg, [src_machine, "ecc2:megastructure_component"], f"ecc2:giant_{kind}_unit",
        ))

    # --- Water Gas / Gas Furnace / Gas Generator / Culture Incubator / Organic Fermenter (5) ---
    emit("water_gas_machine", shaped(
        cfg, ["ISI", "UMU", "EIE"],
        {
            "I": "ic2:iron_plate",
            "S": "ic2:solar_generator",
            "U": "ic2:fluid_cell",
            "M": "ic2:machine",
            "E": "ic2:circuit",
        },
        "ecc2:water_gas_machine",
    ))
    emit("gas_furnace", shaped(
        cfg, ["IFI", "UMU", "IEI"],
        {
            "I": "ic2:iron_plate",
            "F": "ic2:iron_furnace",
            "U": "ic2:fluid_cell",
            "M": "ic2:machine",
            "E": "ic2:circuit",
        },
        "ecc2:gas_furnace",
    ))
    emit("gas_generator", shaped(
        cfg, ["CGC", "UMU", "EOE"],
        {
            "C": "ic2:insulated_copper_cable",
            "G": "ic2:generator",
            "U": "ic2:fluid_cell",
            "M": "ic2:machine",
            "E": "ic2:circuit",
            "O": "ic2:electric_motor",
        },
        "ecc2:gas_generator",
    ))
    emit("culture_incubator", shaped(
        cfg, ["GGG", "UMU", "CEC"],
        {
            "G": "minecraft:glass",
            "U": "ic2:fluid_cell",
            "M": "ic2:machine",
            "C": "ic2:copper_plate",
            "E": "ic2:circuit",
        },
        "ecc2:culture_incubator",
    ))
    emit("organic_fermenter", shaped(
        cfg, ["RFR", "UMU", "ECE"],
        {
            "R": "ic2:reinforced_glass",
            "F": "ic2:fermenter",
            "U": "ic2:fluid_cell",
            "M": "ic2:machine",
            "E": "ic2:circuit",
            "C": "ic2:copper_plate",
        },
        "ecc2:organic_fermenter",
    ))

    return count


def generate_mt_recipes(out_root: Path, cfg: TargetConfig) -> int:
    """Emit all 22 canonical ASP 4.3.0 Molecular Transformer JSON recipes."""
    mt_dir = out_root / "data" / MOD_ID / cfg.recipe_dir / "molecular_transformer"
    # Tuple: (name, input_spec, input_count, output_spec_or_tag, output_count, energy_eu, required_tags)
    mt_entries: List[Tuple[str, str, int, str, int, int, List[str]]] = [
        # 13 unconditional vanilla + IC2 + EcC2 entries
        ("wither_skull_to_nether_star", "minecraft:wither_skeleton_skull", 1, "minecraft:nether_star", 1, 250_000_000, []),
        ("iron_to_iridium_ore", cfg.tag("ingots/iron"), 1, "ic2:iridium_ore", 1, 9_000_000, []),
        ("netherrack_to_gunpowder", "minecraft:netherrack", 1, "minecraft:gunpowder", 2, 70_000, []),
        ("sand_to_gravel", "minecraft:sand", 1, "minecraft:gravel", 1, 50_000, []),
        ("dirt_to_clay", "minecraft:dirt", 1, "minecraft:clay", 1, 50_000, []),
        ("charcoal_to_coal", "minecraft:charcoal", 1, "minecraft:coal", 1, 60_000, []),
        ("glowstone_dust_to_sunnarium_part", "minecraft:glowstone_dust", 1, "ecc2:sunnarium_part", 1, 1_000_000, []),
        ("glowstone_to_sunnarium", "minecraft:glowstone", 1, "ecc2:sunnarium", 1, 9_000_000, []),
        ("yellow_wool_to_glowstone", "minecraft:yellow_wool", 1, "minecraft:glowstone", 1, 500_000, []),
        ("blue_wool_to_lapis_block", "minecraft:blue_wool", 1, "minecraft:lapis_block", 1, 500_000, []),
        ("red_wool_to_redstone_block", "minecraft:red_wool", 1, "minecraft:redstone_block", 1, 500_000, []),
        ("coal_to_industrial_diamond", "minecraft:coal", 1, "ic2:industrial_diamond", 1, 9_000_000, []),
        ("industrial_diamond_to_diamond", "ic2:industrial_diamond", 1, "minecraft:diamond", 1, 1_000_000, []),
        # 9 conditional external-mod tag-aware entries
        ("lapis_to_sapphire", "minecraft:lapis_lazuli", 1, cfg.tag("gems/sapphire"), 1, 5_000_000, [cfg.tag("gems/sapphire")]),
        ("redstone_to_ruby", "minecraft:redstone", 1, cfg.tag("gems/ruby"), 1, 5_000_000, [cfg.tag("gems/ruby")]),
        ("titanium_dust_to_chrome_dust", cfg.tag("dusts/titanium"), 1, cfg.tag("dusts/chrome"), 1, 500_000, [cfg.tag("dusts/titanium"), cfg.tag("dusts/chrome")]),
        ("titanium_ingot_to_chrome_ingot", cfg.tag("ingots/titanium"), 1, cfg.tag("ingots/chrome"), 1, 500_000, [cfg.tag("ingots/titanium"), cfg.tag("ingots/chrome")]),
        ("quartz_to_certus_quartz", "minecraft:quartz", 1, cfg.tag("gems/certus_quartz"), 1, 500_000, [cfg.tag("gems/certus_quartz")]),
        ("copper_to_nickel", cfg.tag("ingots/copper"), 1, cfg.tag("ingots/nickel"), 1, 300_000, [cfg.tag("ingots/nickel")]),
        ("tin_to_silver", cfg.tag("ingots/tin"), 1, cfg.tag("ingots/silver"), 1, 500_000, [cfg.tag("ingots/tin"), cfg.tag("ingots/silver")]),
        ("silver_to_gold", cfg.tag("ingots/silver"), 1, "minecraft:gold_ingot", 1, 500_000, [cfg.tag("ingots/silver")]),
        ("gold_to_platinum", cfg.tag("ingots/gold"), 1, cfg.tag("ingots/platinum"), 1, 9_000_000, [cfg.tag("ingots/platinum")]),
    ]

    for name, inp, in_cnt, out_spec, out_cnt, energy, req_tags in mt_entries:
        in_obj = {"tag": inp[1:]} if inp.startswith("#") else {"item": inp}
        if out_spec.startswith("#"):
            out_obj: Dict[str, Any] = {"tag": out_spec[1:], "count": out_cnt}
        else:
            out_obj = cfg.result(out_spec, out_cnt)
        doc: Dict[str, Any] = {
            "type": f"{MOD_ID}:molecular_transformer",
            "input": in_obj,
            "input_count": in_cnt,
            "output": out_obj,
            "energy": energy,
        }
        if req_tags:
            doc[cfg.cond_key] = [cfg.tag_not_empty_condition(t) for t in req_tags]
        write_json(mt_dir / f"{name}.json", doc)

    return len(mt_entries)


# =============================================================================
# 3. LOOT TABLES, TAGS & WORLDGEN
# =============================================================================

def generate_loot_tables(out_root: Path, cfg: TargetConfig) -> int:
    ldir = out_root / "data" / MOD_ID / cfg.loot_dir / "blocks"
    count = 0
    for bid in ALL_PLACEABLE_BLOCKS:
        # All 23 blocks (including methane_ice_cluster: drops itself with zero fortune bonus!)
        doc = {
            "type": "minecraft:block",
            "pools": [
                {
                    "rolls": 1,
                    "conditions": [{"condition": "minecraft:survives_explosion"}],
                    "entries": [{"type": "minecraft:item", "name": f"{MOD_ID}:{bid}"}],
                }
            ],
        }
        write_json(ldir / f"{bid}.json", doc)
        count += 1
    return count


def generate_tags(out_root: Path, cfg: TargetConfig) -> int:
    count = 0

    def emit_tag(ns: str, kind_dir: str, name: str, values: List[Any]) -> None:
        nonlocal count
        write_json(
            out_root / "data" / ns / "tags" / kind_dir / f"{name}.json",
            {"replace": False, "values": values},
        )
        count += 1

    # Minecraft block mining tags
    emit_tag("minecraft", cfg.tag_block_dir, "mineable/pickaxe", [f"{MOD_ID}:{b}" for b in ALL_PLACEABLE_BLOCKS])
    iron_tier_blocks = (
        ["methane_ice_cluster"]
        + SOLAR_PANELS
        + ["molecular_transformer"]
        + WIND_KINETIC_GENERATORS
        + WATER_KINETIC_GENERATORS
        + GIANT_UNIT_BLOCKS
    )
    emit_tag("minecraft", cfg.tag_block_dir, "needs_iron_tool", [f"{MOD_ID}:{b}" for b in iron_tier_blocks])

    # Vanilla dyeable tag (enables workbench dyeing for Hybrid & Ultimate Solar Helmets)
    emit_tag("minecraft", cfg.tag_item_dir, "dyeable", [f"{MOD_ID}:{h}" for h in sorted(DYEABLE_HELMETS)])

    # Common ingot tags (c: for 1.21.1/26.x, forge: for 1.19.2)
    emit_tag(cfg.common_ns, cfg.tag_item_dir, "ingots/iridium", [f"{MOD_ID}:iridium_ingot"])
    emit_tag(cfg.common_ns, cfg.tag_item_dir, "ingots/uranium", [f"{MOD_ID}:uranium_ingot", {"id": "ic2:uranium_ingot", "required": False}])

    # Common fluid tags
    emit_tag(cfg.common_ns, cfg.tag_fluid_dir, "water_gas", [f"{MOD_ID}:water_gas", f"{MOD_ID}:flowing_water_gas"])
    emit_tag(cfg.common_ns, cfg.tag_fluid_dir, "natural_gas", [f"{MOD_ID}:natural_gas", f"{MOD_ID}:flowing_natural_gas"])
    emit_tag(cfg.common_ns, cfg.tag_fluid_dir, "culture_medium", [f"{MOD_ID}:culture_medium", f"{MOD_ID}:flowing_culture_medium"])
    emit_tag(cfg.common_ns, cfg.tag_fluid_dir, "biogas", [
        {"id": "ic2:biogas", "required": False},
        {"id": "ic2:flowing_biogas", "required": False},
    ])

    # EcoCraft 2 unified gas_fuel fluid tag (Water Gas + Natural Gas + Biogas)
    emit_tag(MOD_ID, cfg.tag_fluid_dir, "gas_fuel", [
        f"{MOD_ID}:water_gas",
        f"{MOD_ID}:flowing_water_gas",
        f"{MOD_ID}:natural_gas",
        f"{MOD_ID}:flowing_natural_gas",
        {"id": "ic2:biogas", "required": False},
        {"id": "ic2:flowing_biogas", "required": False},
    ])

    # Biome tag for seafloor Methane Ice Cluster worldgen
    emit_tag(MOD_ID, "worldgen/biome", "has_methane_ice_cluster", [
        "minecraft:deep_ocean",
        "minecraft:deep_lukewarm_ocean",
        "minecraft:deep_cold_ocean",
        "minecraft:deep_frozen_ocean",
        "minecraft:frozen_ocean",
    ])

    return count


def generate_worldgen(out_root: Path, cfg: TargetConfig) -> int:
    data_mod = out_root / "data" / MOD_ID

    # ConfiguredFeature: 3-7 block seafloor crystalline cluster replacing ocean seafloor sediments & shallow stone
    configured = {
        "type": "minecraft:ore",
        "config": {
            "size": 6,
            "discard_chance_on_air_exposure": 0.0,
            "targets": [
                {
                    "target": {"predicate_type": "minecraft:block_match", "block": "minecraft:gravel"},
                    "state": {"Name": f"{MOD_ID}:methane_ice_cluster"},
                },
                {
                    "target": {"predicate_type": "minecraft:block_match", "block": "minecraft:sand"},
                    "state": {"Name": f"{MOD_ID}:methane_ice_cluster"},
                },
                {
                    "target": {"predicate_type": "minecraft:block_match", "block": "minecraft:dirt"},
                    "state": {"Name": f"{MOD_ID}:methane_ice_cluster"},
                },
                {
                    "target": {"predicate_type": "minecraft:block_match", "block": "minecraft:clay"},
                    "state": {"Name": f"{MOD_ID}:methane_ice_cluster"},
                },
                {
                    "target": {"predicate_type": "minecraft:tag_match", "tag": "minecraft:stone_ore_replaceables"},
                    "state": {"Name": f"{MOD_ID}:methane_ice_cluster"},
                },
            ],
        },
    }
    write_json(data_mod / "worldgen" / "configured_feature" / "methane_ice_cluster.json", configured)

    # PlacedFeature: spawns directly along the seafloor & shallow benthic sediment under deep/frozen oceans
    placed = {
        "feature": f"{MOD_ID}:methane_ice_cluster",
        "placement": [
            {"type": "minecraft:count", "count": 7},
            {"type": "minecraft:in_square"},
            {"type": "minecraft:heightmap", "heightmap": "OCEAN_FLOOR_WG"},
            {"type": "minecraft:biome"},
        ],
    }
    write_json(data_mod / "worldgen" / "placed_feature" / "methane_ice_cluster.json", placed)

    # BiomeModifier: deep ocean variants + frozen ocean
    biome_mod = {
        "type": cfg.biome_mod_type,
        "biomes": f"#{MOD_ID}:has_methane_ice_cluster",
        "features": f"{MOD_ID}:methane_ice_cluster",
        "step": "underground_ores",
    }
    write_json(data_mod / cfg.biome_mod_dir / "add_methane_ice_cluster.json", biome_mod)

    return 3


def generate_target(target: str, output_dir: Path) -> Dict[str, int]:
    cfg = TargetConfig(target)
    output_dir.mkdir(parents=True, exist_ok=True)
    return {
        "assets_json": generate_assets(output_dir, cfg),
        "crafting_recipes": generate_crafting_recipes(output_dir, cfg),
        "mt_recipes": generate_mt_recipes(output_dir, cfg),
        "loot_tables": generate_loot_tables(output_dir, cfg),
        "tags": generate_tags(output_dir, cfg),
        "worldgen": generate_worldgen(output_dir, cfg),
    }


def main() -> None:
    parser = argparse.ArgumentParser(description="Generate EcoCraft 2 multi-version resource & data JSONs.")
    parser.add_argument("--target", required=True, choices=TARGETS, help="Target platform version")
    parser.add_argument("--output", required=True, type=Path, help="Output resource/data root directory")
    args = parser.parse_args()

    stats = generate_target(args.target, args.output)
    total = sum(stats.values())
    print(f"[{args.target}] Generated {total} JSON files into {args.output}: {stats}")


if __name__ == "__main__":
    main()
