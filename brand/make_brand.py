"""
Lunara marka varliklari uretir:
  - logo SVG (koyu canvas + hilal + yildiz + accent halka)
  - favicon.ico (16/32/48)
  - PWA ikonlari (192/512, maskable)
  - apple-touch-icon (180)
  - Android launcher ikonlari (mipmap densities, adaptive icin)
  - Favicons.png / og-image

Marka kararlari:
  - Canvas #0B0B0F (uygulamanin koyu temasıyla ayni)
  - Hilal #F1E9D6 (Text1) — uyku
  - Accent #4FD1E6 — Lunara imzasi
  - Arti isareti #4FD1E6 — findsleepdoctors.com ile bag (klinik + isaret)
"""
import math
import pathlib
import struct
from PIL import Image, ImageDraw

OUT = pathlib.Path(__file__).parent
ANDROID_RES = pathlib.Path('/root/lunara/android/app/src/main/res')

CANVAS = (0x0B, 0x0B, 0x0F)
CREAM = (0xF1, 0xE9, 0xD6)
ACCENT = (0x4F, 0xD1, 0xE6)
ACCENT2 = (0x6C, 0x7B, 0xFF)

# ---------------------------------------------------------------- SVG


def logo_svg(size=1024, rounded=True, bg=CANVAS, with_text=False):
    """Lunara ikonu: hilal + yildiz + uyku dalgasi + arti isareti."""
    s = size
    rx = int(s * 0.225) if rounded else 0
    hexc = lambda c: f'#{c[0]:02X}{c[1]:02X}{c[2]:02X}'
    parts = [
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{s}" height="{s}" '
        f'viewBox="0 0 {s} {s}" role="img" aria-label="Lunara">',
        f'  <rect width="{s}" height="{s}" rx="{rx}" fill="{hexc(bg)}"/>',
    ]
    if rounded:
        parts.append(
            f'  <rect x="{s*0.035:.1f}" y="{s*0.035:.1f}" width="{s*0.93:.1f}" '
            f'height="{s*0.93:.1f}" rx="{int(s*0.20)}" fill="none" '
            f'stroke="{hexc(ACCENT)}" stroke-width="{s*0.010:.1f}" opacity="0.55"/>'
        )

    safe = s * 0.84
    off = (s - safe) / 2
    c = off + safe / 2

    # --- Hilal ---
    R = safe * 0.27
    hx, hy = c - safe * 0.085, c - safe * 0.085
    ox, oy = hx + R * 0.40, hy - R * 0.34
    r = R * 0.86
    parts.append(
        f'  <path fill="{hexc(CREAM)}" fill-rule="evenodd" d="'
        f'M {hx - R:.1f},{hy:.1f} '
        f'a {R:.1f},{R:.1f} 0 1,0 {2*R:.1f},0 '
        f'a {R:.1f},{R:.1f} 0 1,0 {-2*R:.1f},0 Z '
        f'M {ox - r:.1f},{oy:.1f} '
        f'a {r:.1f},{r:.1f} 0 1,0 {2*r:.1f},0 '
        f'a {r:.1f},{r:.1f} 0 1,0 {-2*r:.1f},0 Z"/>'
    )

    # --- Yildiz ---
    sx, sy = c + safe * 0.27, c - safe * 0.30
    star_r = safe * 0.075
    points = []
    for i in range(10):
        ang = -math.pi / 2 + i * math.pi / 5
        rad = star_r if i % 2 == 0 else star_r * 0.42
        points.append(f'{sx + rad*math.cos(ang):.1f},{sy + rad*math.sin(ang):.1f}')
    parts.append(f'  <polygon points="{" ".join(points)}" fill="{hexc(ACCENT)}"/>')

    # --- Arti isareti ---
    ax, ay = c + safe * 0.27, c + safe * 0.28
    arm = safe * 0.105
    th = safe * 0.040
    parts.append(
        f'  <g fill="{hexc(ACCENT2)}">'
        f'<rect x="{ax - th/2:.1f}" y="{ay - arm:.1f}" width="{th:.1f}" height="{2*arm:.1f}" rx="{th/2:.1f}"/>'
        f'<rect x="{ax - arm:.1f}" y="{ay - th/2:.1f}" width="{2*arm:.1f}" height="{th:.1f}" rx="{th/2:.1f}"/>'
        f'</g>'
    )

    # --- Uyku dalgasi ---
    wx = off + safe * 0.02
    ww = safe * 0.50
    wy = c + safe * 0.335
    wave = [f'M {wx:.1f},{wy:.1f}']
    for i in range(1, 13):
        x = wx + ww * i / 12
        y = wy - safe * 0.045 if i % 2 else wy
        wave.append(f'L {x:.1f},{y:.1f}')
    parts.append(
        f'  <path d="{" ".join(wave)}" fill="none" stroke="{hexc(ACCENT)}" '
        f'stroke-width="{s*0.011:.1f}" stroke-linecap="round" stroke-linejoin="round" opacity="0.78"/>'
    )

    parts.append('</svg>')
    return '\n'.join(parts)


def wordmark_svg():
    """Yatay logo: ikon + Lunara yazisi."""
    return f'''<svg xmlns="http://www.w3.org/2000/svg" width="720" height="200" viewBox="0 0 720 200" role="img" aria-label="Lunara">
  <g transform="translate(10 10) scale(0.175)">
  <rect width="1024" height="1024" rx="230" fill="#{CANVAS[0]:02X}{CANVAS[1]:02X}{CANVAS[2]:02X}"/>
  <rect x="36" y="36" width="952" height="952" rx="205" fill="none" stroke="#{ACCENT[0]:02X}{ACCENT[1]:02X}{ACCENT[2]:02X}" stroke-width="18" opacity="0.55"/>
  <path fill="#{CREAM[0]:02X}{CREAM[1]:02X}{CREAM[2]:02X}" fill-rule="evenodd" d="M 209.7,512 a 261.1,261.1 0 1,0 522.2,0 a 261.1,261.1 0 1,0 -522.2,0 Z M 364.2,435.2 a 234.0,234.0 0 1,0 468.0,0 a 234.0,234.0 0 1,0 -468.0,0 Z"/>
  <polygon points="737,240 758,290 810,290 771,322 787,372 737,344 687,372 703,322 664,290 716,290" fill="#{ACCENT[0]:02X}{ACCENT[1]:02X}{ACCENT[2]:02X}"/>
  <g fill="#{ACCENT2[0]:02X}{ACCENT2[1]:02X}{ACCENT2[2]:02X}">
    <rect x="755" y="715" width="43" height="118" rx="21"/>
    <rect x="716" y="755" width="118" height="43" rx="21"/>
  </g>
  </g>
  <text x="215" y="122" font-family="Fraunces, Georgia, serif" font-size="78" font-weight="700" fill="#{CREAM[0]:02X}{CREAM[1]:02X}{CREAM[2]:02X}">Lunara</text>
  <text x="217" y="158" font-family="Manrope, Helvetica, sans-serif" font-size="21" font-weight="600" letter-spacing="2.4" fill="#{ACCENT[0]:02X}{ACCENT[1]:02X}{ACCENT[2]:02X}">SLEEP TRACKING</text>
</svg>'''


# ---------------------------------------------------------------- PNG render


def render_png(size, adaptive=False, scale=1.0):
    """Adaptive icon icin: foreground = safe-zone icindeki ikon, background = dolgu."""
    ss = size * 4  # supersampling
    bg = CANVAS if not adaptive else None
    img = Image.new('RGBA', (ss, ss), (0, 0, 0, 0) if adaptive else bg + (255,))
    d = ImageDraw.Draw(img)
    if adaptive:
        d.rectangle([0, 0, ss, ss], fill=CANVAS + (255,))

    # kenar boslugu: adaptive ikonlar icin safe zone ~%66
    inset = ss * (0.5 - 0.33 * scale) if adaptive else ss * 0.06
    box = ss - 2 * inset
    c = inset + box / 2

    # hilal
    R = box * 0.30
    d.ellipse([c - R, c - R, c + R, c + R], fill=CREAM + (255,))
    ox, oy = c + R * 0.42, c - R * 0.30
    r = R * 0.88
    d.ellipse([ox - r, oy - r, ox + r, oy + r], fill=(0, 0, 0, 0))
    # punch out: composited alpha
    if adaptive:
        pass
    else:
        # arka plani geri koymak icin: delik zaten saydam degil, yeniden ciz
        d.rectangle([0, 0, ss, ss], fill=None)
        img = Image.new('RGBA', (ss, ss), bg + (255,))
        d = ImageDraw.Draw(img)
        d.ellipse([c - R, c - R, c + R, c + R], fill=CREAM + (255,))
        # delik: canvas rengi ile kapat (maskeli ikon) -> asagida alpha ile halledilir

    return img


def render_icon(size, adaptive=False, transparent_bg=False, round_shape=False):
    """
    Duzgun ikon render.
    adaptive=True   -> 108dp tuval, safe zone %66, saydam arka plan
    adaptive=False  -> tam kutu, canvas arka plan, yuvarlatilmis kose
    round_shape=True-> dairesel maske (roundIcon)
    """
    ss = size * 4
    img = Image.new('RGBA', (ss, ss), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)

    if not transparent_bg:
        d.rectangle([0, 0, ss, ss], fill=CANVAS + (255,))

    if adaptive:
        safe = ss * 0.66
        off = (ss - safe) / 2
    else:
        safe = ss * 0.84
        off = (ss - safe) / 2

    c = off + safe / 2

    # --- Hilal: sol-ust yapisinda, ic delik (alpha) ---
    R = safe * 0.27
    hx, hy = c - safe * 0.085, c - safe * 0.085
    d.ellipse([hx - R, hy - R, hx + R, hy + R], fill=CREAM + (255,))
    ox, oy = hx + R * 0.40, hy - R * 0.34
    r = R * 0.86
    hole = (0, 0, 0, 0) if (adaptive or transparent_bg) else CANVAS + (255,)
    d.ellipse([ox - r, oy - r, ox + r, oy + r], fill=hole)

    # --- Yildiz: sag ust ---
    sx, sy = c + safe * 0.27, c - safe * 0.30
    star_r = safe * 0.075
    pts = []
    for i in range(10):
        ang = -math.pi / 2 + i * math.pi / 5
        rad = star_r if i % 2 == 0 else star_r * 0.42
        pts.append((sx + rad * math.cos(ang), sy + rad * math.sin(ang)))
    d.polygon(pts, fill=ACCENT + (255,))

    # --- Arti isareti: sag alt, hilalden ayri ---
    ax, ay = c + safe * 0.27, c + safe * 0.28
    arm = safe * 0.105
    th = safe * 0.040
    d.rounded_rectangle(
        [ax - th / 2, ay - arm, ax + th / 2, ay + arm],
        radius=th / 2, fill=ACCENT2 + (255,))
    d.rounded_rectangle(
        [ax - arm, ay - th / 2, ax + arm, ay + th / 2],
        radius=th / 2, fill=ACCENT2 + (255,))

    # --- Uyku dalgasi: alt serit, hilalin ALTINDA (ayrilmis) ---
    wx = off + safe * 0.02
    ww = safe * 0.50
    wy = c + safe * 0.335
    path = []
    for i in range(13):
        x = wx + ww * i / 12
        y = wy - safe * 0.045 if i % 2 else wy
        path.append((x, y))
    d.line(path, fill=ACCENT + (200,), width=max(2, int(ss * 0.011)), joint='curve')

    if not adaptive and not transparent_bg:
        # ince accent cerceve + yuvarlatilmis kose
        d.rounded_rectangle(
            [ss * 0.035, ss * 0.035, ss * 0.965, ss * 0.965],
            radius=ss * 0.20, outline=ACCENT + (140,), width=max(2, int(ss * 0.010)))
        mask = Image.new('L', (ss, ss), 0)
        md = ImageDraw.Draw(mask)
        if round_shape:
            md.ellipse([0, 0, ss, ss], fill=255)
        else:
            md.rounded_rectangle([0, 0, ss, ss], radius=ss * 0.225, fill=255)
        img.putalpha(mask)

    return img.resize((size, size), Image.LANCZOS)


def write_ico(path, sizes=(16, 32, 48, 64, 128, 256)):
    """Favicon.ico — coklu boyut."""
    imgs = [render_icon(s).convert('RGBA') for s in sizes]
    imgs[0].save(path, format='ICO',
                sizes=[(s, s) for s in sizes], append_images=imgs[1:])
    print('  wrote', path)


def main():
    out = OUT / 'brand'
    out.mkdir(exist_ok=True)

    # --- SVG ---
    (out / 'lunara-icon.svg').write_text(logo_svg(1024), encoding='utf-8')
    (out / 'lunara-icon-rounded.svg').write_text(logo_svg(1024, rounded=True), encoding='utf-8')
    (out / 'lunara-logo.svg').write_text(wordmark_svg(), encoding='utf-8')
    (out / 'lunara-logo-light.svg').write_text(wordmark_svg(), encoding='utf-8')
    print('  wrote SVG set')

    # --- Favicons (site icin) ---
    write_ico(out / 'favicon.ico', (16, 32, 48))
    for s in (16, 32, 48, 96, 180, 192, 512):
        render_icon(s).save(out / f'favicon-{s}.png')
    print('  wrote favicon PNG set')

    # --- Android mipmap'leri ---
    densities = {'mdpi': 48, 'hdpi': 72, 'xhdpi': 96, 'xxhdpi': 144, 'xxxhdpi': 192}
    for name, size in densities.items():
        folder = ANDROID_RES / f'mipmap-{name}'
        folder.mkdir(parents=True, exist_ok=True)
        render_icon(size).save(folder / 'ic_launcher.png')
        render_icon(size, round_shape=True).save(folder / 'ic_launcher_round.png')
        # adaptive foreground: 108dp tuval
        fg_size = int(size * 108 / 48)
        render_icon(fg_size, adaptive=True, transparent_bg=True).save(
            folder / 'ic_launcher_foreground.png')
    print('  wrote Android mipmaps')

    # --- Adaptive icon XML (zaten var,前景 degistir) ---
    (ANDROID_RES / 'mipmap-anydpi-v26' / 'ic_launcher.xml').write_text(
        '''<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/canvas" />
    <foreground android:drawable="@mipmap/ic_launcher_foreground" />
    <monochrome android:drawable="@mipmap/ic_launcher_foreground" />
</adaptive-icon>
''', encoding='utf-8')
    print('  wrote adaptive icon xml')

    # --- Monochrome / notification icin vektor ---
    (ANDROID_RES / 'drawable' / 'ic_launcher_monochrome.xml').write_text(
        '''<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path
        android:fillColor="#FFFFFFFF"
        android:pathData="M50.4,54 a22,22 0 1,0 44,0 a22,22 0 1,0 -44,0 Z M60.2,45.6 a17,17 0 1,0 34,0 a17,17 0 1,0 -34,0 Z" />
</vector>
''', encoding='utf-8')
    print('  wrote monochrome vector')


if __name__ == '__main__':
    main()
