# Zytale playable boundary, 2026-10-22

The exact saved-world coordinates and the derived playable mask are in `playable-boundary-2026-10-22.json` (SHA-256 `72e9ab60bdc8d751f8858433458b3e621d8222c9c5365dca2ed978b2489165ce`). This is project context for **The Village - Zytale 306 Working Copy**.

- Marker: `Rock_Marble_Brick_Smooth`. All 16 region files were copied from a consistent closed-game save snapshot and scanned across 9,118 stored chunks.
- Scanned 2,291 marker blocks in 1,714 distinct X/Z columns. The outer contour uses 2,210 actual blocks in 1,633 X/Z columns. Eighty-one blocks of the same ID are unrelated interior markers.
- Four absent cells on the southern Z=270 run are treated as part of the continuous perimeter by the user's explicit instruction: `(-77,270)`, `(-75,270)`, `(-35,270)`, `(-33,270)`. They are in `virtualBridgeXZ`, separate from actual saved blocks.
- Bounds: X `-166..278`, Z `-72..275`. The closed contour and interior comprise 118,799 playable X/Z columns, of which 117,162 are strictly interior.
- `playableRowSpans` is the authoritative compact mask: each row is `[Z, [[XStart,XEnd], ...]]`, with inclusive endpoints. Include all Y for every listed X/Z column, without an upper or lower limit.
- The snapshot and mask hashes, source region hashes, marker coordinates including Y, and remaining same-ID blocks are in the JSON. The mask was independently reconstructed from row spans and byte-compared with the derived binary mask.
- The prior screenshot and legacy playable hulls are visual/history references only. Rescan after boundary blocks change.
