#!/usr/bin/env python3
"""Comprehensive asset, localization, GUI coordinate, and 4-target data pack verifier for Ecolera."""

from __future__ import annotations

import argparse
import json
import os
import re
import shutil
import sys
import zipfile
from pathlib import Path
from typing import Any, Dict, List, Set

from PIL import Image

from generate_data import (
    ALL_PLACEABLE_BLOCKS,
    DYEABLE_HELMETS,
    FLUID_BLOCKS,
    GIANT_KINDS,
    GIANT_UNIT_BLOCKS,
    SOLAR_HELMETS,
    STANDALONE_ITEMS,
    TARGETS,
    TargetConfig,
    generate_target,
)

REPO_ROOT = Path(__file__).resolve().parent.parent
ASSETS_ROOT = REPO_ROOT / "assets" / "ecera"
TEX_ROOT = ASSETS_ROOT / "textures"
LANG_ROOT = ASSETS_ROOT / "lang"
GUI_COORDS_PATH = Path(__file__).resolve().parent / "gui_coordinates.json"
MHERA_LIBS_DIR = Path(os.environ.get("ECERA_MHERA_LIBS_DIR", str(REPO_ROOT.parent / ".mhera-libs")))
MHERA_REF_JAR = MHERA_LIBS_DIR / "mhera-neoforge-1.21.1-2.0.0.jar"

FORBIDDEN_HARDCODED_RECIPE_ITEMS = {
    "mhera:iron_plate",
    "mhera:copper_plate",
    "mhera:tin_plate",
    "mhera:steel_plate",
    "mhera:rubber",
    "mhera:circuit",
    "mhera:advanced_circuit",
    "minecraft:glass",
    "minecraft:glowstone_dust",
    "minecraft:redstone",
    "minecraft:lapis_block",
    "minecraft:nether_star",
}

PATH_RE = re.compile(r"^[a-z0-9/._-]+$")


class Verifier:
    def __init__(self) -> None:
        self.errors: List[str] = []
        self.stats: Dict[str, Any] = {}

    def fail(self, msg: str) -> None:
        self.errors.append(msg)

    def load_mhera_known_ids(self) -> Set[str]:
        """Load verified Machinera 2.0.0 item/block IDs from read-only local JAR if present."""
        if not MHERA_REF_JAR.exists():
            return set()
        with zipfile.ZipFile(MHERA_REF_JAR) as z:
            lang = json.loads(z.read("assets/mhera/lang/en_us.json"))
        ids = set()
        for k in lang:
            if k.startswith(("item.mhera.", "block.mhera.")):
                parts = k.split(".")
                if len(parts) == 3:
                    ids.add(f"mhera:{parts[2]}")
        return ids

    def verify_textures(self) -> None:
        pngs = sorted(TEX_ROOT.rglob("*.png"))
        mcmetas = sorted(TEX_ROOT.rglob("*.mcmeta"))
        self.stats["png_count"] = len(pngs)
        self.stats["mcmeta_count"] = len(mcmetas)

        if not pngs:
            self.fail("No PNG files found under assets/ecera/textures/")
            return

        for p in pngs:
            rel = p.relative_to(TEX_ROOT).as_posix()
            if not PATH_RE.match(rel):
                self.fail(f"Invalid uppercase/illegal texture path: {rel}")
            try:
                with Image.open(p) as im:
                    im.load()
                    if im.mode != "RGBA":
                        self.fail(f"Texture not RGBA ({im.mode}): {rel}")
                    w, h = im.size
                    if w <= 0 or h <= 0:
                        self.fail(f"Invalid dimensions {w}x{h}: {rel}")
                    if rel.startswith("gui/") and not rel.startswith("gui/icons/"):
                        if (w, h) != (256, 256):
                            self.fail(f"GUI PNG must be 256x256, got {w}x{h}: {rel}")
                    if rel.startswith("models/armor/") or rel.startswith("entity/equipment/humanoid/"):
                        if (w, h) != (64, 32):
                            self.fail(f"Armor texture must be 64x32, got {w}x{h}: {rel}")
                    mcmeta = Path(str(p) + ".mcmeta")
                    if mcmeta.exists():
                        meta = json.loads(mcmeta.read_text(encoding="utf-8"))
                        if "animation" not in meta:
                            self.fail(f"Invalid .mcmeta (missing animation): {rel}.mcmeta")
                        if h % w != 0 or h < w:
                            self.fail(f"Animated strip {rel} has invalid frame height {w}x{h}")
            except Exception as exc:
                self.fail(f"Corrupt PNG {rel}: {exc}")

        # Verify critical explicitly named textures exist
        must_exist = [
            "block/quantum_solar_panel_top.png",
            "block/quantum_solar_panel_top.png.mcmeta",
            "block/quantum_generator.png",
            "block/quantum_generator_active.png",
            "block/molecular_transformer_particle.png",
            "entity/molecular_transformer.png",
            "entity/plasma.png",
            "entity/particles.png",
            "models/armor/advanced_solar_layer_1.png",
            "models/armor/hybrid_solar_layer_1.png",
            "models/armor/hybrid_solar_layer_1_overlay.png",
            "models/armor/ultimate_hybrid_solar_layer_1.png",
            "models/armor/ultimate_hybrid_solar_layer_1_overlay.png",
            "entity/equipment/humanoid/advanced_solar.png",
            "entity/equipment/humanoid/hybrid_solar.png",
            "entity/equipment/humanoid/hybrid_solar_overlay.png",
            "entity/equipment/humanoid/ultimate_hybrid_solar.png",
            "entity/equipment/humanoid/ultimate_hybrid_solar_overlay.png",
            "item/pv_power_upgrade.png",
            "block/methane_ice_cluster.png",
            "block/methane_ice_cluster.png.mcmeta",
            "block/fluid/water_gas_still.png",
            "block/fluid/water_gas_flow.png",
            "block/fluid/natural_gas_still.png",
            "block/fluid/natural_gas_flow.png",
            "block/fluid/culture_medium_still.png",
            "block/fluid/culture_medium_flow.png",
            "gui/solar_panel.png",
            "gui/quantum_generator.png",
            "gui/molecular_transformer.png",
            "gui/kinetic_generator.png",
            "gui/giant_machine.png",
            "gui/water_gas_machine.png",
            "gui/gas_furnace.png",
            "gui/gas_generator.png",
            "gui/culture_incubator.png",
            "gui/organic_fermenter.png",
            "gui/icons/port_access_gear.png",
            "gui/icons/auto_spread.png",
        ]
        for kind in GIANT_KINDS:
            must_exist.extend([
                f"block/giant_{kind}_unit.png",
                f"block/giant_casing_{kind}_top.png",
                f"block/giant_casing_{kind}_side.png",
                f"block/giant_casing_{kind}_bottom.png",
                f"block/giant_core_front_{kind}.png",
                f"block/giant_core_front_{kind}_active.png",
                f"block/giant_core_front_{kind}_active.png.mcmeta",
                f"block/giant_port_{kind}_full.png",
                f"block/giant_port_{kind}_input.png",
                f"block/giant_port_{kind}_output.png",
                f"block/giant_port_{kind}_blocked.png",
            ])
        for rel in must_exist:
            if not (TEX_ROOT / rel).exists():
                self.fail(f"Required texture/mcmeta missing: assets/ecera/textures/{rel}")

    def verify_gui_coordinates(self) -> None:
        if not GUI_COORDS_PATH.exists():
            self.fail(f"Missing GUI coordinates manifest: {GUI_COORDS_PATH}")
            return
        coords = json.loads(GUI_COORDS_PATH.read_text(encoding="utf-8"))
        self.stats["gui_count"] = len([k for k in coords if k != "icons"])
        for name, spec in coords.items():
            if name == "icons":
                continue
            px0, py0, pw, ph = spec["panel"]
            if px0 < 0 or py0 < 0 or px0 + pw > 256 or py0 + ph > 256:
                self.fail(f"GUI {name} panel {spec['panel']} exceeds 256x256")
            for sname, rect in spec.get("sprites", {}).items():
                sx, sy, sw, sh = rect
                if sx < 0 or sy < 0 or sx + sw > 256 or sy + sh > 256:
                    self.fail(f"GUI {name} sprite {sname} {rect} exceeds 256x256")
                # Ensure sprite does not collide with main panel
                overlap_x = sx < px0 + pw and sx + sw > px0
                overlap_y = sy < py0 + ph and sy + sh > py0
                if overlap_x and overlap_y:
                    self.fail(f"GUI {name} sprite {sname} {rect} overlaps panel {spec['panel']}")

    def verify_lang(self) -> None:
        en_p = LANG_ROOT / "en_us.json"
        zh_p = LANG_ROOT / "zh_cn.json"
        if not en_p.exists() or not zh_p.exists():
            self.fail("Missing en_us.json or zh_cn.json")
            return
        en = json.loads(en_p.read_text(encoding="utf-8"))
        zh = json.loads(zh_p.read_text(encoding="utf-8"))
        en_keys, zh_keys = set(en.keys()), set(zh.keys())
        self.stats["lang_keys_en"] = len(en_keys)
        self.stats["lang_keys_zh"] = len(zh_keys)

        for k in sorted(en_keys - zh_keys):
            self.fail(f"Lang key missing in zh_cn.json: {k}")
        for k in sorted(zh_keys - en_keys):
            self.fail(f"Lang key missing in en_us.json: {k}")
        for lang_name, table in [("en_us", en), ("zh_cn", zh)]:
            for k, v in table.items():
                if not isinstance(v, str) or not v.strip():
                    self.fail(f"Empty/non-string lang value in {lang_name}: {k}")

        # Verify 100% coverage of all blocks, items, fluids
        for b in ALL_PLACEABLE_BLOCKS + FLUID_BLOCKS:
            if f"block.ecera.{b}" not in en:
                self.fail(f"Missing block translation key: block.ecera.{b}")
        for it in STANDALONE_ITEMS:
            if f"item.ecera.{it}" not in en:
                self.fail(f"Missing item translation key: item.ecera.{it}")
        for fl in FLUID_BLOCKS:
            if f"fluid_type.ecera.{fl}" not in en:
                self.fail(f"Missing fluid_type translation key: fluid_type.ecera.{fl}")
        if en.get("item.ecera.enriched_sunnarium_alloy") != "Enriched Sunnarium Alloy":
            self.fail("item.ecera.enriched_sunnarium_alloy must be 'Enriched Sunnarium Alloy'")

    def verify_target_pack(self, target: str, out_dir: Path, mhera_ids: Set[str]) -> Dict[str, int]:
        cfg = TargetConfig(target)
        stats = generate_target(target, out_dir)
        assets_dir = out_dir / "assets" / "ecera"
        data_dir = out_dir / "data" / "ecera"

        all_jsons = sorted(out_dir.rglob("*.json"))
        for jp in all_jsons:
            rel = jp.relative_to(out_dir).as_posix()
            if not PATH_RE.match(rel):
                self.fail(f"[{target}] Invalid JSON path: {rel}")
            try:
                json.loads(jp.read_text(encoding="utf-8"))
            except Exception as exc:
                self.fail(f"[{target}] Invalid JSON {rel}: {exc}")

        # 1. Check all blockstates reference existing block models
        for bs in sorted((assets_dir / "blockstates").glob("*.json")):
            doc = json.loads(bs.read_text(encoding="utf-8"))
            variants_dict = doc.get("variants", {})
            if bs.stem.startswith("giant_") and bs.stem.endswith("_unit"):
                if not any("role=front" in k for k in variants_dict):
                    self.fail(f"[{target}] Giant blockstate {bs.name} missing role=front variants")
            for state_key, entry in variants_dict.items():
                entries = entry if isinstance(entry, list) else [entry]
                for e in entries:
                    m = e.get("model", "")
                    if m.startswith("ecera:"):
                        mpath = assets_dir / "models" / f"{m.split(':', 1)[1]}.json"
                        if not mpath.exists():
                            self.fail(f"[{target}] Blockstate {bs.name} ({state_key}) references missing model {m}")

        # 2. Check all block & item models reference existing parent models & existing PNG textures
        for mp in sorted((assets_dir / "models").rglob("*.json")):
            doc = json.loads(mp.read_text(encoding="utf-8"))
            parent = doc.get("parent", "")
            if parent.startswith("ecera:"):
                ppath = assets_dir / "models" / f"{parent.split(':', 1)[1]}.json"
                if not ppath.exists():
                    self.fail(f"[{target}] Model {mp.relative_to(assets_dir)} references missing parent {parent}")
            for tkey, tval in doc.get("textures", {}).items():
                if tval.startswith("#"):
                    continue
                if tval.startswith("ecera:"):
                    tex_rel = tval.split(":", 1)[1]
                    if not PATH_RE.match(tex_rel):
                        self.fail(f"[{target}] Illegal texture path {tval} in {mp.name}")
                    tpath = TEX_ROOT / f"{tex_rel}.png"
                    if not tpath.exists():
                        self.fail(f"[{target}] Model {mp.relative_to(assets_dir)} references missing texture {tval}")

        # 3. Check 26.x items/*.json and 1.21+/26.x equipment/*.json
        if cfg.is_26x:
            items_dir = assets_dir / "items"
            expected_items = set(ALL_PLACEABLE_BLOCKS + STANDALONE_ITEMS)
            actual_items = {p.stem for p in items_dir.glob("*.json")}
            for missing in sorted(expected_items - actual_items):
                self.fail(f"[{target}] Missing 26.x item definition: assets/ecera/items/{missing}.json")
            for ip in sorted(items_dir.glob("*.json")):
                doc = json.loads(ip.read_text(encoding="utf-8"))
                m = doc.get("model", {}).get("model", "")
                if m.startswith("ecera:"):
                    mpath = assets_dir / "models" / f"{m.split(':', 1)[1]}.json"
                    if not mpath.exists():
                        self.fail(f"[{target}] Item def {ip.name} references missing model {m}")

        if not cfg.is_1192:
            eq_dir = assets_dir / "equipment"
            for ep in sorted(eq_dir.glob("*.json")):
                doc = json.loads(ep.read_text(encoding="utf-8"))
                for layer in doc.get("layers", {}).get("humanoid", []):
                    tex = layer.get("texture", "")
                    if tex.startswith("ecera:"):
                        tpath = TEX_ROOT / "entity" / "equipment" / "humanoid" / f"{tex.split(':', 1)[1]}.png"
                        if not tpath.exists():
                            self.fail(f"[{target}] Equipment {ep.name} references missing humanoid texture {tex}")

        # 4. Check crafting recipes + ensure quantum_generator has NO survival recipe + validate MHERA IDs
        rdir = data_dir / cfg.recipe_dir
        craft_files = sorted(rdir.glob("*.json"))
        mt_files = sorted((rdir / "molecular_transformer").glob("*.json"))
        if len(mt_files) != 22:
            self.fail(f"[{target}] Expected 22 Molecular Transformer recipes, found {len(mt_files)}")

        ecera_valid = {f"ecera:{x}" for x in ALL_PLACEABLE_BLOCKS + STANDALONE_ITEMS}

        def check_item_id(item_id: str, src: str) -> None:
            if item_id.startswith("#"):
                return
            if item_id.startswith("ecera:") and item_id not in ecera_valid:
                self.fail(f"[{target}] Unknown ecera item {item_id} in {src}")
            if item_id.startswith("mhera:") and mhera_ids and item_id not in mhera_ids:
                self.fail(f"[{target}] Unknown Machinera 2.0.0 item {item_id} in {src}")

        def check_ingredient_item(item_id: str, src: str) -> None:
            check_item_id(item_id, src)
            if not item_id.startswith("#") and item_id in FORBIDDEN_HARDCODED_RECIPE_ITEMS:
                self.fail(f"[{target}] Hardcoded item {item_id} should use a common tag in {src}")

        for rf in craft_files:
            doc = json.loads(rf.read_text(encoding="utf-8"))
            res = doc.get("result", {})
            res_id = res.get("item") if cfg.is_1192 else res.get("id")
            if not res_id:
                self.fail(f"[{target}] Recipe {rf.name} missing version-appropriate result key")
            elif res_id == "ecera:quantum_generator":
                self.fail(f"[{target}] Creative-only ecc2:quantum_generator MUST NOT have a crafting recipe!")
            else:
                check_item_id(res_id, rf.name)

            if doc.get("type") == "minecraft:crafting_shaped":
                for k, v in doc.get("key", {}).items():
                    if cfg.is_26x:
                        if not isinstance(v, str):
                            self.fail(f"[{target}] 26.x shaped key {k} in {rf.name} must be string, got {type(v)}")
                        else:
                            check_ingredient_item(v, rf.name)
                    else:
                        if not isinstance(v, dict) or not ("item" in v or "tag" in v):
                            self.fail(f"[{target}] {target} shaped key {k} in {rf.name} must be item/tag dict")
                        elif "item" in v:
                            check_ingredient_item(v["item"], rf.name)
            elif doc.get("type") in ("minecraft:crafting_shapeless", "ecera:solar_helmet_upgrade"):
                for ing in doc.get("ingredients", []):
                    if cfg.is_26x:
                        if not isinstance(ing, str):
                            self.fail(f"[{target}] 26.x shapeless ingredient in {rf.name} must be string")
                        else:
                            check_ingredient_item(ing, rf.name)
                    else:
                        if not isinstance(ing, dict) or not ("item" in ing or "tag" in ing):
                            self.fail(f"[{target}] {target} shapeless ingredient in {rf.name} must be item/tag dict")
                        elif "item" in ing:
                            check_ingredient_item(ing["item"], rf.name)

        for mf in mt_files:
            doc = json.loads(mf.read_text(encoding="utf-8"))
            if doc.get("type") != "ecera:molecular_transformer":
                self.fail(f"[{target}] MT recipe {mf.name} wrong type {doc.get('type')}")
            if not isinstance(doc.get("energy"), int) or doc["energy"] <= 0:
                self.fail(f"[{target}] MT recipe {mf.name} invalid energy {doc.get('energy')}")
            if "item" in doc.get("input", {}):
                check_item_id(doc["input"]["item"], f"mt/{mf.name}")
            out = doc.get("output", {})
            if "tag" not in out:
                oid = out.get("item") if cfg.is_1192 else out.get("id")
                if not oid:
                    self.fail(f"[{target}] MT recipe {mf.name} missing output item/id")
                else:
                    check_item_id(oid, f"mt/{mf.name}")

        # 5. Check loot tables (all 23 blocks; methane_ice_cluster has no fortune bonus)
        ldir = data_dir / cfg.loot_dir / "blocks"
        for bid in ALL_PLACEABLE_BLOCKS:
            lp = ldir / f"{bid}.json"
            if not lp.exists():
                self.fail(f"[{target}] Missing loot table for block {bid}")
        mic_loot = (ldir / "methane_ice_cluster.json").read_text(encoding="utf-8")
        if "apply_bonus" in mic_loot or "fortune" in mic_loot:
            self.fail(f"[{target}] methane_ice_cluster loot table must NOT apply fortune multiplication")

        # 6. Check worldgen cross-references
        cf_p = data_dir / "worldgen" / "configured_feature" / "methane_ice_cluster.json"
        pf_p = data_dir / "worldgen" / "placed_feature" / "methane_ice_cluster.json"
        bm_p = data_dir / cfg.biome_mod_dir / "add_methane_ice_cluster.json"
        bt_p = data_dir / "tags" / "worldgen" / "biome" / "has_methane_ice_cluster.json"
        for p in (cf_p, pf_p, bm_p, bt_p):
            if not p.exists():
                self.fail(f"[{target}] Missing worldgen file: {p.relative_to(out_dir)}")

        # 7. Common Tag directory, namespace, rename, parent-aggregation, and version gates
        self._verify_common_tags(target, cfg, out_dir, rdir)

        return stats

    def _verify_common_tags(self, target: str, cfg: TargetConfig, out_dir: Path, rdir: Path) -> None:
        data_root = out_dir / "data"
        forbidden_ns = "c" if cfg.is_1192 else "forge"
        if (data_root / forbidden_ns).exists():
            self.fail(f"[{target}] Forbidden tag/data namespace directory data/{forbidden_ns}/ present")

        bad_tag_dirs = {"item", "block", "fluid"} if cfg.is_1192 else {"items", "blocks", "fluids"}
        for ns_dir in data_root.iterdir():
            tags_dir = ns_dir / "tags"
            if not tags_dir.is_dir():
                continue
            for sub in tags_dir.iterdir():
                if sub.name in bad_tag_dirs:
                    self.fail(f"[{target}] Wrong singular/plural tag directory: {sub.relative_to(out_dir)}")

        # Scan all data JSONs for namespace leakage (c:/neoforge: on 1.19.2; forge: on 1.21+/26.x)
        leak_tokens = ('"c:', '"#c:', '"neoforge:', '"#neoforge:') if cfg.is_1192 else ('"forge:', '"#forge:')
        for jp in sorted(data_root.rglob("*.json")):
            raw = jp.read_text(encoding="utf-8")
            for tok in leak_tokens:
                if tok in raw:
                    self.fail(f"[{target}] Namespace leak {tok} in {jp.relative_to(out_dir)}")

        def read_tag_values(ns: str, kind_dir: str, rel_name: str) -> List[Any]:
            tp = data_root / ns / "tags" / kind_dir / f"{rel_name}.json"
            if not tp.exists():
                self.fail(f"[{target}] Missing required tag: {tp.relative_to(out_dir)}")
                return []
            return json.loads(tp.read_text(encoding="utf-8")).get("values", [])

        ns = cfg.common_ns
        # Parent aggregation gates
        assert_pairs = [
            (ns, cfg.tag_item_dir, "ingots/iridium", ["ecera:iridium_ingot"]),
            (ns, cfg.tag_item_dir, "ingots", [f"#{ns}:ingots/iridium"]),
            (ns, cfg.tag_item_dir, "armors/helmets", [f"ecera:{h}" for h in SOLAR_HELMETS]),
            (ns, cfg.tag_item_dir, "armors", [f"#{ns}:armors/helmets"]),
            (ns, cfg.tag_item_dir, "plates/iridium_iron", ["ecera:iridium_iron_plate"]),
            (ns, cfg.tag_item_dir, "plates/reinforced_iridium_iron", ["ecera:reinforced_iridium_iron_plate"]),
            (ns, cfg.tag_item_dir, "plates/irradiant_reinforced", ["ecera:irradiant_reinforced_plate"]),
            (ns, cfg.tag_item_dir, "plates", [f"#{ns}:plates/iridium_iron", f"#{ns}:plates/reinforced_iridium_iron", f"#{ns}:plates/irradiant_reinforced"]),
            (ns, cfg.tag_item_dir, "buckets/water_gas", ["ecera:water_gas_bucket"]),
            (ns, cfg.tag_item_dir, "buckets/natural_gas", ["ecera:natural_gas_bucket"]),
            (ns, cfg.tag_item_dir, "buckets/culture_medium", ["ecera:culture_medium_bucket"]),
            (ns, cfg.tag_item_dir, "buckets", [f"#{ns}:buckets/water_gas", f"#{ns}:buckets/natural_gas", f"#{ns}:buckets/culture_medium"]),
            (ns, cfg.tag_block_dir, "relocation_not_supported", [f"ecera:{b}" for b in GIANT_UNIT_BLOCKS + ["molecular_transformer"]]),
            (ns, cfg.tag_block_dir, "ores/methane_ice", ["ecera:methane_ice_cluster"]),
            (ns, cfg.tag_item_dir, "ores/methane_ice", ["ecera:methane_ice_cluster"]),
            ("ecera", cfg.tag_fluid_dir, "gas_fuel", [f"#{ns}:water_gas", f"#{ns}:natural_gas"]),
            ("ecera", cfg.tag_item_dir, "culture_nutrients", ["minecraft:bone_meal", "minecraft:rotten_flesh"]),
        ]
        for t_ns, kdir, t_name, required_vals in assert_pairs:
            vals = read_tag_values(t_ns, kdir, t_name)
            for rv in required_vals:
                if rv not in vals:
                    self.fail(f"[{target}] Tag {t_ns}:{t_name} missing required entry {rv}")

        # Negative guards: methane_ice MUST NOT pollute parent ores; irradiant_glass_pane MUST NOT pollute glass_panes
        if (data_root / ns / "tags" / cfg.tag_block_dir / "ores.json").exists():
            self.fail(f"[{target}] methane_ice_cluster must NOT emit parent {ns}:ores block tag")
        if (data_root / ns / "tags" / cfg.tag_item_dir / "ores.json").exists():
            self.fail(f"[{target}] methane_ice_cluster must NOT emit parent {ns}:ores item tag")
        if (data_root / ns / "tags" / cfg.tag_item_dir / "glass_panes.json").exists():
            self.fail(f"[{target}] irradiant_glass_pane must NOT emit {ns}:glass_panes item tag")

        # 1.21+ exclusive vanilla/common tags must NOT appear on Forge 1.19.2
        v121_only_paths = [
            data_root / "minecraft" / "tags" / cfg.tag_item_dir / "dyeable.json",
            data_root / "minecraft" / "tags" / cfg.tag_item_dir / "head_armor.json",
            data_root / "minecraft" / "tags" / cfg.tag_item_dir / "enchantable" / "armor.json",
            data_root / "minecraft" / "tags" / cfg.tag_item_dir / "enchantable" / "head_armor.json",
            data_root / "minecraft" / "tags" / cfg.tag_item_dir / "enchantable" / "equippable.json",
            data_root / ns / "tags" / cfg.tag_fluid_dir / "gaseous.json",
        ]
        for p in v121_only_paths:
            if cfg.is_1192 and p.exists():
                self.fail(f"[{target}] 1.21+ exclusive tag must not exist on 1.19.2: {p.relative_to(out_dir)}")
            if not cfg.is_1192 and not p.exists():
                self.fail(f"[{target}] Missing 1.21+ tag: {p.relative_to(out_dir)}")

        # Cross-version tag rename checks
        ci_doc = json.loads((rdir / "culture_incubator.json").read_text(encoding="utf-8"))
        ci_glass = ci_doc["key"]["G"]
        expected_glass = "forge:glass" if cfg.is_1192 else "c:glass_blocks"
        actual_glass = ci_glass[1:] if isinstance(ci_glass, str) else ci_glass.get("tag", "")
        if actual_glass != expected_glass:
            self.fail(f"[{target}] culture_incubator glass key expected {expected_glass}, got {actual_glass!r}")

        nr_doc = json.loads((rdir / "molecular_transformer" / "netherrack_to_gunpowder.json").read_text(encoding="utf-8"))
        expected_nr = "forge:netherrack" if cfg.is_1192 else "c:netherracks"
        if nr_doc.get("input", {}).get("tag") != expected_nr:
            self.fail(f"[{target}] netherrack_to_gunpowder expected {expected_nr}, got {nr_doc.get('input')}")

        sd_doc = json.loads((rdir / "molecular_transformer" / "sand_to_gravel.json").read_text(encoding="utf-8"))
        expected_sd = "forge:sand" if cfg.is_1192 else "c:sands"
        if sd_doc.get("input", {}).get("tag") != expected_sd:
            self.fail(f"[{target}] sand_to_gravel expected {expected_sd}, got {sd_doc.get('input')}")


def main() -> None:
    parser = argparse.ArgumentParser(description="Verify Ecolera art, lang, GUI coords, and 4-target packs.")
    parser.add_argument("--check-dir", type=Path, default=Path("/tmp/ecera-data-check"), help="Directory for 4-target generation check")
    args = parser.parse_args()

    v = Verifier()
    mhera_ids = v.load_mhera_known_ids()
    v.verify_textures()
    v.verify_gui_coordinates()
    v.verify_lang()

    if args.check_dir.exists():
        shutil.rmtree(args.check_dir)
    args.check_dir.mkdir(parents=True, exist_ok=True)

    target_stats: Dict[str, Dict[str, int]] = {}
    for t in TARGETS:
        target_stats[t] = v.verify_target_pack(t, args.check_dir / t, mhera_ids)

    print("=== Ecolera Asset & Data Verification Summary ===")
    print(f"PNG textures: {v.stats.get('png_count', 0)} | .mcmeta animations: {v.stats.get('mcmeta_count', 0)}")
    print(f"GUIs documented in gui_coordinates.json: {v.stats.get('gui_count', 0)} (+ port_access_gear icon)")
    print(f"Lang keys: en_us={v.stats.get('lang_keys_en', 0)}, zh_cn={v.stats.get('lang_keys_zh', 0)}")
    print(f"Machinera 2.0.0 IDs cross-checked: {len(mhera_ids)}")
    for t, st in target_stats.items():
        print(f"  Target {t:16s}: {st} (total={sum(st.values())} JSONs)")

    if v.errors:
        print(f"\nFAILED with {len(v.errors)} error(s):")
        for e in v.errors:
            print("  ERROR:", e)
        sys.exit(1)
    else:
        print("\nALL CHECKS PASSED WITH 0 ERRORS!")


if __name__ == "__main__":
    main()
