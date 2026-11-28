#!/usr/bin/env python3
"""Clean-room repository audit and built JAR contract verifier for Ecolera (ecera)."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import subprocess
import sys
import zipfile
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent.parent

FORBIDDEN_CLASS_PREFIXES = (
    "com/industrialage/",
    "mezz/jei/",
    "dev/emi/emi/",
    "snownee/jade/",
    "mcp/mobius/waila/",
    "mcjty/theoneprobe/",
    "top/theillusivec4/curios/",
    "com/chocohead/",
)

LEGAL_FILES = ("LICENSE", "COPYING", "LICENSE-ASSETS", "NOTICE.md")

FORBIDDEN_TRACKED_PATTERNS = (
    "asp-research",
    "Advanced Solar Panels",
    ".ic2-libs",
    ".mce-libs",
    ".mhera-libs",
    "raw-extract",
)

FORBIDDEN_SOURCE_PATTERNS = (
    re.compile(r"\bSystem\.out\b"),
    re.compile(r"com\.chocohead"),
    re.compile(r"\badvsolar\b"),
    re.compile(r"asp-research"),
    re.compile(r"\bfunc_\d+_[a-zA-Z]+_?\b"),
    re.compile(r"\bfield_\d+_[a-zA-Z]+_?\b"),
    re.compile(r"/Users/alex/"),
)

ALLOWED_JARS = {
    "gradle/wrapper/gradle-wrapper.jar",
    "forge-1.19.2/gradle/wrapper/gradle-wrapper.jar",
}


def audit_cleanroom() -> list[str]:
    errors: list[str] = []
    tracked = subprocess.check_output(
        ["git", "-C", str(REPO_ROOT), "ls-files"], text=True
    ).splitlines()

    for rel in tracked:
        for pat in FORBIDDEN_TRACKED_PATTERNS:
            if pat in rel:
                errors.append(f"Forbidden tracked path: {rel}")
        if rel.endswith(".jar") and rel not in ALLOWED_JARS:
            errors.append(f"Forbidden tracked JAR: {rel}")

    java_files = [
        REPO_ROOT / rel
        for rel in tracked
        if rel.endswith(".java") and (REPO_ROOT / rel).is_file()
    ]
    for path in java_files:
        text = path.read_text(encoding="utf-8")
        rel = path.relative_to(REPO_ROOT)
        for rx in FORBIDDEN_SOURCE_PATTERNS:
            if rx.search(text):
                errors.append(f"Forbidden pattern {rx.pattern!r} in {rel}")

    # Also verify no hardcoded personal local path leaks in docs/scripts/configs.
    for rel in tracked:
        if rel == "tools/ci/audit_jars.py":
            continue
        p = REPO_ROOT / rel
        if p.is_file() and p.suffix in {".md", ".py", ".gradle", ".properties", ".toml", ".json", ".yml", ".yaml"}:
            if "/Users/alex/" in p.read_text(encoding="utf-8", errors="ignore"):
                errors.append(f"Hardcoded local user path in {rel}")

    print(f"[cleanroom] Checked {len(tracked)} tracked files ({len(java_files)} Java sources): {len(errors)} errors")
    return errors


def audit_jar(jar_path: Path, target: str) -> list[str]:
    errors: list[str] = []
    if not jar_path.is_file():
        return [f"JAR not found: {jar_path}"]

    sha256 = hashlib.sha256(jar_path.read_bytes()).hexdigest()
    is_forge = target.startswith("forge-")

    with zipfile.ZipFile(jar_path) as zf:
        names = set(zf.namelist())

        class_count = 0
        png_count = 0
        json_count = 0

        for name in sorted(names):
            for prefix in FORBIDDEN_CLASS_PREFIXES:
                if name.startswith(prefix):
                    errors.append(f"Leaked third-party/host class in {jar_path.name}: {name}")
            if name.endswith(".class"):
                class_count += 1
            elif name.startswith("assets/ecera/") and name.endswith(".png"):
                png_count += 1
            elif name.endswith(".json") or name.endswith(".mcmeta"):
                json_count += 1
                try:
                    json.loads(zf.read(name).decode("utf-8"))
                except Exception as exc:
                    errors.append(f"Invalid JSON {name} in {jar_path.name}: {exc}")

        for legal in LEGAL_FILES:
            if legal not in names:
                errors.append(f"Missing legal file {legal} in {jar_path.name}")

        if "ecera.mixins.json" not in names:
            errors.append(f"Missing ecera.mixins.json in {jar_path.name}")
        else:
            mixin_cfg = json.loads(zf.read("ecera.mixins.json").decode("utf-8"))
            pkg = mixin_cfg.get("package", "").replace(".", "/")
            for m in mixin_cfg.get("mixins", []) + mixin_cfg.get("client", []):
                cls = f"{pkg}/{m.replace('.', '/')}.class"
                if cls not in names:
                    errors.append(f"Declared Mixin class missing in {jar_path.name}: {cls}")

        meta_path = "META-INF/mods.toml" if is_forge else "META-INF/neoforge.mods.toml"
        if meta_path not in names:
            errors.append(f"Missing {meta_path} in {jar_path.name}")
        else:
            meta_text = zf.read(meta_path).decode("utf-8")
            if "${" in meta_text:
                errors.append(f"Unexpanded template placeholder in {meta_path} of {jar_path.name}")
            if 'modId="ecera"' not in meta_text and 'modId = "ecera"' not in meta_text:
                errors.append(f"Missing ecera modId in {meta_path}")

        if is_forge:
            if "ecera.refmap.json" not in names:
                errors.append(f"Missing ecera.refmap.json in {jar_path.name}")
            manifest = zf.read("META-INF/MANIFEST.MF").decode("utf-8") if "META-INF/MANIFEST.MF" in names else ""
            if "MixinConfigs: ecera.mixins.json" not in manifest:
                errors.append(f"Missing MixinConfigs manifest entry in {jar_path.name}")
            for name in sorted(names):
                if name.startswith(("data/c/", "data/neoforge/", "data/ecera/recipe/", "data/ecera/loot_table/")):
                    errors.append(f"1.21+ data path leaked into Forge 1.19.2 JAR {jar_path.name}: {name}")
                if re.match(r"^data/[^/]+/tags/(item|block|fluid)/", name):
                    errors.append(f"Singular tag directory leaked into Forge 1.19.2 JAR {jar_path.name}: {name}")
            if "data/minecraft/tags/items/dyeable.json" in names:
                errors.append(f"1.21+ minecraft:dyeable tag leaked into Forge 1.19.2 JAR {jar_path.name}")
            for req in (
                "data/forge/tags/items/ingots.json",
                "data/forge/tags/items/armors.json",
                "data/forge/tags/items/buckets.json",
                "data/forge/tags/blocks/relocation_not_supported.json",
                "data/ecera/tags/items/culture_nutrients.json",
            ):
                if req not in names:
                    errors.append(f"Missing required Forge 1.19.2 tag {req} in {jar_path.name}")
        else:
            for name in sorted(names):
                if name.startswith(("data/forge/", "data/ecera/recipes/", "data/ecera/loot_tables/")):
                    errors.append(f"Forge 1.19.2 data path leaked into NeoForge JAR {jar_path.name}: {name}")
                if re.match(r"^data/[^/]+/tags/(items|blocks|fluids)/", name):
                    errors.append(f"Plural tag directory leaked into NeoForge JAR {jar_path.name}: {name}")
            for req in (
                "data/c/tags/item/ingots.json",
                "data/c/tags/item/armors.json",
                "data/c/tags/item/buckets.json",
                "data/c/tags/block/relocation_not_supported.json",
                "data/c/tags/fluid/gaseous.json",
                "data/minecraft/tags/item/head_armor.json",
                "data/ecera/tags/item/culture_nutrients.json",
            ):
                if req not in names:
                    errors.append(f"Missing required NeoForge tag {req} in {jar_path.name}")

        if png_count < 150:
            errors.append(f"Expected >= 150 PNG textures in {jar_path.name}, found {png_count}")
        if class_count < 150:
            errors.append(f"Expected >= 150 compiled classes in {jar_path.name}, found {class_count}")

    print(
        f"[jar:{target}] {jar_path.name} ({jar_path.stat().st_size:,} B, sha256={sha256}) "
        f"classes={class_count} pngs={png_count} jsons={json_count} errors={len(errors)}"
    )
    return errors


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--cleanroom-only", action="store_true")
    parser.add_argument("--jar", type=Path)
    parser.add_argument("--target", type=str, default="")
    args = parser.parse_args()

    errors: list[str] = []
    if args.cleanroom_only or args.jar is None:
        errors.extend(audit_cleanroom())
    if args.jar is not None:
        errors.extend(audit_jar(args.jar, args.target or args.jar.stem))

    if errors:
        for err in errors:
            print(f"ERROR: {err}", file=sys.stderr)
        return 1
    print("ALL AUDIT CHECKS PASSED")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
