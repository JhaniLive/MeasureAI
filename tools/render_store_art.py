"""Renders Play Store art in the app's brand style (Inter, OFL-licensed).

  python tools/render_store_art.py banner                 -> store/feature-graphic.png (1024x500)
  python tools/render_store_art.py shot IN.png OUT.png "Title" "Subtitle"
                                                          -> 1080x2160 framed screenshot

Play requires screenshots no taller than 2:1; the LG G8's 1080x2460 screen is 2.28:1, so raw
captures are placed in a branded 1080x2160 frame with a caption.
"""
import math
import sys

from PIL import Image, ImageDraw, ImageFilter, ImageFont

FONT_DIR = "C:/Program Files/Android/Android Studio/jbr/lib/fonts/"
SEMI = FONT_DIR + "Inter-SemiBold.otf"
REG = FONT_DIR + "Inter-Regular.otf"

TEAL = (0x1D, 0xE9, 0xD0)
WHITE = (255, 255, 255)
DEEP = (0x05, 0x0F, 0x0E)
GLOW = (0x12, 0x43, 0x3E)
MUTED = (0x9F, 0xB5, 0xB1)


def backdrop(w, h, cx, cy, radius):
    """Deep-teal radial glow, as on the launcher icon and loader."""
    img = Image.new("RGB", (w, h), DEEP)
    small = Image.new("RGB", (w // 4, h // 4))
    px = small.load()
    for y in range(small.height):
        for x in range(small.width):
            t = min(1.0, math.hypot(x * 4 - cx, y * 4 - cy) / radius)
            t = t * t * (3 - 2 * t)
            px[x, y] = tuple(int(GLOW[i] + (DEEP[i] - GLOW[i]) * t) for i in range(3))
    return small.resize((w, h), Image.BICUBIC).filter(ImageFilter.GaussianBlur(6))


def dot_grid(img, top, spacing, alpha):
    """Faint surface-grid dots, like detected surfaces in the app."""
    layer = Image.new("RGBA", img.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    for y in range(top, img.height, spacing):
        for x in range(spacing // 2, img.width, spacing):
            fade = (y - top) / max(1, img.height - top)
            a = int(alpha * (0.35 + 0.65 * fade))
            r = 2 + 2 * fade
            d.ellipse([x - r, y - r, x + r, y + r], fill=TEAL + (a,))
    img.paste(layer, (0, 0), layer)


def logo(img, cx, cy, size):
    """The crosshair-over-ruler logo, same geometry as res/drawable/ic_launcher_foreground.xml."""
    ss = 4
    s = size * ss
    layer = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    u = s / 60.0

    def p(x, y):
        return ((x - 24) * u, (y - 24) * u)

    def circle(x, y, r, **kw):
        (ax, ay), (bx, by) = p(x - r, y - r), p(x + r, y + r)
        d.ellipse([ax, ay, bx, by], **kw)

    def line(x0, y0, x1, y1, w, color):
        d.line([p(x0, y0), p(x1, y1)], fill=color, width=int(w * u))
        for x, y in ((x0, y0), (x1, y1)):
            circle(x, y, w / 2, fill=color)

    circle(54, 45, 11 + 1.75, outline=TEAL + (255,), width=int(3.5 * u))
    for seg in ((54, 28, 54, 32), (54, 58, 54, 61), (35, 45, 39, 45), (69, 45, 73, 45)):
        line(*seg, 3, TEAL + (255,))
    circle(54, 45, 3, fill=WHITE + (255,))
    line(36, 71, 72, 71, 3, WHITE + (255,))
    for x, h in ((44, 4), (49, 2.5), (54, 5), (59, 2.5), (64, 4)):
        line(x, 71, x, 71 - h, 1.8, WHITE + (217,))
    for x in (36, 72):
        circle(x, 71, 5.4, fill=WHITE + (255,))
        circle(x, 71, 3.6, fill=TEAL + (255,))

    layer = layer.resize((size, size), Image.LANCZOS)
    img.paste(layer, (int(cx - size / 2), int(cy - size / 2)), layer)


def wordmark(d, x, y, size):
    f = ImageFont.truetype(SEMI, size)
    d.text((x, y), "Measure", font=f, fill=WHITE)
    d.text((x + d.textlength("Measure", font=f), y), "AR", font=f, fill=TEAL)


def pill(d, x, y, text, font):
    w = d.textlength(text, font=font)
    h = font.size + 22
    d.rounded_rectangle([x, y, x + w + 36, y + h], radius=h / 2, outline=TEAL + (160,), width=2,
                        fill=(0x0E, 0x1F, 0x1D))
    d.text((x + 18, y + 9), text, font=font, fill=TEAL)
    return x + w + 36


def banner(out="store/feature-graphic.png"):
    w, h = 1024, 500
    img = backdrop(w, h, 800, 230, 620)
    dot_grid(img, 330, 34, 70)
    d = ImageDraw.Draw(img, "RGBA")
    logo(img, 800, 235, 330)
    wordmark(d, 64, 120, 84)
    d.text((68, 222), "Measure anything with your camera", font=ImageFont.truetype(REG, 30), fill=MUTED)
    x = 68
    chip = ImageFont.truetype(SEMI, 20)
    for label in ("Length", "Height", "Area", "Volume"):
        x = pill(d, x, 300, label, chip) + 12
    img.save(out)
    print("wrote", out)


def framed_shot(src, out, title, subtitle):
    w, h = 1080, 2160
    img = backdrop(w, h, w / 2, 380, 1500)
    d = ImageDraw.Draw(img, "RGBA")
    ft = ImageFont.truetype(SEMI, 64)
    fs = ImageFont.truetype(REG, 34)
    d.text((w / 2, 120), title, font=ft, fill=WHITE, anchor="mt")
    d.text((w / 2, 212), subtitle, font=fs, fill=MUTED, anchor="mt")

    shot = Image.open(src).convert("RGB")
    # Crop the status bar (clock, notifications) so the frame looks clean
    shot = shot.crop((0, int(shot.height * 0.035), shot.width, shot.height))
    target_h = h - 330
    target_w = int(shot.width * target_h / shot.height)
    shot = shot.resize((target_w, target_h), Image.LANCZOS)
    radius = 56
    mask = Image.new("L", shot.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, target_w, target_h], radius=radius, fill=255)
    x = (w - target_w) // 2
    y = 300
    # Soft teal glow + thin border around the phone screen
    glow = Image.new("RGBA", img.size, (0, 0, 0, 0))
    ImageDraw.Draw(glow).rounded_rectangle([x - 6, y - 6, x + target_w + 6, y + target_h + 6], radius=radius + 6,
                                           fill=TEAL + (70,))
    img.paste(glow.filter(ImageFilter.GaussianBlur(18)), (0, 0), glow.filter(ImageFilter.GaussianBlur(18)))
    img.paste(shot, (x, y), mask)
    d = ImageDraw.Draw(img, "RGBA")
    d.rounded_rectangle([x, y, x + target_w, y + target_h], radius=radius, outline=TEAL + (120,), width=3)
    img.save(out)
    print("wrote", out)


if __name__ == "__main__":
    if sys.argv[1] == "banner":
        banner()
    elif sys.argv[1] == "shot":
        framed_shot(*sys.argv[2:6])
