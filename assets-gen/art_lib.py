#!/usr/bin/env python3
"""Shared deterministic IC2NE blue-slate pixel-art drawing primitives."""

from __future__ import annotations

import json
import math
from pathlib import Path
from typing import Iterable, Tuple

from PIL import Image, ImageDraw

RGBA = Tuple[int, int, int, int]

# Canonical IC2NE industrial blue-slate palette
PAL = {
    "outline": (24, 30, 38, 255),
    "border_dark": (36, 44, 54, 255),
    "shadow_deep": (44, 52, 64, 255),
    "slot_dark": (57, 66, 79, 255),       # #39424f
    "slate_dark": (91, 102, 116, 255),    # #5b6674
    "slate_mid": (114, 125, 139, 255),    # #727d8b
    "slate_light": (148, 159, 173, 255),  # #949fad
    "panel_face": (176, 185, 196, 255),   # #b0b9c4 (IC2NE brushed steel face)
    "panel_hi": (194, 202, 212, 255),     # #c2cad4
    "bevel_hi": (214, 222, 230, 255),     # #d6dee6
    "bevel_white": (236, 241, 246, 255),  # #ecf1f6
    "lcd_bg": (19, 30, 40, 255),          # #131e28
    "lcd_bg2": (14, 50, 60, 255),         # #0e323c
    "lcd_border": (10, 16, 22, 255),
    "cyan_glow": (46, 232, 255, 255),     # #2ee8ff
    "cyan_mid": (24, 176, 214, 255),
    "cyan_dark": (16, 98, 128, 255),
    "cyan_white": (205, 250, 255, 255),
    "blue_port": (61, 158, 255, 255),     # #3d9eff
    "blue_dark": (28, 86, 164, 255),
    "orange_port": (255, 155, 46, 255),   # #ff9b2e
    "orange_dark": (176, 86, 22, 255),
    "gold_hi": (255, 232, 112, 255),
    "gold_mid": (236, 184, 52, 255),
    "gold_dark": (152, 102, 20, 255),
    "emerald_hi": (118, 255, 168, 255),
    "emerald_mid": (58, 206, 118, 255),
    "emerald_dark": (22, 118, 66, 255),
    "purple_hi": (196, 136, 255, 255),
    "purple_mid": (122, 64, 198, 255),
    "purple_dark": (54, 24, 104, 255),
    "red_led": (235, 68, 68, 255),
    "red_dark": (112, 28, 32, 255),
    "bronze_hi": (218, 162, 102, 255),
    "bronze_mid": (176, 118, 64, 255),
    "bronze_dark": (108, 68, 34, 255),
    "teal_hi": (86, 216, 204, 255),
    "teal_mid": (46, 156, 148, 255),
    "teal_dark": (24, 92, 92, 255),
    "transparent": (0, 0, 0, 0),
}


def clamp(v: float, lo: int = 0, hi: int = 255) -> int:
    return max(lo, min(hi, int(round(v))))


def lerp_color(c1: RGBA, c2: RGBA, t: float) -> RGBA:
    t = max(0.0, min(1.0, t))
    return (
        clamp(c1[0] + (c2[0] - c1[0]) * t),
        clamp(c1[1] + (c2[1] - c1[1]) * t),
        clamp(c1[2] + (c2[2] - c1[2]) * t),
        clamp(c1[3] + (c2[3] - c1[3]) * t),
    )


def shade(c: RGBA, factor: float) -> RGBA:
    return (
        clamp(c[0] * factor),
        clamp(c[1] * factor),
        clamp(c[2] * factor),
        c[3],
    )


def det_hash(x: int, y: int, seed: int = 0) -> float:
    """Deterministic pseudo-random float in [0, 1)."""
    n = (x * 374761393 + y * 668265263 + seed * 1442695041) & 0xFFFFFFFF
    n = ((n ^ (n >> 13)) * 1274126177) & 0xFFFFFFFF
    n = n ^ (n >> 16)
    return (n & 0xFFFFFF) / float(0x1000000)


def px(img: Image.Image, x: int, y: int, color: RGBA) -> None:
    if 0 <= x < img.width and 0 <= y < img.height:
        if color[3] == 255:
            img.putpixel((x, y), color)
        elif color[3] > 0:
            bg = img.getpixel((x, y))
            a = color[3] / 255.0
            ba = bg[3] / 255.0
            out_a = a + ba * (1.0 - a)
            if out_a <= 0:
                return
            r = (color[0] * a + bg[0] * ba * (1.0 - a)) / out_a
            g = (color[1] * a + bg[1] * ba * (1.0 - a)) / out_a
            b = (color[2] * a + bg[2] * ba * (1.0 - a)) / out_a
            img.putpixel((x, y), (clamp(r), clamp(g), clamp(b), clamp(out_a * 255)))


def fill_rect(img: Image.Image, x: int, y: int, w: int, h: int, color: RGBA) -> None:
    for yy in range(y, y + h):
        for xx in range(x, x + w):
            px(img, xx, yy, color)


def draw_rect_border(img: Image.Image, x: int, y: int, w: int, h: int, color: RGBA) -> None:
    for xx in range(x, x + w):
        px(img, xx, y, color)
        px(img, xx, y + h - 1, color)
    for yy in range(y, y + h):
        px(img, x, yy, color)
        px(img, x + w - 1, yy, color)


def make_casing_tile(
    base: RGBA = PAL["slate_mid"],
    hi: RGBA = PAL["bevel_hi"],
    lo: RGBA = PAL["slot_dark"],
    seed: int = 1,
    rivets: bool = True,
    dark_outer: bool = True,
    accent: RGBA | None = None,
) -> Image.Image:
    """Create a 16x16 IC2NE-style beveled industrial metal block face."""
    img = Image.new("RGBA", (16, 16), base)
    # Brushed subtle metallic texture
    for y in range(16):
        row_shift = -4 if (y % 2 == 0) else 3
        for x in range(16):
            h = (det_hash(x, y, seed) - 0.5) * 14 + row_shift
            c = (
                clamp(base[0] + h),
                clamp(base[1] + h),
                clamp(base[2] + h + 2),
                255,
            )
            img.putpixel((x, y), c)

    if dark_outer:
        draw_rect_border(img, 0, 0, 16, 16, PAL["border_dark"])
        # Inner bevel
        for i in range(1, 15):
            px(img, i, 1, hi)
            px(img, 1, i, hi)
            px(img, i, 14, lo)
            px(img, 14, i, lo)
        px(img, 1, 1, PAL["bevel_white"])
        px(img, 14, 1, base)
        px(img, 1, 14, base)
        px(img, 14, 14, PAL["shadow_deep"])
    else:
        for i in range(16):
            px(img, i, 0, hi)
            px(img, 0, i, hi)
            px(img, i, 15, lo)
            px(img, 15, i, lo)

    if rivets:
        for rx, ry in [(2, 2), (13, 2), (2, 13), (13, 13)]:
            px(img, rx, ry, PAL["shadow_deep"])
            if rx < 8 and ry < 8:
                px(img, rx + 1, ry + 1, PAL["slate_light"])

    if accent is not None:
        for x in range(4, 12):
            px(img, x, 2, accent)
            px(img, x, 13, shade(accent, 0.7))

    return img


def make_bevel_box(
    img: Image.Image,
    x: int,
    y: int,
    w: int,
    h: int,
    fill: RGBA = PAL["slate_mid"],
    raised: bool = True,
    hi: RGBA = PAL["bevel_hi"],
    lo: RGBA = PAL["slot_dark"],
    outer: RGBA | None = None,
) -> None:
    """Draw a beveled box (raised panel or recessed slot/well)."""
    if outer is not None:
        draw_rect_border(img, x, y, w, h, outer)
        x += 1
        y += 1
        w -= 2
        h -= 2
    fill_rect(img, x, y, w, h, fill)
    tl = hi if raised else lo
    br = lo if raised else hi
    for xx in range(x, x + w - 1):
        px(img, xx, y, tl)
    for yy in range(y, y + h - 1):
        px(img, x, yy, tl)
    for xx in range(x + 1, x + w):
        px(img, xx, y + h - 1, br)
    for yy in range(y + 1, y + h):
        px(img, x + w - 1, yy, br)
    # Corner blend
    mid = lerp_color(tl, br, 0.5)
    px(img, x + w - 1, y, mid)
    px(img, x, y + h - 1, mid)


def draw_gui_panel(
    w: int,
    h: int,
    seed: int = 10,
    accent: RGBA | None = PAL["cyan_mid"],
) -> Image.Image:
    """Create a 256x256 RGBA canvas with an authentic IC2NE blue-slate panel at (0,0,w,h)."""
    canvas = Image.new("RGBA", (256, 256), PAL["transparent"])
    # Outer dark border (rounded-corner industrial feel)
    fill_rect(canvas, 1, 0, w - 2, h, PAL["outline"])
    fill_rect(canvas, 0, 1, w, h - 2, PAL["outline"])
    # Secondary dark rim
    draw_rect_border(canvas, 1, 1, w - 2, h - 2, PAL["border_dark"])
    # Main brushed steel interior
    for yy in range(2, h - 2):
        # Horizontal IC2NE brushed metallic sheen (subtle vertical gradient + 2px micro-band)
        grad = 1.04 - 0.08 * (yy / max(1, h))
        band = 3 if (yy % 2 == 0) else -2
        for xx in range(2, w - 2):
            noise = (det_hash(xx, yy, seed) - 0.5) * 4
            r = clamp(PAL["panel_face"][0] * grad + band + noise)
            g = clamp(PAL["panel_face"][1] * grad + band + noise)
            b = clamp(PAL["panel_face"][2] * grad + band + noise + 2)
            canvas.putpixel((xx, yy), (r, g, b, 255))

    # Crisp 2px bevel highlight on top/left, shadow on bottom/right
    for xx in range(2, w - 2):
        px(canvas, xx, 2, PAL["bevel_white"])
        px(canvas, xx, 3, PAL["bevel_hi"])
        px(canvas, xx, h - 3, PAL["slot_dark"])
        px(canvas, xx, h - 4, PAL["slate_dark"])
    for yy in range(2, h - 2):
        px(canvas, 2, yy, PAL["bevel_white"])
        px(canvas, 3, yy, PAL["bevel_hi"])
        px(canvas, w - 3, yy, PAL["slot_dark"])
        px(canvas, w - 4, yy, PAL["slate_dark"])

    # Corner rivets (IC2NE signature triangular corner screws!)
    for cx, cy in [(4, 4), (w - 6, 4), (4, h - 6), (w - 6, h - 6)]:
        px(canvas, cx, cy, PAL["bevel_white"])
        px(canvas, cx + 1, cy, PAL["slate_dark"])
        px(canvas, cx, cy + 1, PAL["slate_dark"])
        px(canvas, cx + 1, cy + 1, PAL["border_dark"])

    # Subtle sci-fi cyan header accent line under title
    if accent is not None:
        for xx in range(8, w - 8):
            px(canvas, xx, 15, shade(accent, 0.65))
            if xx % 4 == 0:
                px(canvas, xx, 15, accent)

    return canvas


def draw_slot(
    img: Image.Image,
    x: int,
    y: int,
    rim_accent: RGBA | None = None,
    size: int = 18,
) -> None:
    """Draw an IC2NE 18x18 recessed item slot (item sits at x+1, y+1)."""
    if rim_accent is not None:
        draw_rect_border(img, x - 1, y - 1, size + 2, size + 2, rim_accent)
    make_bevel_box(
        img, x, y, size, size,
        fill=PAL["slate_mid"],
        raised=False,
        hi=PAL["bevel_hi"],
        lo=PAL["slot_dark"],
    )
    # Extra crisp inner corner darkening
    px(img, x, y, PAL["border_dark"])


def draw_player_inventory(img: Image.Image, x: int, y: int) -> None:
    """Draw 9x3 player inventory at (x, y) and 9x1 hotbar at (x, y + 58)."""
    # Subtle outer frame around player inv
    make_bevel_box(img, x - 2, y - 2, 166, 78, fill=PAL["panel_face"], raised=False, hi=PAL["bevel_hi"], lo=PAL["slate_dark"])
    for row in range(3):
        for col in range(9):
            draw_slot(img, x + col * 18, y + row * 18)
    for col in range(9):
        draw_slot(img, x + col * 18, y + 58)


def draw_lcd(
    img: Image.Image,
    x: int,
    y: int,
    w: int,
    h: int,
    accent: RGBA | None = PAL["cyan_dark"],
    scanlines: bool = True,
) -> None:
    """Draw a recessed IC2NE deep blue-slate LCD readout screen."""
    make_bevel_box(
        img, x, y, w, h,
        fill=PAL["lcd_bg"],
        raised=False,
        hi=PAL["bevel_hi"],
        lo=PAL["border_dark"],
    )
    draw_rect_border(img, x + 1, y + 1, w - 2, h - 2, PAL["lcd_border"])
    if scanlines:
        for yy in range(y + 2, y + h - 2, 2):
            for xx in range(x + 2, x + w - 2):
                px(img, xx, yy, (14, 34, 46, 255))
    if accent is not None:
        # Subtle corner tech brackets
        for dx in range(3):
            px(img, x + 2 + dx, y + 2, accent)
            px(img, x + w - 3 - dx, y + 2, accent)
            px(img, x + 2 + dx, y + h - 3, accent)
            px(img, x + w - 3 - dx, y + h - 3, accent)


def draw_tank_frame(img: Image.Image, x: int, y: int, w: int = 20, h: int = 54) -> None:
    """Draw an IC2NE fluid tank window (inner fluid region is x+2, y+2, w-4, h-4)."""
    make_bevel_box(img, x, y, w, h, fill=PAL["lcd_bg"], raised=False, hi=PAL["bevel_hi"], lo=PAL["border_dark"])
    draw_rect_border(img, x + 1, y + 1, w - 2, h - 2, PAL["lcd_border"])
    # Subtle inner glass reflection & green/cyan scale ticks
    inner_x, inner_y, inner_w, inner_h = x + 2, y + 2, w - 4, h - 4
    for yy in range(inner_y, inner_y + inner_h):
        px(img, inner_x + 1, yy, (26, 48, 60, 255))
    for i in range(1, 10):
        ty = inner_y + int(inner_h * i / 10)
        tick_w = 5 if i == 5 else 3
        for dx in range(tick_w):
            px(img, inner_x + inner_w - 1 - dx, ty, (52, 168, 118, 255))


def draw_tank_overlay_sprite(img: Image.Image, x: int, y: int, w: int = 16, h: int = 50) -> None:
    """Draw transparent glass graduation overlay sprite for rendering over liquid."""
    for yy in range(y, y + h):
        px(img, x + 1, yy, (220, 245, 255, 65))
        px(img, x + 2, yy, (220, 245, 255, 30))
    for i in range(1, 10):
        ty = y + int(h * i / 10)
        tick_w = 6 if i == 5 else 3
        col = (90, 255, 190, 220) if i == 5 else (60, 210, 150, 180)
        for dx in range(tick_w):
            px(img, x + w - 1 - dx, ty, col)


def draw_arrow_24x15(
    img: Image.Image,
    x: int,
    y: int,
    fill_color: RGBA,
    hi_color: RGBA,
    border_color: RGBA = PAL["outline"],
) -> None:
    """Draw a crisp 24x15 right-pointing industrial progress arrow."""
    # Shaft y=4..10 (h=7), x=1..14; Head triangle x=14..22
    mask = set()
    for yy in range(4, 11):
        for xx in range(1, 15):
            mask.add((xx, yy))
    for step in range(8):
        xx = 15 + step
        for yy in range(step, 15 - step):
            if 0 <= yy < 15:
                mask.add((xx, yy))
    for xx, yy in mask:
        # Check border
        is_edge = any((xx + dx, yy + dy) not in mask for dx, dy in [(-1, 0), (1, 0), (0, -1), (0, 1)])
        if is_edge:
            px(img, x + xx, y + yy, border_color)
        else:
            t = max(0.0, min(1.0, (yy - 2) / 11.0))
            px(img, x + xx, y + yy, lerp_color(hi_color, fill_color, t))


def draw_bolt_14x14(img: Image.Image, x: int, y: int, lit: bool) -> None:
    """Draw a crisp 14x14 EU lightning bolt icon."""
    pattern = [
        "......##......",
        ".....###......",
        "....####......",
        "...#####......",
        "..######......",
        ".###########..",
        ".##########...",
        ".....#####....",
        "....#####.....",
        "...#####......",
        "..####........",
        "..###.........",
        ".##...........",
        ".#............",
    ]
    c_hi = (255, 245, 130, 255) if lit else PAL["slate_light"]
    c_lo = (245, 132, 24, 255) if lit else PAL["slot_dark"]
    for yy, row in enumerate(pattern):
        for xx, ch in enumerate(row):
            if ch == "#":
                px(img, x + xx, y + yy, lerp_color(c_hi, c_lo, yy / 13.0))


def draw_flame_14x14(img: Image.Image, x: int, y: int, lit: bool, blue: bool = True) -> None:
    """Draw a crisp 14x14 gas flame icon (blue flame when blue=True)."""
    pattern = [
        "......##......",
        ".....###......",
        "....####......",
        "...#####..#...",
        "..######.##...",
        ".###########..",
        ".###########..",
        ".####oo#####..",
        ".###oooo####..",
        ".###oooo####..",
        "..##oooo###...",
        "..###oo####...",
        "...#######....",
        "....#####.....",
    ]
    if not lit:
        c_outer = PAL["slot_dark"]
        c_inner = PAL["slate_dark"]
    elif blue:
        c_outer = (36, 178, 255, 255)
        c_inner = (210, 250, 255, 255)
    else:
        c_outer = (245, 118, 28, 255)
        c_inner = (255, 242, 130, 255)
    for yy, row in enumerate(pattern):
        for xx, ch in enumerate(row):
            if ch == "#":
                px(img, x + xx, y + yy, shade(c_outer, 0.8 + 0.3 * (1 - yy / 14.0)))
            elif ch == "o":
                px(img, x + xx, y + yy, c_inner)


def draw_sun_14x14(img: Image.Image, x: int, y: int, lit: bool = True) -> None:
    """Draw a 14x14 solar day icon."""
    c_core = (255, 248, 175, 255) if lit else PAL["slate_dark"]
    c_mid = (255, 198, 48, 255) if lit else PAL["slot_dark"]
    c_ray = (255, 162, 24, 255) if lit else PAL["border_dark"]
    cx, cy = 6.5, 6.5
    for yy in range(14):
        for xx in range(14):
            d = math.hypot(xx - cx, yy - cy)
            if d <= 2.3:
                px(img, x + xx, y + yy, c_core)
            elif d <= 4.0:
                px(img, x + xx, y + yy, c_mid)
    # 8 crisp rays
    for rx, ry in [
        (6, 0), (7, 0), (6, 1), (7, 1),
        (6, 12), (7, 12), (6, 13), (7, 13),
        (0, 6), (0, 7), (1, 6), (1, 7),
        (12, 6), (12, 7), (13, 6), (13, 7),
        (2, 2), (11, 2), (2, 11), (11, 11),
    ]:
        px(img, x + rx, y + ry, c_ray)


def draw_moon_14x14(img: Image.Image, x: int, y: int) -> None:
    """Draw a 14x14 crescent moon + star icon."""
    cx, cy = 6.5, 6.8
    for yy in range(14):
        for xx in range(14):
            d1 = math.hypot(xx - cx, yy - cy)
            d2 = math.hypot(xx - (cx + 2.5), yy - (cy - 1.5))
            if d1 <= 5.0 and d2 > 4.0:
                t = max(0.0, min(1.0, (xx - 2) / 9.0))
                px(img, x + xx, y + yy, lerp_color((225, 236, 255, 255), (132, 158, 208, 255), t))
    # Tiny star
    px(img, x + 10, y + 3, (235, 246, 255, 255))
    px(img, x + 11, y + 6, (180, 210, 250, 255))


def draw_rain_14x14(img: Image.Image, x: int, y: int) -> None:
    """Draw a 14x14 rain cloud + cyan raindrops icon."""
    cloud = [
        "..............",
        "....####......",
        "..########....",
        ".###########..",
        ".############.",
        "..##########..",
        "..............",
    ]
    for yy, row in enumerate(cloud):
        for xx, ch in enumerate(row):
            if ch == "#":
                t = yy / 6.0
                px(img, x + xx, y + yy, lerp_color((196, 210, 226, 255), (96, 112, 134, 255), t))
    # Diagonal cyan rain streaks
    drops = [(3, 8), (2, 10), (6, 8), (5, 10), (4, 12), (9, 8), (8, 10), (11, 9), (10, 11)]
    for dx, dy in drops:
        px(img, x + dx, y + dy, (74, 212, 255, 255))
        px(img, x + dx, y + dy + 1, (38, 146, 224, 255))


def draw_blocked_sky_14x14(img: Image.Image, x: int, y: int) -> None:
    """Draw a 14x14 blocked-sky indicator."""
    draw_rect_border(img, x + 1, y + 1, 12, 12, PAL["red_dark"])
    for i in range(3, 11):
        px(img, x + i, y + i, PAL["red_led"])
        px(img, x + 13 - i, y + i, PAL["red_led"])


def draw_energy_bar_24x14(img: Image.Image, x: int, y: int, filled: bool) -> None:
    """Draw a 24x14 segmented sci-fi cyan/blue EU storage bar."""
    if not filled:
        make_bevel_box(img, x, y, 24, 14, fill=PAL["lcd_bg"], raised=False, hi=PAL["slate_light"], lo=PAL["border_dark"])
        for sx in range(x + 4, x + 22, 4):
            for yy in range(y + 2, y + 12):
                px(img, sx, yy, (26, 44, 58, 255))
    else:
        for xx in range(x, x + 24):
            seg = (xx - x) % 4
            for yy in range(y, y + 14):
                if yy == y or yy == y + 13 or xx == x or xx == x + 23:
                    px(img, xx, yy, PAL["cyan_dark"])
                elif seg == 3:
                    px(img, xx, yy, (18, 72, 98, 255))
                else:
                    t = abs(yy - (y + 6.5)) / 6.5
                    px(img, xx, yy, lerp_color(PAL["cyan_white"], PAL["cyan_mid"], t))


def add_item_outline(img: Image.Image, outline_col: RGBA = PAL["outline"]) -> Image.Image:
    """Add crisp 1px dark outline around opaque pixels of a 16x16 item sprite."""
    out = img.copy()
    w, h = img.size
    for y in range(h):
        for x in range(w):
            if img.getpixel((x, y))[3] == 0:
                for dx, dy in [(-1, 0), (1, 0), (0, -1), (0, 1)]:
                    nx, ny = x + dx, y + dy
                    if 0 <= nx < w and 0 <= ny < h and img.getpixel((nx, ny))[3] >= 128:
                        out.putpixel((x, y), outline_col)
                        break
    return out


def save_png(img: Image.Image, path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    img.save(path, format="PNG", optimize=True)


def save_mcmeta(path: Path, frametime: int = 3, interpolate: bool = True, frames: Iterable[int] | None = None) -> None:
    anim: dict = {"frametime": frametime}
    if interpolate:
        anim["interpolate"] = True
    if frames is not None:
        anim["frames"] = list(frames)
    mcmeta_path = Path(str(path) + ".mcmeta")
    mcmeta_path.parent.mkdir(parents=True, exist_ok=True)
    mcmeta_path.write_text(json.dumps({"animation": anim}, indent=2) + "\n", encoding="utf-8")
