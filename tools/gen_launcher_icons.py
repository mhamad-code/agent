#!/usr/bin/env python3
"""Generates the glass launcher icon: adaptive vectors and legacy PNGs.

Geometry matches AppLogo.kt: a rounded square (radius 23.5% of the side) with a 45
degree gradient c2 -> c3 -> c4, a pale ring in c5 at 35%, a diagonal sheen, a chevron of
two round-capped bars (the lower one translucent), and a soft shadow tinted with c4.

Run from the repo root:  python3 tools/gen_launcher_icons.py
"""

import os
from PIL import Image, ImageDraw, ImageFilter

MASTER = 1024
GLASS = 70               # inset of the glass square in the master canvas
SIDE = MASTER - GLASS * 2
RADIUS = int(SIDE * 0.235)

# id, c2, c3, c4, c5  (the six-token palettes from Palettes.kt)
THEMES = [
    ("forest",    "163832", "235347", "8EB69B", "DAF1DE"),
    ("ocean",     "253A52", "3A506B", "5BC0DE", "F4D6B0"),
    ("amoled",    "1F1F1F", "2E2E2E", "8EB69B", "DAF1DE"),
    ("dracula",   "343746", "44475A", "BD93F9", "F8F8F2"),
    ("nord",      "434C5E", "4C566A", "88C0D0", "ECEFF4"),
    ("solarized", "0B3D4A", "14505F", "B58900", "FDF6E3"),
    ("blossom",   "FFFFFF", "D5E5E5", "738585", "24302F"),
    ("carnival",  "FFFFFF", "B9C9E6", "6C6EA1", "232752"),
    ("lilac",     "FFFFFF", "DCD3EE", "6F6AA8", "2C2A52"),
    ("cobalt",    "12309C", "1951FC", "5E9BFC", "CBE9FD"),
    ("ember",     "6B4520", "8A5E2C", "DEC67E", "FFEB97"),
]

RES = "app/src/main/res"


def rgb(hex_color):
    h = hex_color.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def lerp(a, b, t):
    return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3))


def gradient(size, c2, c3, c4):
    img = Image.new("RGB", (256, 256))
    px = img.load()
    for y in range(256):
        for x in range(256):
            t = (x + y) / 510.0
            color = lerp(c2, c3, t / 0.55) if t < 0.55 else lerp(c3, c4, (t - 0.55) / 0.45)
            px[x, y] = color
    return img.resize((size, size), Image.LANCZOS)


def chevron(draw, scale, offset, width_ratio, color_top, color_bottom):
    """Two round-capped bars. Coordinates are in glass-relative units."""
    def pt(x, y):
        return (offset + x * SIDE * scale, offset + y * SIDE * scale)

    stroke = int(SIDE * scale * width_ratio)
    top = pt(0.315, 0.255)
    tip = pt(0.662, 0.500)
    bottom = pt(0.342, 0.752)

    for a, b, color in ((top, tip, color_top), (bottom, tip, color_bottom)):
        draw.line([a, b], fill=color, width=stroke)
        for p in (a, b):
            draw.ellipse(
                [p[0] - stroke / 2, p[1] - stroke / 2, p[0] + stroke / 2, p[1] + stroke / 2],
                fill=color,
            )


def render(c2h, c3h, c4h, c5h):
    c2, c3, c4, c5 = (rgb(c) for c in (c2h, c3h, c4h, c5h))
    canvas = Image.new("RGBA", (MASTER, MASTER), (0, 0, 0, 0))

    # Soft shadow tinted with the muted colour, behind the glass.
    shadow = Image.new("RGBA", (MASTER, MASTER), (0, 0, 0, 0))
    sd = ImageDraw.Draw(shadow)
    sd.rounded_rectangle(
        [GLASS, GLASS + 18, GLASS + SIDE, GLASS + SIDE + 18],
        radius=RADIUS,
        fill=c4 + (105,),
    )
    canvas.alpha_composite(shadow.filter(ImageFilter.GaussianBlur(38)))

    # Glass body.
    body = gradient(MASTER, c2, c3, c4).convert("RGBA")
    mask = Image.new("L", (MASTER, MASTER), 0)
    ImageDraw.Draw(mask).rounded_rectangle(
        [GLASS, GLASS, GLASS + SIDE, GLASS + SIDE], radius=RADIUS, fill=255
    )
    canvas.paste(body, (0, 0), mask)

    # Diagonal sheen across the top left, clipped to the glass.
    sheen = Image.new("RGBA", (MASTER, MASTER), (0, 0, 0, 0))
    sh = ImageDraw.Draw(sheen)
    band = [
        (0, 0.55 * SIDE + GLASS),
        (0.55 * SIDE + GLASS, 0),
        (0.78 * SIDE + GLASS, 0),
        (0, 0.78 * SIDE + GLASS),
    ]
    sh.polygon(band, fill=(255, 255, 255, 52))
    sheen.putalpha(Image.composite(sheen.getchannel("A"), Image.new("L", (MASTER, MASTER), 0), mask))
    canvas.alpha_composite(sheen)

    draw = ImageDraw.Draw(canvas)
    # Outer ring.
    ring = 10
    draw.rounded_rectangle(
        [GLASS + 46, GLASS + 46, GLASS + SIDE - 46, GLASS + SIDE - 46],
        radius=RADIUS - 46,
        outline=c5 + (90,),
        width=ring,
    )

    # Chevron shadow, then the two bars.
    glow = Image.new("RGBA", (MASTER, MASTER), (0, 0, 0, 0))
    gd = ImageDraw.Draw(glow)
    chevron(gd, 1.0, GLASS + 16, 0.145, (0, 0, 0, 46), (0, 0, 0, 34))
    canvas.alpha_composite(glow.filter(ImageFilter.GaussianBlur(12)))
    draw = ImageDraw.Draw(canvas)
    chevron(draw, 1.0, GLASS, 0.145, c5 + (230,), c5 + (158,))

    return canvas


def write_pngs(master, stem, round_icon=False):
    for density, size in (("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192)):
        path = os.path.join(RES, f"mipmap-{density}", f"{stem}.png")
        os.makedirs(os.path.dirname(path), exist_ok=True)
        img = master.resize((size, size), Image.LANCZOS)
        if round_icon:
            mask = Image.new("L", (size, size), 0)
            ImageDraw.Draw(mask).ellipse([0, 0, size - 1, size - 1], fill=255)
            img.putalpha(Image.composite(img.getchannel("A"), Image.new("L", (size, size), 0), mask))
        img.save(path)


def vector(path, extra):
    with open(path, "w") as f:
        f.write(extra)


def write_vectors(theme_id, c2, c3, c4, c5):
    header = (
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
        '    xmlns:aapt="http://schemas.android.com/aapt"\n'
        '    android:width="108dp"\n'
        '    android:height="108dp"\n'
        '    android:viewportWidth="108"\n'
        '    android:viewportHeight="108">\n'
    )
    bg = header + (
        '    <path android:pathData="M0,0h108v108h-108z">\n'
        '        <aapt:attr name="android:fillColor">\n'
        '            <gradient android:type="linear" android:startX="8" android:startY="8"'
        ' android:endX="100" android:endY="100">\n'
        f'                <item android:color="#FF{c2}" android:offset="0" />\n'
        f'                <item android:color="#FF{c3}" android:offset="0.52" />\n'
        f'                <item android:color="#FF{c4}" android:offset="1" />\n'
        '            </gradient>\n'
        '        </aapt:attr>\n'
        '    </path>\n'
        '    <path android:pathData="M-20,60 L64,-24 L110,-24 L0,100 Z" android:fillColor="#3DFFFFFF" />\n'
        '    <path android:pathData="M-24,34 L34,-24 L52,-24 L-18,40 Z" android:fillColor="#1FFFFFFF" />\n'
        '</vector>\n'
    )
    fg = header + (
        f'    <path android:pathData="M36.7,31.1 L69.1,54" android:strokeColor="#E6{c5}"'
        ' android:strokeWidth="13.5" android:strokeLineCap="round" />\n'
        f'    <path android:pathData="M39.2,77.5 L69.1,54" android:strokeColor="#9E{c5}"'
        ' android:strokeWidth="13.5" android:strokeLineCap="round" />\n'
        '</vector>\n'
    )
    mono = header + (
        '    <path android:pathData="M36.7,31.1 L69.1,54" android:strokeColor="#FFFFFFFF"'
        ' android:strokeWidth="13.5" android:strokeLineCap="round" />\n'
        '    <path android:pathData="M39.2,77.5 L69.1,54" android:strokeColor="#FFFFFFFF"'
        ' android:strokeWidth="13.5" android:strokeLineCap="round" />\n'
        '</vector>\n'
    )

    if theme_id == "forest":
        drawable = os.path.join(RES, "drawable")
        os.makedirs(drawable, exist_ok=True)
        vector(os.path.join(drawable, "ic_launcher_glass_bg.xml"), bg)
        vector(os.path.join(drawable, "ic_launcher_glass_fg.xml"), fg)
        vector(os.path.join(drawable, "ic_launcher_monochrome.xml"), mono)
        adaptive = (
            '<?xml version="1.0" encoding="utf-8"?>\n'
            '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
            '    <background android:drawable="@drawable/ic_launcher_glass_bg" />\n'
            '    <foreground android:drawable="@drawable/ic_launcher_glass_fg" />\n'
            '    <monochrome android:drawable="@drawable/ic_launcher_monochrome" />\n'
            '</adaptive-icon>\n'
        )
        for name in ("ic_launcher.xml", "ic_launcher_round.xml"):
            path = os.path.join(RES, "mipmap-anydpi-v26", name)
            os.makedirs(os.path.dirname(path), exist_ok=True)
            vector(path, adaptive)
    else:
        drawable = os.path.join(RES, "drawable")
        os.makedirs(drawable, exist_ok=True)
        vector(os.path.join(drawable, f"ic_launcher_glass_bg_{theme_id}.xml"), bg)
        vector(os.path.join(drawable, f"ic_launcher_glass_fg_{theme_id}.xml"), fg)
        adaptive = (
            '<?xml version="1.0" encoding="utf-8"?>\n'
            '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
            f'    <background android:drawable="@drawable/ic_launcher_glass_bg_{theme_id}" />\n'
            f'    <foreground android:drawable="@drawable/ic_launcher_glass_fg_{theme_id}" />\n'
            '    <monochrome android:drawable="@drawable/ic_launcher_monochrome" />\n'
            '</adaptive-icon>\n'
        )
        path = os.path.join(RES, "mipmap-anydpi-v26", f"ic_launcher_{theme_id}.xml")
        os.makedirs(os.path.dirname(path), exist_ok=True)
        vector(path, adaptive)


def main():
    for theme_id, c2, c3, c4, c5 in THEMES:
        master = render(c2, c3, c4, c5)
        if theme_id == "forest":
            write_pngs(master, "ic_launcher")
            write_pngs(master, "ic_launcher_round", round_icon=True)
            master.resize((512, 512), Image.LANCZOS).save("/tmp/icon_preview.png")
        else:
            write_pngs(master, f"ic_launcher_{theme_id}")
        write_vectors(theme_id, c2, c3, c4, c5)
        print("built", theme_id)


if __name__ == "__main__":
    main()
