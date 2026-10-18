#!/usr/bin/env python3
"""Build the splash trace as a Lottie animation from the mark's SVG path.

Writes brand/openplay-splash.json (the asset for lottie-web, lottie-android and
bootanimation frame export) and brand/openplay-splash.lottie.js (the same data as a
script, so the library page can play it when opened from file://).

Timeline matches brand/diplay-splash-trace.svg at 60 fps, 2.6 s:
  0.00-1.60  outline traces (trim path end 0 -> 100), glow trails by 80 ms
  1.25-2.05  gradient fill fades in, mark settles from 96% to 100%
  1.80-2.60  outline and glow fade out

Run from the repository root:  python3 design/ui-library/brand/build_lottie.py
"""
import json
import os
import re

HERE = os.path.dirname(os.path.abspath(__file__))
FPS = 60
FRAMES = round(2.6 * FPS)
W, H = 180, 178          # canvas: the mark (150 x 148) with 15 units of room for the glow
OFFSET = (15, 15)

MARK = (
    "M58.0615 0C54.0815 0 50.8035 3.28714 50.8035 7.2785V38.1089C50.8035 38.9159 50.406 39.6654 49.7407 40.1181"
    "C49.0746 40.5708 48.2344 40.6627 47.4867 40.3641L22.9912 30.186C22.0721 29.8037 21.113 29.6314 20.1702 29.6412"
    "C17.3425 29.6704 14.65 31.3633 13.5073 34.1287L0.54667 65.5079C-0.976196 69.1951 0.794 73.4982 4.47372 75.0189"
    "L34.1829 87.3013C34.8644 87.5843 35.3763 88.1591 35.5815 88.87C35.7867 89.5804 35.6571 90.3365 35.2316 90.9406"
    "L15.7831 115.624C13.3605 118.79 13.9632 123.402 17.1202 125.831L43.9929 146.511C47.1496 148.94 51.7489 148.331 54.1711 145.165"
    "L73.0819 121.159C73.5407 120.559 74.2461 120.211 75.0002 120.211C75.7538 120.211 76.4592 120.559 76.9179 121.159"
    "L95.8288 145.165C98.2514 148.331 102.849 148.94 106.007 146.511L132.88 125.831C136.037 123.402 136.64 118.791 134.217 115.624"
    "L114.768 90.9406C114.343 90.3374 114.214 89.5804 114.418 88.87C114.624 88.1591 115.136 87.5843 115.817 87.3013"
    "L145.526 75.0189C149.206 73.4982 150.976 69.1963 149.453 65.5079L136.491 34.1287C135.349 31.3635 132.656 29.6706 129.829 29.6412"
    "C128.886 29.6315 127.927 29.8048 127.008 30.186L102.512 40.3641C101.765 40.6631 100.924 40.5708 100.258 40.1181"
    "C99.5892 39.6654 99.1917 38.9159 99.1917 38.1089V7.2785C99.1917 3.28714 95.9141 0 91.9341 0H58.0615Z"
)


def svg_to_lottie_shape(d):
    """Absolute M/C/L/H/V/Z path -> Lottie bezier {v, i, o, c} (tangents relative to vertices)."""
    tokens = re.findall(r"[MCLHVZ]|-?\d*\.?\d+(?:e-?\d+)?", d)
    v, i, o = [], [], []
    pos = (0.0, 0.0)
    cmd, k = None, 0

    def num():
        nonlocal k
        val = float(tokens[k])
        k += 1
        return val

    while k < len(tokens):
        if tokens[k].isalpha():
            cmd = tokens[k]
            k += 1
            if cmd == "Z":
                continue
        if cmd == "M":
            pos = (num(), num())
            v.append(list(pos)); i.append([0, 0]); o.append([0, 0])
            cmd = "L"
        elif cmd == "C":
            c1, c2, end = (num(), num()), (num(), num()), (num(), num())
            o[-1] = [c1[0] - pos[0], c1[1] - pos[1]]
            v.append(list(end)); i.append([c2[0] - end[0], c2[1] - end[1]]); o.append([0, 0])
            pos = end
        elif cmd in "LHV":
            if cmd == "L":
                pos = (num(), num())
            elif cmd == "H":
                pos = (num(), pos[1])
            else:
                pos = (pos[0], num())
            v.append(list(pos)); i.append([0, 0]); o.append([0, 0])
    # The path ends on its start point: fold the duplicate vertex into the first one.
    if len(v) > 1 and abs(v[0][0] - v[-1][0]) < 1e-6 and abs(v[0][1] - v[-1][1]) < 1e-6:
        i[0] = i[-1]
        v.pop(); i.pop(); o.pop()
    r = lambda pts: [[round(x, 4), round(y, 4)] for x, y in pts]
    return {"c": True, "v": r(v), "i": r(i), "o": r(o)}


def f(seconds):
    return round(seconds * FPS)


def kf(*frames, ease=((0.65, 0), (0.35, 1))):
    """[(seconds, value), ...] -> animated Lottie property. Default ease: in-out (like CSS)."""
    (ox, oy), (ix, iy) = ease
    keys = []
    for n, (t, val) in enumerate(frames):
        key = {"t": f(t), "s": val if isinstance(val, list) else [val]}
        if n < len(frames) - 1:
            key["o"] = {"x": [ox], "y": [oy]}
            key["i"] = {"x": [ix], "y": [iy]}
        keys.append(key)
    return {"a": 1, "k": keys}


EXPO_OUT = ((0.16, 1), (0.3, 1))
EASE_IN = ((0.4, 0), (1, 1))
static = lambda val: {"a": 0, "k": val}
rgb = lambda h: [int(h[n:n + 2], 16) / 255 for n in (1, 3, 5)]


def group(name, items, scale=None):
    return {
        "ty": "gr", "nm": name, "it": items + [{
            "ty": "tr", "nm": "Transform",
            "a": static([75, 74]), "p": static([75, 74]),
            "s": scale or static([100, 100]), "r": static(0), "o": static(100),
        }],
    }


def build():
    shape = svg_to_lottie_shape(MARK)
    path = {"ty": "sh", "nm": "Mark", "ks": static(shape)}
    fill = group("Fill", [
        path,
        {
            "ty": "gf", "nm": "Gradient", "r": 2, "t": 1,
            "o": kf((1.25, 0), (2.05, 100), ease=EXPO_OUT),
            "s": static([75, 0]), "e": static([75, 148]),
            "g": {"p": 3, "k": static([0, *rgb("#3DFF6E"), 0.55, *rgb("#0E9E3E"), 1, *rgb("#021A0C")])},
        },
    ], scale=kf((1.25, [96, 96]), (2.05, [100, 100]), ease=EXPO_OUT))

    def stroke_group(name, width, color, opacity, delay):
        return group(name, [
            path,
            {"ty": "tm", "nm": "Trace", "s": static(0), "o": static(0), "m": 1,
             "e": kf((delay, 0), (1.6 + delay, 100))},
            {"ty": "st", "nm": "Stroke", "c": static([*rgb(color), 1]), "w": static(width),
             "lc": 2, "lj": 2, "o": kf((1.8, opacity), (2.6, 0), ease=EASE_IN)},
        ])

    layer = {
        "ddd": 0, "ind": 1, "ty": 4, "nm": "OpenPlay mark", "sr": 1, "ao": 0,
        "ip": 0, "op": FRAMES, "st": 0, "bm": 0,
        "ks": {
            "o": static(100), "r": static(0), "a": static([0, 0]),
            "p": static([*OFFSET, 0]), "s": static([100, 100, 100]),
        },
        # Lottie draws the first shape on top: trace, then glow, then fill.
        "shapes": [
            stroke_group("Trace", 2.2, "#FFFFFF", 100, 0),
            stroke_group("Glow", 7, "#3DFF6E", 35, 0.08),
            fill,
        ],
    }
    return {
        "v": "5.12.2", "fr": FPS, "ip": 0, "op": FRAMES, "w": W, "h": H,
        "nm": "OpenPlay splash trace", "ddd": 0, "assets": [], "layers": [layer],
    }


def main():
    data = build()
    text = json.dumps(data, separators=(",", ":"))
    with open(os.path.join(HERE, "openplay-splash.json"), "w") as out:
        out.write(text)
    with open(os.path.join(HERE, "openplay-splash.lottie.js"), "w") as out:
        out.write("/* Generated by build_lottie.py. Same data as openplay-splash.json. */\n")
        out.write(f"window.OPENPLAY_SPLASH_LOTTIE = {text};\n")
    verts = len(data["layers"][0]["shapes"][0]["it"][0]["ks"]["k"]["v"])
    print(f"openplay-splash.json: {FRAMES} frames at {FPS} fps, {verts} vertices, {len(text)} bytes")


if __name__ == "__main__":
    main()
