import math, sys
from fontTools.ttLib import TTFont
from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen
from fontTools.pens.boundsPen import BoundsPen

ROOT = sys.argv[1]
LAT = TTFont('/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf')
JP = TTFont('/usr/share/fonts/opentype/noto/NotoSansCJK-Bold.ttc', fontNumber=0)

def f(v): 
    s = ("%.2f" % v).rstrip('0').rstrip('.')
    return s if s not in ('-0', '') else '0'

def glyph(font, ch, size, cx, cy):
    gs = font.getGlyphSet(); name = font.getBestCmap()[ord(ch)]
    bp = BoundsPen(gs); gs[name].draw(bp); x0, y0, x1, y1 = bp.bounds
    s = size / font['head'].unitsPerEm
    mx, my = (x0 + x1) / 2, (y0 + y1) / 2
    pen = SVGPathPen(gs, ntos=f)
    tp = TransformPen(pen, (s, 0, 0, -s, cx - s * mx, cy + s * my))
    gs[name].draw(tp)
    return pen.getCommands()

def hexp(R, cx=54, cy=54):
    pts = [(cx, cy - R), (cx + .866 * R, cy - R / 2), (cx + .866 * R, cy + R / 2), (cx, cy + R), (cx - .866 * R, cy + R / 2), (cx - .866 * R, cy - R / 2)]
    return "M" + "L".join(f(x) + "," + f(y) for x, y in pts) + "Z"

def star(x, y, rot=0, r=4.5):
    out = []
    for a in (90, 30, -30):
        t = math.radians(a + rot)
        dx, dy = r * math.cos(t), r * math.sin(t)
        out.append("M%s,%sL%s,%s" % (f(x - dx), f(y - dy), f(x + dx), f(y + dy)))
    return "".join(out)

def keyhole(cx=54, cy=51):
    r = 6
    circle = "M%s,%sa%s,%s 0,1 0,%s,0a%s,%s 0,1 0,-%s,0Z" % (f(cx - r), f(cy), r, r, 2 * r, r, r, 2 * r)
    stem = "M%s,%sL%s,%sL%s,%sL%s,%sZ" % (f(cx - 2.8), f(cy + 3.5), f(cx + 2.8), f(cy + 3.5), f(cx + 5.5), f(cy + 15.5), f(cx - 5.5), f(cy + 15.5))
    return circle + stem

BG, L1, L2, L3, L4, GOLD, MID, PALE = "#2F2A6B", "#C8C1FF", "#8F83F0", "#5B4BD6", "#1B1545", "#FFC857", "#5B4BD6", "#E3DFFF"
R = [44, 36, 28, 20]
outer = [(71.3, 24, 10), (88.6, 54, 0), (71.3, 84, -8), (36.7, 84, 12), (19.4, 54, 0), (36.7, 24, -10)]
mid = [(54, 22, 6), (81.7, 38, 0), (81.7, 70, -6), (54, 86, 0), (26.3, 70, 8), (26.3, 38, 0)]
corners = [("Ж", LAT, 14, 24), ("あ", JP, 94, 24), ("ع", LAT, 14, 94), ("Ω", LAT, 94, 94)]

# ---------- Compose data ----------
def kq(s): return '"' + s + '"'
kt = ["package com.babelmanager.app.core.ui", "", "// GERADO por tools/gen_icon.py: geometria do ícone do Babel Manager (coordenadas 0..108). Não editar à mão.", "internal object BabelMarkData {"]
kt.append("    val rings = listOf(" + ", ".join(kq(hexp(r)) for r in R) + ")")
kt.append("    val outerStars = listOf(" + ", ".join("Star(%s, %sf, %sf)" % (kq(star(x, y, rot)), f(x), f(y)) for x, y, rot in outer) + ")")
kt.append("    val midStars = listOf(" + ", ".join("Star(%s, %sf, %sf)" % (kq(star(x, y, rot)), f(x), f(y)) for x, y, rot in mid) + ")")
kt.append("    val keyhole = " + kq(keyhole()))
kt.append("    val glyphs = listOf(" + ", ".join("Glyph(%s, %sf, %sf)" % (kq(glyph(ft, ch, 13, cx, cy)), f(cx), f(cy)) for ch, ft, cx, cy in corners) + ")")
kt.append("    class Star(val path: String, val x: Float, val y: Float)")
kt.append("    class Glyph(val path: String, val x: Float, val y: Float)")
kt.append("}")
open(ROOT + "/app/src/main/java/com/babelmanager/app/core/ui/BabelMarkData.kt", "w").write("\n".join(kt) + "\n")

# ---------- Adaptive foreground (cantos -> letras no anel externo) ----------
S = 0.78
ring_letters = [("Ж", LAT, 71.3, 24), ("あ", JP, 36.7, 24), ("ع", LAT, 71.3, 84), ("Ω", LAT, 36.7, 84)]
fg = ['<?xml version="1.0" encoding="utf-8"?>',
      '<!-- GERADO por tools/gen_icon.py. Galeria hexagonal de Babel: anéis, fechadura, asteriscos e letras de alfabetos diferentes. -->',
      '<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">',
      '    <group android:scaleX="%s" android:scaleY="%s" android:pivotX="54" android:pivotY="54">' % (S, S)]
def fpath(d, color): fg.append('        <path android:fillColor="%s" android:pathData="%s" />' % (color, d))
def spath(d, color): fg.append('        <path android:strokeColor="%s" android:strokeWidth="2" android:strokeLineCap="round" android:pathData="%s" />' % (color, d))
for r, c in zip(R, (L1, L2, L3, L4)): fpath(hexp(r), c)
fpath(keyhole(), GOLD)
spath("".join(star(x, y, rot) for x, y, rot in outer if x in (88.6, 19.4)), MID)
spath("".join(star(x, y, rot) for x, y, rot in mid), PALE)
for ch, ft, cx, cy in ring_letters: fpath(glyph(ft, ch, 9.5, cx, cy), BG)
fg += ['    </group>', '</vector>']
open(ROOT + "/app/src/main/res/drawable/ic_launcher_foreground.xml", "w").write("\n".join(fg) + "\n")

# ---------- Monocromático ----------
def ring_outline(R_): return hexp(R_)
mono = ['<?xml version="1.0" encoding="utf-8"?>',
        '<!-- GERADO por tools/gen_icon.py. Versão de uma cor (ícone temático do Android 13+): dois hexágonos e a fechadura. -->',
        '<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">',
        '    <group android:scaleX="%s" android:scaleY="%s" android:pivotX="54" android:pivotY="54">' % (S, S),
        '        <path android:strokeColor="#000000" android:strokeWidth="3.5" android:strokeLineJoin="round" android:pathData="%s" />' % hexp(40),
        '        <path android:strokeColor="#000000" android:strokeWidth="3.5" android:strokeLineJoin="round" android:pathData="%s" />' % hexp(28),
        '        <path android:fillColor="#000000" android:pathData="%s" />' % keyhole(),
        '    </group>', '</vector>']
open(ROOT + "/app/src/main/res/drawable/ic_launcher_monochrome.xml", "w").write("\n".join(mono) + "\n")
print("ok")
