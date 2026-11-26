#!/usr/bin/env python3
"""Comprehensive asset, localization, GUI coordinate, and 4-target data pack verifier for EcoCraft 2."""

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
    FLUID_BLOCKS,
    STANDALONE_ITEMS,
    TARGETS,
    TargetConfig,
    generate_target,
)

REPO_ROOT = Path(__file__).resolve().parent.parent
ASSETS_ROOT = REPO_ROOT / "assets" / "ecc2"
TEX_ROOT = ASSETS_ROOT / "textures"
LANG_ROOT = ASSETS_ROOT / "lang"
GUI_COORDS_PATH = Path(__file__).resolve().parent / "gui_coordinates.json"
IC2_LIBS_DIR = Path(os.environ.get("ECC2_IC2_LIBS_DIR", str(REPO_ROOT.parent / ".ic2-libs")))
IC2_REF_JAR = IC2_LIBS_DIR / "ic2-neoforge-1.21.1-1.0.8.jar"

PATH_RE = re.compile(r"^[a-z0-9/._-]+$")


class Verifier:
    def __init__(self) -> None:
        self.errors: List[str] = []
        self.stats: Dict[str, Any] = {}

    def fail(self, msg: str) -> None:
        self.errors.append(msg)

    def load_ic2_known_ids(self) -> Set[str]:
        """Load verified IC2NE 1.0.6 item/block IDs from read-only local JAR if present."""
        if not IC2_REF_JAR.exists():
            return set()
        with zipfile.ZipFile(IC2_REF_JAR) as z:
            lang = json.loads(z.read("assets/ic2/lang/en_us.json"))
        ids = set()
        for k in lang:
            if k.startswith(("item.ic2.", "block.ic2.")):
                parts = k.split(".")
                if len(parts) == 3:
                    ids.add(f"ic2:{parts[2]}")
        return ids

    def verify_textures(self) -> None:
        pngs = sorted(TEX_ROOT.rglob("*.png"))
        mcmetas = sorted(TEX_ROOT.rglob("*.mcmeta"))
        self.stats["png_count"] = len(pngs)
        self.stats["mcmeta_count"] = len(mcmetas)

        if not pngs:
            self.fail("No PNG files found under assets/ecc2/textures/")
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
            "block/giant_port_full.png",
            "block/giant_port_input.png",
            "block/giant_port_output.png",
            "block/giant_port_blocked.png",
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
        ]
        for rel in must_exist:
            if not (TEX_ROOT / rel).exists():
                self.fail(f"Required texture/mcmeta missing: assets/ecc2/textures/{rel}")

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
            if f"block.ecc2.{b}" not in en:
                self.fail(f"Missing block translation key: block.ecc2.{b}")
        for it in STANDALONE_ITEMS:
            if f"item.ecc2.{it}" not in en:
                self.fail(f"Missing item translation key: item.ecc2.{it}")
        for fl in FLUID_BLOCKS:
            if f"fluid_type.ecc2.{fl}" not in en:
                self.fail(f"Missing fluid_type translation key: fluid_type.ecc2.{fl}")
        if en.get("item.ecc2.enriched_sunnarium_alloy") != "Enriched Sunnarium Alloy":
            self.fail("item.ecc2.enriched_sunnarium_alloy must be 'Enriched Sunnarium Alloy'")

    def verify_target_pack(self, target: str, out_dir: Path, ic2_ids: Set[str]) -> Dict[str, int]:
        cfg = TargetConfig(target)
        stats = generate_target(target, out_dir)
        assets_dir = out_dir / "assets" / "ecc2"
        data_dir = out_dir / "data" / "ecc2"

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
            for state_key, entry in doc.get("variants", {}).items():
                entries = entry if isinstance(entry, list) else [entry]
                for e in entries:
                    m = e.get("model", "")
                    if m.startswith("ecc2:"):
                        mpath = assets_dir / "models" / f"{m.split(':', 1)[1]}.json"
                        if not mpath.exists():
                            self.fail(f"[{target}] Blockstate {bs.name} ({state_key}) references missing model {m}")

        # 2. Check all block & item models reference existing parent models & existing PNG textures
        for mp in sorted((assets_dir / "models").rglob("*.json")):
            doc = json.loads(mp.read_text(encoding="utf-8"))
            parent = doc.get("parent", "")
            if parent.startswith("ecc2:"):
                ppath = assets_dir / "models" / f"{parent.split(':', 1)[1]}.json"
                if not ppath.exists():
                    self.fail(f"[{target}] Model {mp.relative_to(assets_dir)} references missing parent {parent}")
            for tkey, tval in doc.get("textures", {}).items():
                if tval.startswith("#"):
                    continue
                if tval.startswith("ecc2:"):
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
                self.fail(f"[{target}] Missing 26.x item definition: assets/ecc2/items/{missing}.json")
            for ip in sorted(items_dir.glob("*.json")):
                doc = json.loads(ip.read_text(encoding="utf-8"))
                m = doc.get("model", {}).get("model", "")
                if m.startswith("ecc2:"):
                    mpath = assets_dir / "models" / f"{m.split(':', 1)[1]}.json"
                    if not mpath.exists():
                        self.fail(f"[{target}] Item def {ip.name} references missing model {m}")

        if not cfg.is_1192:
            eq_dir = assets_dir / "equipment"
            for ep in sorted(eq_dir.glob("*.json")):
                doc = json.loads(ep.read_text(encoding="utf-8"))
                for layer in doc.get("layers", {}).get("humanoid", []):
                    tex = layer.get("texture", "")
                    if tex.startswith("ecc2:"):
                        tpath = TEX_ROOT / "entity" / "equipment" / "humanoid" / f"{tex.split(':', 1)[1]}.png"
                        if not tpath.exists():
                            self.fail(f"[{target}] Equipment {ep.name} references missing humanoid texture {tex}")

        # 4. Check crafting recipes + ensure quantum_generator has NO survival recipe + validate IC2 IDs
        rdir = data_dir / cfg.recipe_dir
        craft_files = sorted(rdir.glob("*.json"))
        mt_files = sorted((rdir / "molecular_transformer").glob("*.json"))
        if len(mt_files) != 22:
            self.fail(f"[{target}] Expected 22 Molecular Transformer recipes, found {len(mt_files)}")

        ecc2_valid = {f"ecc2:{x}" for x in ALL_PLACEABLE_BLOCKS + STANDALONE_ITEMS}

        def check_item_id(item_id: str, src: str) -> None:
            if item_id.startswith("#"):
                return
            if item_id.startswith("ecc2:") and item_id not in ecc2_valid:
                self.fail(f"[{target}] Unknown ecc2 item {item_id} in {src}")
            if item_id.startswith("ic2:") and ic2_ids and item_id not in ic2_ids:
                self.fail(f"[{target}] Unknown IC2NE 1.0.6 item {item_id} in {src}")

        for rf in craft_files:
            doc = json.loads(rf.read_text(encoding="utf-8"))
            res = doc.get("result", {})
            res_id = res.get("item") if cfg.is_1192 else res.get("id")
            if not res_id:
                self.fail(f"[{target}] Recipe {rf.name} missing version-appropriate result key")
            elif res_id == "ecc2:quantum_generator":
                self.fail(f"[{target}] Creative-only ecc2:quantum_generator MUST NOT have a crafting recipe!")
            else:
                check_item_id(res_id, rf.name)

            if doc.get("type") == "minecraft:crafting_shaped":
                for k, v in doc.get("key", {}).items():
                    if cfg.is_26x:
                        if not isinstance(v, str):
                            self.fail(f"[{target}] 26.x shaped key {k} in {rf.name} must be string, got {type(v)}")
                        else:
                            check_item_id(v, rf.name)
                    else:
                        if not isinstance(v, dict) or not ("item" in v or "tag" in v):
                            self.fail(f"[{target}] {target} shaped key {k} in {rf.name} must be item/tag dict")
                        elif "item" in v:
                            check_item_id(v["item"], rf.name)
            elif doc.get("type") in ("minecraft:crafting_shapeless", "ecc2:solar_helmet_upgrade"):
                for ing in doc.get("ingredients", []):
                    if cfg.is_26x:
                        if not isinstance(ing, str):
                            self.fail(f"[{target}] 26.x shapeless ingredient in {rf.name} must be string")
                        else:
                            check_item_id(ing, rf.name)
                    else:
                        if not isinstance(ing, dict) or not ("item" in ing or "tag" in ing):
                            self.fail(f"[{target}] {target} shapeless ingredient in {rf.name} must be item/tag dict")
                        elif "item" in ing:
                            check_item_id(ing["item"], rf.name)

        for mf in mt_files:
            doc = json.loads(mf.read_text(encoding="utf-8"))
            if doc.get("type") != "ecc2:molecular_transformer":
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

        return stats


def main() -> None:
    parser = argparse.ArgumentParser(description="Verify EcoCraft 2 art, lang, GUI coords, and 4-target packs.")
    parser.add_argument("--check-dir", type=Path, default=Path("/tmp/ecc2-data-check"), help="Directory for 4-target generation check")
    args = parser.parse_args()

    v = Verifier()
    ic2_ids = v.load_ic2_known_ids()
    v.verify_textures()
    v.verify_gui_coordinates()
    v.verify_lang()

    if args.check_dir.exists():
        shutil.rmtree(args.check_dir)
    args.check_dir.mkdir(parents=True, exist_ok=True)

    target_stats: Dict[str, Dict[str, int]] = {}
    for t in TARGETS:
        target_stats[t] = v.verify_target_pack(t, args.check_dir / t, ic2_ids)

    print("=== EcoCraft 2 Asset & Data Verification Summary ===")
    print(f"PNG textures: {v.stats.get('png_count', 0)} | .mcmeta animations: {v.stats.get('mcmeta_count', 0)}")
    print(f"GUIs documented in gui_coordinates.json: {v.stats.get('gui_count', 0)} (+ port_access_gear icon)")
    print(f"Lang keys: en_us={v.stats.get('lang_keys_en', 0)}, zh_cn={v.stats.get('lang_keys_zh', 0)}")
    print(f"IC2NE 1.0.6 IDs cross-checked: {len(ic2_ids)}")
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
