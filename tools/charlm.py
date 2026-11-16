#!/usr/bin/env python3
"""build the character model the keyboard asks when a flick lands on an edge.

    python3 tools/charlm.py                       # download the word list, write the asset
    python3 tools/charlm.py --words count_1w.txt  # from a list you already have

the source is Peter Norvig's count_1w.txt (norvig.com/ngrams): the 333,333 most common words of
Google's trillion-word web corpus, with their counts. every word is read as "^^^word", weighted by
its count, and the model is P(next letter | the last three letters of the word so far), "^" where
the word hasn't got three yet. it never looks across a space: the keyboard cuts its context at the
last non-letter too.

the asset (app/src/main/assets/charlm.bin), little-endian:
    "CLM1"
    729 x 26 bytes   the 3-gram: context id a*27+b, symbols 0 = "^", 1..26 = a..z
    u32 n            how many 4-gram contexts are kept
    n x u16          their ids, a*729+b*27+c, ascending
    n x 26 bytes     their rows
every byte is round(-log2(p) * 8), capped at 255. a 4-gram context that isn't kept falls back to
the 3-gram row of its last two symbols. each order is mixed with the one below it (EPS of the lower
order), so nothing is ever impossible, just very unlikely.
"""
import argparse, math, os, struct, sys, urllib.request
from collections import defaultdict

URL = "https://norvig.com/ngrams/count_1w.txt"
EPS = 0.02
KEEP_SHARE = 2e-7  # a 4-gram context is kept when it carries this share of all letters typed
OUT = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "assets", "charlm.bin")
A = 27


def quant(p):
    return min(255, round(-math.log2(p) * 8))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--words")
    ap.add_argument("--out", default=OUT)
    args = ap.parse_args()
    path = args.words
    if not path:
        path = os.path.join(os.path.expanduser("~/.cache"), "charlm-count_1w.txt")
        if not os.path.exists(path):
            os.makedirs(os.path.dirname(path), exist_ok=True)
            urllib.request.urlretrieve(URL, path)

    c1 = [0.0] * 26
    c3 = defaultdict(lambda: [0.0] * 26)
    c4 = defaultdict(lambda: [0.0] * 26)
    for line in open(path):
        w, n = line.split("\t")
        if not w.isascii() or not w.isalpha():
            continue
        n = float(n)
        s = [0, 0, 0] + [ord(ch) - 96 for ch in w.lower()]
        for i in range(3, len(s)):
            x = s[i] - 1
            c1[x] += n
            c3[s[i - 2] * A + s[i - 1]][x] += n
            c4[s[i - 3] * A * A + s[i - 2] * A + s[i - 1]][x] += n

    total = sum(c1)
    p1 = [(1 - EPS) * c / total + EPS / 26 for c in c1]

    def mix(row, lower):
        t = sum(row)
        if t == 0:
            return lower
        return [(1 - EPS) * c / t + EPS * l for c, l in zip(row, lower)]

    p3 = [mix(c3.get(i, [0.0] * 26), p1) for i in range(A * A)]
    kept = sorted(i for i, row in c4.items() if sum(row) >= total * KEEP_SHARE)

    with open(args.out, "wb") as f:
        f.write(b"CLM1")
        for row in p3:
            f.write(bytes(quant(p) for p in row))
        f.write(struct.pack("<I", len(kept)))
        f.write(struct.pack(f"<{len(kept)}H", *kept))
        for i in kept:
            f.write(bytes(quant(p) for p in mix(c4[i], p3[i % (A * A)])))
    size = os.path.getsize(args.out)
    print(f"{len(c4)} 4-gram contexts seen, {len(kept)} kept; {size // 1024} KB -> {os.path.normpath(args.out)}")


if __name__ == "__main__":
    sys.exit(main())
