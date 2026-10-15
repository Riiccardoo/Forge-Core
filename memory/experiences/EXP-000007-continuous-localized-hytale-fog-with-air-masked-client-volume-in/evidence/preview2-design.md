# Atmosphere Tool 2.0.0-preview.2

This update retains the volumetric client backend visually approved by the user in preview.1. It changes materials, adds independent area transparency, translates the interface to English, and bounds CPU/GPU work more tightly. No controls have been added to the menu, following the user's correction.

## Appearance

The persistent 3-D noise texture is still created once. New material functions define broad structure, fine detail, positive background density, height distribution, soft shading and time-continuous flow. Bounds and the air/solid mask do not move.

| Style | Structure and movement |
| --- | --- |
| Natural | Rounded banks with gentle drift |
| Mist | Long horizontal ribbons carried sideways |
| Dense | Broad, heavy banks with slow rolling motion |
| Uniform | Smooth veil with very subtle internal drift |
| Low | Broad lower banks with gradual height falloff |
| Ground | Shallow streams concentrated near the area's bottom |
| Deep | Tall, heavy columns rising slowly |
| Cold | Thin, wind-sheared waves |
| Ethereal | Delicate elongated wisps |
| Organic | Rounded, smoothly deforming lobes |
| Turbulent | Smaller, faster eddies |
| Arcane | Crossing undulating veils |
| Miasma | Curled rising billows and a heavier lower bank |
| Gothic | Narrow vertical curtains drifting downward |

RGB remains independent of style, including intentional black. Existing style IDs (`green`, `red`, `violet`) are preserved for compatibility, with English display names Organic, Turbulent and Arcane. User-created names are never translated.

## Transparency without menu additions

Select the area and keep the selection active (use **Observe** rather than Close), then enter:

`/atmo mist transparency 50`

The range is 0–100: 0 retains full fog; 100 makes it invisible. Density, color, shape, bounds and ON/OFF state are retained. This setting applies only to the selected area and is saved automatically. Creative mode is required.

Transparency caps the area's integrated optical coverage: `coverage = (1 - transparency/100) * (1 - exp(-opticalDepth))`. It does not simply multiply density. The per-step ratio of capped transmittance also handles overlapping areas. Wire format size and version remain compatible: the formerly unused color alpha component carries this opacity ceiling. Schema 2 has an additive `transparency` integer; missing values default to zero. Full-opacity rays may stop at the existing 0.8% residual-transmittance threshold.

## Scalability and measured limits

- No particles, entities, asset hot reloads or per-frame terrain rebuilds.
- GPU budget adjusts automatically from asynchronous timing queries of the final post-process pass. It uses a smoothed average, a 1.4–2.4 ms hysteresis range, changes by eight samples at most every 30 collected measurements, and stays between 32 and 128 maximum samples per ray. Resolution also caps the budget. Sampling distance increases as the budget decreases. Animation time is never stepped or paused.
- Existing game/profiler timer queries are respected. Unavailable results are polled without a blocking wait. If timing is unavailable, the conservative initial budget and resolution cap remain active. The timing target is a controller threshold, not a guaranteed frame time: the minimum budget can still exceed it.
- Areas which do not intersect a pixel's view ray, or have 100% transparency, are excluded before material evaluation. Opaque rays stop early. Fine texture and selected bounds remain the same at every budget; lower budgets approximate optical integration less precisely.
- Sampling uses a stable stride and a shortened final segment, avoiding a simultaneous repositioning of all samples whenever the visible length crosses an integer step count.
- Terrain scanning yields after approximately 2 ms or 32,768 visited cells per pass, whichever comes first; passes occur every 500 ms. Initial generation and detection of terrain edits can therefore take longer on slower CPUs. Distance-field construction runs on a worker, not the world thread. A stable volume is not republished for animation or player movement.
- Existing hard limits remain: 16 active areas, 4,194,304 cells in their combined bounding grid, dimensions up to 1,024 cells. Sparse, widely separated areas can exceed the combined-grid limit; there is no silent cropping. This is not an unlimited-world or remote-multiplayer solution.
- Maximum mask payload is 16 MiB plus an 808-byte header; GPU R16F mask is at most 8 MiB and noise is 64 KiB. The Java scan/distance/encoded arrays can coexist at about 40 MiB for the maximum volume, excluding JVM, temporary I/O and driver overhead. The client pending float payload is up to 16 MiB, with a transient second read buffer possible.

Isolated tests on the installed RTX 5060 (not Hytale FPS):

| Scene | 1080p, 160 samples/.55 spacing | 1080p, automatic minimum 32/2.25 | 4K, automatic minimum |
| --- | ---: | ---: | ---: |
| One area, view from inside | 2.56 ms | 0.71 ms | 2.62 ms |
| 16 fully overlapping areas, varied styles, 90% transparency | 37.39 ms | 9.16 ms | 35.56 ms |

The extreme overlap case remains expensive even after the reduction. Lower-powered GPUs have not been physically tested; no minimum FPS guarantee is made. The controls bound work and respond to measured load, but do not make high resolution and arbitrary overlap free. The recorded test is useful for comparing budgets on this machine, not extrapolating precise FPS to other hardware or other mods.

## Validation

- Java regression: old-schema loading, new-field copying/invalidation, input bounds, save/reload, preserved density/user name, wire alpha, solid exclusion, full selection coverage, maximum payload. Maximum 4M-cell encoding measured 83.59 ms on the worker in this run.
- 14 styles rendered at density 10 in the same scene and tint; the closest image pair has RMSE 0.01096. All have continuous animation; maximum frame difference at 60 Hz remains below 0.007 in the test. These are numerical checks plus inspection of the contact sheet, not a claim about every possible camera/scene.
- One-area 50% opacity matches half of full premultiplied coverage within 0.0000035 in a non-truncated case; density reduction produces a different result. Transparent and overlapping-area cases pass.
- Reduced-budget rendering compared with 160 samples: worst tested RGBA RMSE 0.00413. Tests cover outside, inside and distant views.
- 32 GPU stress timings cover 720p, 1080p, 1440p and 4K, single-area and extreme-overlap cases.
- Six actual native post-shader variants compile on the driver. Isolated DLL integration passes camera reconstruction, depth occlusion, disabled/expired session, adaptive budget, existing profiler query compatibility and zero GL errors.
- Native temporary world confirms full selection, solid edits, stable publication, color/style/transparency edits, OFF/delete/shutdown and no Common asset registration/removal. Production JAR boots and shuts down cleanly.

Evidence is stored in `docs/evidence/2.0.0-preview.2`. `styles-density10.jpg` is an isolated shader-rendered test scene, not gameplay footage. Final artistic judgment of this update still belongs to the next in-game session.

## Installation and rollback

`upgrade-client-volume.ps1` updates only the verified preview.1 installation with Hytale closed, checks the exact executable and tested package hashes, backs up JAR/DLL/shaders and the current area file, and verifies that the save remains unchanged. It rolls back partial installation on failure. The prior approved build is kept under the workspace's `backups/atmosphere-before-2.0.0-preview.2` directory.

`rollback-preview2.ps1` restores the approved preview.1 JAR and client files while preserving current saved worlds and areas. The older `install-client-volume.ps1` and `rollback-client-volume.ps1` are historical scripts for the original transition from 1.3.12; do not use them for this update.

Compatibility remains Windows Hytale 0.7.2, validated executable build, local singleplayer transport. The executable itself is not patched; shader resources extracted for local compilation tests are not distributed.
