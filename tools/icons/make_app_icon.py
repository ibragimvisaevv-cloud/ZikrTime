"""Иконка приложения «Зикр-тайм»: циферблат из золотых бусин тасбиха, восьмиконечная звезда, стрелки.

Создаёт:
  - Android adaptive icon: drawable/ic_launcher_background.xml, ic_launcher_foreground.xml, ic_launcher_monochrome.xml
  - SVG (полная иконка) для превью и для PNG (iOS 1024, Google Play 512)
Запуск: python3 tools/icons/make_app_icon.py <res-папка> <svg-файл>
"""
import math
import pathlib
import sys

C = 54.0  # центр холста 108×108
GOLD_LIGHT, GOLD, GOLD_DARK = "#FFF1C4", "#E2B659", "#946B1C"
IVORY = "#FFF7E3"


def f(x):
    return f"{x:.2f}".rstrip("0").rstrip(".")


def circle(cx, cy, r):
    return f"M{f(cx - r)},{f(cy)} a{f(r)},{f(r)} 0 1,0 {f(2 * r)},0 a{f(r)},{f(r)} 0 1,0 {f(-2 * r)},0 Z"


def polar(r, deg, cx=C, cy=C):
    a = math.radians(deg)
    return cx + r * math.sin(a), cy - r * math.cos(a)


def star8(cx, cy, r):
    """Восьмиконечная звезда (руб эль-хизб) — контур из двух квадратов как один многоугольник."""
    pts = []
    r_in = r * math.cos(math.radians(45)) / math.cos(math.radians(22.5))
    for i in range(16):
        rr = r if i % 2 == 0 else r_in
        pts.append(polar(rr, i * 22.5, cx, cy))
    return "M" + " L".join(f"{f(x)},{f(y)}" for x, y in pts) + " Z"


# ---------- Модель иконки ----------
RING_R = 26.0
BEADS = []
for i in range(12):
    deg = i * 30
    x, y = polar(RING_R, deg)
    BEADS.append((x, y, 4.3 if i == 0 else 3.2))

MINUTE = (0, 17.5)    # угол, длина
HOUR = (62, 11.5)

# Узор фона: решётка восьмиконечных звёзд
PATTERN = []
step = 18.0
for row in range(-1, 8):
    for col in range(-1, 8):
        cx = col * step + (step / 2 if row % 2 else 0)
        cy = row * step * 0.87
        PATTERN.append(star8(cx, cy, 6.2))


# ---------- Android VectorDrawable ----------
def radial(cx, cy, r, stops):
    items = "".join(f'<item android:offset="{o}" android:color="{c}"/>' for o, c in stops)
    return (f'<aapt:attr name="android:fillColor"><gradient android:type="radial" android:centerX="{f(cx)}" '
            f'android:centerY="{f(cy)}" android:gradientRadius="{f(r)}">{items}</gradient></aapt:attr>')


def vector(body, extra_ns=True):
    ns = ' xmlns:aapt="http://schemas.android.com/aapt"' if extra_ns else ""
    return ('<?xml version="1.0" encoding="utf-8"?>\n'
            f'<vector xmlns:android="http://schemas.android.com/apk/res/android"{ns}\n'
            '    android:width="108dp" android:height="108dp"\n'
            '    android:viewportWidth="108" android:viewportHeight="108">\n' + body + '</vector>\n')


def android_background():
    body = ('  <path android:pathData="M0,0 H108 V108 H0 Z">\n    '
            + radial(C, 40, 84, [("0", "#1B7C63"), ("0.55", "#0C4F40"), ("1", "#04281F")]) + '\n  </path>\n')
    body += ('  <path android:pathData="' + " ".join(PATTERN) + '" android:fillColor="#00000000" '
             'android:strokeColor="#FFFFFF" android:strokeAlpha="0.07" android:strokeWidth="0.6" />\n')
    return vector(body)


def android_foreground():
    body = ""
    # Тонкая «нить» тасбиха
    body += (f'  <path android:pathData="{circle(C, C, RING_R)}" android:fillColor="#00000000" '
             f'android:strokeColor="{GOLD}" android:strokeAlpha="0.55" android:strokeWidth="0.8" />\n')
    # Звезда за стрелками
    body += (f'  <path android:pathData="{star8(C, C, 15.5)}" android:fillColor="#00000000" '
             f'android:strokeColor="{GOLD}" android:strokeAlpha="0.45" android:strokeWidth="0.9" android:strokeLineJoin="round" />\n')
    # Тени бусин
    for x, y, r in BEADS:
        body += f'  <path android:pathData="{circle(x + 0.5, y + 0.8, r)}" android:fillColor="#000000" android:fillAlpha="0.28" />\n'
    # Бусины-жемчужины с бликом
    for x, y, r in BEADS:
        body += (f'  <path android:pathData="{circle(x, y, r)}">\n    '
                 + radial(x - r * 0.35, y - r * 0.45, r * 1.35,
                          [("0", GOLD_LIGHT), ("0.45", GOLD), ("1", GOLD_DARK)]) + '\n  </path>\n')
    # Стрелки: тень и сами стрелки
    hands = " ".join(f"M{f(C)},{f(C)} L{f(polar(l, a)[0])},{f(polar(l, a)[1])}" for a, l in (MINUTE, HOUR))
    shadow = " ".join(f"M{f(C + 0.6)},{f(C + 0.9)} L{f(polar(l, a)[0] + 0.6)},{f(polar(l, a)[1] + 0.9)}" for a, l in (MINUTE, HOUR))
    body += (f'  <path android:pathData="{shadow}" android:strokeColor="#000000" android:strokeAlpha="0.3" '
             f'android:strokeWidth="3.4" android:strokeLineCap="round" android:fillColor="#00000000" />\n')
    body += (f'  <path android:pathData="{hands}" android:strokeColor="{IVORY}" '
             f'android:strokeWidth="3.4" android:strokeLineCap="round" android:fillColor="#00000000" />\n')
    # Центр
    body += (f'  <path android:pathData="{circle(C, C, 3.4)}">\n    '
             + radial(C - 1, C - 1.3, 4.5, [("0", GOLD_LIGHT), ("0.5", GOLD), ("1", GOLD_DARK)]) + '\n  </path>\n')
    body += f'  <path android:pathData="{circle(C, C, 1.1)}" android:fillColor="{IVORY}" />\n'
    return vector(body)


def android_monochrome():
    body = ""
    for x, y, r in BEADS:
        body += f'  <path android:pathData="{circle(x, y, r)}" android:fillColor="#FFFFFF" />\n'
    hands = " ".join(f"M{f(C)},{f(C)} L{f(polar(l, a)[0])},{f(polar(l, a)[1])}" for a, l in (MINUTE, HOUR))
    body += (f'  <path android:pathData="{hands}" android:strokeColor="#FFFFFF" android:strokeWidth="3.4" '
             f'android:strokeLineCap="round" android:fillColor="#00000000" />\n')
    body += f'  <path android:pathData="{circle(C, C, 3.4)}" android:fillColor="#FFFFFF" />\n'
    return vector(body, extra_ns=False)


# ---------- SVG (для превью и PNG) ----------
def svg(size=1024, rounded=False):
    defs = ['<radialGradient id="bg" cx="54" cy="40" r="84" gradientUnits="userSpaceOnUse">'
            '<stop offset="0" stop-color="#1B7C63"/><stop offset="0.55" stop-color="#0C4F40"/><stop offset="1" stop-color="#04281F"/></radialGradient>',
            f'<radialGradient id="cap" cx="{C - 1}" cy="{C - 1.3}" r="4.5" gradientUnits="userSpaceOnUse">'
            f'<stop offset="0" stop-color="{GOLD_LIGHT}"/><stop offset="0.5" stop-color="{GOLD}"/><stop offset="1" stop-color="{GOLD_DARK}"/></radialGradient>']
    parts = ['<rect width="108" height="108" fill="url(#bg)"/>',
             f'<path d="{" ".join(PATTERN)}" fill="none" stroke="#FFFFFF" stroke-opacity="0.07" stroke-width="0.6"/>',
             f'<path d="{circle(C, C, RING_R)}" fill="none" stroke="{GOLD}" stroke-opacity="0.55" stroke-width="0.8"/>',
             f'<path d="{star8(C, C, 15.5)}" fill="none" stroke="{GOLD}" stroke-opacity="0.45" stroke-width="0.9" stroke-linejoin="round"/>']
    for x, y, r in BEADS:
        parts.append(f'<path d="{circle(x + 0.5, y + 0.8, r)}" fill="#000" fill-opacity="0.28"/>')
    for i, (x, y, r) in enumerate(BEADS):
        defs.append(f'<radialGradient id="b{i}" cx="{f(x - r * 0.35)}" cy="{f(y - r * 0.45)}" r="{f(r * 1.35)}" gradientUnits="userSpaceOnUse">'
                    f'<stop offset="0" stop-color="{GOLD_LIGHT}"/><stop offset="0.45" stop-color="{GOLD}"/><stop offset="1" stop-color="{GOLD_DARK}"/></radialGradient>')
        parts.append(f'<path d="{circle(x, y, r)}" fill="url(#b{i})"/>')
    hands = " ".join(f"M{f(C)},{f(C)} L{f(polar(l, a)[0])},{f(polar(l, a)[1])}" for a, l in (MINUTE, HOUR))
    shadow = " ".join(f"M{f(C + 0.6)},{f(C + 0.9)} L{f(polar(l, a)[0] + 0.6)},{f(polar(l, a)[1] + 0.9)}" for a, l in (MINUTE, HOUR))
    parts.append(f'<path d="{shadow}" stroke="#000" stroke-opacity="0.3" stroke-width="3.4" stroke-linecap="round" fill="none"/>')
    parts.append(f'<path d="{hands}" stroke="{IVORY}" stroke-width="3.4" stroke-linecap="round" fill="none"/>')
    parts.append(f'<path d="{circle(C, C, 3.4)}" fill="url(#cap)"/>')
    parts.append(f'<path d="{circle(C, C, 1.1)}" fill="{IVORY}"/>')
    # Для iOS и магазина иконка «полнокадровая»: обрезаем внешние 18 dp (как маска адаптивной иконки)
    view = "18 18 72 72"
    clip = '<clipPath id="r"><rect x="18" y="18" width="72" height="72" rx="16"/></clipPath>' if rounded else ""
    g_open = '<g clip-path="url(#r)">' if rounded else "<g>"
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="{view}" width="{size}" height="{size}">'
            f'<defs>{"".join(defs)}{clip}</defs>{g_open}{"".join(parts)}</g></svg>')


if __name__ == "__main__":
    res = pathlib.Path(sys.argv[1])
    (res / "drawable").mkdir(parents=True, exist_ok=True)
    (res / "drawable" / "ic_launcher_background.xml").write_text(android_background())
    (res / "drawable" / "ic_launcher_foreground.xml").write_text(android_foreground())
    (res / "drawable" / "ic_launcher_monochrome.xml").write_text(android_monochrome())
    if len(sys.argv) > 2:
        pathlib.Path(sys.argv[2]).write_text(svg(1024))
        pathlib.Path(sys.argv[2].replace(".svg", "_rounded.svg")).write_text(svg(1024, rounded=True))
    print("ok")
