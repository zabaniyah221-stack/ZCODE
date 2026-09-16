import sys, time, subprocess
from PIL import ImageGrab

# usage: blink2.py <title-substr> <seconds> <fps> <out-prefix>
# Ukur fraksi piksel near-white full-frame + separuh kanan per frame.
# Untuk blink <2ms di bawah resolusi sampling 13fps: hasil 0% = LEMAH,
# jangan dikutip sebagai bukti hilang.
title, secs, fps, prefix = sys.argv[1], float(sys.argv[2]), float(sys.argv[3]), sys.argv[4]

def win_geom(pat):
    out = subprocess.check_output(['wmctrl', '-lG']).decode()
    for ln in out.splitlines():
        if (' ' + pat) in ln or ln.endswith(' ' + pat):
            p = ln.split(None, 7)
            return tuple(map(int, p[2:6]))
    return None

g = None
for _ in range(60):
    g = win_geom(title)
    if g: break
    time.sleep(0.5)
if not g:
    print('NOWINDOW'); sys.exit(1)
x, y, w, h = g
print('geom', g, flush=True)

def fracs(im):
    w2, h2 = im.size
    px = im.load()
    tot = wh = 0
    rtot = rwh = 0
    for yy in range(0, h2, 4):
        for xx in range(0, w2, 4):
            tot += 1
            r, gg, b = px[xx, yy]
            if r > 235 and gg > 235 and b > 235:
                wh += 1
            if xx >= w2 // 2:
                rtot += 1
                if r > 235 and gg > 235 and b > 235:
                    rwh += 1
    return wh / max(1, tot), rwh / max(1, rtot)

t0 = time.time()
dt = 1.0 / fps
n = int(secs * fps)
maxf = maxr = -1.0
for i in range(n):
    im = ImageGrab.grab(bbox=(x, y, x + w, y + h)).convert('RGB')
    f, r = fracs(im)
    if i % 13 == 0:
        print('frame %03d full=%.2f%% right=%.2f%%' % (i, f * 100, r * 100), flush=True)
    if f > maxf:
        maxf = f
        im.save(prefix + '_worst_full.png')
    if r > maxr:
        maxr = r
        im.save(prefix + '_worst_right.png')
    time.sleep(max(0, dt - (time.time() - t0 - i * dt)))
print('MAXFULL=%.2f%% MAXRIGHT=%.2f%%' % (maxf * 100, maxr * 100), flush=True)
