"""Генератор иконок интерфейса: Android VectorDrawable (res/drawable) + SVG-превью.

Все иконки 24×24, линейный стиль (толщина линии 1.8), цвет задаётся при отрисовке (tint).
Запуск: python3 tools/icons/make_icons.py <папка res/drawable> [папка для SVG]
"""
import math
import pathlib
import sys

SW = 1.8  # толщина линии


def f(x):
    return f"{x:.2f}".rstrip("0").rstrip(".")


def circle(cx, cy, r):
    return f"M{f(cx - r)},{f(cy)} a{f(r)},{f(r)} 0 1,0 {f(2 * r)},0 a{f(r)},{f(r)} 0 1,0 {f(-2 * r)},0 Z"


def rays(cx, cy, r1, r2, n=8, start=0.0):
    out = []
    for i in range(n):
        a = math.radians(start + i * 360 / n)
        out.append(f"M{f(cx + r1 * math.sin(a))},{f(cy - r1 * math.cos(a))} L{f(cx + r2 * math.sin(a))},{f(cy - r2 * math.cos(a))}")
    return " ".join(out)


def crescent(cx, cy, r, dx, dy, r2):
    """Полумесяц: внешний круг (cx,cy,r) минус круг (cx+dx, cy+dy, r2). Возвращает замкнутый контур."""
    # Точки пересечения двух окружностей
    x0, y0, x1, y1 = cx, cy, cx + dx, cy + dy
    d = math.hypot(dx, dy)
    a = (r * r - r2 * r2 + d * d) / (2 * d)
    h = math.sqrt(max(r * r - a * a, 0))
    xm, ym = x0 + a * dx / d, y0 + a * dy / d
    p1 = (xm + h * dy / d, ym - h * dx / d)
    p2 = (xm - h * dy / d, ym + h * dx / d)
    # Внешняя дуга (большая) от p1 к p2, затем внутренняя дуга обратно
    return (f"M{f(p1[0])},{f(p1[1])} A{f(r)},{f(r)} 0 1,0 {f(p2[0])},{f(p2[1])} "
            f"A{f(r2)},{f(r2)} 0 0,1 {f(p1[0])},{f(p1[1])} Z")


def star(cx, cy, r_out, r_in, n=5, rot=0):
    pts = []
    for i in range(2 * n):
        r = r_out if i % 2 == 0 else r_in
        a = math.radians(rot + i * 180 / n)
        pts.append((cx + r * math.sin(a), cy - r * math.cos(a)))
    return "M" + " L".join(f"{f(x)},{f(y)}" for x, y in pts) + " Z"


# Каждый элемент: ("stroke", d) — линия; ("fill", d) — заливка.
ICONS = {
    # --- Время намаза ---
    "ic_prayer_fajr": [  # рассвет: горизонт, восходящий край солнца и лучи
        ("stroke", "M3,18 H21"),
        ("stroke", "M7.5,18 a4.5,4.5 0 0,1 9,0"),
        ("stroke", " ".join(rays(12, 18, 6.6, 9.2, n=1, start=a) for a in (-68, -34, 0, 34, 68))),
    ],
    "ic_prayer_sunrise": [  # солнце над горизонтом
        ("stroke", circle(12, 11, 3.6)),
        ("stroke", rays(12, 11, 5.8, 7.6, n=8)),
        ("stroke", "M4,21 H20"),
    ],
    "ic_prayer_dhuhr": [  # солнце в зените
        ("stroke", circle(12, 12, 4.2)),
        ("stroke", rays(12, 12, 6.6, 9, n=8)),
    ],
    "ic_prayer_asr": [  # послеполуденное солнце: наполовину закрашенный круг
        ("stroke", circle(12, 12, 4.2)),
        ("fill", "M12,7.8 A4.2,4.2 0 0,0 12,16.2 Z"),
        ("stroke", rays(12, 12, 6.6, 9, n=8)),
    ],
    "ic_prayer_maghrib": [  # закат: горизонт, край солнца и стрелка вниз
        ("stroke", "M3,18 H21"),
        ("stroke", "M7.5,18 a4.5,4.5 0 0,1 9,0"),
        ("stroke", "M12,3.5 V10 M9.2,7.4 L12,10.2 L14.8,7.4"),
    ],
    "ic_prayer_isha": [  # полумесяц и звезда
        ("stroke", crescent(10.5, 13, 7, 3.6, -3.2, 6.2)),
        ("fill", star(18, 5.6, 2.6, 1.1, n=5)),
    ],
    # --- Разделы ---
    "ic_nav_prayer": [  # мечеть: купол с полумесяцем и два минарета
        ("stroke", "M6.5,20.5 V14 a5.5,5.5 0 0,1 11,0 V20.5"),
        ("stroke", "M12,8.5 V6.2"),
        ("fill", crescent(12, 4.4, 1.7, 0.9, -0.6, 1.4)),
        ("stroke", "M2.5,20.5 H21.5"),
        ("stroke", "M3.6,20.5 V11 M3.6,11 L3.6,9.6 M20.4,20.5 V11 M20.4,11 L20.4,9.6"),
        ("stroke", "M10.2,20.5 V17 a1.8,1.8 0 0,1 3.6,0 V20.5"),
    ],
    "ic_nav_knowledge": [  # раскрытая книга
        ("stroke", "M12,6.5 C10,5 7,4.6 3.5,5 V18.5 C7,18.1 10,18.6 12,20 C14,18.6 17,18.1 20.5,18.5 V5 C17,4.6 14,5 12,6.5 Z"),
        ("stroke", "M12,6.5 V20"),
    ],
    "ic_nav_focus": [  # песочные часы
        ("stroke", "M6.5,3.5 H17.5 M6.5,20.5 H17.5"),
        ("stroke", "M7.5,3.5 C7.5,8 10.5,10 12,12 C13.5,10 16.5,8 16.5,3.5"),
        ("stroke", "M7.5,20.5 C7.5,16 10.5,14 12,12 C13.5,14 16.5,16 16.5,20.5"),
        ("fill", "M9.6,19.2 C10.2,17.4 11.2,16.4 12,15.6 C12.8,16.4 13.8,17.4 14.4,19.2 Z"),
    ],
    "ic_nav_more": [  # четыре квадрата
        ("stroke", "M4,4 H10 V10 H4 Z M14,4 H20 V10 H14 Z M4,14 H10 V20 H4 Z M14,14 H20 V20 H14 Z"),
    ],
    # --- Функции ---
    "ic_qibla": [  # компас со стрелкой
        ("stroke", circle(12, 12, 9)),
        ("fill", "M12,5.2 L14.6,12 L12,18.8 L9.4,12 Z"),
        ("stroke", circle(12, 12, 0.01)),
    ],
    "ic_tasbih": [  # чётки: кольцо бусин и кисточка
        *[("fill", circle(12 + 6.6 * math.sin(math.radians(a)), 10.5 - 6.6 * math.cos(math.radians(a)), 1.25))
          for a in range(0, 360, 30) if a != 180],
        ("fill", circle(12, 17.6, 1.7)),
        ("stroke", "M12,19.3 V22 M10.6,22 L12,19.6 L13.4,22"),
    ],
    "ic_alarm": [  # будильник
        ("stroke", circle(12, 13, 7.2)),
        ("stroke", "M12,9.2 V13 L14.6,14.8"),
        ("stroke", "M3.8,6.2 L6.6,3.6 M20.2,6.2 L17.4,3.6"),
        ("stroke", "M7.2,20.2 L5.8,21.8 M16.8,20.2 L18.2,21.8"),
    ],
    "ic_language": [  # глобус
        ("stroke", circle(12, 12, 9)),
        ("stroke", "M3,12 H21"),
        ("stroke", "M12,3 C9.3,6 9.3,18 12,21 C14.7,18 14.7,6 12,3 Z"),
    ],
    "ic_calc": [  # солнце с часами: метод расчёта
        ("stroke", circle(12, 12, 8.5)),
        ("stroke", "M12,7 V12 L15.5,14"),
        ("stroke", "M12,1.8 V3.4 M12,20.6 V22.2 M1.8,12 H3.4 M20.6,12 H22.2"),
    ],
    "ic_kaaba": [  # Кааба спереди: пояс-кисва и дверь
        ("stroke", "M4.5,5.5 H19.5 V20 H4.5 Z"),
        ("fill", "M4.5,8.6 H19.5 V10.6 H4.5 Z"),
        ("stroke", "M13.6,20 V14.6 H17 V20"),
    ],
    "ic_check": [
        ("stroke", "M5,12.5 L10,17.2 L19,7.5"),
    ],
    # --- Магазин ---
    "ic_nav_shop": [  # сумка для покупок
        ("stroke", "M5,8.5 H19 L18,20.5 H6 Z"),
        ("stroke", "M9,10.5 V7 a3,3 0 0,1 6,0 V10.5"),
    ],
    "ic_shop_perfume": [  # флакон духов
        ("stroke", "M7,11 H17 a1.5,1.5 0 0,1 1.5,1.5 V19 a1.5,1.5 0 0,1 -1.5,1.5 H7 a1.5,1.5 0 0,1 -1.5,-1.5 V12.5 a1.5,1.5 0 0,1 1.5,-1.5 Z"),
        ("stroke", "M10,11 V8 H14 V11"),
        ("stroke", "M9,5 H15"),
        ("stroke", "M12,5 V8"),
    ],
    "ic_shop_car": [  # автомобиль
        ("stroke", "M4,16.5 V13 L6,8.5 H18 L20,13 V16.5 Z"),
        ("stroke", "M4,13 H20"),
        ("stroke", circle(7.5, 17, 1.6)),
        ("stroke", circle(16.5, 17, 1.6)),
    ],
    "ic_shop_book": [  # книга с закладкой
        ("stroke", "M6,4 H17 a1,1 0 0,1 1,1 V20 H7 a2,2 0 0,1 -2,-2 V5 a1,1 0 0,1 1,-1 Z"),
        ("stroke", "M5,18 a2,2 0 0,1 2,-2 H18"),
        ("stroke", "M10,4 V10 L12,8.6 L14,10 V4"),
    ],
    "ic_shop_clothes": [  # одежда
        ("stroke", "M9,4 L4,7 L5.5,11 L7.5,10 V20 H16.5 V10 L18.5,11 L20,7 L15,4 a3,3 0 0,1 -6,0 Z"),
    ],
    "ic_shop_food": [  # финики / халяль-продукты: тарелка с плодами
        ("stroke", "M4,13 H20 a8,6 0 0,1 -16,0 Z"),
        ("stroke", "M8.5,10.5 a1.6,2.4 0 1,1 3.2,0 a1.6,2.4 0 1,1 -3.2,0"),
        ("stroke", "M12.5,10 a1.6,2.4 0 1,1 3.2,0 a1.6,2.4 0 1,1 -3.2,0"),
    ],
    "ic_shop_gift": [  # подарок
        ("stroke", "M4.5,9 H19.5 V12 H4.5 Z"),
        ("stroke", "M6,12 V20 H18 V12"),
        ("stroke", "M12,9 V20"),
        ("stroke", "M12,9 C10,5 7,6 8,8 C8.6,9 12,9 12,9 C12,9 15.4,9 16,8 C17,6 14,5 12,9 Z"),
    ],
    "ic_shop_store": [  # витрина магазина
        ("stroke", "M4,9 L5.5,4.5 H18.5 L20,9 Z"),
        ("stroke", "M4,9 a2.67,2 0 0,0 5.33,0 a2.67,2 0 0,0 5.33,0 a2.67,2 0 0,0 5.33,0"),
        ("stroke", "M5.5,11.5 V20 H18.5 V11.5"),
        ("stroke", "M10,20 V15 H14 V20"),
    ],
    "ic_telegram": [  # бумажный самолётик
        ("stroke", "M20.5,4.5 L3.5,11 L9.5,13.5 L17,7.5 L11,14.8 L11.5,19.5 L14,16 L18,19 Z"),
    ],
    "ic_phone": [  # телефонная трубка
        ("stroke", "M6.5,4 H9.5 L11,8 L9,9.5 a11,11 0 0,0 5.5,5.5 L16,13 L20,14.5 V17.5 a2,2 0 0,1 -2,2 A15,15 0 0,1 4.5,6 a2,2 0 0,1 2,-2 Z"),
    ],
    "ic_pin": [  # метка на карте
        ("stroke", "M12,21 C12,21 5.5,14.5 5.5,9.5 a6.5,6.5 0 0,1 13,0 C18.5,14.5 12,21 12,21 Z"),
        ("stroke", circle(12, 9.5, 2.3)),
    ],
    "ic_chat": [  # облачко сообщения (WhatsApp)
        ("stroke", "M12,4 a8,7.5 0 1,1 -4.2,13.9 L4,19.5 L5.2,15.8 A7.5,7.5 0 0,1 12,4 Z"),
    ],
    "ic_mosque": [  # мечеть (для карты мест)
        ("stroke", "M6.5,20.5 V14 a5.5,5.5 0 0,1 11,0 V20.5"),
        ("stroke", "M12,8.5 V6.2"),
        ("fill", crescent(12, 4.4, 1.7, 0.9, -0.6, 1.4)),
        ("stroke", "M2.5,20.5 H21.5"),
        ("stroke", "M3.6,20.5 V11 M3.6,11 L3.6,9.6 M20.4,20.5 V11 M20.4,11 L20.4,9.6"),
        ("stroke", "M10.2,20.5 V17 a1.8,1.8 0 0,1 3.6,0 V20.5"),
    ],
    "ic_prayer_rug": [  # молельный коврик с михрабом и бахромой
        ("stroke", "M6,4.5 H18 V19.5 H6 Z"),
        ("stroke", "M8.5,17 V10.5 a3.5,3.5 0 0,1 7,0 V17 Z"),
        ("stroke", "M7,2.5 V4.5 M9.5,2.5 V4.5 M12,2.5 V4.5 M14.5,2.5 V4.5 M17,2.5 V4.5"),
        ("stroke", "M7,19.5 V21.5 M9.5,19.5 V21.5 M12,19.5 V21.5 M14.5,19.5 V21.5 M17,19.5 V21.5"),
    ],
    "ic_camera": [  # Instagram: камера
        ("stroke", "M7,4.5 H17 a2.5,2.5 0 0,1 2.5,2.5 V17 a2.5,2.5 0 0,1 -2.5,2.5 H7 a2.5,2.5 0 0,1 -2.5,-2.5 V7 a2.5,2.5 0 0,1 2.5,-2.5 Z"),
        ("stroke", circle(12, 12, 3.4)),
        ("fill", circle(16.6, 7.6, 0.9)),
    ],
}


def to_vector(elements):
    out = ['<?xml version="1.0" encoding="utf-8"?>',
           '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
           '    android:width="24dp"', '    android:height="24dp"',
           '    android:viewportWidth="24"', '    android:viewportHeight="24">']
    for kind, d in elements:
        if kind == "stroke":
            out.append(f'    <path android:pathData="{d}"\n        android:strokeColor="#FF000000" android:strokeWidth="{SW}"\n'
                       f'        android:strokeLineCap="round" android:strokeLineJoin="round" android:fillColor="#00000000" />')
        else:
            out.append(f'    <path android:pathData="{d}" android:fillColor="#FF000000" />')
    out.append("</vector>")
    return "\n".join(out) + "\n"


def to_svg(elements, color="#0F5C4A"):
    parts = []
    for kind, d in elements:
        if kind == "stroke":
            parts.append(f'<path d="{d}" fill="none" stroke="{color}" stroke-width="{SW}" stroke-linecap="round" stroke-linejoin="round"/>')
        else:
            parts.append(f'<path d="{d}" fill="{color}"/>')
    return "".join(parts)


if __name__ == "__main__":
    res = pathlib.Path(sys.argv[1])
    res.mkdir(parents=True, exist_ok=True)
    for name, els in ICONS.items():
        (res / f"{name}.xml").write_text(to_vector(els))
    if len(sys.argv) > 2:
        cells = []
        for name, els in ICONS.items():
            cells.append(
                f'<div style="display:inline-block;width:120px;text-align:center;font:11px sans-serif;margin:6px">'
                f'<svg viewBox="0 0 24 24" width="48" height="48">{to_svg(els)}</svg>'
                f'<svg viewBox="0 0 24 24" width="24" height="24" style="margin-left:8px">{to_svg(els)}</svg>'
                f'<div>{name}</div></div>')
        pathlib.Path(sys.argv[2]).write_text(f'<html><body style="background:#FBF8F1;margin:12px">{"".join(cells)}</body></html>')
    print(len(ICONS), "icons")
