#!/usr/bin/env python3
"""Deterministic IC2NE-style procedural pixel art & GUI generator for EcoCraft 2 (ecc2).

Clean-room original procedural textures aligned to IC2NE's blue-slate industrial aesthetic.
Outputs all PNGs + .png.mcmeta files into assets/ecc2/textures/ and writes
assets-gen/gui_coordinates.json for pixel-perfect screen/menu alignment.
"""

from __future__ import annotations

import json
import math
from pathlib import Path
from typing import Dict, List, Tuple

from PIL import Image

from art_lib import (
    PAL,
    RGBA,
    add_item_outline,
    clamp,
    det_hash,
    draw_arrow_24x15,
    draw_blocked_sky_14x14,
    draw_bolt_14x14,
    draw_energy_bar_24x14,
    draw_flame_14x14,
    draw_gui_panel,
    draw_lcd,
    draw_moon_14x14,
    draw_player_inventory,
    draw_rain_14x14,
    draw_rect_border,
    draw_slot,
    draw_sun_14x14,
    draw_tank_frame,
    draw_tank_overlay_sprite,
    fill_rect,
    lerp_color,
    make_bevel_box,
    make_casing_tile,
    px,
    save_mcmeta,
    save_png,
    shade,
)

REPO_ROOT = Path(__file__).resolve().parent.parent
TEX_ROOT = REPO_ROOT / "assets" / "ecc2" / "textures"
GUI_COORDS_PATH = Path(__file__).resolve().parent / "gui_coordinates.json"


# =============================================================================
# 1. ASP 2 BLOCKS (16x16 & animated strips)
# =============================================================================

def gen_solar_panel_set(
    name: str,
    frame_base: RGBA,
    frame_hi: RGBA,
    cell_dark: RGBA,
    cell_mid: RGBA,
    cell_hi: RGBA,
    bus_color: RGBA,
    side_stripe: RGBA,
    seed: int,
    animated_top: bool = False,
) -> None:
    def draw_top_frame(phase: float = 0.0) -> Image.Image:
        img = make_casing_tile(frame_base, frame_hi, shade(frame_base, 0.55), seed=seed, rivets=False)
        # Inner photovoltaic cell bed (2..13)
        for y in range(2, 14):
            for x in range(2, 14):
                # 3x3 cell grid lines at 5, 6? Better: 3 cells of 3px separated by 1px busbar at x=5,10 / y=5,10 -> cells 2-4, 6-9, 11-13
                if x in (5, 10) or y in (5, 10):
                    c = bus_color
                    if animated_top and (x in (5, 10) and y in (5, 10)):
                        glow = 0.5 + 0.5 * math.sin(phase * 2 * math.pi)
                        c = lerp_color(bus_color, PAL["cyan_white"], glow)
                    elif x in (5, 10) and y in (5, 10):
                        c = lerp_color(bus_color, PAL["bevel_white"], 0.45)
                    px(img, x, y, c)
                else:
                    # Subtle diagonal photovoltaic sheen + crystal micro-grain
                    diag = (x + y - 4) / 22.0
                    if animated_top:
                        wave = 0.5 + 0.5 * math.sin((diag * 2.0 - phase) * 2 * math.pi)
                        t = 0.35 * diag + 0.65 * wave
                    else:
                        t = diag
                    grain = (det_hash(x, y, seed + 7) - 0.5) * 0.14
                    c = lerp_color(cell_hi, cell_dark, max(0.0, min(1.0, t + grain)))
                    # Micro silicon sub-stripes
                    if (y % 2 == 0) and not animated_top:
                        c = lerp_color(c, cell_mid, 0.3)
                    px(img, x, y, c)
        # Crisp dark inset around PV bed
        draw_rect_border(img, 1, 1, 14, 14, PAL["border_dark"])
        # Corner specular dots
        px(img, 2, 2, lerp_color(cell_hi, PAL["bevel_white"], 0.5))
        return img

    if animated_top:
        strip = Image.new("RGBA", (16, 64), PAL["transparent"])
        for f in range(4):
            strip.paste(draw_top_frame(f / 4.0), (0, f * 16))
        out_top = TEX_ROOT / "block" / f"{name}_top.png"
        save_png(strip, out_top)
        save_mcmeta(out_top, frametime=5, interpolate=True)
    else:
        save_png(draw_top_frame(), TEX_ROOT / "block" / f"{name}_top.png")

    # Side texture (IC2NE solar_generator_sides style: colored PV lip + metallic vents + bolts)
    side = make_casing_tile(frame_base, frame_hi, shade(frame_base, 0.52), seed=seed + 11, rivets=False)
    # Top PV trim bar (y=2..3)
    fill_rect(side, 2, 2, 12, 2, side_stripe)
    for x in range(2, 14):
        px(side, x, 2, lerp_color(side_stripe, PAL["bevel_white"], 0.25))
    # Horizontal mid separator recess (y=8)
    fill_rect(side, 2, 8, 12, 1, PAL["border_dark"])
    fill_rect(side, 2, 9, 12, 1, frame_hi)
    # Power vent / heatsink slots (y=5..6)
    for x in range(3, 13, 2):
        px(side, x, 5, PAL["lcd_bg"])
        px(side, x, 6, PAL["slot_dark"])
    # Bottom contact bolts
    for bx in (3, 12):
        px(side, bx, 12, PAL["border_dark"])
        px(side, bx, 11, bus_color)
    save_png(side, TEX_ROOT / "block" / f"{name}_side.png")

    # Bottom texture (heavy grounded plate with EU contact ring)
    bot = make_casing_tile(shade(frame_base, 0.85), PAL["slate_light"], PAL["shadow_deep"], seed=seed + 23, rivets=True)
    draw_rect_border(bot, 4, 4, 8, 8, PAL["border_dark"])
    fill_rect(bot, 5, 5, 6, 6, PAL["slot_dark"])
    draw_rect_border(bot, 6, 6, 4, 4, bus_color)
    save_png(bot, TEX_ROOT / "block" / f"{name}_bottom.png")


def gen_asp_blocks() -> None:
    # Advanced Solar Panel: Deep Sapphire Blue + Cool Silver Frame
    gen_solar_panel_set(
        "advanced_solar_panel",
        frame_base=(126, 138, 152, 255),
        frame_hi=PAL["bevel_hi"],
        cell_dark=(18, 38, 92, 255),
        cell_mid=(38, 76, 156, 255),
        cell_hi=(84, 146, 232, 255),
        bus_color=(196, 214, 234, 255),
        side_stripe=(34, 72, 148, 255),
        seed=101,
    )
    # Hybrid Solar Panel: Deep Cobalt + Warm Pale-Gold Busbars
    gen_solar_panel_set(
        "hybrid_solar_panel",
        frame_base=(96, 108, 124, 255),
        frame_hi=(196, 206, 218, 255),
        cell_dark=(16, 30, 74, 255),
        cell_mid=(34, 62, 136, 255),
        cell_hi=(76, 132, 224, 255),
        bus_color=PAL["gold_mid"],
        side_stripe=(212, 166, 48, 255),
        seed=102,
    )
    # Ultimate Hybrid Solar Panel: Cosmic Purple-Indigo + Iridium Silver Frame
    gen_solar_panel_set(
        "ultimate_hybrid_solar_panel",
        frame_base=(146, 154, 168, 255),
        frame_hi=PAL["bevel_white"],
        cell_dark=(36, 16, 78, 255),
        cell_mid=(84, 42, 158, 255),
        cell_hi=(162, 106, 242, 255),
        bus_color=(220, 232, 248, 255),
        side_stripe=(122, 66, 204, 255),
        seed=103,
    )
    # Quantum Solar Panel: Cyan-White Shimmering Lattice + Dark Titanium Frame
    gen_solar_panel_set(
        "quantum_solar_panel",
        frame_base=(68, 80, 96, 255),
        frame_hi=(176, 224, 238, 255),
        cell_dark=(14, 52, 74, 255),
        cell_mid=(26, 156, 198, 255),
        cell_hi=(184, 248, 255, 255),
        bus_color=PAL["cyan_glow"],
        side_stripe=PAL["cyan_mid"],
        seed=104,
        animated_top=True,
    )

    # Quantum Generator (inactive & animated active)
    def draw_qg(active: bool, phase: float = 0.0) -> Image.Image:
        img = make_casing_tile((58, 68, 82, 255), (156, 174, 194, 255), (30, 38, 48, 255), seed=105, rivets=True)
        # Diagonal industrial reinforcement struts
        for i in range(2, 14):
            px(img, i, i, (44, 52, 64, 255))
            px(img, 15 - i, i, (44, 52, 64, 255))
        # Central octagonal core window
        fill_rect(img, 4, 4, 8, 8, PAL["lcd_bg"])
        draw_rect_border(img, 4, 4, 8, 8, PAL["cyan_mid"] if active else PAL["slate_dark"])
        pulse = 0.55 + 0.45 * math.sin(phase * 2 * math.pi) if active else 0.0
        for y in range(5, 11):
            for x in range(5, 11):
                d = math.hypot(x - 7.5, y - 7.5)
                if active:
                    if d < 1.6:
                        px(img, x, y, PAL["cyan_white"])
                    elif d < 2.9:
                        px(img, x, y, lerp_color(PAL["cyan_mid"], PAL["cyan_glow"], pulse))
                    else:
                        px(img, x, y, lerp_color(PAL["lcd_bg2"], PAL["cyan_dark"], pulse))
                else:
                    if d < 2.2:
                        px(img, x, y, (34, 72, 92, 255))
        # Corner accent LEDs
        led = lerp_color(PAL["cyan_mid"], PAL["cyan_white"], pulse) if active else PAL["red_dark"]
        for lx, ly in [(3, 3), (12, 3), (3, 12), (12, 12)]:
            px(img, lx, ly, led)
        return img

    save_png(draw_qg(False), TEX_ROOT / "block" / "quantum_generator.png")
    qg_strip = Image.new("RGBA", (16, 64), PAL["transparent"])
    for f in range(4):
        qg_strip.paste(draw_qg(True, f / 4.0), (0, f * 16))
    qg_act_path = TEX_ROOT / "block" / "quantum_generator_active.png"
    save_png(qg_strip, qg_act_path)
    save_mcmeta(qg_act_path, frametime=4, interpolate=True)

    # Molecular Transformer Particle (16x16 dark steel + glowing cyan emitter ring)
    mt_p = make_casing_tile((64, 74, 88, 255), (168, 184, 200, 255), (32, 38, 48, 255), seed=106, rivets=True)
    for y in range(3, 13):
        for x in range(3, 13):
            d = math.hypot(x - 7.5, y - 7.5)
            if 2.6 <= d <= 4.3:
                px(mt_p, x, y, PAL["cyan_glow"] if d < 3.5 else PAL["cyan_mid"])
            elif d < 2.6:
                px(mt_p, x, y, PAL["lcd_bg"])
    px(mt_p, 7, 7, PAL["cyan_white"])
    px(mt_p, 8, 8, PAL["cyan_white"])
    save_png(mt_p, TEX_ROOT / "block" / "molecular_transformer_particle.png")


# =============================================================================
# 2. ASP 2 3D ENTITY / MODEL TEXTURES
# =============================================================================

def gen_entity_textures() -> None:
    # 2a. entity/molecular_transformer.png (128x64 sleek IC2NE dark-steel & glowing cyan frame UV)
    mt = Image.new("RGBA", (128, 64), (56, 65, 78, 255))
    # Brushed steel base across entire 128x64 sheet
    for y in range(64):
        for x in range(128):
            h = (det_hash(x, y, 201) - 0.5) * 12 + (-3 if y % 2 == 0 else 3)
            mt.putpixel((x, y), (clamp(62 + h), clamp(72 + h), clamp(86 + h), 255))

    # Region 1: Top & Bottom Base Pedestals (0..64, 0..32)
    make_bevel_box(mt, 0, 0, 64, 32, fill=(74, 84, 98, 255), raised=True, hi=PAL["bevel_hi"], lo=PAL["border_dark"])
    # Pedestal glowing cyan containment rings
    for bx in (4, 20, 36, 52):
        draw_rect_border(mt, bx, 4, 10, 10, PAL["cyan_mid"])
        fill_rect(mt, bx + 2, 6, 6, 6, PAL["lcd_bg"])
        draw_rect_border(mt, bx + 3, 7, 4, 4, PAL["cyan_glow"])
    # Lower pedestal panel trim & vents
    for bx in range(4, 60, 8):
        fill_rect(mt, bx, 18, 6, 10, PAL["slot_dark"])
        fill_rect(mt, bx + 1, 20, 4, 2, PAL["cyan_dark"])
        fill_rect(mt, bx + 1, 24, 4, 2, PAL["cyan_mid"])

    # Region 2: Vertical Containment Pillars & Coils (64..128, 0..32)
    make_bevel_box(mt, 64, 0, 64, 32, fill=(84, 96, 112, 255), raised=True, hi=PAL["bevel_hi"], lo=PAL["border_dark"])
    for col in range(68, 124, 8):
        fill_rect(mt, col, 2, 6, 28, (50, 58, 70, 255))
        # Glowing cyan vertical conduit strip down each pillar
        fill_rect(mt, col + 2, 4, 2, 24, PAL["cyan_mid"])
        for ry in range(5, 27, 4):
            fill_rect(mt, col + 1, ry, 4, 2, PAL["bevel_hi"])
            fill_rect(mt, col + 2, ry, 2, 1, PAL["cyan_white"])

    # Region 3: Rotating Energy Rings / Emitter Caps (0..64, 32..64)
    make_bevel_box(mt, 0, 32, 64, 32, fill=(44, 54, 66, 255), raised=True, hi=PAL["slate_light"], lo=PAL["outline"])
    for y in range(36, 60, 6):
        fill_rect(mt, 4, y, 56, 3, PAL["cyan_dark"])
        fill_rect(mt, 6, y + 1, 52, 1, PAL["cyan_glow"])
    # Rivet strips
    for rx in range(4, 61, 6):
        px(mt, rx, 34, PAL["bevel_white"])
        px(mt, rx, 61, PAL["bevel_white"])

    # Region 4: Containment Glass & Emitter Core Accent (64..128, 32..64)
    make_bevel_box(mt, 64, 32, 64, 32, fill=(18, 42, 58, 220), raised=False, hi=PAL["cyan_mid"], lo=PAL["lcd_border"])
    for y in range(34, 62):
        for x in range(66, 126):
            if (x + y) % 10 in (0, 1):
                px(mt, x, y, (140, 236, 255, 110))
    save_png(mt, TEX_ROOT / "entity" / "molecular_transformer.png")

    # 2b. entity/plasma.png (256x256, 4x4 = 16 frames of 64x64 glowing cyan-white plasma core)
    plasma = Image.new("RGBA", (256, 256), PAL["transparent"])
    for idx in range(16):
        fx = (idx % 4) * 64
        fy = (idx // 4) * 64
        phase = (idx / 16.0) * 2.0 * math.pi
        for ly in range(64):
            dy = (ly - 31.5) / 31.5
            for lx in range(64):
                dx = (lx - 31.5) / 31.5
                r = math.hypot(dx, dy)
                if r >= 1.0:
                    continue
                ang = math.atan2(dy, dx)
                # Rotating plasma filaments (3-fold + 5-fold harmonic interference)
                swirl1 = 0.5 + 0.5 * math.sin(3.0 * ang + phase + r * 4.5)
                swirl2 = 0.5 + 0.5 * math.cos(5.0 * ang - 2.0 * phase - r * 3.0)
                filament = 0.65 + 0.35 * (0.6 * swirl1 + 0.4 * swirl2)
                radial = math.pow(max(0.0, 1.0 - r), 1.65)
                intensity = min(1.0, radial * filament * 1.45)
                alpha = clamp(min(1.0, math.pow(max(0.0, 1.0 - r), 1.35) * (0.75 + 0.35 * filament)) * 255)
                if intensity > 0.65:
                    col = lerp_color(PAL["cyan_glow"], (250, 255, 255, alpha), (intensity - 0.65) / 0.35)
                elif intensity > 0.25:
                    col = lerp_color((20, 110, 220, alpha), PAL["cyan_glow"], (intensity - 0.25) / 0.40)
                else:
                    col = (18, 66, 175, alpha)
                plasma.putpixel((fx + lx, fy + ly), (col[0], col[1], col[2], alpha))
    save_png(plasma, TEX_ROOT / "entity" / "plasma.png")

    # 2c. entity/particles.png (256x256, 8x8 = 64 cells of 32x32 sparkles/stars/rings)
    parts = Image.new("RGBA", (256, 256), PAL["transparent"])
    for gy in range(8):
        for gx in range(8):
            ox, oy = gx * 32, gy * 32
            style = (gx + gy * 8) % 4
            rot = ((gx * 7 + gy * 13) % 16) * (math.pi / 8.0)
            scale = 0.55 + 0.4 * ((gx % 4) / 3.0)
            tint = [PAL["cyan_glow"], PAL["cyan_white"], PAL["gold_hi"], (136, 196, 255, 255)][(gx + gy) % 4]
            for ly in range(32):
                dy = (ly - 15.5) / (14.5 * scale)
                for lx in range(32):
                    dx = (lx - 15.5) / (14.5 * scale)
                    r = math.hypot(dx, dy)
                    if r > 1.15:
                        continue
                    rx = dx * math.cos(rot) - dy * math.sin(rot)
                    ry = dx * math.sin(rot) + dy * math.cos(rot)
                    if style == 0:
                        # 4-point star sparkle
                        star = max(0.0, 1.0 - (abs(rx) * 3.2 + abs(ry) * 0.85)) + max(0.0, 1.0 - (abs(ry) * 3.2 + abs(rx) * 0.85))
                        val = max(star, max(0.0, 1.0 - r * 1.4))
                    elif style == 1:
                        # Soft glowing orb
                        val = math.pow(max(0.0, 1.0 - r), 1.8)
                    elif style == 2:
                        # Energy ring
                        val = max(0.0, 1.0 - abs(r - 0.62) * 4.5)
                    else:
                        # Diamond mote
                        val = max(0.0, 1.0 - (abs(rx) + abs(ry)) * 1.15)
                    val = min(1.0, val)
                    if val > 0.02:
                        col = lerp_color(tint, (255, 255, 255, 255), val * val)
                        parts.putpixel((ox + lx, oy + ly), (col[0], col[1], col[2], clamp(val * 255)))
    save_png(parts, TEX_ROOT / "entity" / "particles.png")


# =============================================================================
# 3. ASP 2 ITEMS + SOLAR HELMETS + ARMOR LAYERS
# =============================================================================

def draw_ingot_sprite(base: RGBA, hi: RGBA, lo: RGBA, glow: RGBA | None = None, seed: int = 1) -> Image.Image:
    """16x16 isometric IC2NE-style beveled ingot."""
    img = Image.new("RGBA", (16, 16), PAL["transparent"])
    # Classic clean diagonal ingot silhouette
    top_poly = [
        (5, 4), (6, 4), (7, 4), (8, 4), (9, 4), (10, 4),
        (4, 5), (5, 5), (6, 5), (7, 5), (8, 5), (9, 5), (10, 5), (11, 5),
        (3, 6), (4, 6), (5, 6), (6, 6), (7, 6), (8, 6), (9, 6), (10, 6), (11, 6), (12, 6),
        (3, 7), (4, 7), (5, 7), (6, 7), (7, 7), (8, 7), (9, 7), (10, 7), (11, 7), (12, 7),
        (2, 8), (3, 8), (4, 8), (5, 8), (6, 8), (7, 8), (8, 8), (9, 8), (10, 8), (11, 8), (12, 8), (13, 8),
    ]
    side_poly = [
        (2, 9), (3, 9), (4, 9), (5, 9), (6, 9), (7, 9), (8, 9), (9, 9), (10, 9), (11, 9), (12, 9), (13, 9),
        (2, 10), (3, 10), (4, 10), (5, 10), (6, 10), (7, 10), (8, 10), (9, 10), (10, 10), (11, 10), (12, 10), (13, 10),
        (3, 11), (4, 11), (5, 11), (6, 11), (7, 11), (8, 11), (9, 11), (10, 11), (11, 11), (12, 11),
    ]
    for x, y in top_poly:
        t = (x + (y - 4) * 2) / 20.0
        n = (det_hash(x, y, seed) - 0.5) * 0.12
        px(img, x, y, lerp_color(hi, base, max(0.0, min(1.0, t + n))))
    for x, y in side_poly:
        t = (x - 2) / 11.0
        px(img, x, y, lerp_color(base, lo, t))
    # Specular glint on top-left ridge
    for x in (5, 6, 7):
        px(img, x, 5, lerp_color(hi, PAL["bevel_white"], 0.6))
    if glow is not None:
        for gx, gy in [(5, 7), (6, 7), (9, 7), (10, 7), (7, 9), (8, 9)]:
            px(img, gx, gy, glow)
    return add_item_outline(img)


def draw_plate_sprite(base: RGBA, hi: RGBA, lo: RGBA, core: RGBA | None = None, corner: RGBA | None = None, seed: int = 2) -> Image.Image:
    """16x16 industrial beveled alloy plate."""
    img = Image.new("RGBA", (16, 16), PAL["transparent"])
    for y in range(2, 14):
        for x in range(2, 14):
            if (x in (2, 13) and y in (2, 13)):
                continue  # Chamfered corners
            t = ((x - 2) + (y - 2)) / 22.0
            n = (det_hash(x, y, seed) - 0.5) * 0.1
            c = lerp_color(hi, base, max(0.0, min(1.0, t + n)))
            if y == 2 or x == 2:
                c = hi
            elif y == 13 or x == 13:
                c = lo
            px(img, x, y, c)
    # Corner rivets / iridium inlays
    rivet_col = corner if corner is not None else lo
    for rx, ry in [(4, 4), (11, 4), (4, 11), (11, 11)]:
        px(img, rx, ry, rivet_col)
    if core is not None:
        # Glowing diamond / circuit core
        for y in range(5, 11):
            for x in range(5, 11):
                if abs(x - 7.5) + abs(y - 7.5) <= 2.6:
                    px(img, x, y, core)
        px(img, 7, 7, PAL["bevel_white"])
        px(img, 8, 7, lerp_color(core, PAL["bevel_white"], 0.6))
    return add_item_outline(img)


def draw_orb_sprite(outer: RGBA, mid: RGBA, core: RGBA, radius: float = 5.4, sparkles: bool = True) -> Image.Image:
    """16x16 radiant sunnarium / crystal orb."""
    img = Image.new("RGBA", (16, 16), PAL["transparent"])
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            if d <= radius:
                t = d / radius
                if t < 0.35:
                    c = lerp_color(core, mid, t / 0.35)
                else:
                    c = lerp_color(mid, outer, (t - 0.35) / 0.65)
                px(img, x, y, c)
    if sparkles:
        for sx, sy in [(7, 1), (8, 1), (7, 14), (8, 14), (1, 7), (1, 8), (14, 7), (14, 8)]:
            px(img, sx, sy, lerp_color(mid, core, 0.5))
    return add_item_outline(img, shade(outer, 0.45))


def draw_helmet_item(
    shell_hi: RGBA,
    shell_mid: RGBA,
    shell_lo: RGBA,
    visor: RGBA,
    crest: RGBA,
    dyeable_base_only: bool = False,
    overlay_only: bool = False,
) -> Image.Image:
    """16x16 sleek sci-fi IC2NE solar helmet sprite (supports dyeable layer0 + overlay layer1)."""
    img = Image.new("RGBA", (16, 16), PAL["transparent"])
    # Helmet dome silhouette
    dome = [
        "................",
        "....########....",
        "...##########...",
        "..############..",
        "..############..",
        ".##############.",
        ".###VVVVVVVV###.",
        ".###VVVVVVVV###.",
        ".###VVVVVVVV###.",
        ".####oooooo####.",
        ".####..oo..####.",
        "..###......###..",
        "..###......###..",
        "...##......##...",
        "................",
        "................",
    ]
    crest_pts = {(6, 2), (7, 2), (8, 2), (9, 2), (5, 3), (6, 3), (7, 3), (8, 3), (9, 3), (10, 3)}
    for y, row in enumerate(dome):
        for x, ch in enumerate(row):
            if ch == ".":
                continue
            if (x, y) in crest_pts:
                if not dyeable_base_only:
                    c = lerp_color(crest, PAL["bevel_white"], 0.35 if y == 2 else 0.0)
                    px(img, x, y, c)
                continue
            if ch == "#":
                if not overlay_only:
                    t = ((x - 1) / 14.0) * 0.5 + ((y - 1) / 12.0) * 0.5
                    c = lerp_color(shell_hi, shell_lo, t) if t > 0.5 else lerp_color(shell_hi, shell_mid, t * 2)
                    px(img, x, y, c)
            elif ch == "V":
                if not dyeable_base_only:
                    c = lerp_color(PAL["bevel_white"], visor, 0.35 if x in (5, 6) and y == 6 else 0.85)
                    px(img, x, y, c)
            elif ch == "o":
                if not dyeable_base_only:
                    px(img, x, y, PAL["slot_dark"])
    if not overlay_only:
        return add_item_outline(img)
    return img


def draw_armor_layer(
    shell_hi: RGBA,
    shell_mid: RGBA,
    shell_lo: RGBA,
    visor: RGBA,
    crest: RGBA,
    dyeable_base_only: bool = False,
    overlay_only: bool = False,
) -> Image.Image:
    """64x32 Minecraft humanoid layer_1 armor texture (helmet head UV at 0..32, 0..16 + hat at 32..64, 0..16)."""
    img = Image.new("RGBA", (64, 32), PAL["transparent"])

    # Fill head base faces: top(8,0,8,8), right(0,8,8,8), front(8,8,8,8), left(16,8,8,8), back(24,8,8,8)
    if not overlay_only:
        for fx, fy in [(8, 0), (0, 8), (8, 8), (16, 8), (24, 8)]:
            for y in range(8):
                for x in range(8):
                    t = (x + y) / 14.0
                    c = lerp_color(shell_hi, shell_lo, t) if t > 0.5 else lerp_color(shell_hi, shell_mid, t * 2)
                    if x in (0, 7) or y in (0, 7):
                        c = shade(c, 0.82)
                    px(img, fx + x, fy + y, c)

    if not dyeable_base_only:
        # Top photovoltaic solar array on helmet top (8,0)-(15,7)
        for y in range(1, 7):
            for x in range(1, 7):
                c = PAL["bevel_white"] if (x == 3 or y == 3) else crest
                px(img, 8 + x, y, c)
        # Glowing visor on front (8,8)-(15,15) -> rows y=3..5, cols x=1..6
        for y in range(3, 6):
            for x in range(1, 7):
                px(img, 8 + x, 8 + y, lerp_color(visor, PAL["bevel_white"], 0.4 if (x == 2 and y == 3) else 0.0))
        # Ear vents on right (0,8) & left (16,8)
        for ex in (0, 16):
            fill_rect(img, ex + 2, 11, 4, 3, PAL["slot_dark"])
            fill_rect(img, ex + 3, 12, 2, 1, visor)
        # Back energy cell strip (24,8)
        fill_rect(img, 26, 10, 4, 4, PAL["slot_dark"])
        fill_rect(img, 27, 11, 2, 2, crest)
        # Outer Hat layer accent ridge (40,0 top solar bezel & 40,8 front visor rim)
        draw_rect_border(img, 40, 0, 8, 8, shade(crest, 0.9))
        for x in range(1, 7):
            px(img, 40 + x, 10, visor)

    return img


def gen_asp_items_and_armor() -> None:
    # 14 ASP crafting items
    save_png(draw_orb_sprite((208, 134, 18, 255), PAL["gold_mid"], (255, 252, 200, 255), radius=5.4, sparkles=True),
             TEX_ROOT / "item" / "sunnarium.png")
    save_png(draw_orb_sprite((214, 146, 24, 255), PAL["gold_hi"], (255, 255, 224, 255), radius=3.5, sparkles=False),
             TEX_ROOT / "item" / "sunnarium_part.png")
    save_png(draw_plate_sprite((66, 76, 90, 255), (138, 152, 168, 255), (36, 42, 52, 255), core=PAL["gold_hi"], corner=PAL["gold_mid"], seed=301),
             TEX_ROOT / "item" / "sunnarium_alloy.png")
    save_png(draw_ingot_sprite((46, 156, 76, 255), (146, 255, 168, 255), (20, 74, 38, 255), glow=PAL["gold_hi"], seed=302),
             TEX_ROOT / "item" / "irradiant_uranium.png")
    save_png(draw_orb_sprite((54, 186, 108, 255), PAL["gold_hi"], (255, 255, 240, 255), radius=5.8, sparkles=True),
             TEX_ROOT / "item" / "enriched_sunnarium.png")
    save_png(draw_plate_sprite((46, 56, 70, 255), (126, 148, 168, 255), (24, 30, 40, 255), core=PAL["emerald_hi"], corner=PAL["gold_hi"], seed=303),
             TEX_ROOT / "item" / "enriched_sunnarium_alloy.png")

    # Irradiant Glass Pane (translucent cyan-gold framed reinforced pane)
    igp = Image.new("RGBA", (16, 16), PAL["transparent"])
    fill_rect(igp, 2, 2, 12, 12, (116, 224, 242, 165))
    for i in range(3, 13):
        px(igp, i, 15 - i, (255, 248, 196, 220))
        if i + 1 < 13:
            px(igp, i + 1, 15 - i, (215, 250, 255, 195))
    draw_rect_border(igp, 2, 2, 12, 12, PAL["gold_mid"])
    draw_rect_border(igp, 1, 1, 14, 14, PAL["slate_dark"])
    save_png(igp, TEX_ROOT / "item" / "irradiant_glass_pane.png")

    save_png(draw_plate_sprite((146, 154, 164, 255), (218, 226, 234, 255), (84, 92, 104, 255), corner=(236, 246, 255, 255), seed=304),
             TEX_ROOT / "item" / "iridium_iron_plate.png")
    save_png(draw_plate_sprite((96, 106, 120, 255), (192, 206, 222, 255), (48, 56, 68, 255), core=(206, 222, 242, 255), corner=PAL["bevel_white"], seed=305),
             TEX_ROOT / "item" / "reinforced_iridium_iron_plate.png")
    save_png(draw_plate_sprite((84, 96, 114, 255), (198, 214, 232, 255), (42, 50, 64, 255), core=PAL["cyan_glow"], corner=PAL["gold_hi"], seed=306),
             TEX_ROOT / "item" / "irradiant_reinforced_plate.png")
    save_png(draw_ingot_sprite((196, 210, 224, 255), (242, 250, 255, 255), (118, 134, 154, 255), glow=(224, 252, 255, 255), seed=307),
             TEX_ROOT / "item" / "iridium_ingot.png")

    # MT Core (octagonal dark-steel cage + glowing cyan plasma sphere)
    mtc = Image.new("RGBA", (16, 16), PAL["transparent"])
    for y in range(2, 14):
        for x in range(2, 14):
            d = math.hypot(x - 7.5, y - 7.5)
            if d <= 2.7:
                px(mtc, x, y, lerp_color(PAL["cyan_white"], PAL["cyan_glow"], d / 2.7))
            elif d <= 4.0:
                px(mtc, x, y, PAL["lcd_bg2"])
            elif abs(x - 7.5) + abs(y - 7.5) <= 8.2:
                px(mtc, x, y, PAL["slate_light"] if (x < 7 or y < 7) else PAL["slate_dark"])
    for cx, cy in [(7, 2), (8, 2), (7, 13), (8, 13), (2, 7), (2, 8), (13, 7), (13, 8)]:
        px(mtc, cx, cy, PAL["cyan_mid"])
    save_png(add_item_outline(mtc), TEX_ROOT / "item" / "mt_core.png")

    # Quantum Core (cyan-white diamond lattice inside iridium-gold containment ring)
    qc = Image.new("RGBA", (16, 16), PAL["transparent"])
    for y in range(1, 15):
        for x in range(1, 15):
            md = abs(x - 7.5) + abs(y - 7.5)
            if md <= 2.4:
                px(qc, x, y, PAL["cyan_white"])
            elif md <= 4.2:
                px(qc, x, y, PAL["cyan_glow"])
            elif md <= 5.6:
                px(qc, x, y, PAL["gold_mid"] if (x + y) % 2 == 0 else PAL["bevel_hi"])
            elif md <= 6.8:
                px(qc, x, y, PAL["slate_dark"])
    save_png(add_item_outline(qc), TEX_ROOT / "item" / "quantum_core.png")

    # 3 Solar Helmets (item sprites + armor layer_1 + 26.x equipment/humanoid)
    # 1) Advanced Solar Helmet (non-dyeable sleek dark nano shell + sapphire solar crest + cyan visor)
    save_png(
        draw_helmet_item((92, 102, 118, 255), (54, 62, 74, 255), (30, 36, 44, 255), PAL["cyan_glow"], (56, 128, 236, 255)),
        TEX_ROOT / "item" / "advanced_solar_helmet.png",
    )
    adv_armor = draw_armor_layer((92, 102, 118, 255), (54, 62, 74, 255), (30, 36, 44, 255), PAL["cyan_glow"], (56, 128, 236, 255))
    save_png(adv_armor, TEX_ROOT / "models" / "armor" / "advanced_solar_layer_1.png")
    save_png(adv_armor, TEX_ROOT / "entity" / "equipment" / "humanoid" / "advanced_solar.png")

    # 2) Hybrid Solar Helmet (dyeable white/light-gray base + cobalt/gold overlay!)
    save_png(
        draw_helmet_item((244, 246, 250, 255), (210, 216, 224, 255), (156, 164, 176, 255), PAL["cyan_glow"], PAL["gold_mid"], dyeable_base_only=True),
        TEX_ROOT / "item" / "hybrid_solar_helmet.png",
    )
    save_png(
        draw_helmet_item((244, 246, 250, 255), (210, 216, 224, 255), (156, 164, 176, 255), PAL["cyan_glow"], PAL["gold_mid"], overlay_only=True),
        TEX_ROOT / "item" / "hybrid_solar_helmet_overlay.png",
    )
    hyb_base = draw_armor_layer((244, 246, 250, 255), (210, 216, 224, 255), (156, 164, 176, 255), PAL["cyan_glow"], PAL["gold_mid"], dyeable_base_only=True)
    hyb_over = draw_armor_layer((244, 246, 250, 255), (210, 216, 224, 255), (156, 164, 176, 255), PAL["cyan_glow"], PAL["gold_mid"], overlay_only=True)
    save_png(hyb_base, TEX_ROOT / "models" / "armor" / "hybrid_solar_layer_1.png")
    save_png(hyb_over, TEX_ROOT / "models" / "armor" / "hybrid_solar_layer_1_overlay.png")
    save_png(hyb_base, TEX_ROOT / "entity" / "equipment" / "humanoid" / "hybrid_solar.png")
    save_png(hyb_over, TEX_ROOT / "entity" / "equipment" / "humanoid" / "hybrid_solar_overlay.png")

    # 3) Ultimate Hybrid Solar Helmet (dyeable white/silver base + violet-iridium overlay!)
    save_png(
        draw_helmet_item((248, 250, 255, 255), (218, 222, 232, 255), (164, 170, 184, 255), PAL["purple_hi"], PAL["purple_mid"], dyeable_base_only=True),
        TEX_ROOT / "item" / "ultimate_hybrid_solar_helmet.png",
    )
    save_png(
        draw_helmet_item((248, 250, 255, 255), (218, 222, 232, 255), (164, 170, 184, 255), PAL["purple_hi"], PAL["purple_mid"], overlay_only=True),
        TEX_ROOT / "item" / "ultimate_hybrid_solar_helmet_overlay.png",
    )
    ult_base = draw_armor_layer((248, 250, 255, 255), (218, 222, 232, 255), (164, 170, 184, 255), PAL["purple_hi"], PAL["purple_mid"], dyeable_base_only=True)
    ult_over = draw_armor_layer((248, 250, 255, 255), (218, 222, 232, 255), (164, 170, 184, 255), PAL["purple_hi"], PAL["purple_mid"], overlay_only=True)
    save_png(ult_base, TEX_ROOT / "models" / "armor" / "ultimate_hybrid_solar_layer_1.png")
    save_png(ult_over, TEX_ROOT / "models" / "armor" / "ultimate_hybrid_solar_layer_1_overlay.png")
    save_png(ult_base, TEX_ROOT / "entity" / "equipment" / "humanoid" / "ultimate_hybrid_solar.png")
    save_png(ult_over, TEX_ROOT / "entity" / "equipment" / "humanoid" / "ultimate_hybrid_solar_overlay.png")


# =============================================================================
# 4. ADVANCED WIND & WATER KINETIC GENERATORS (6 machines x 6 faces = 36 PNGs)
# =============================================================================

def gen_kinetic_generators() -> None:
    tiers = {
        "durable": {
            "base": (108, 120, 130, 255),
            "ring": PAL["bronze_mid"],
            "ring_hi": PAL["bronze_hi"],
            "accent": PAL["teal_hi"],
            "led_on": PAL["teal_hi"],
        },
        "overclocked": {
            "base": (102, 108, 118, 255),
            "ring": (224, 118, 32, 255),
            "ring_hi": (255, 178, 66, 255),
            "accent": (246, 132, 34, 255),
            "led_on": (255, 188, 64, 255),
        },
        "precision": {
            "base": (156, 166, 178, 255),
            "ring": PAL["gold_mid"],
            "ring_hi": PAL["gold_hi"],
            "accent": PAL["cyan_glow"],
            "led_on": PAL["cyan_white"],
        },
    }

    seed = 400
    for tier_name, cfg in tiers.items():
        for kind in ("wind", "water"):
            seed += 10
            prefix = f"{tier_name}_{kind}_kinetic_generator"
            is_water = kind == "water"

            def draw_front(active: bool) -> Image.Image:
                img = make_casing_tile(cfg["base"], PAL["bevel_hi"], PAL["slot_dark"], seed=seed, rivets=False)
                # Recessed circular rotor shaft cavity
                for y in range(2, 14):
                    for x in range(2, 14):
                        d = math.hypot(x - 7.5, y - 7.5)
                        if 4.4 <= d <= 5.8:
                            # Tier-colored wear/cooling/precision ring!
                            c = cfg["ring_hi"] if (x + y < 15) else cfg["ring"]
                            if active and (x in (7, 8) or y in (7, 8)):
                                c = cfg["led_on"]
                            px(img, x, y, c)
                        elif 2.3 <= d < 4.4:
                            px(img, x, y, PAL["lcd_bg"])
                            if is_water and ((x + y) % 3 == 0):
                                px(img, x, y, (24, 82, 128, 255))
                        elif d < 2.3:
                            # Central drive shaft hub
                            c = PAL["bevel_hi"] if (x <= 7 and y <= 7) else PAL["slate_dark"]
                            px(img, x, y, c)
                # Center shaft keyway
                px(img, 7, 7, cfg["led_on"] if active else PAL["border_dark"])
                px(img, 8, 8, PAL["border_dark"])
                # Corner hydro-seal studs (blue for water, dark steel for wind)
                corner_c = (46, 178, 242, 255) if is_water else PAL["shadow_deep"]
                for cx, cy in [(2, 2), (13, 2), (2, 13), (13, 13)]:
                    px(img, cx, cy, corner_c)
                    if active:
                        px(img, cx + (1 if cx < 8 else -1), cy, cfg["led_on"])
                return img

            save_png(draw_front(False), TEX_ROOT / "block" / f"{prefix}_front.png")
            save_png(draw_front(True), TEX_ROOT / "block" / f"{prefix}_front_active.png")

            # Back face: IC2NE kinetic output shaft (KU coupler port)
            back = make_casing_tile(cfg["base"], PAL["bevel_hi"], PAL["slot_dark"], seed=seed + 1, rivets=False)
            draw_rect_border(back, 2, 2, 12, 12, PAL["border_dark"])
            fill_rect(back, 3, 3, 10, 10, PAL["lcd_bg"])
            # 8-tooth kinetic coupler socket
            for tx, ty in [(7, 3), (8, 3), (7, 12), (8, 12), (3, 7), (3, 8), (12, 7), (12, 8)]:
                px(back, tx, ty, cfg["ring_hi"])
            for tx, ty in [(5, 4), (10, 4), (5, 11), (10, 11), (4, 5), (4, 10), (11, 5), (11, 10)]:
                px(back, tx, ty, PAL["slate_mid"])
            fill_rect(back, 6, 6, 4, 4, PAL["slate_light"])
            px(back, 6, 6, PAL["bevel_white"])
            px(back, 9, 9, PAL["border_dark"])
            # Tier corner markers
            for cx, cy in [(2, 2), (13, 2), (2, 13), (13, 13)]:
                px(back, cx, cy, cfg["accent"])
            save_png(back, TEX_ROOT / "block" / f"{prefix}_back.png")

            # Side face: industrial vents + tier band (overclocked gets cooling fin slats!)
            side = make_casing_tile(cfg["base"], PAL["bevel_hi"], PAL["slot_dark"], seed=seed + 2, rivets=True)
            fill_rect(side, 2, 3, 12, 2, cfg["ring"])
            fill_rect(side, 2, 3, 12, 1, cfg["ring_hi"])
            if tier_name == "overclocked":
                # Heavy vertical cooling fins
                for x in range(3, 13, 2):
                    fill_rect(side, x, 6, 1, 7, PAL["border_dark"])
                    fill_rect(side, x + 1, 6, 1, 7, cfg["ring_hi"])
            else:
                # Horizontal intake louvers
                for y in (6, 8, 10):
                    fill_rect(side, 3, y, 10, 1, PAL["lcd_bg"])
                    fill_rect(side, 3, y + 1, 10, 1, PAL["slate_light"])
            if is_water:
                fill_rect(side, 2, 12, 12, 1, (46, 168, 232, 255))
            save_png(side, TEX_ROOT / "block" / f"{prefix}_side.png")

            # Top face: reinforced service hatch + tier indicator bar
            top = make_casing_tile(cfg["base"], PAL["bevel_hi"], PAL["slot_dark"], seed=seed + 3, rivets=True)
            draw_rect_border(top, 4, 4, 8, 8, PAL["border_dark"])
            fill_rect(top, 5, 5, 6, 6, shade(cfg["base"], 0.88))
            fill_rect(top, 5, 6, 6, 1, cfg["ring_hi"])
            fill_rect(top, 5, 9, 6, 1, cfg["accent"])
            save_png(top, TEX_ROOT / "block" / f"{prefix}_top.png")

            # Bottom face: bolted mounting base
            bot = make_casing_tile(shade(cfg["base"], 0.82), PAL["slate_light"], PAL["shadow_deep"], seed=seed + 4, rivets=True)
            draw_rect_border(bot, 3, 3, 10, 10, PAL["slot_dark"])
            fill_rect(bot, 6, 6, 4, 4, cfg["ring"])
            save_png(bot, TEX_ROOT / "block" / f"{prefix}_bottom.png")


# =============================================================================
# 5. GIANT MACHINES (3x3x3 Multiblock — 5 Fully Differentiated Industrial Lines)
# =============================================================================

GIANT_THEMES: Dict[str, Dict[str, RGBA]] = {
    "macerator": {
        "base": (96, 106, 118, 255),
        "hi": (216, 224, 232, 255),
        "lo": (50, 58, 68, 255),
        "accent": (246, 198, 42, 255),
        "accent_hi": (255, 238, 126, 255),
        "accent_dark": (156, 108, 16, 255),
        "secondary": (28, 33, 40, 255),
        "led_off": (122, 92, 24, 255),
    },
    "compressor": {
        "base": (58, 66, 80, 255),
        "hi": (158, 176, 200, 255),
        "lo": (26, 32, 42, 255),
        "accent": (46, 132, 248, 255),
        "accent_hi": (132, 202, 255, 255),
        "accent_dark": (20, 62, 144, 255),
        "secondary": (126, 142, 164, 255),
        "led_off": (24, 62, 122, 255),
    },
    "recycler": {
        "base": (84, 102, 104, 255),
        "hi": (198, 222, 220, 255),
        "lo": (40, 56, 58, 255),
        "accent": (46, 214, 114, 255),
        "accent_hi": (152, 255, 190, 255),
        "accent_dark": (18, 110, 58, 255),
        "secondary": (30, 52, 46, 255),
        "led_off": (22, 96, 52, 255),
    },
    "extractor": {
        "base": (106, 98, 90, 255),
        "hi": (226, 212, 192, 255),
        "lo": (54, 46, 40, 255),
        "accent": (222, 138, 46, 255),
        "accent_hi": (255, 212, 104, 255),
        "accent_dark": (128, 66, 18, 255),
        "secondary": (224, 178, 76, 255),
        "led_off": (124, 76, 24, 255),
    },
    "ore_washer": {
        "base": (48, 72, 94, 255),
        "hi": (156, 204, 232, 255),
        "lo": (22, 38, 54, 255),
        "accent": (36, 218, 242, 255),
        "accent_hi": (176, 250, 255, 255),
        "accent_dark": (14, 96, 132, 255),
        "secondary": (34, 146, 230, 255),
        "led_off": (20, 88, 118, 255),
    },
}


def draw_giant_emblem(
    img: Image.Image,
    kind: str,
    active: bool,
    ox: int = 3,
    oy: int = 3,
    frame: int = 0,
) -> None:
    """Draw a 10x10 unmistakable, optionally animated machine mechanism emblem inside a viewport."""
    theme = GIANT_THEMES[kind]
    acc = theme["accent"]
    acc_hi = theme["accent_hi"]
    acc_dark = theme["accent_dark"]
    f = frame % 4

    if kind == "macerator":
        # Bold counter-rotating serrated grinder jaws + glowing amber sparks when active
        steel_hi = PAL["bevel_white"] if active else PAL["bevel_hi"]
        steel_mid = PAL["slate_light"]
        steel_lo = PAL["slate_dark"]
        # Roller bodies (left x=1..2, right x=7..8)
        fill_rect(img, ox + 1, oy + 1, 2, 8, steel_mid)
        fill_rect(img, ox + 7, oy + 1, 2, 8, steel_mid)
        for y in range(1, 9):
            px(img, ox + 1, oy + y, steel_hi)
            px(img, ox + 8, oy + y, steel_lo)
        # Rotating interlocking grinder teeth along center bite (x=3..6)
        left_shift = f % 2 if active else 0
        right_shift = (f + 1) % 2 if active else 1
        for y in range(1, 9):
            if (y + left_shift) % 2 == 0:
                px(img, ox + 3, oy + y, acc_hi if active else steel_hi)
                px(img, ox + 4, oy + y, acc if active else steel_mid)
            if (y + right_shift) % 2 == 0:
                px(img, ox + 5, oy + y, acc if active else steel_mid)
                px(img, ox + 6, oy + y, acc_hi if active else steel_hi)
        if active:
            # Crushing spark flash traveling down the jaw gap
            spark_y = 2 + (f * 2) % 6
            px(img, ox + 4, oy + spark_y, PAL["bevel_white"])
            px(img, ox + 5, oy + spark_y, acc_hi)
            px(img, ox + 5, oy + ((spark_y + 3) % 7) + 1, (255, 148, 36, 255))
        # Amber roller end-caps
        fill_rect(img, ox + 1, oy + 0, 2, 1, acc)
        fill_rect(img, ox + 7, oy + 0, 2, 1, acc)
        fill_rect(img, ox + 1, oy + 9, 2, 1, acc_dark)
        fill_rect(img, ox + 7, oy + 9, 2, 1, acc_dark)

    elif kind == "compressor":
        # Heavy forged hydraulic press rams clamping inward on a cobalt-charged core
        stroke = [0, 1, 2, 1][f] if active else 0
        steel_hi = PAL["bevel_white"] if active else PAL["bevel_hi"]
        steel_mid = (116, 132, 152, 255)
        # Cobalt side hydraulic guide rails
        fill_rect(img, ox + 0, oy + 1, 1, 8, acc if active else acc_dark)
        fill_rect(img, ox + 9, oy + 1, 1, 8, acc if active else acc_dark)
        # Vertical hydraulic piston shafts
        fill_rect(img, ox + 4, oy + 0, 2, 1 + stroke, steel_mid)
        fill_rect(img, ox + 4, oy + 9 - stroke, 2, 1 + stroke, steel_mid)
        # Upper heavy press ram
        top_y = 1 + stroke
        fill_rect(img, ox + 1, oy + top_y, 8, 2, steel_mid)
        fill_rect(img, ox + 2, oy + top_y, 6, 1, steel_hi)
        fill_rect(img, ox + 2, oy + top_y + 1, 6, 1, acc if active else acc_dark)
        # Lower heavy anvil ram
        bot_y = 7 - stroke
        fill_rect(img, ox + 1, oy + bot_y, 8, 2, steel_mid)
        fill_rect(img, ox + 2, oy + bot_y, 6, 1, acc if active else acc_dark)
        fill_rect(img, ox + 2, oy + bot_y + 1, 6, 1, PAL["slate_dark"])
        # Compressed material / pressure flash in center gap
        if active:
            flash = PAL["bevel_white"] if stroke == 2 else acc_hi
            fill_rect(img, ox + 3, oy + 4, 4, 2, acc)
            fill_rect(img, ox + 4, oy + 4, 2, 2, flash)
        else:
            fill_rect(img, ox + 4, oy + 4, 2, 2, acc_dark)

    elif kind == "recycler":
        # Glowing emerald-green cyclic 4-arrow recycle loop with clean gaps between segments
        g_hi = acc_hi if active else acc
        g_mid = acc if active else acc_dark
        g_lo = acc_dark
        # Four separated directional arrows chasing clockwise around the dark chamber
        arms = [
            [(2, 2), (3, 2), (4, 2), (5, 2), (6, 2), (5, 1), (5, 3)],  # top ->
            [(7, 3), (7, 4), (7, 5), (7, 6), (6, 5), (8, 5)],          # right v
            [(7, 7), (6, 7), (5, 7), (4, 7), (3, 7), (4, 6), (4, 8)],  # bottom <-
            [(2, 6), (2, 5), (2, 4), (2, 3), (1, 4), (3, 4)],          # left ^
        ]
        for a_idx, pts in enumerate(arms):
            if active:
                if a_idx == f:
                    col = PAL["bevel_white"]
                elif a_idx == (f - 1) % 4:
                    col = g_hi
                else:
                    col = g_mid
            else:
                col = g_hi if a_idx in (0, 2) else g_mid
            for dx, dy in pts:
                px(img, ox + dx, oy + dy, col)
        # Inner dark shredder core + emerald status pulse
        fill_rect(img, ox + 4, oy + 4, 2, 2, PAL["lcd_bg"])
        if active:
            px(img, ox + 4 + (f % 2), oy + 4 + ((f // 2) % 2), g_hi)
        else:
            px(img, ox + 4, oy + 4, g_lo)

    elif kind == "extractor":
        # Brass resin tap spout + glowing amber-gold teardrop falling into warm resin basin
        brass_hi = theme["accent_hi"] if active else theme["secondary"]
        brass_mid = theme["secondary"]
        copper_dark = acc_dark
        # Top brass manifold & tap nozzle (y=0..2)
        fill_rect(img, ox + 1, oy + 0, 8, 2, brass_mid)
        fill_rect(img, ox + 2, oy + 0, 6, 1, brass_hi)
        fill_rect(img, ox + 4, oy + 2, 2, 1, brass_mid)
        px(img, ox + 4, oy + 2, brass_hi)
        # Bottom amber resin basin (y=8..9)
        fill_rect(img, ox + 1, oy + 8, 8, 2, copper_dark)
        fill_rect(img, ox + 2, oy + 8, 6, 1, acc if active else copper_dark)
        if active:
            # Animated plump teardrop detaching from tap (y=3..6) and splashing on frame 3
            drop_y = [3, 4, 5, 3][f]
            # Teardrop shape: tip (2px) -> wide belly (4px) -> rounded bottom (2px)
            for dx in (4, 5):
                px(img, ox + dx, oy + drop_y, acc_hi if dx == 4 else acc)
            if drop_y + 1 <= 7:
                for dx in (3, 4, 5, 6):
                    px(img, ox + dx, oy + drop_y + 1, PAL["bevel_white"] if dx == 4 else acc)
            if drop_y + 2 <= 7:
                for dx in (4, 5):
                    px(img, ox + dx, oy + drop_y + 2, acc_hi if dx == 4 else acc_dark)
            if f == 3:
                # Bright splash ripple in resin basin
                px(img, ox + 2, oy + 7, acc_hi)
                px(img, ox + 7, oy + 7, acc_hi)
                fill_rect(img, ox + 3, oy + 8, 4, 1, acc_hi)
        else:
            # Suspended amber resin teardrop (clean dark gap above basin)
            for dx in (4, 5):
                px(img, ox + dx, oy + 4, acc_hi if dx == 4 else acc)
            for dx in (3, 4, 5, 6):
                px(img, ox + dx, oy + 5, acc_hi if dx == 4 else acc)
            for dx in (4, 5):
                px(img, ox + dx, oy + 6, acc)

    elif kind == "ore_washer":
        # Pressurized circular water-jet wash chamber with 4-vane spinning aqua vortex
        w_white = PAL["cyan_white"]
        w_hi = acc_hi if active else acc
        w_mid = acc if active else theme["secondary"]
        w_lo = theme["secondary"] if active else acc_dark
        ang_shift = f * (math.pi / 4.0) if active else 0.0
        for y in range(0, 10):
            for x in range(0, 10):
                dx = x - 4.5
                dy = y - 4.5
                r = math.hypot(dx, dy)
                if r <= 4.7:
                    ang = math.atan2(dy, dx) + ang_shift
                    # Concentric wash drum rim + 4-arm swirling water jets
                    if 3.7 <= r <= 4.7:
                        px(img, ox + x, oy + y, w_lo if (x + y) % 2 == 0 else (18, 66, 104, 255))
                    elif r < 1.3:
                        px(img, ox + x, oy + y, w_white if active else w_hi)
                    else:
                        vane = math.sin(4.0 * ang + r * 0.9)
                        if vane > 0.35:
                            px(img, ox + x, oy + y, w_hi if active else w_mid)
                        elif vane > -0.25:
                            px(img, ox + x, oy + y, w_mid if active else w_lo)
                        else:
                            px(img, ox + x, oy + y, (14, 46, 76, 255))
        # Pressurized corner spray nozzles
        nozzle = acc_hi if active else PAL["slate_light"]
        for nx, ny in [(0, 0), (9, 0), (0, 9), (9, 9)]:
            px(img, ox + nx, oy + ny, nozzle)


def draw_giant_casing_set(kind: str, seed: int) -> None:
    """Generate distinct top, bottom, and side formed casing textures for one giant machine kind."""
    t = GIANT_THEMES[kind]
    base, hi, lo = t["base"], t["hi"], t["lo"]
    acc, acc_hi, acc_dark, sec = t["accent"], t["accent_hi"], t["accent_dark"], t["secondary"]

    top = make_casing_tile(base, hi, lo, seed=seed, rivets=True)
    side = make_casing_tile(base, hi, lo, seed=seed + 1, rivets=True)
    bot = make_casing_tile(shade(base, 0.80), shade(hi, 0.82), PAL["shadow_deep"], seed=seed + 2, rivets=True)

    # Bottom structural ribs + machine-colored corner anchor tabs
    for y in (4, 8, 11):
        fill_rect(bot, 2, y, 12, 1, PAL["border_dark"])
    for cx, cy in [(2, 2), (12, 2), (2, 12), (12, 12)]:
        fill_rect(bot, cx, cy, 2, 2, acc_dark)
        px(bot, cx, cy, acc)

    if kind == "macerator":
        # Top: Diagonal amber/black hazard frame + heavy steel crusher hopper grate
        for x in range(2, 14):
            c_top = acc if (x % 3 != 0) else sec
            c_bot = acc if ((x + 1) % 3 != 0) else sec
            px(top, x, 2, c_top)
            px(top, x, 13, c_bot)
        for y in range(3, 13):
            px(top, 2, y, acc if (y % 3 != 0) else sec)
            px(top, 13, y, acc if ((y + 1) % 3 != 0) else sec)
        fill_rect(top, 4, 4, 8, 8, PAL["lcd_bg"])
        draw_rect_border(top, 4, 4, 8, 8, PAL["border_dark"])
        for y in (5, 7, 9):
            fill_rect(top, 5, y, 6, 1, PAL["slate_light"])
            px(top, 5, y, hi)
        # Side: Iconic amber/black diagonal hazard bands (top y=2..3 & bottom y=12..13) + crusher jaw vents
        for y in (2, 3, 12, 13):
            for x in range(2, 14):
                stripe = ((x + y) // 2) % 2 == 0
                px(side, x, y, acc if stripe else sec)
                if stripe and y in (2, 12):
                    px(side, x, y, acc_hi)
        fill_rect(side, 2, 4, 12, 1, PAL["border_dark"])
        fill_rect(side, 2, 11, 12, 1, PAL["border_dark"])
        # Recessed jaw vents in center (y=5..10)
        fill_rect(side, 3, 5, 10, 6, PAL["lcd_bg"])
        draw_rect_border(side, 3, 5, 10, 6, PAL["border_dark"])
        for x in range(4, 12):
            if x % 2 == 0:
                px(side, x, 6, hi)
                px(side, x, 7, PAL["slate_light"])
            else:
                px(side, x, 8, PAL["slate_light"])
                px(side, x, 9, hi)

    elif kind == "compressor":
        # Top: Heavy dark gunmetal forged clamp frame + cobalt hydraulic pressure ring + center piston cap
        draw_rect_border(top, 2, 2, 12, 12, acc_dark)
        draw_rect_border(top, 3, 3, 10, 10, acc)
        fill_rect(top, 5, 5, 6, 6, shade(base, 1.15))
        draw_rect_border(top, 5, 5, 6, 6, PAL["border_dark"])
        fill_rect(top, 6, 6, 4, 4, sec)
        px(top, 6, 6, hi)
        for cx, cy in [(2, 2), (13, 2), (2, 13), (13, 13)]:
            px(top, cx, cy, acc_hi)
        # Side: Thick vertical hydraulic clamp ribs (x=3..4, x=11..12) + cobalt pressure rails & seam
        fill_rect(side, 2, 2, 12, 1, acc)
        fill_rect(side, 2, 13, 12, 1, acc_dark)
        fill_rect(side, 2, 7, 12, 2, PAL["lcd_bg"])
        fill_rect(side, 5, 7, 6, 1, acc_hi)
        fill_rect(side, 5, 8, 6, 1, acc_dark)
        # Heavy vertical clamp columns overlapping the seam
        for rx in (3, 11):
            fill_rect(side, rx, 2, 2, 12, sec)
            fill_rect(side, rx, 2, 1, 12, hi)
            px(side, rx, 3, acc_hi)
            px(side, rx, 12, acc)
            px(side, rx + 1, 7, PAL["border_dark"])
            px(side, rx + 1, 8, PAL["border_dark"])

    elif kind == "recycler":
        # Top: Emerald perimeter trim + dark shredder intake chute with diagonal blades
        draw_rect_border(top, 2, 2, 12, 12, acc)
        px(top, 2, 2, acc_hi)
        fill_rect(top, 4, 4, 8, 8, PAL["lcd_bg"])
        draw_rect_border(top, 4, 4, 8, 8, acc_dark)
        for i in range(4, 11):
            px(top, i, i, PAL["slate_light"])
            if i + 2 < 12:
                px(top, i + 2, i, acc_dark)
            if i - 2 >= 4:
                px(top, i - 2, i, acc_dark)
        # Side: Twin bright emerald-green recycle trim bands + staggered shredder louver slats
        fill_rect(side, 2, 2, 12, 2, acc)
        fill_rect(side, 2, 2, 12, 1, acc_hi)
        fill_rect(side, 2, 12, 12, 2, acc_dark)
        fill_rect(side, 2, 12, 12, 1, acc)
        # Shredder louvers (y=5..10)
        for y in (5, 7, 9):
            fill_rect(side, 3, y, 10, 1, PAL["lcd_bg"])
            fill_rect(side, 3, y + 1, 10, 1, hi)
            px(side, 3 if (y == 7) else 12, y, acc)

    elif kind == "extractor":
        # Top: Brass central valve cap + 4 radial cylindrical copper resin manifolds
        for x in range(2, 14):
            px(top, x, 6, acc_hi)
            px(top, x, 7, acc)
            px(top, x, 8, acc)
            px(top, x, 9, acc_dark)
        for y in range(2, 14):
            px(top, 6, y, acc_hi)
            px(top, 7, y, acc)
            px(top, 8, y, acc)
            px(top, 9, y, acc_dark)
        draw_rect_border(top, 5, 5, 6, 6, PAL["border_dark"])
        fill_rect(top, 6, 6, 4, 4, sec)
        fill_rect(top, 7, 7, 2, 2, acc_hi)
        # Side: Brass top/bottom trim collars + horizontal copper resin pipe + glowing amber sight window
        fill_rect(side, 2, 2, 12, 1, acc_hi)
        fill_rect(side, 2, 3, 12, 1, sec)
        fill_rect(side, 2, 12, 12, 1, sec)
        fill_rect(side, 2, 13, 12, 1, acc_dark)
        # Horizontal cylindrical copper resin manifold pipe (y=6..9)
        fill_rect(side, 2, 6, 12, 1, acc_hi)
        fill_rect(side, 2, 7, 12, 2, acc)
        fill_rect(side, 2, 9, 12, 1, acc_dark)
        # Brass pipe coupling collars at x=4 and x=11
        for cx in (4, 11):
            fill_rect(side, cx, 5, 1, 6, sec)
            px(side, cx, 5, acc_hi)
            px(side, cx, 10, acc_dark)
        # Central glowing amber resin sight-glass window
        fill_rect(side, 6, 6, 4, 4, PAL["border_dark"])
        fill_rect(side, 7, 7, 2, 2, (255, 198, 48, 255))
        px(side, 7, 7, PAL["bevel_white"])

    elif kind == "ore_washer":
        # Top: Deep marine-slate basin grate + crossing aqua-cyan hydro-manifolds
        draw_rect_border(top, 2, 2, 12, 12, acc_dark)
        fill_rect(top, 3, 3, 10, 10, (16, 44, 68, 255))
        # Crossing hydro pipes
        fill_rect(top, 7, 2, 2, 12, acc)
        fill_rect(top, 2, 7, 12, 2, acc)
        fill_rect(top, 7, 2, 1, 12, acc_hi)
        fill_rect(top, 2, 7, 12, 1, acc_hi)
        fill_rect(top, 6, 6, 4, 4, acc_dark)
        fill_rect(top, 7, 7, 2, 2, PAL["cyan_white"])
        # Side: Twin vertical aqua water-gauge side strips + center pressurized hydro-manifold pipe
        for gx in (2, 12):
            fill_rect(side, gx, 2, 2, 12, PAL["lcd_bg"])
            for gy in range(3, 13):
                c = acc_hi if gy % 3 == 0 else (acc if gy > 4 else sec)
                px(side, gx, gy, c)
                px(side, gx + 1, gy, sec if gy % 2 == 0 else acc_dark)
        # Horizontal pressurized aqua-cyan hydro-manifold across center (x=4..11, y=6..9)
        fill_rect(side, 4, 6, 8, 1, acc_hi)
        fill_rect(side, 4, 7, 8, 2, sec)
        fill_rect(side, 4, 9, 8, 1, acc_dark)
        for rx in (6, 9):
            fill_rect(side, rx, 5, 1, 6, hi)
            px(side, rx, 10, lo)

    save_png(top, TEX_ROOT / "block" / f"giant_casing_{kind}_top.png")
    save_png(side, TEX_ROOT / "block" / f"giant_casing_{kind}_side.png")
    save_png(bot, TEX_ROOT / "block" / f"giant_casing_{kind}_bottom.png")


def draw_giant_port_tile(kind: str, mode_name: str, bezel: RGBA, dark: RGBA, mode: str, seed: int) -> Image.Image:
    """Draw a corner port block combining the machine's color-coded casing frame with crisp I/O port indicators."""
    t = GIANT_THEMES[kind]
    img = make_casing_tile(t["base"], t["hi"], t["lo"], seed=seed, rivets=False)
    acc, acc_hi, acc_dark = t["accent"], t["accent_hi"], t["accent_dark"]

    # Machine-themed outer frame accent strip along edge y=1, y=14, x=1, x=14 + bold corners
    for i in range(2, 14):
        if kind == "macerator":
            c_top = acc if ((i // 2) % 2 == 0) else t["secondary"]
            c_bot = acc if (((i + 1) // 2) % 2 == 0) else t["secondary"]
        else:
            c_top = acc_hi if i in (2, 3, 12, 13) else acc
            c_bot = acc if i in (2, 3, 12, 13) else acc_dark
        px(img, i, 1, c_top)
        px(img, i, 14, c_bot)
        px(img, 1, i, c_top)
        px(img, 14, i, c_bot)
    for cx, cy in [(1, 1), (14, 1), (1, 14), (14, 14)]:
        px(img, cx, cy, acc_hi if (cx == 1 and cy == 1) else acc)

    # Dark isolation border + unmistakable glowing port mode bezel
    draw_rect_border(img, 2, 2, 12, 12, PAL["outline"])
    draw_rect_border(img, 3, 3, 10, 10, bezel)
    draw_rect_border(img, 4, 4, 8, 8, dark)
    fill_rect(img, 5, 5, 6, 6, PAL["lcd_bg"])

    if mode == "both":
        # Full (In+Out): crisp symmetric bidirectional cyan diamond/arrows
        for x in (7, 8):
            px(img, x, 5, PAL["cyan_white"])
            px(img, x, 10, PAL["cyan_white"])
        for x in (6, 7, 8, 9):
            px(img, x, 6, PAL["cyan_glow"])
            px(img, x, 9, PAL["cyan_glow"])
        px(img, 5, 7, PAL["cyan_mid"])
        px(img, 10, 7, PAL["cyan_mid"])
        px(img, 5, 8, PAL["cyan_mid"])
        px(img, 10, 8, PAL["cyan_mid"])
        fill_rect(img, 7, 7, 2, 2, PAL["cyan_white"])
    elif mode == "in":
        # Input Only: unmistakable bright blue inward funnel arrow
        fill_rect(img, 6, 5, 4, 2, bezel)
        fill_rect(img, 5, 7, 6, 2, PAL["cyan_white"])
        fill_rect(img, 7, 9, 2, 2, bezel)
    elif mode == "out":
        # Output Only: unmistakable bright orange outward eject arrow
        fill_rect(img, 7, 5, 2, 2, bezel)
        fill_rect(img, 5, 7, 6, 2, PAL["gold_hi"])
        fill_rect(img, 6, 9, 4, 2, bezel)
    else:
        # Blocked: sealed dark-gray shutter + muted red X
        for y in range(5, 11, 2):
            fill_rect(img, 5, y, 6, 1, PAL["slot_dark"])
        for i in range(4):
            px(img, 6 + i, 6 + i, (172, 70, 74, 255))
            px(img, 9 - i, 6 + i, (172, 70, 74, 255))
    return img


def gen_giant_machines() -> None:
    # Remove obsolete un-differentiated legacy giant textures if present
    for obsolete in (
        "giant_casing_top.png",
        "giant_casing_bottom.png",
        "giant_casing_side.png",
        "giant_port_full.png",
        "giant_port_input.png",
        "giant_port_output.png",
        "giant_port_blocked.png",
    ):
        (TEX_ROOT / "block" / obsolete).unlink(missing_ok=True)

    # Megastructure Component item (16x16 heavy interlocking truss + glowing cyan core)
    mc = Image.new("RGBA", (16, 16), PAL["transparent"])
    draw_rect_border(mc, 1, 1, 14, 14, PAL["slate_light"])
    draw_rect_border(mc, 2, 2, 12, 12, PAL["slate_dark"])
    for i in range(2, 14):
        px(mc, i, i, PAL["bevel_hi"])
        px(mc, 15 - i, i, PAL["slate_mid"])
    fill_rect(mc, 5, 5, 6, 6, PAL["lcd_bg"])
    draw_rect_border(mc, 5, 5, 6, 6, PAL["cyan_mid"])
    fill_rect(mc, 6, 6, 4, 4, PAL["cyan_glow"])
    px(mc, 7, 7, PAL["cyan_white"])
    for cx, cy in [(2, 2), (13, 2), (2, 13), (13, 13)]:
        px(mc, cx, cy, PAL["gold_mid"])
    save_png(add_item_outline(mc), TEX_ROOT / "item" / "megastructure_component.png")

    port_styles = {
        "full": (PAL["cyan_glow"], PAL["cyan_dark"], "both"),
        "input": (PAL["blue_port"], PAL["blue_dark"], "in"),
        "output": (PAL["orange_port"], PAL["orange_dark"], "out"),
        "blocked": (PAL["slate_dark"], PAL["border_dark"], "blocked"),
    }

    kinds = ["macerator", "compressor", "recycler", "extractor", "ore_washer"]
    for idx, kind in enumerate(kinds):
        t = GIANT_THEMES[kind]
        base, hi, lo = t["base"], t["hi"], t["lo"]
        acc, acc_hi, acc_dark = t["accent"], t["accent_hi"], t["accent_dark"]

        # 1. Per-kind formed casing faces (top / side / bottom)
        draw_giant_casing_set(kind, seed=500 + idx * 10)

        # 2. Per-kind unformed unit block (distinct casing palette + accent trim + emblem)
        unit = make_casing_tile(shade(base, 1.08), hi, lo, seed=510 + idx, rivets=False)
        for i in range(2, 14):
            if kind == "macerator":
                c = acc if ((i // 2) % 2 == 0) else t["secondary"]
                px(unit, i, 2, c)
                px(unit, i, 13, c)
            elif kind == "compressor":
                px(unit, 2, i, acc if i % 2 == 0 else acc_dark)
                px(unit, 13, i, acc if i % 2 == 0 else acc_dark)
            elif kind == "recycler":
                px(unit, i, 2, acc)
                px(unit, i, 13, acc_dark)
            elif kind == "extractor":
                px(unit, i, 2, t["secondary"])
                px(unit, i, 13, acc)
            elif kind == "ore_washer":
                px(unit, 2, i, acc if i % 2 == 0 else t["secondary"])
                px(unit, 13, i, acc if i % 2 == 0 else t["secondary"])
        fill_rect(unit, 3, 3, 10, 10, PAL["lcd_bg"])
        draw_rect_border(unit, 2, 2, 12, 12, acc_dark)
        # Re-apply bold corner modular lock tabs in machine accent color
        for cx, cy in [(2, 2), (13, 2), (2, 13), (13, 13)]:
            px(unit, cx, cy, acc_hi)
        for cx, cy in [(1, 7), (1, 8), (14, 7), (14, 8), (7, 1), (8, 1), (7, 14), (8, 14)]:
            px(unit, cx, cy, acc)
        draw_giant_emblem(unit, kind, active=False, ox=3, oy=3, frame=0)
        save_png(unit, TEX_ROOT / "block" / f"giant_{kind}_unit.png")

        # 3. Per-kind formed Front-Center / Core display (inactive)
        core_off = make_casing_tile(base, hi, lo, seed=520 + idx, rivets=False)
        for i in range(1, 15):
            if kind == "macerator" and (i <= 3 or i >= 12):
                px(core_off, i, 1, acc if i % 2 == 0 else t["secondary"])
                px(core_off, i, 14, acc if i % 2 == 0 else t["secondary"])
            elif i in (1, 2, 13, 14):
                px(core_off, i, 1, acc)
                px(core_off, i, 14, acc_dark)
        fill_rect(core_off, 2, 2, 12, 12, PAL["lcd_bg"])
        draw_rect_border(core_off, 2, 2, 12, 12, acc_dark)
        for lx, ly in [(1, 1), (14, 1), (1, 14), (14, 14)]:
            px(core_off, lx, ly, t["led_off"])
        draw_giant_emblem(core_off, kind, active=False, ox=3, oy=3, frame=0)
        save_png(core_off, TEX_ROOT / "block" / f"giant_core_front_{kind}.png")

        # 4. Per-kind formed Front-Center / Core display (4-frame animated active)
        strip = Image.new("RGBA", (16, 64), PAL["transparent"])
        for f in range(4):
            pulse = 0.5 + 0.5 * math.sin((f / 4.0) * 2 * math.pi)
            core_on = make_casing_tile(base, hi, lo, seed=520 + idx, rivets=False)
            fill_rect(core_on, 2, 2, 12, 12, lerp_color(PAL["lcd_bg"], acc_dark, 0.28))
            draw_rect_border(core_on, 2, 2, 12, 12, lerp_color(acc, acc_hi, pulse))
            draw_giant_emblem(core_on, kind, active=True, ox=3, oy=3, frame=f)
            # Machine-tinted glowing corner LEDs + side accent pulses
            led = lerp_color(acc_hi, PAL["bevel_white"], pulse)
            for lx, ly in [(1, 1), (14, 1), (1, 14), (14, 14)]:
                px(core_on, lx, ly, led)
            for mx, my in [(7, 1), (8, 1), (7, 14), (8, 14)]:
                px(core_on, mx, my, lerp_color(acc, acc_hi, pulse))
            strip.paste(core_on, (0, f * 16))
        act_path = TEX_ROOT / "block" / f"giant_core_front_{kind}_active.png"
        save_png(strip, act_path)
        save_mcmeta(act_path, frametime=4, interpolate=False)

        # 5. Per-kind 4 Corner Port Blocks (machine-colored outer frame + crisp port mode bezel)
        for mode_name, (bezel, dark, mode) in port_styles.items():
            port_img = draw_giant_port_tile(kind, mode_name, bezel, dark, mode, seed=540 + idx)
            save_png(port_img, TEX_ROOT / "block" / f"giant_port_{kind}_{mode_name}.png")


# =============================================================================
# 6. WATER GAS / NATURAL GAS / BIOGAS BLOCKS, FLUIDS & ITEMS
# =============================================================================

def gen_gas_and_bio_blocks() -> None:
    # --- Water Gas Machine ---
    # Top: Embedded photovoltaic mini-array
    wgm_top = make_casing_tile(PAL["slate_mid"], PAL["bevel_hi"], PAL["slot_dark"], seed=601, rivets=True)
    for y in range(3, 13):
        for x in range(3, 13):
            if x in (6, 9) or y in (6, 9):
                px(wgm_top, x, y, PAL["bevel_hi"])
            else:
                px(wgm_top, x, y, lerp_color((64, 128, 214, 255), (22, 48, 108, 255), (x + y - 6) / 18.0))
    draw_rect_border(wgm_top, 2, 2, 12, 12, PAL["border_dark"])
    save_png(wgm_top, TEX_ROOT / "block" / "water_gas_machine_top.png")

    def draw_wgm_front(active: bool, phase: float = 0.0) -> Image.Image:
        img = make_casing_tile(PAL["slate_mid"], PAL["bevel_hi"], PAL["slot_dark"], seed=602, rivets=False)
        # Left sight glass (blue water) + Right sight glass (pale cyan water gas)
        fill_rect(img, 2, 3, 4, 10, PAL["lcd_bg"])
        draw_rect_border(img, 2, 3, 4, 10, PAL["border_dark"])
        fill_rect(img, 3, 6, 2, 6, (44, 136, 224, 255))
        fill_rect(img, 10, 3, 4, 10, PAL["lcd_bg"])
        draw_rect_border(img, 10, 3, 4, 10, PAL["border_dark"])
        gas_col = (164, 232, 240, 255) if active else (96, 152, 166, 255)
        fill_rect(img, 11, 5, 2, 7, gas_col)
        # Center reaction chamber + LED
        fill_rect(img, 6, 4, 4, 8, PAL["slot_dark"])
        pulse = 0.5 + 0.5 * math.sin(phase * 2 * math.pi)
        px(img, 7, 5, lerp_color(PAL["cyan_mid"], PAL["cyan_white"], pulse) if active else PAL["red_dark"])
        px(img, 8, 5, lerp_color(PAL["cyan_mid"], PAL["cyan_white"], pulse) if active else PAL["red_dark"])
        fill_rect(img, 7, 7, 2, 4, lerp_color(PAL["cyan_dark"], PAL["cyan_glow"], pulse) if active else PAL["lcd_bg"])
        return img

    save_png(draw_wgm_front(False), TEX_ROOT / "block" / "water_gas_machine_front.png")
    strip = Image.new("RGBA", (16, 64), PAL["transparent"])
    for f in range(4):
        strip.paste(draw_wgm_front(True, f / 4.0), (0, f * 16))
    p = TEX_ROOT / "block" / "water_gas_machine_front_active.png"
    save_png(strip, p)
    save_mcmeta(p, frametime=4, interpolate=True)

    wgm_side = make_casing_tile(PAL["slate_mid"], PAL["bevel_hi"], PAL["slot_dark"], seed=603, rivets=True)
    fill_rect(wgm_side, 3, 6, 10, 4, PAL["slot_dark"])
    fill_rect(wgm_side, 4, 7, 8, 2, (132, 198, 210, 255))
    save_png(wgm_side, TEX_ROOT / "block" / "water_gas_machine_side.png")
    save_png(make_casing_tile(PAL["slate_dark"], PAL["slate_light"], PAL["shadow_deep"], seed=604), TEX_ROOT / "block" / "water_gas_machine_bottom.png")

    # --- Gas Furnace ---
    def draw_gf_front(active: bool, phase: float = 0.0) -> Image.Image:
        img = make_casing_tile((104, 114, 126, 255), PAL["bevel_hi"], PAL["slot_dark"], seed=610, rivets=True)
        # Upper smelting window + lower blue gas burner grate
        fill_rect(img, 3, 3, 10, 5, PAL["lcd_bg"])
        draw_rect_border(img, 3, 3, 10, 5, PAL["border_dark"])
        fill_rect(img, 3, 9, 10, 4, PAL["lcd_bg"])
        draw_rect_border(img, 3, 9, 10, 4, PAL["border_dark"])
        if active:
            flicker = 0.5 + 0.5 * math.sin(phase * 2 * math.pi)
            for x in range(4, 12):
                h_col = lerp_color((24, 152, 248, 255), (196, 248, 255, 255), 0.4 + 0.6 * det_hash(x, int(phase * 4), 611))
                px(img, x, 6, h_col)
                px(img, x, 5, lerp_color((16, 102, 214, 255), PAL["cyan_glow"], flicker))
                px(img, x, 11, PAL["cyan_glow"] if x % 2 == 0 else (48, 178, 255, 255))
                px(img, x, 10, PAL["cyan_white"] if (x + int(phase * 4)) % 3 == 0 else (28, 134, 232, 255))
        else:
            for x in range(4, 12, 2):
                px(img, x, 10, PAL["slot_dark"])
                px(img, x, 11, PAL["slate_dark"])
        return img

    save_png(draw_gf_front(False), TEX_ROOT / "block" / "gas_furnace_front.png")
    strip = Image.new("RGBA", (16, 64), PAL["transparent"])
    for f in range(4):
        strip.paste(draw_gf_front(True, f / 4.0), (0, f * 16))
    p = TEX_ROOT / "block" / "gas_furnace_front_active.png"
    save_png(strip, p)
    save_mcmeta(p, frametime=3, interpolate=True)
    save_png(make_casing_tile((104, 114, 126, 255), PAL["bevel_hi"], PAL["slot_dark"], seed=612, accent=(46, 168, 240, 255)), TEX_ROOT / "block" / "gas_furnace_side.png")
    gf_top = make_casing_tile((104, 114, 126, 255), PAL["bevel_hi"], PAL["slot_dark"], seed=613, rivets=True)
    for y in (5, 7, 9, 11):
        fill_rect(gf_top, 4, y, 8, 1, PAL["border_dark"])
    save_png(gf_top, TEX_ROOT / "block" / "gas_furnace_top.png")
    save_png(make_casing_tile(PAL["slate_dark"], PAL["slate_light"], PAL["shadow_deep"], seed=614), TEX_ROOT / "block" / "gas_furnace_bottom.png")

    # --- Gas Generator ---
    def draw_gg_front(active: bool, phase: float = 0.0) -> Image.Image:
        img = make_casing_tile((98, 110, 124, 255), PAL["bevel_hi"], PAL["slot_dark"], seed=620, rivets=False)
        # Top blue-green turbine gauge LCD
        fill_rect(img, 3, 2, 10, 4, PAL["lcd_bg"])
        draw_rect_border(img, 3, 2, 10, 4, PAL["border_dark"])
        pulse = 0.5 + 0.5 * math.sin(phase * 2 * math.pi)
        if active:
            for x in range(4, 12):
                px(img, x, 3, lerp_color(PAL["teal_mid"], PAL["cyan_white"], (x - 4) / 8.0 * (0.6 + 0.4 * pulse)))
                px(img, x, 4, PAL["cyan_mid"])
        else:
            fill_rect(img, 4, 4, 8, 1, PAL["lcd_bg2"])
        # Dual lower turbine vents
        for tx in (3, 9):
            fill_rect(img, tx, 8, 4, 5, PAL["lcd_bg"])
            draw_rect_border(img, tx, 8, 4, 5, PAL["border_dark"])
            if active:
                fill_rect(img, tx + 1, 9, 2, 3, lerp_color(PAL["cyan_mid"], PAL["cyan_white"], pulse))
            else:
                fill_rect(img, tx + 1, 9, 2, 3, PAL["slot_dark"])
        return img

    save_png(draw_gg_front(False), TEX_ROOT / "block" / "gas_generator_front.png")
    strip = Image.new("RGBA", (16, 64), PAL["transparent"])
    for f in range(4):
        strip.paste(draw_gg_front(True, f / 4.0), (0, f * 16))
    p = TEX_ROOT / "block" / "gas_generator_front_active.png"
    save_png(strip, p)
    save_mcmeta(p, frametime=3, interpolate=True)
    save_png(make_casing_tile((98, 110, 124, 255), PAL["bevel_hi"], PAL["slot_dark"], seed=621, accent=PAL["teal_hi"]), TEX_ROOT / "block" / "gas_generator_side.png")
    gg_top = make_casing_tile((98, 110, 124, 255), PAL["bevel_hi"], PAL["slot_dark"], seed=622, rivets=True)
    draw_rect_border(gg_top, 5, 5, 6, 6, PAL["teal_mid"])
    fill_rect(gg_top, 6, 6, 4, 4, PAL["lcd_bg"])
    save_png(gg_top, TEX_ROOT / "block" / "gas_generator_top.png")
    save_png(make_casing_tile(PAL["slate_dark"], PAL["slate_light"], PAL["shadow_deep"], seed=623), TEX_ROOT / "block" / "gas_generator_bottom.png")

    # --- Methane Ice Cluster (translucent icy-blue crystalline clathrate with faint pulse) ---
    mic_strip = Image.new("RGBA", (16, 64), PAL["transparent"])
    for f in range(4):
        pulse = 0.5 + 0.5 * math.sin((f / 4.0) * 2 * math.pi)
        tile = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
        for y in range(16):
            for x in range(16):
                # Faceted icy crystal pattern with slightly translucent edges
                edge = min(x, 15 - x, y, 15 - y)
                facet1 = math.sin((x * 0.7 + y * 0.5)) * 0.5 + 0.5
                facet2 = math.cos((x * 0.4 - y * 0.8)) * 0.5 + 0.5
                grain = det_hash(x, y, 630)
                v = 0.45 * facet1 + 0.35 * facet2 + 0.20 * grain
                # Internal trapped methane gas bubble pockets glow softly!
                d_bubble = min(math.hypot(x - 5, y - 5), math.hypot(x - 11, y - 10), math.hypot(x - 9, y - 4))
                if d_bubble < 2.2:
                    c = lerp_color((168, 240, 255, 235), (235, 252, 255, 250), 0.5 + 0.5 * pulse)
                elif edge == 0:
                    c = (130, 212, 248, 200)
                else:
                    base_c = lerp_color((62, 148, 212, 215), (176, 234, 255, 235), v)
                    if (x + y) % 5 == 0 and v > 0.55:
                        base_c = lerp_color(base_c, (238, 252, 255, 245), 0.45 + 0.25 * pulse)
                    c = base_c
                tile.putpixel((x, y), c)
        mic_strip.paste(tile, (0, f * 16))
    mic_path = TEX_ROOT / "block" / "methane_ice_cluster.png"
    save_png(mic_strip, mic_path)
    save_mcmeta(mic_path, frametime=8, interpolate=True)

    # --- Culture Incubator ---
    def draw_ci_front(active: bool, phase: float = 0.0) -> Image.Image:
        img = make_casing_tile((110, 122, 134, 255), PAL["bevel_hi"], PAL["slot_dark"], seed=640, rivets=True)
        # Tall central glass bio-culture cylinder
        fill_rect(img, 4, 2, 8, 12, PAL["lcd_bg"])
        draw_rect_border(img, 4, 2, 8, 12, PAL["border_dark"])
        # Steel retention rings across glass
        fill_rect(img, 4, 5, 8, 1, PAL["slate_light"])
        fill_rect(img, 4, 10, 8, 1, PAL["slate_light"])
        pulse = 0.5 + 0.5 * math.sin(phase * 2 * math.pi)
        for y in range(3, 13):
            if y in (5, 10):
                continue
            for x in range(5, 11):
                if active:
                    bub = ((x * 3 + y + int(phase * 4)) % 7 == 0)
                    c = PAL["emerald_hi"] if bub else lerp_color(PAL["emerald_dark"], PAL["emerald_mid"], 0.5 + 0.4 * pulse)
                    if x == 6:
                        c = lerp_color(c, PAL["bevel_white"], 0.3)
                    px(img, x, y, c)
                else:
                    px(img, x, y, (28, 66, 52, 255) if y > 7 else (20, 42, 48, 255))
        return img

    save_png(draw_ci_front(False), TEX_ROOT / "block" / "culture_incubator_front.png")
    strip = Image.new("RGBA", (16, 64), PAL["transparent"])
    for f in range(4):
        strip.paste(draw_ci_front(True, f / 4.0), (0, f * 16))
    p = TEX_ROOT / "block" / "culture_incubator_front_active.png"
    save_png(strip, p)
    save_mcmeta(p, frametime=4, interpolate=True)
    save_png(make_casing_tile((110, 122, 134, 255), PAL["bevel_hi"], PAL["slot_dark"], seed=641, accent=PAL["emerald_mid"]), TEX_ROOT / "block" / "culture_incubator_side.png")
    save_png(make_casing_tile((110, 122, 134, 255), PAL["bevel_hi"], PAL["slot_dark"], seed=642, rivets=True), TEX_ROOT / "block" / "culture_incubator_top.png")
    save_png(make_casing_tile(PAL["slate_dark"], PAL["slate_light"], PAL["shadow_deep"], seed=643), TEX_ROOT / "block" / "culture_incubator_bottom.png")

    # --- Organic Fermenter ---
    def draw_of_front(active: bool, phase: float = 0.0) -> Image.Image:
        img = make_casing_tile((96, 110, 118, 255), PAL["bevel_hi"], PAL["slot_dark"], seed=650, rivets=True)
        # Wide pressurized emerald bio-vat window
        fill_rect(img, 3, 4, 10, 8, PAL["lcd_bg"])
        draw_rect_border(img, 3, 4, 10, 8, PAL["border_dark"])
        pulse = 0.5 + 0.5 * math.sin(phase * 2 * math.pi)
        for y in range(5, 11):
            for x in range(4, 12):
                if active:
                    swirl = 0.5 + 0.5 * math.sin((x + y) * 0.7 + phase * 2 * math.pi)
                    c = lerp_color((42, 148, 72, 255), (168, 236, 92, 255), swirl)
                    px(img, x, y, c)
                else:
                    px(img, x, y, (28, 74, 48, 255))
        # Pressure valve + top indicator LED
        px(img, 4, 2, PAL["emerald_hi"] if active else PAL["red_dark"])
        px(img, 11, 2, (238, 194, 62, 255) if active else PAL["slot_dark"])
        fill_rect(img, 6, 2, 4, 1, PAL["bronze_mid"])
        return img

    save_png(draw_of_front(False), TEX_ROOT / "block" / "organic_fermenter_front.png")
    strip = Image.new("RGBA", (16, 64), PAL["transparent"])
    for f in range(4):
        strip.paste(draw_of_front(True, f / 4.0), (0, f * 16))
    p = TEX_ROOT / "block" / "organic_fermenter_front_active.png"
    save_png(strip, p)
    save_mcmeta(p, frametime=4, interpolate=True)
    save_png(make_casing_tile((96, 110, 118, 255), PAL["bevel_hi"], PAL["slot_dark"], seed=651, accent=(136, 214, 78, 255)), TEX_ROOT / "block" / "organic_fermenter_side.png")
    of_top = make_casing_tile((96, 110, 118, 255), PAL["bevel_hi"], PAL["slot_dark"], seed=652, rivets=True)
    draw_rect_border(of_top, 4, 4, 8, 8, PAL["emerald_dark"])
    fill_rect(of_top, 5, 5, 6, 6, PAL["slot_dark"])
    fill_rect(of_top, 6, 6, 4, 4, PAL["emerald_mid"])
    save_png(of_top, TEX_ROOT / "block" / "organic_fermenter_top.png")
    save_png(make_casing_tile(PAL["slate_dark"], PAL["slate_light"], PAL["shadow_deep"], seed=653), TEX_ROOT / "block" / "organic_fermenter_bottom.png")


def gen_fluid_textures() -> None:
    """Generate bright, seamlessly looping animated still (16x256) and flow (32x512) fluid strips."""
    fluids = {
        "water_gas": ((178, 224, 232, 220), (235, 252, 255, 240), -1),
        "natural_gas": ((156, 218, 255, 210), (228, 248, 255, 235), -1),
        "culture_medium": ((98, 218, 146, 240), (186, 255, 206, 250), 1),
    }
    for fname, (c_lo, c_hi, direction) in fluids.items():
        # Still: 16x16 x 16 frames = 16x256
        still = Image.new("RGBA", (16, 256), PAL["transparent"])
        for f in range(16):
            ph = (f / 16.0) * 2 * math.pi
            for y in range(16):
                for x in range(16):
                    w1 = math.sin(x * 2 * math.pi / 16.0 + ph) * math.cos(y * 2 * math.pi / 16.0 - direction * ph)
                    w2 = math.sin((x + y) * 2 * math.pi / 8.0 - direction * ph)
                    t = 0.5 + 0.3 * w1 + 0.2 * w2
                    still.putpixel((x, f * 16 + y), lerp_color(c_lo, c_hi, t))
        still_p = TEX_ROOT / "block" / "fluid" / f"{fname}_still.png"
        save_png(still, still_p)
        save_mcmeta(still_p, frametime=3, interpolate=True)

        # Flow: 32x32 x 16 frames = 32x512
        flow = Image.new("RGBA", (32, 512), PAL["transparent"])
        for f in range(16):
            shift = f * 2 * direction
            for y in range(32):
                sy = (y + shift) % 32
                for x in range(32):
                    streak = math.sin(x * 2 * math.pi / 8.0) * 0.35 + math.sin((sy * 2 * math.pi / 16.0) + math.sin(x * 0.4)) * 0.65
                    t = 0.5 + 0.45 * streak
                    flow.putpixel((x, f * 32 + y), lerp_color(c_lo, c_hi, t))
        flow_p = TEX_ROOT / "block" / "fluid" / f"{fname}_flow.png"
        save_png(flow, flow_p)
        save_mcmeta(flow_p, frametime=2, interpolate=True)


# Exact IC2NE 16x16 cell frame palette & template (matching ic2 fluid_cell.png & empty_cell.png)
IC2_CELL_PALETTE: Dict[str, RGBA] = {
    ".": (0, 0, 0, 0),
    "O": (0x28, 0x30, 0x3B, 255),  # dark slate outline & right-side notch
    "H": (0xE9, 0xF1, 0xF7, 255),  # bright top/right glass-rim highlight
    "M": (0xB2, 0xC0, 0xCE, 255),  # upper-right metallic bevel
    "W": (0xFF, 0xFF, 0xFF, 255),  # left specular streak
    "L": (0x7E, 0x8E, 0x9F, 255),  # left/bottom casing midtone
    "D": (0x4E, 0x5B, 0x6B, 255),  # recessed frame & right body
    "U": (0x26, 0x2D, 0x35, 255),  # universal fluid_cell empty window
    "E": (0x56, 0x60, 0x6B, 255),  # basic empty_cell tin window backing
}

IC2_CELL_FRAME = [
    "....OOOOOOOO....",
    "...OHHHHHMMMO...",
    "..OHWDDDDDDHHO..",
    "..OLWD____HDDO..",
    "..OLWD____HDDO..",
    "..OLWD____HDDO..",
    "..OLWD____HDDO..",
    "..OLWD____HDOO..",
    "..OLWD____HDOO..",
    "..OLWD____HDDO..",
    "..OLWD____HDDO..",
    "..OLWD____HDDO..",
    "..OLWD____HDDO..",
    "..ODDDDDDDDDDO..",
    "...OLLLDDDDDO...",
    "....OOOOOOOO....",
]


def draw_cell_sprite(
    window_palette: Tuple[RGBA, RGBA, RGBA, RGBA],
    pattern: List[str],
    basic: bool = False,
    basic_glint: RGBA | None = None,
) -> Image.Image:
    """16x16 cell drawn pixel-accurately on IC2NE's exact fluid_cell / empty_cell frame.

    Window interior is x=6..9, y=3..12 (4x10).
    For universal cells (basic=False), all 10 rows of the window are filled using ``pattern``
    with codes '0' (brightest highlight), '1' (light), '2' (mid), '3' (deep shade).
    For basic tin cells (basic=True), rows y=3..6 retain IC2's empty_cell tin backing (with an
    optional tinted glint at (6, 5)) and rows y=7..12 show the filled liquid window, matching
    IC2NE's basic_water_cell / basic_lava_cell convention.
    """
    img = Image.new("RGBA", (16, 16), PAL["transparent"])
    c0, c1, c2, c3 = window_palette
    code_map = {"0": c0, "1": c1, "2": c2, "3": c3}
    for y, row in enumerate(IC2_CELL_FRAME):
        for x, ch in enumerate(row):
            if ch == "_":
                if basic and y < 7:
                    if basic_glint is not None and x == 6 and y == 5:
                        px(img, x, y, basic_glint)
                    else:
                        px(img, x, y, IC2_CELL_PALETTE["E"])
                else:
                    py = (y - 7) if basic else (y - 3)
                    px_code = pattern[py][x - 6]
                    px(img, x, y, code_map[px_code])
            else:
                px(img, x, y, IC2_CELL_PALETTE[ch])
    return img


def draw_bucket_sprite(fluid_lo: RGBA, fluid_hi: RGBA) -> Image.Image:
    """16x16 crisp iron bucket filled with glowing fluid/gas."""
    img = Image.new("RGBA", (16, 16), PAL["transparent"])
    # Rim & bucket trapezoid
    for y in range(5, 14):
        half = 5 if y < 7 else (4 if y < 11 else 3)
        for x in range(8 - half, 8 + half):
            t = (x - (8 - half)) / max(1, (2 * half - 1))
            c = lerp_color((226, 232, 240, 255), (104, 114, 128, 255), t)
            if y in (6, 13):
                c = shade(c, 0.85)
            px(img, x, y, c)
    # Brimming fluid ellipse inside bucket mouth (y=4..7, x=4..11)
    for y in range(4, 8):
        for x in range(4, 12):
            if (y in (4, 7) and x in (4, 11)):
                continue
            t = (x - 4) / 7.0
            px(img, x, y, lerp_color(fluid_hi, fluid_lo, t))
    # Subtle handle arc
    for hx, hy in [(3, 5), (3, 6), (12, 5), (12, 6), (4, 4), (11, 4)]:
        px(img, hx, hy, (192, 200, 210, 255))
    return add_item_outline(img)


def gen_gas_and_bio_items() -> None:
    # 4 Named Cells (reusing IC2NE's exact fluid_cell / empty_cell frame silhouette;
    # biogas cell is provided directly by ic2:biogas_cell)
    # 1) Water Gas Cell (pale misty aqua-turquoise syngas wisps)
    water_gas_pal = (
        (232, 252, 255, 255),  # 0: luminous vapor highlight
        (162, 232, 240, 255),  # 1: pale aqua gas
        (92, 188, 204, 255),   # 2: mid turquoise gas
        (46, 126, 146, 255),   # 3: shaded depth
    )
    water_gas_pat = [
        "0121",
        "0012",
        "1232",
        "0113",
        "1022",
        "1233",
        "0122",
        "1102",
        "2123",
        "1223",
    ]
    save_png(
        draw_cell_sprite(water_gas_pal, water_gas_pat, basic=False),
        TEX_ROOT / "item" / "water_gas_cell.png",
    )

    # 2) Natural Gas Cell (crisp icy methane sky-blue gas bubbles)
    natural_gas_pal = (
        (216, 246, 255, 255),  # 0: bright icy specular
        (114, 208, 255, 255),  # 1: clear sky-blue methane
        (42, 152, 238, 255),   # 2: rich glacial blue
        (18, 92, 168, 255),    # 3: deep pressure shade
    )
    natural_gas_pat = [
        "0012",
        "1213",
        "0123",
        "1022",
        "1233",
        "0112",
        "1203",
        "0123",
        "1123",
        "1233",
    ]
    save_png(
        draw_cell_sprite(natural_gas_pal, natural_gas_pat, basic=False),
        TEX_ROOT / "item" / "natural_gas_cell.png",
    )

    # 3) Culture Medium Cell (vibrant emerald-mint bio-nutrient liquid)
    culture_pal = (
        (198, 255, 222, 255),  # 0: bright nutrient sheen
        (92, 232, 148, 255),   # 1: lively emerald-mint
        (34, 174, 94, 255),    # 2: rich bio-green
        (16, 108, 56, 255),    # 3: deep shaded green
    )
    culture_pat = [
        "0011",
        "0233",
        "0123",
        "1213",
        "1233",
        "0123",
        "1013",
        "1123",
        "1223",
        "1233",
    ]
    save_png(
        draw_cell_sprite(culture_pal, culture_pat, basic=False),
        TEX_ROOT / "item" / "culture_medium_cell.png",
    )

    # 4) Basic Culture Medium Cell (IC2NE empty_cell tin frame + lower emerald liquid fill)
    basic_culture_pat = [
        "1213",
        "0123",
        "1233",
        "2013",
        "1223",
        "2233",
    ]
    save_png(
        draw_cell_sprite(
            culture_pal,
            basic_culture_pat,
            basic=True,
            basic_glint=(108, 152, 130, 255),
        ),
        TEX_ROOT / "item" / "basic_culture_medium_cell.png",
    )

    # 3 Buckets
    save_png(draw_bucket_sprite((112, 184, 198, 255), (218, 248, 252, 255)), TEX_ROOT / "item" / "water_gas_bucket.png")
    save_png(draw_bucket_sprite((74, 174, 244, 255), (206, 242, 255, 255)), TEX_ROOT / "item" / "natural_gas_bucket.png")
    save_png(draw_bucket_sprite((46, 176, 98, 255), (146, 252, 182, 255)), TEX_ROOT / "item" / "culture_medium_bucket.png")

    # PV Panel Power Upgrade (IC2NE-aligned 16x16 upgrade cartridge: metallic bevel casing,
    # 2 solar-gold top corner LEDs, 3 bottom gold pins, blue PV cell grid + gold sun/boost motif)
    save_png(draw_pv_power_upgrade(), TEX_ROOT / "item" / "pv_power_upgrade.png")


def draw_pv_power_upgrade() -> Image.Image:
    """Procedurally generate a 16x16 IC2NE-aligned upgrade cartridge for PV Panel Power Upgrade."""
    img = Image.new("RGBA", (16, 16), PAL["transparent"])

    outline = (0x18, 0x1E, 0x26, 255)
    top_bevel = (0xDC, 0xE6, 0xF0, 255)
    left_bevel = (0xB3, 0xC0, 0xCF, 255)
    right_bevel = (0x83, 0x91, 0xA1, 255)
    bot_bevel = (0x4E, 0x5A, 0x69, 255)
    screen_shadow = (0x0C, 0x10, 0x17, 255)
    screen_bg = (0x12, 0x17, 0x20, 255)
    recess_bar = (0x1A, 0x1F, 0x28, 255)
    pin_root = (0xB2, 0x72, 0x14, 255)
    pin_hi = (0xFF, 0xF8, 0xCC, 255)
    pin_gold = (0xE6, 0xA8, 0x22, 255)
    led_gold = (0xFF, 0xD8, 0x38, 255)

    # Outer cartridge silhouette outline
    fill_rect(img, 2, 0, 12, 1, outline)
    fill_rect(img, 1, 1, 1, 13, outline)
    fill_rect(img, 14, 1, 1, 13, outline)
    fill_rect(img, 2, 15, 12, 1, outline)

    # Top metallic bevel + 2 glowing solar-gold corner LEDs
    fill_rect(img, 3, 1, 10, 1, top_bevel)
    px(img, 2, 1, led_gold)
    px(img, 13, 1, led_gold)

    # Left & right metallic bevels + bottom bevel
    fill_rect(img, 2, 2, 1, 10, left_bevel)
    fill_rect(img, 13, 2, 1, 10, right_bevel)
    fill_rect(img, 2, 12, 11, 1, bot_bevel)
    px(img, 13, 12, outline)

    # Bottom connector recess & 3 gold contact pins (identical pitch to IC2NE upgrades)
    fill_rect(img, 2, 13, 12, 1, recess_bar)
    fill_rect(img, 2, 14, 12, 1, outline)
    for pin_x in (3, 7, 11):
        px(img, pin_x, 13, pin_root)
        px(img, pin_x, 14, pin_hi)
        px(img, pin_x + 1, 14, pin_gold)

    # Dark recessed display viewport (x=3..12, y=2..11)
    fill_rect(img, 3, 2, 10, 10, screen_bg)
    fill_rect(img, 3, 2, 10, 1, screen_shadow)
    fill_rect(img, 3, 2, 1, 10, screen_shadow)

    # Upper-left radiant golden sun motif (x=4..7, y=2..5)
    sun_white = (255, 252, 224, 255)
    sun_gold = (255, 226, 56, 255)
    sun_amber = (255, 158, 34, 255)
    px(img, 5, 3, sun_white)
    px(img, 6, 3, sun_gold)
    px(img, 5, 4, sun_gold)
    px(img, 6, 4, sun_gold)
    for rx, ry in [(5, 2), (6, 2), (4, 3), (7, 3), (4, 4), (7, 4), (5, 5), (6, 5)]:
        px(img, rx, ry, sun_amber)

    # Upper-right crisp gold double up-chevron ^^ (power-up motif echoing IC2 overclocker)
    px(img, 10, 2, sun_white)
    px(img, 9, 3, sun_gold)
    px(img, 11, 3, sun_gold)
    px(img, 10, 4, sun_gold)
    px(img, 9, 5, sun_amber)
    px(img, 11, 5, sun_amber)

    # Lower crisp blue 3x2 solar photovoltaic cell grid (x=4..11, y=7..11)
    pv_hi = (98, 186, 255, 255)
    pv_mid = (42, 116, 232, 255)
    pv_deep = (22, 62, 154, 255)
    bus_hi = (232, 246, 255, 255)
    bus_mid = (168, 210, 248, 255)
    bus_lo = (116, 158, 206, 255)

    for y in range(7, 12):
        for x in range(4, 12):
            is_vbus = x in (6, 9)
            is_hbus = y == 9
            if is_vbus and is_hbus:
                c = bus_hi
            elif is_hbus:
                c = bus_mid
            elif is_vbus:
                c = bus_hi if y == 7 else (bus_mid if y < 11 else bus_lo)
            else:
                if y == 7:
                    c = pv_hi if x in (4, 7, 10) else pv_mid
                elif y == 8:
                    c = pv_mid if x in (4, 7, 10) else pv_deep
                elif y == 10:
                    c = pv_mid if x in (4, 7, 10) else pv_deep
                else:
                    c = pv_deep
            px(img, x, y, c)
    # Specular solar glint on top-left PV silicon cell
    px(img, 4, 7, (166, 224, 255, 255))

    return img


# =============================================================================
# 7. ALL 10 GUIs (256x256 PNGs) + PORT ACCESS GEAR ICON + gui_coordinates.json
# =============================================================================

def gen_guis() -> Dict[str, dict]:
    coords: Dict[str, dict] = {}

    # --- 7.1 solar_panel.png (194x168) ---
    g = draw_gui_panel(194, 168, seed=701)
    draw_lcd(g, 18, 20, 158, 38, accent=PAL["cyan_mid"])
    # Sky condition slot inset inside LCD
    draw_rect_border(g, 23, 31, 16, 16, PAL["cyan_dark"])
    # Empty energy bar inside LCD
    draw_energy_bar_24x14(g, 46, 25, filled=False)
    # 4 Charging slots
    charge_slots = []
    for i in range(4):
        sx, sy = 61 + i * 18, 62
        draw_slot(g, sx, sy)
        charge_slots.append({"frame": [sx, sy, 18, 18], "item": [sx + 1, sy + 1]})
    draw_bolt_14x14(g, 43, 64, lit=True)
    draw_player_inventory(g, 16, 85)
    # Sprites at x >= 196
    draw_energy_bar_24x14(g, 196, 0, filled=True)
    draw_sun_14x14(g, 196, 16, lit=True)
    draw_moon_14x14(g, 196, 32)
    draw_rain_14x14(g, 196, 48)
    draw_blocked_sky_14x14(g, 196, 64)
    save_png(g, TEX_ROOT / "gui" / "solar_panel.png")
    coords["solar_panel"] = {
        "texture": "ecc2:textures/gui/solar_panel.png",
        "panel": [0, 0, 194, 168],
        "lcd": [18, 20, 158, 38],
        "sky_icon_pos": [24, 32, 14, 14],
        "energy_bar_pos": [46, 25, 24, 14],
        "text_area": [76, 24, 94, 30],
        "charge_slots": charge_slots,
        "player_inventory": {"top_left_item": [17, 86], "hotbar_item": [17, 144]},
        "sprites": {
            "energy_bar_fill": [196, 0, 24, 14],
            "sky_day": [196, 16, 14, 14],
            "sky_night": [196, 32, 14, 14],
            "sky_rain": [196, 48, 14, 14],
            "sky_none": [196, 64, 14, 14],
        },
    }

    # --- 7.2 quantum_generator.png (176x193) ---
    g = draw_gui_panel(176, 193, seed=702)
    draw_lcd(g, 10, 18, 156, 42, accent=PAL["cyan_glow"])
    # Active LED bezel inside LCD top-right
    draw_rect_border(g, 151, 23, 12, 12, PAL["cyan_dark"])
    # Recessed control deck for production & tier buttons
    make_bevel_box(g, 10, 64, 156, 42, fill=PAL["slate_dark"], raised=False, hi=PAL["bevel_hi"], lo=PAL["border_dark"])
    draw_player_inventory(g, 7, 110)
    # Sprites at x >= 180
    # LED active (10x10)
    fill_rect(g, 180, 0, 10, 10, PAL["lcd_bg"])
    fill_rect(g, 181, 1, 8, 8, PAL["cyan_glow"])
    fill_rect(g, 183, 3, 4, 4, PAL["cyan_white"])
    # LED inactive (10x10)
    fill_rect(g, 180, 12, 10, 10, PAL["lcd_bg"])
    fill_rect(g, 181, 13, 8, 8, PAL["red_dark"])
    fill_rect(g, 183, 15, 4, 4, PAL["red_led"])
    # Button sprites (24x16)
    make_bevel_box(g, 180, 26, 24, 16, fill=PAL["slate_mid"], raised=True, hi=PAL["bevel_hi"], lo=PAL["border_dark"])
    make_bevel_box(g, 180, 44, 24, 16, fill=PAL["slate_light"], raised=True, hi=PAL["cyan_white"], lo=PAL["cyan_dark"])
    make_bevel_box(g, 180, 62, 24, 16, fill=PAL["lcd_bg2"], raised=True, hi=PAL["cyan_glow"], lo=PAL["cyan_dark"])
    save_png(g, TEX_ROOT / "gui" / "quantum_generator.png")
    coords["quantum_generator"] = {
        "texture": "ecc2:textures/gui/quantum_generator.png",
        "panel": [0, 0, 176, 193],
        "lcd": [10, 18, 156, 42],
        "led_pos": [152, 24, 10, 10],
        "button_deck": [10, 64, 156, 42],
        "production_buttons": [[12 + i * 26, 68, 24, 16] for i in range(6)],
        "tier_buttons": [[12 + i * 26, 87, 24, 16] for i in range(6)],
        "player_inventory": {"top_left_item": [8, 111], "hotbar_item": [8, 169]},
        "sprites": {
            "led_active": [180, 0, 10, 10],
            "led_inactive": [180, 12, 10, 10],
            "button_normal": [180, 26, 24, 16],
            "button_hover": [180, 44, 24, 16],
            "button_selected": [180, 62, 24, 16],
        },
    }

    # --- 7.3 molecular_transformer.png (220x193) ---
    g = draw_gui_panel(220, 193, seed=703)
    # Left transformation reactor bay
    make_bevel_box(g, 12, 22, 32, 74, fill=PAL["slate_dark"], raised=False, hi=PAL["bevel_hi"], lo=PAL["border_dark"])
    draw_slot(g, 19, 25, rim_accent=PAL["blue_port"])
    # Vertical 10x15 conduit between input and output
    make_bevel_box(g, 22, 46, 12, 17, fill=PAL["lcd_bg"], raised=False, hi=PAL["slate_light"], lo=PAL["border_dark"])
    draw_slot(g, 19, 67, rim_accent=PAL["orange_port"])
    # Right telemetry LCD
    draw_lcd(g, 52, 22, 156, 74, accent=PAL["cyan_glow"])
    draw_player_inventory(g, 29, 110)
    # Sprites at x >= 224: vertical 10x15 cyan beam + idle
    for yy in range(15):
        for xx in range(10):
            d = abs(xx - 4.5) / 4.5
            c = lerp_color(PAL["cyan_white"], PAL["cyan_mid"], d)
            if yy >= 12 and abs(xx - 4.5) > (15 - yy) * 1.4:
                continue
            px(g, 224 + xx, yy, c)
    fill_rect(g, 224, 18, 10, 15, PAL["lcd_bg2"])
    save_png(g, TEX_ROOT / "gui" / "molecular_transformer.png")
    coords["molecular_transformer"] = {
        "texture": "ecc2:textures/gui/molecular_transformer.png",
        "panel": [0, 0, 220, 193],
        "input_slot": {"frame": [19, 25, 18, 18], "item": [20, 26]},
        "output_slot": {"frame": [19, 67, 18, 18], "item": [20, 68]},
        "progress_bar_pos": [23, 47, 10, 15],
        "lcd": [52, 22, 156, 74],
        "player_inventory": {"top_left_item": [30, 111], "hotbar_item": [30, 169]},
        "sprites": {
            "progress_fill": [224, 0, 10, 15],
            "progress_idle": [224, 18, 10, 15],
        },
    }

    # --- 7.4 kinetic_generator.png (176x166) ---
    g = draw_gui_panel(176, 166, seed=704)
    # Turbine housing graphic behind rotor slot
    make_bevel_box(g, 66, 20, 44, 26, fill=PAL["slate_dark"], raised=False, hi=PAL["bevel_hi"], lo=PAL["border_dark"])
    draw_slot(g, 79, 24, rim_accent=PAL["cyan_dark"])
    # Tier badge frame top-right
    make_bevel_box(g, 134, 18, 34, 12, fill=PAL["lcd_bg"], raised=False, hi=PAL["bevel_hi"], lo=PAL["border_dark"])
    # Rotor durability bar track (44, 48, 88, 7)
    make_bevel_box(g, 43, 47, 90, 9, fill=PAL["lcd_bg"], raised=False, hi=PAL["bevel_hi"], lo=PAL["border_dark"])
    # Bottom LCD readout
    draw_lcd(g, 16, 59, 144, 20, accent=PAL["cyan_mid"])
    draw_player_inventory(g, 7, 83)
    # Sprites at x >= 180 (note: 180 + 74 <= 254! Keep durability bar width 72px so 180+72=252 <= 256!)
    # Wait: let's make durability bar width 72px centered at x=52 (52+72=124 -> centered in 176px!) so sprite fits inside 256px cleanly!
    # Re-draw durability bar track at (51, 47, 74, 9) over the clean panel!
    g = draw_gui_panel(176, 166, seed=704)
    make_bevel_box(g, 66, 20, 44, 25, fill=PAL["slate_dark"], raised=False, hi=PAL["bevel_hi"], lo=PAL["border_dark"])
    draw_slot(g, 79, 23, rim_accent=PAL["cyan_dark"])
    make_bevel_box(g, 134, 18, 34, 12, fill=PAL["lcd_bg"], raised=False, hi=PAL["bevel_hi"], lo=PAL["border_dark"])
    make_bevel_box(g, 51, 47, 74, 9, fill=PAL["lcd_bg"], raised=False, hi=PAL["bevel_hi"], lo=PAL["border_dark"])
    draw_lcd(g, 16, 59, 144, 20, accent=PAL["cyan_mid"])
    draw_player_inventory(g, 7, 83)
    # Durability bars (72x7 at x=180..252)
    for row_y, (c_hi, c_lo) in [
        (0, ((118, 255, 168, 255), (36, 168, 92, 255))),
        (10, ((255, 238, 112, 255), (218, 152, 28, 255))),
        (20, ((255, 112, 112, 255), (196, 36, 42, 255))),
    ]:
        for xx in range(72):
            for yy in range(7):
                c = lerp_color(c_hi, c_lo, yy / 6.0)
                if xx % 6 == 5:
                    c = shade(c, 0.72)
                px(g, 180 + xx, row_y + yy, c)
    # Tier badges (34x12)
    make_bevel_box(g, 180, 32, 34, 12, fill=PAL["bronze_dark"], raised=True, hi=PAL["teal_hi"], lo=PAL["outline"])
    make_bevel_box(g, 180, 46, 34, 12, fill=PAL["orange_dark"], raised=True, hi=PAL["gold_hi"], lo=PAL["outline"])
    make_bevel_box(g, 180, 60, 34, 12, fill=PAL["slate_dark"], raised=True, hi=PAL["gold_hi"], lo=PAL["cyan_dark"])
    # Status icons (12x12)
    fill_rect(g, 182, 78, 8, 8, PAL["cyan_glow"])
    fill_rect(g, 184, 80, 4, 4, PAL["cyan_white"])
    fill_rect(g, 196, 78, 8, 8, PAL["orange_port"])
    fill_rect(g, 199, 79, 2, 4, PAL["outline"])
    px(g, 199, 84, PAL["outline"])
    px(g, 200, 84, PAL["outline"])
    save_png(g, TEX_ROOT / "gui" / "kinetic_generator.png")
    coords["kinetic_generator"] = {
        "texture": "ecc2:textures/gui/kinetic_generator.png",
        "panel": [0, 0, 176, 166],
        "rotor_slot": {"frame": [79, 23, 18, 18], "item": [80, 24]},
        "tier_badge_pos": [134, 18, 34, 12],
        "durability_bar_pos": [52, 48, 72, 7],
        "lcd": [16, 59, 144, 20],
        "player_inventory": {"top_left_item": [8, 84], "hotbar_item": [8, 142]},
        "sprites": {
            "durability_green": [180, 0, 72, 7],
            "durability_yellow": [180, 10, 72, 7],
            "durability_red": [180, 20, 72, 7],
            "badge_durable": [180, 32, 34, 12],
            "badge_overclocked": [180, 46, 34, 12],
            "badge_precision": [180, 60, 34, 12],
            "status_active": [180, 76, 12, 12],
            "status_overwind": [194, 76, 12, 12],
        },
    }

    # --- 7.5 giant_machine.png (256x236 FLAGSHIP SCI-FI IC2NE PANEL!) ---
    g = draw_gui_panel(256, 236, seed=705, accent=PAL["cyan_glow"])
    # Header telemetry status pill (top-right)
    draw_lcd(g, 152, 4, 96, 11, accent=None, scanlines=False)

    # Left 3x3 Blue-Framed Input Matrix tray (9, 21, 64, 79)
    make_bevel_box(g, 9, 21, 64, 79, fill=(78, 92, 110, 255), raised=False, hi=PAL["blue_port"], lo=PAL["border_dark"])
    input_slots = []
    micro_bars = []
    for row in range(3):
        for col in range(3):
            sx = 12 + col * 20
            sy = 24 + row * 24
            draw_slot(g, sx, sy, rim_accent=PAL["blue_port"])
            # 16x3 micro-progress bar directly under each input slot
            fill_rect(g, sx + 1, sy + 19, 16, 3, PAL["lcd_bg"])
            draw_rect_border(g, sx, sy + 18, 18, 5, PAL["border_dark"])
            input_slots.append({"lane": row * 3 + col, "frame": [sx, sy, 18, 18], "item": [sx + 1, sy + 1]})
            micro_bars.append({"lane": row * 3 + col, "pos": [sx + 1, sy + 19, 16, 3]})

    # Center EU Core Column (78, 23, 18, 56 -> inner 80, 25, 14, 52)
    make_bevel_box(g, 78, 23, 18, 56, fill=PAL["lcd_bg"], raised=False, hi=PAL["bevel_hi"], lo=PAL["border_dark"])
    draw_rect_border(g, 79, 24, 16, 54, PAL["cyan_dark"])
    for gy in range(29, 77, 6):
        fill_rect(g, 80, gy, 14, 1, (18, 42, 56, 255))
    # Battery discharge slot right below EU Core
    draw_slot(g, 78, 82, rim_accent=PAL["cyan_dark"])

    # Center Flow Arrow (100, 44, 24, 15) + compact status boxes above/below
    draw_lcd(g, 99, 23, 26, 16, accent=PAL["cyan_dark"], scanlines=False)
    draw_arrow_24x15(g, 100, 44, PAL["slot_dark"], PAL["slate_mid"])
    draw_lcd(g, 99, 64, 26, 15, accent=None, scanlines=False)

    # Washer 64,000 mB Water Tank Window (128, 23, 20, 76 -> inner 130, 25, 16, 72)
    draw_tank_frame(g, 128, 23, 20, 76)

    # Mid-lower telemetry LCD (10, 105, 114, 40)
    draw_lcd(g, 10, 105, 114, 40, accent=PAL["cyan_glow"])
    # Washer Water container slots right below water tank (129, 105) & (129, 125)
    draw_slot(g, 129, 105, rim_accent=PAL["blue_port"])
    draw_slot(g, 129, 125, rim_accent=PAL["slate_dark"])

    # Right 3x3 Orange-Framed Output Matrix tray (153, 21, 64, 79)
    make_bevel_box(g, 153, 21, 64, 79, fill=(96, 86, 80, 255), raised=False, hi=PAL["orange_port"], lo=PAL["border_dark"])
    output_slots = []
    for row in range(3):
        for col in range(3):
            sx = 156 + col * 20
            sy = 24 + row * 24
            draw_slot(g, sx, sy, rim_accent=PAL["orange_port"])
            output_slots.append({"index": row * 3 + col, "frame": [sx, sy, 18, 18], "item": [sx + 1, sy + 1]})

    # Far-Right 4 Standard Upgrade Slots (x=225, y=24, 44, 64, 84)
    upgrade_slots = []
    for i in range(4):
        ux, uy = 225, 24 + i * 20
        draw_slot(g, ux, uy, rim_accent=PAL["slate_dark"])
        upgrade_slots.append({"index": i, "frame": [ux, uy, 18, 18], "item": [ux + 1, uy + 1]})

    # Mini 8-port status bar below Output Matrix (153, 105, 64, 40) + Port Access Gear Button dock (224, 114, 20, 20)
    draw_lcd(g, 153, 105, 64, 40, accent=PAL["cyan_mid"], scanlines=False)
    make_bevel_box(g, 224, 114, 20, 20, fill=PAL["slate_mid"], raised=True, hi=PAL["bevel_hi"], lo=PAL["border_dark"])

    # Player Inventory centered at bottom (47, 151 -> hotbar 47, 209)
    draw_player_inventory(g, 47, 151)

    # Sprites in bottom strip y=237..255 (256x19 area)
    # 1) micro_progress_fill (2, 238, 16, 3) & bg (2, 243, 16, 3)
    for xx in range(16):
        c = lerp_color(PAL["cyan_mid"], PAL["cyan_white"], xx / 15.0)
        px(g, 2 + xx, 238, PAL["cyan_white"])
        px(g, 2 + xx, 239, c)
        px(g, 2 + xx, 240, PAL["cyan_dark"])
    fill_rect(g, 2, 243, 16, 3, PAL["lcd_bg"])
    # 2) flow_arrow_fill (24, 238, 24, 15)
    draw_arrow_24x15(g, 24, 238, PAL["cyan_mid"], PAL["cyan_white"])
    # 3) eu_segment_fill (52, 238, 14, 16) — tileable glowing cyan core strip
    for yy in range(16):
        for xx in range(14):
            d = abs(xx - 6.5) / 6.5
            c = lerp_color(PAL["cyan_white"], PAL["cyan_mid"], d)
            if yy % 4 == 3:
                c = shade(c, 0.72)
            px(g, 52 + xx, 238 + yy, c)
    # 4) 4 port mode mini icons (12x12)
    for ix, (col, ch) in enumerate([
        (PAL["cyan_glow"], "F"),
        (PAL["blue_port"], "I"),
        (PAL["orange_port"], "O"),
        (PAL["slate_dark"], "X"),
    ]):
        bx = 70 + ix * 14
        make_bevel_box(g, bx, 238, 12, 12, fill=PAL["lcd_bg"], raised=True, hi=col, lo=shade(col, 0.5))
        fill_rect(g, bx + 3, 241, 6, 6, col)
        if ch == "X":
            fill_rect(g, bx + 4, 242, 4, 4, PAL["red_dark"])
    # 5) Active/Idle LEDs (8x8)
    fill_rect(g, 128, 238, 8, 8, PAL["cyan_glow"])
    fill_rect(g, 130, 240, 4, 4, PAL["cyan_white"])
    fill_rect(g, 138, 238, 8, 8, PAL["slot_dark"])
    # 6) Tank scale overlay tile (16x16 at 150, 238)
    draw_tank_overlay_sprite(g, 150, 238, 16, 16)
    # 7) Gear button normal / hover (18x18 at 170, 237 and 190, 237)
    make_bevel_box(g, 170, 237, 18, 18, fill=PAL["slate_mid"], raised=True, hi=PAL["bevel_hi"], lo=PAL["border_dark"])
    make_bevel_box(g, 190, 237, 18, 18, fill=PAL["lcd_bg2"], raised=True, hi=PAL["cyan_glow"], lo=PAL["cyan_dark"])

    save_png(g, TEX_ROOT / "gui" / "giant_machine.png")
    coords["giant_machine"] = {
        "texture": "ecc2:textures/gui/giant_machine.png",
        "panel": [0, 0, 256, 236],
        "header_status_lcd": [152, 4, 96, 11],
        "input_slots": input_slots,
        "micro_progress_bars": micro_bars,
        "eu_core_bar": [80, 25, 14, 52],
        "battery_slot": {"frame": [78, 82, 18, 18], "item": [79, 83]},
        "active_batch_lcd": [99, 23, 26, 16],
        "flow_arrow_pos": [100, 44, 24, 15],
        "eu_draw_lcd": [99, 64, 26, 15],
        "water_tank": {"frame": [128, 23, 20, 76], "fluid": [130, 25, 16, 72]},
        "washer_container_in_slot": {"frame": [129, 105, 18, 18], "item": [130, 106]},
        "washer_container_out_slot": {"frame": [129, 125, 18, 18], "item": [130, 126]},
        "telemetry_lcd": [10, 105, 114, 40],
        "output_slots": output_slots,
        "upgrade_slots": upgrade_slots,
        "port_summary_lcd": [153, 105, 64, 40],
        "port_access_button": [224, 114, 20, 20],
        "player_inventory": {"top_left_item": [48, 152], "hotbar_item": [48, 210]},
        "sprites": {
            "micro_progress_fill": [2, 238, 16, 3],
            "micro_progress_bg": [2, 243, 16, 3],
            "flow_arrow_fill": [24, 238, 24, 15],
            "eu_segment_fill": [52, 238, 14, 16],
            "port_full_icon": [70, 238, 12, 12],
            "port_input_icon": [84, 238, 12, 12],
            "port_output_icon": [98, 238, 12, 12],
            "port_blocked_icon": [112, 238, 12, 12],
            "led_active": [128, 238, 8, 8],
            "led_idle": [138, 238, 8, 8],
            "tank_scale_overlay": [150, 238, 16, 16],
            "gear_button_bg": [170, 237, 18, 18],
            "gear_button_hover": [190, 237, 18, 18],
        },
    }

    # --- 7.6 water_gas_machine.png (176x180) ---
    g = draw_gui_panel(176, 180, seed=706)
    draw_tank_frame(g, 12, 20, 20, 54)
    draw_slot(g, 36, 20)
    draw_slot(g, 36, 56)
    draw_slot(g, 60, 24)
    draw_rect_border(g, 83, 21, 16, 16, PAL["slate_dark"])
    draw_arrow_24x15(g, 60, 46, PAL["slot_dark"], PAL["slate_mid"])
    draw_bolt_14x14(g, 62, 65, lit=False)
    draw_slot(g, 82, 63)
    draw_tank_frame(g, 106, 20, 20, 54)
    draw_slot(g, 130, 20)
    draw_slot(g, 130, 56)
    wgm_up = []
    for i in range(4):
        draw_slot(g, 152, 18 + i * 18)
        wgm_up.append({"index": i, "frame": [152, 18 + i * 18, 18, 18], "item": [153, 19 + i * 18]})
    draw_player_inventory(g, 7, 97)
    # Sprites
    draw_arrow_24x15(g, 180, 0, PAL["cyan_mid"], PAL["cyan_white"])
    draw_bolt_14x14(g, 180, 18, lit=True)
    draw_sun_14x14(g, 180, 34, lit=True)
    draw_moon_14x14(g, 180, 50)
    draw_tank_overlay_sprite(g, 180, 68, 16, 50)
    save_png(g, TEX_ROOT / "gui" / "water_gas_machine.png")
    coords["water_gas_machine"] = {
        "texture": "ecc2:textures/gui/water_gas_machine.png",
        "panel": [0, 0, 176, 180],
        "water_tank": {"frame": [12, 20, 20, 54], "fluid": [14, 22, 16, 50]},
        "water_cell_in": {"frame": [36, 20, 18, 18], "item": [37, 21]},
        "water_cell_out": {"frame": [36, 56, 18, 18], "item": [37, 57]},
        "coal_dust_slot": {"frame": [60, 24, 18, 18], "item": [61, 25]},
        "solar_icon_pos": [84, 22, 14, 14],
        "progress_arrow_pos": [60, 46, 24, 15],
        "energy_bolt_pos": [62, 65, 14, 14],
        "battery_slot": {"frame": [82, 63, 18, 18], "item": [83, 64]},
        "gas_tank": {"frame": [106, 20, 20, 54], "fluid": [108, 22, 16, 50]},
        "gas_cell_in": {"frame": [130, 20, 18, 18], "item": [131, 21]},
        "gas_cell_out": {"frame": [130, 56, 18, 18], "item": [131, 57]},
        "upgrade_slots": wgm_up,
        "player_inventory": {"top_left_item": [8, 98], "hotbar_item": [8, 156]},
        "sprites": {
            "progress_arrow": [180, 0, 24, 15],
            "energy_bolt": [180, 18, 14, 14],
            "solar_on": [180, 34, 14, 14],
            "solar_off": [180, 50, 14, 14],
            "tank_overlay": [180, 68, 16, 50],
        },
    }

    # --- 7.7 gas_furnace.png (176x166) ---
    g = draw_gui_panel(176, 166, seed=707)
    draw_tank_frame(g, 14, 20, 20, 54)
    draw_slot(g, 38, 20)
    draw_slot(g, 38, 56)
    draw_slot(g, 66, 20, rim_accent=PAL["blue_port"])
    draw_flame_14x14(g, 68, 41, lit=False, blue=True)
    draw_arrow_24x15(g, 90, 36, PAL["slot_dark"], PAL["slate_mid"])
    draw_slot(g, 120, 34, rim_accent=PAL["orange_port"])
    gf_up = []
    for i in range(4):
        draw_slot(g, 152, 8 + i * 18)
        gf_up.append({"index": i, "frame": [152, 8 + i * 18, 18, 18], "item": [153, 9 + i * 18]})
    draw_player_inventory(g, 7, 83)
    draw_flame_14x14(g, 180, 0, lit=True, blue=True)
    draw_arrow_24x15(g, 180, 18, PAL["cyan_mid"], PAL["cyan_white"])
    draw_tank_overlay_sprite(g, 180, 36, 16, 50)
    save_png(g, TEX_ROOT / "gui" / "gas_furnace.png")
    coords["gas_furnace"] = {
        "texture": "ecc2:textures/gui/gas_furnace.png",
        "panel": [0, 0, 176, 166],
        "gas_tank": {"frame": [14, 20, 20, 54], "fluid": [16, 22, 16, 50]},
        "gas_cell_in": {"frame": [38, 20, 18, 18], "item": [39, 21]},
        "gas_cell_out": {"frame": [38, 56, 18, 18], "item": [39, 57]},
        "input_slot": {"frame": [66, 20, 18, 18], "item": [67, 21]},
        "flame_pos": [68, 41, 14, 14],
        "progress_arrow_pos": [90, 36, 24, 15],
        "output_slot": {"frame": [120, 34, 18, 18], "item": [121, 35]},
        "upgrade_slots": gf_up,
        "player_inventory": {"top_left_item": [8, 84], "hotbar_item": [8, 142]},
        "sprites": {
            "flame_blue": [180, 0, 14, 14],
            "progress_arrow": [180, 18, 24, 15],
            "tank_overlay": [180, 36, 16, 50],
        },
    }

    # --- 7.8 gas_generator.png (176x166) ---
    g = draw_gui_panel(176, 166, seed=708)
    draw_tank_frame(g, 18, 20, 20, 54)
    draw_slot(g, 44, 20)
    draw_slot(g, 44, 56)
    draw_flame_14x14(g, 76, 36, lit=False, blue=True)
    draw_energy_bar_24x14(g, 98, 24, filled=False)
    draw_slot(g, 134, 22, rim_accent=PAL["cyan_dark"])
    draw_lcd(g, 72, 54, 86, 22, accent=PAL["teal_mid"])
    draw_player_inventory(g, 7, 83)
    draw_flame_14x14(g, 180, 0, lit=True, blue=True)
    draw_energy_bar_24x14(g, 180, 18, filled=True)
    draw_tank_overlay_sprite(g, 180, 36, 16, 50)
    save_png(g, TEX_ROOT / "gui" / "gas_generator.png")
    coords["gas_generator"] = {
        "texture": "ecc2:textures/gui/gas_generator.png",
        "panel": [0, 0, 176, 166],
        "gas_tank": {"frame": [18, 20, 20, 54], "fluid": [20, 22, 16, 50]},
        "gas_cell_in": {"frame": [44, 20, 18, 18], "item": [45, 21]},
        "gas_cell_out": {"frame": [44, 56, 18, 18], "item": [45, 57]},
        "flame_pos": [76, 36, 14, 14],
        "eu_bar_pos": [98, 24, 24, 14],
        "charge_slot": {"frame": [134, 22, 18, 18], "item": [135, 23]},
        "lcd": [72, 54, 86, 22],
        "player_inventory": {"top_left_item": [8, 84], "hotbar_item": [8, 142]},
        "sprites": {
            "flame_blue": [180, 0, 14, 14],
            "eu_bar_fill": [180, 18, 24, 14],
            "tank_overlay": [180, 36, 16, 50],
        },
    }

    # --- 7.9 culture_incubator.png (176x180) ---
    g = draw_gui_panel(176, 180, seed=709, accent=PAL["emerald_mid"])
    draw_tank_frame(g, 12, 20, 20, 54)
    draw_slot(g, 36, 20)
    draw_slot(g, 36, 56)
    draw_slot(g, 66, 24, rim_accent=PAL["emerald_mid"])
    draw_arrow_24x15(g, 64, 46, PAL["slot_dark"], PAL["slate_mid"])
    draw_bolt_14x14(g, 62, 65, lit=False)
    draw_slot(g, 82, 63)
    draw_tank_frame(g, 106, 20, 20, 54)
    draw_slot(g, 130, 20)
    draw_slot(g, 130, 56)
    ci_up = []
    for i in range(4):
        draw_slot(g, 152, 18 + i * 18)
        ci_up.append({"index": i, "frame": [152, 18 + i * 18, 18, 18], "item": [153, 19 + i * 18]})
    draw_player_inventory(g, 7, 97)
    draw_arrow_24x15(g, 180, 0, PAL["emerald_mid"], PAL["emerald_hi"])
    draw_bolt_14x14(g, 180, 18, lit=True)
    draw_tank_overlay_sprite(g, 180, 36, 16, 50)
    save_png(g, TEX_ROOT / "gui" / "culture_incubator.png")
    coords["culture_incubator"] = {
        "texture": "ecc2:textures/gui/culture_incubator.png",
        "panel": [0, 0, 176, 180],
        "water_tank": {"frame": [12, 20, 20, 54], "fluid": [14, 22, 16, 50]},
        "water_cell_in": {"frame": [36, 20, 18, 18], "item": [37, 21]},
        "water_cell_out": {"frame": [36, 56, 18, 18], "item": [37, 57]},
        "bonemeal_slot": {"frame": [66, 24, 18, 18], "item": [67, 25]},
        "progress_arrow_pos": [64, 46, 24, 15],
        "energy_bolt_pos": [62, 65, 14, 14],
        "battery_slot": {"frame": [82, 63, 18, 18], "item": [83, 64]},
        "culture_tank": {"frame": [106, 20, 20, 54], "fluid": [108, 22, 16, 50]},
        "culture_cell_in": {"frame": [130, 20, 18, 18], "item": [131, 21]},
        "culture_cell_out": {"frame": [130, 56, 18, 18], "item": [131, 57]},
        "upgrade_slots": ci_up,
        "player_inventory": {"top_left_item": [8, 98], "hotbar_item": [8, 156]},
        "sprites": {
            "progress_arrow": [180, 0, 24, 15],
            "energy_bolt": [180, 18, 14, 14],
            "tank_overlay": [180, 36, 16, 50],
        },
    }

    # --- 7.10 organic_fermenter.png (176x180) ---
    g = draw_gui_panel(176, 180, seed=710, accent=(136, 214, 78, 255))
    draw_tank_frame(g, 12, 20, 20, 54)
    draw_slot(g, 36, 20)
    draw_slot(g, 36, 56)
    draw_slot(g, 60, 20, rim_accent=PAL["emerald_mid"])
    draw_slot(g, 82, 20, rim_accent=PAL["slate_dark"])
    draw_arrow_24x15(g, 64, 44, PAL["slot_dark"], PAL["slate_mid"])
    draw_bolt_14x14(g, 62, 65, lit=False)
    draw_slot(g, 82, 63)
    draw_tank_frame(g, 106, 20, 20, 54)
    draw_slot(g, 130, 20)
    draw_slot(g, 130, 56)
    of_up = []
    for i in range(4):
        draw_slot(g, 152, 18 + i * 18)
        of_up.append({"index": i, "frame": [152, 18 + i * 18, 18, 18], "item": [153, 19 + i * 18]})
    draw_player_inventory(g, 7, 97)
    draw_arrow_24x15(g, 180, 0, (144, 214, 68, 255), (226, 255, 142, 255))
    draw_bolt_14x14(g, 180, 18, lit=True)
    draw_tank_overlay_sprite(g, 180, 36, 16, 50)
    save_png(g, TEX_ROOT / "gui" / "organic_fermenter.png")
    coords["organic_fermenter"] = {
        "texture": "ecc2:textures/gui/organic_fermenter.png",
        "panel": [0, 0, 176, 180],
        "culture_tank": {"frame": [12, 20, 20, 54], "fluid": [14, 22, 16, 50]},
        "culture_cell_in": {"frame": [36, 20, 18, 18], "item": [37, 21]},
        "culture_cell_out": {"frame": [36, 56, 18, 18], "item": [37, 57]},
        "food_input_slot": {"frame": [60, 20, 18, 18], "item": [61, 21]},
        "container_return_slot": {"frame": [82, 20, 18, 18], "item": [83, 21]},
        "progress_arrow_pos": [64, 44, 24, 15],
        "energy_bolt_pos": [62, 65, 14, 14],
        "battery_slot": {"frame": [82, 63, 18, 18], "item": [83, 64]},
        "biogas_tank": {"frame": [106, 20, 20, 54], "fluid": [108, 22, 16, 50]},
        "biogas_cell_in": {"frame": [130, 20, 18, 18], "item": [131, 21]},
        "biogas_cell_out": {"frame": [130, 56, 18, 18], "item": [131, 57]},
        "upgrade_slots": of_up,
        "player_inventory": {"top_left_item": [8, 98], "hotbar_item": [8, 156]},
        "sprites": {
            "progress_arrow": [180, 0, 24, 15],
            "energy_bolt": [180, 18, 14, 14],
            "tank_overlay": [180, 36, 16, 50],
        },
    }

    # --- 7.11 icons/port_access_gear.png (16x16 crisp metallic cyan-rimmed gear) ---
    gear = Image.new("RGBA", (16, 16), PAL["transparent"])
    for y in range(16):
        for x in range(16):
            dx, dy = x - 7.5, y - 7.5
            r = math.hypot(dx, dy)
            ang = math.atan2(dy, dx)
            tooth = 0.85 * math.cos(8 * ang)
            outer_r = 5.4 + max(0.0, tooth) * 1.4
            if 2.2 <= r <= outer_r:
                c = lerp_color(PAL["bevel_white"], PAL["slate_dark"], (x + y) / 30.0)
                if 2.2 <= r <= 3.1:
                    c = PAL["cyan_glow"]
                px(gear, x, y, c)
    save_png(add_item_outline(gear), TEX_ROOT / "gui" / "icons" / "port_access_gear.png")
    coords["icons"] = {
        "port_access_gear": {"texture": "ecc2:textures/gui/icons/port_access_gear.png", "size": [16, 16]}
    }

    GUI_COORDS_PATH.write_text(json.dumps(coords, indent=2) + "\n", encoding="utf-8")
    return coords


def main() -> None:
    gen_asp_blocks()
    gen_entity_textures()
    gen_asp_items_and_armor()
    gen_kinetic_generators()
    gen_giant_machines()
    gen_gas_and_bio_blocks()
    gen_fluid_textures()
    gen_gas_and_bio_items()
    gen_guis()

    pngs = sorted(TEX_ROOT.rglob("*.png"))
    mcmetas = sorted(TEX_ROOT.rglob("*.mcmeta"))
    print(f"Generated {len(pngs)} PNG textures and {len(mcmetas)} .mcmeta animations into {TEX_ROOT}")
    print(f"Wrote GUI coordinates manifest: {GUI_COORDS_PATH}")


if __name__ == "__main__":
    main()
