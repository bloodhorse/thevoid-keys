#!/usr/bin/env python3
"""read bekh's flick log off the phone and find where the keyboard misread him.

    python3 tools/flicks.py                 # pull from the phone, report
    python3 tools/flicks.py --file f.jsonl  # report on a pulled log
    python3 tools/flicks.py --tail 30       # the last 30 touches, one line each

a misfire is read from his corrections: a touch, a backspace within 2.5s, then another touch on
the same key that came out different. the first touch is what the keyboard got wrong, the second
is what he meant.
"""
import argparse, json, math, subprocess, sys
from collections import Counter

PKG = "com.bekh.thevoid.keys.debug"
DEL = "DeleteKeyAction"
FIX_WINDOW_MS = 2500
DIRS = ["down", "down-right", "right", "up-right", "up", "up-left", "left", "down-left"]


def pull(serial):
    a = ["adb"] + (["-s", serial] if serial else [])
    out = subprocess.run(a + ["shell", f"run-as {PKG} sh -c 'cat files/flicks.old.jsonl files/flicks.jsonl 2>/dev/null'"],
                         capture_output=True, text=True).stdout
    return out


def default_serial():
    devs = subprocess.run(["adb", "devices"], capture_output=True, text=True).stdout.split("\n")[1:]
    ids = [l.split()[0] for l in devs if l.strip().endswith("device")]
    for want in ("P2239F000625", "100.96.66.79:5555"):
        if want in ids:
            return want
    return ids[0] if ids else None


def shape(r):
    d = r["dp"]
    pts = [(t, x / d, y / d) for t, x, y in r["pts"]]
    far = max(pts, key=lambda p: p[1] ** 2 + p[2] ** 2)
    end = pts[-1]
    travel = math.hypot(far[1], far[2])
    speed = 0.0
    for i in range(len(pts)):
        for j in range(i + 1, len(pts)):
            dt = pts[j][0] - pts[i][0]
            if dt >= 16:
                speed = max(speed, math.hypot(pts[j][1] - pts[i][1], pts[j][2] - pts[i][2]) / dt)
                break
    ang = math.degrees(math.atan2(far[1], far[2])) % 360
    r["travel"] = travel
    r["end"] = math.hypot(end[1], end[2])
    r["speed"] = speed * 1000
    r["dur"] = end[0]
    r["dir"] = DIRS[int(((ang + 22.5) % 360) // 45)] if travel > 0.5 else "-"
    r["ang"] = ang
    r["min_dp"] = r["min"] / d
    r["flick"] = r["out"] != r["key"]
    return r


def misfires(rs):
    found = []
    for i in range(len(rs) - 2):
        a, b, c = rs[i], rs[i + 1], rs[i + 2]
        if a["key"] in (DEL,) or b["out"] != DEL or c["key"] != a["key"]:
            continue
        if c["t"] - a["t"] > FIX_WINDOW_MS or c["out"] == a["out"]:
            continue
        kind = ("tap, meant flick" if not a["flick"] and c["flick"] else
                "flick, meant tap" if a["flick"] and not c["flick"] else "wrong direction")
        found.append((kind, a, c))
    return found


def line(r):
    return (f"{r['key']:>4} -> {r['out']:<14} {r['path']:<6} travel {r['travel']:5.1f}dp  "
            f"speed {r['speed']:5.0f}dp/s  {r['dur']:4.0f}ms  {r['dir']}")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--file")
    ap.add_argument("--serial")
    ap.add_argument("--tail", type=int)
    o = ap.parse_args()
    raw = open(o.file).read() if o.file else pull(o.serial or default_serial())
    rs = [shape(json.loads(l)) for l in raw.splitlines() if l.strip()]
    if not rs:
        sys.exit("no touches logged yet")
    if o.tail:
        for r in rs[-o.tail:]:
            print(line(r))
        return

    letters = [r for r in rs if len(r["key"]) == 1]
    taps = [r for r in letters if not r["flick"]]
    flicks = [r for r in letters if r["flick"]]
    print(f"{len(rs)} touches, {len(letters)} on letter keys: {len(taps)} taps, {len(flicks)} flicks; "
          f"flick threshold {rs[-1]['min_dp']:.0f}dp ({rs[-1]['min']}px)\n")

    def hist(name, xs, edges):
        c = Counter()
        for x in xs:
            c[next((e for e in edges if x < e), "more")] += 1
        print(name)
        lo = 0
        for e in edges + ["more"]:
            n = c[e]
            lab = f"{lo:>3}-{e}" if e != "more" else f"{lo:>3}+   "
            print(f"  {lab:<8} {'#' * min(n, 60)} {n}")
            lo = e if e != "more" else lo

    edges = [2, 4, 6, 8, 10, 14, 18, 24, 30, 40, 60]
    hist("taps, how far the thumb went (dp):", [r["travel"] for r in taps], edges)
    hist("flicks, how far the thumb went (dp):", [r["travel"] for r in flicks], edges)

    m = misfires(rs)
    print(f"\nmisfires he corrected: {len(m)}")
    for kind, n in Counter(k for k, _, _ in m).most_common():
        print(f"  {n:>3}  {kind}")
    for kind, a, c in m[-15:]:
        print(f"  {kind:<17} got  {line(a)}")
        print(f"  {'':<17} meant {c['out']!r}, his redo went {c['travel']:.0f}dp {c['dir']}")

    missed = [a for k, a, _ in m if k == "tap, meant flick"]
    if missed and taps:
        clean = [r for r in taps if r not in missed]
        print("\nwhat a rule would do (missed flicks rescued / honest taps broken):")
        best = []
        for dist in (4, 5, 6, 8, 10, 12):
            for spd in (0, 100, 150, 200, 300, 400):
                if spd == 0 and dist < 8:
                    continue
                def rule(r):
                    return r["travel"] >= dist and r["speed"] >= spd
                saved = sum(rule(r) for r in missed)
                broke = sum(rule(r) for r in clean)
                best.append((saved - 3 * broke, dist, spd, saved, broke))
        for _, dist, spd, saved, broke in sorted(best, reverse=True)[:6]:
            sp = f" and faster than {spd}dp/s" if spd else ""
            print(f"  flick if it went {dist}dp{sp}: {saved}/{len(missed)} rescued, {broke} taps broken")


main()
