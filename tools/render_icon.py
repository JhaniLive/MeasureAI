"""Renders the launcher icon (same geometry as res/drawable/ic_launcher_*.xml) to PNG.

Usage: python tools/render_icon.py [out.png] [size]
The Play Store listing needs a 512x512 PNG; Google applies the rounded mask itself.
"""
import math
import sys

from PIL import Image, ImageDraw

OUT = sys.argv[1] if len(sys.argv) > 1 else "store/icon-512.png"
SIZE = int(sys.argv[2]) if len(sys.argv) > 2 else 512
SS = 4  # supersampling for smooth edges
TEAL = (0x1D, 0xE9, 0xD0, 255)
WHITE = (255, 255, 255, 255)

# The adaptive icon is 108 units; the visible icon is the central 72 (18..90)
VIEW0, VIEW = 18.0, 72.0
px = SIZE * SS
k = px / VIEW


def P(x, y):
    return ((x - VIEW0) * k, (y - VIEW0) * k)


def W(w):
    return max(1, int(round(w * k)))


img = Image.new("RGBA", (px, px))
# Radial background: #12433E at (54,46) fading to #050F0E at radius 76
c0, c1 = (0x12, 0x43, 0x3E), (0x05, 0x0F, 0x0E)
cx, cy = P(54, 46)
r = 76 * k
bg = img.load()
for yy in range(px):
    for xx in range(px):
        t = min(1.0, math.hypot(xx - cx, yy - cy) / r)
        bg[xx, yy] = tuple(int(c0[i] + (c1[i] - c0[i]) * t) for i in range(3)) + (255,)

d = ImageDraw.Draw(img)


def circle(x, y, rad, fill=None, outline=None, width=0):
    (ax, ay), (bx, by) = P(x - rad, y - rad), P(x + rad, y + rad)
    d.ellipse([ax, ay, bx, by], fill=fill, outline=outline, width=width)


def line(x0, y0, x1, y1, w, color):
    d.line([P(x0, y0), P(x1, y1)], fill=color, width=W(w))
    for x, y in ((x0, y0), (x1, y1)):  # round caps
        circle(x, y, w / 2, fill=color)


# Crosshair
circle(54, 45, 11 + 1.75, outline=TEAL, width=W(3.5))
for x0, y0, x1, y1 in ((54, 28, 54, 32), (54, 58, 54, 61), (35, 45, 39, 45), (69, 45, 73, 45)):
    line(x0, y0, x1, y1, 3, TEAL)
circle(54, 45, 3, fill=WHITE)

# Ruler
line(36, 71, 72, 71, 3, WHITE)
tick = (255, 255, 255, 217)
for x, h in ((44, 4), (49, 2.5), (54, 5), (59, 2.5), (64, 4)):
    line(x, 71, x, 71 - h, 1.8, tick)
for x in (36, 72):
    circle(x, 71, 4.5 + 0.9, fill=WHITE)
    circle(x, 71, 4.5 - 0.9, fill=TEAL)

# Preview of the circular launcher mask (radius 33 of 108), when requested
if "--mask" in sys.argv:
    mask = Image.new("L", (px, px), 0)
    (ax, ay), (bx, by) = P(54 - 33, 54 - 33), P(54 + 33, 54 + 33)
    ImageDraw.Draw(mask).ellipse([ax, ay, bx, by], fill=255)
    img.putalpha(mask)

img = img.resize((SIZE, SIZE), Image.LANCZOS)
(img if "--mask" in sys.argv else img.convert("RGB")).save(OUT)
print("wrote", OUT)
