# Atmosphere Tool 2.0.0 — 2026-10-15

The user approved the appearance of 2.0.0-preview.2 as excellent. This release preserves both GLSL files byte for byte, packages the supported local installation, and adds the requested transparency slider. It does not claim new visual acceptance of the slider in a live game session.

## Installation

Extract `Atmosphere_Tool-2.0.0-Windows.zip`, save and close Hytale, then run `Install.cmd`. The folder contains:

```
Atmosphere-Tool-2.0.0.jar
Atmosphere_Tool_Client_Component/
  opengl32.dll
  AtmosphereClient/
    fog-volume.glsl
    fog-post.glsl
Install.cmd / Install.ps1
Restore.cmd / Restore.ps1
README.txt
package.json
```

The client folder has the requested distributable name. Windows requires the installed loader filename `Client/opengl32.dll`. The installer places the authored shaders under `Client/AtmosphereClient` and the plugin under the standard UserData/Mods directory. The game executable is neither changed nor redistributed. Standard per-user installation requires no administrator account or development tools.

The installer verifies payload SHA-256 hashes and the exact supported Hytale executable; refuses a running game, unknown loader or unknown earlier plugin; and backs up recognized preview.1/preview.2 installations outside scanned Mods directories. A failed copy rolls back the transaction. Repeating installation preserves the original rollback receipt. `Restore.cmd` restores the previous files, or removes this installation on a fresh system, preserving saves. Do not keep duplicate plugin versions in world-specific Mods folders. Other versions and nonstandard UserData locations require manual preparation; `-GameRoot` supports a different game directory.

The actual local installation completed with all four payload hashes verified and eight saved AtmosphereTool JSON files unchanged. Its backup is recorded in `%APPDATA%/Hytale/UserData/AtmosphereClient/installation.json` and under `UserData/AtmosphereToolBackups`.

## Transparency and update cost

The English area menu has **Transparency % (0 = full fog, 100 = invisible)** directly below Density, using the same slider control. It is independent of density, saved per area, and defaults to zero when absent from older data. The existing command remains for compatibility; normal editing uses the slider. No graphics/quality controls were added.

When only transparency, color, density or style changes, the bridge retains the existing mask and active lease, replaces its 808-byte parameter header in a cached packet, and atomically publishes it. Rapid changes coalesce at the bridge's 500 ms cadence. The worker still writes the complete packet; this is not an 808-byte-only disk transfer. The client hashes the mask on its reader thread and skips GPU texture upload when mask bytes and dimensions are unchanged. Geometry, terrain edits, OFF, deletion and world changes retain normal invalidation rules. A rejected worker revision cannot mark unpublished occupancy as current.

## Texture and rendering efficiency

The approved continuous volume renderer uses one shared 32³ R16F noise texture, initialized once: **64 KiB GPU storage**. Material functions sample it continuously in space and time. No animation atlases, particles, animated cubes, runtime Common asset registration or per-frame terrain publication are used. Obsolete particle textures and descriptors are omitted from the release JAR, reducing it from 831,096 to **197,050 bytes**. The ZIP is **283,183 bytes**, including the native component and installer.

The 14 styles, independent RGB, air-only mask, selection clipping and scene-depth occlusion remain as documented in [preview.2](preview2-design.md). The GPU timing controller, 32–128 ray samples, resolution cap, bounded terrain scanning and asynchronous worker also remain. A timing target is not a frame-time guarantee. RTX 5060 tests do not prove performance on slower GPUs. The documented 16-area, 4,194,304-cell combined bounding grid and 1,024-cell axis limits still apply. Widely separated areas consume the intervening grid.

Caching the prior packet adds up to 16 MiB of Java memory. During an occupancy rebuild the main arrays can coexist at roughly 56 MiB at the cell limit, excluding JVM, I/O and driver overhead. GPU terrain mask storage is at most 8 MiB; client float packet buffers can temporarily occupy two 16 MiB allocations. These are limits on this renderer's data, not total game memory.

## Distribution through the mod browser

**The complete installation cannot currently be performed by the in-game mod browser.** The JAR is a conventional server plugin with assets, but the approved fog renderer requires this native client component. Bundling a DLL inside a ZIP or JAR does not cause the game to load it. The package explicitly declares `modBrowserCompleteInstall: false` and `clientComponentRequired: true`.

Hytale's [official modding strategy](https://hytale.com/news/2025/11/hytale-modding-strategy-and-status) does not support client modifications. CurseForge's [installation instructions](https://support.curseforge.com/support/solutions/articles/9000273615-install-hytale-mods) concern normal Mods-directory packages. The browser exists, but does not provide the native client loader used here. No listing was submitted or accepted. Any marketplace listing must disclose the separately installed component and independently verify platform acceptance; this release must not be described as a one-click browser installation.

Compatibility is limited to Windows x64, the validated Hytale 0.7.2 executable fingerprint and local singleplayer. The bridge reads local files; installing the DLL on remote players' computers does not supply a multiplayer transport. Future game updates require renewed compatibility checks. An unrelated OpenGL proxy cannot be overwritten or assumed compatible.

## Verification

- Java regressions: schema/default/copy/save behavior, bounds, air mask, wire header, full volume coverage and settings-only body preservation.
- Native temporary world: repeated transparency changes retain the active lease, final transparency reaches the packet without changing occupancy, terrain edits/OFF/deletion/shutdown work, and no Common asset lifecycle operations occur.
- Production JAR: Hytale 0.7.2 / Java 25 startup, one plugin registration, commands and clean shutdown.
- GPU: 14 style render/animation comparisons, transparency semantics, 32 stress cases and reduced-budget image comparisons; six native post-shader variants compile.
- DLL integration: reconstructed camera/depth, disabled and expired sessions, existing timer-query compatibility, adaptive budget, transparency changes with no new mask upload, zero OpenGL errors.
- Windows PowerShell 5 fixture: fresh and repeated installation, upgrade, restore, injected mid-copy failure rollback, rejection of unknown loader/corrupt payload/wrong executable/running game, saved-data preservation.
- ZIP: exact ten-file inventory, CRC and payload hashes; no game executable, extracted game shaders, test libraries or obsolete particle assets.

Local evidence: `build/release-build.log`, `build/release-native.log`, `build/release-production.log`, `build/distribution-validation.json`, `build/installer-validation.json`, `build/local-install-validation.json`. Test fixtures contain no real worlds. The release UI still requires normal in-game use to confirm its presentation.

## Release hashes

| File | SHA-256 |
| --- | --- |
| ZIP | `333733665222aed07acf0d8b8655b2fe4149034553d0652b921ba2efeee826d4` |
| JAR | `a8134b331bd018dfc60cd5cb253acceeaa060331f5b78581d01a7a2a7478e79c8` |
| opengl32.dll | `22a3fadbec881a91a668f669437ddd21a229ebee4300aaf2a78cff2fc9fe2c1c` |
| fog-volume.glsl | `946408d9c64253e613e5afe7bc627e06e9bb02b034f492e1b4534047b8746463` |
| fog-post.glsl | `e2552492ec247b14920e118619588e4ce8df9b162961c7650256417b90537b7c` |

Build: `build-volume.ps1`, native tests: `smoke-client-volume.ps1 -RenderProbe` and without that switch; bundle: `packaging/build_distribution.py`; installer tests: `powershell.exe -NoProfile -ExecutionPolicy Bypass -File packaging/installer_fixture_test.ps1`. The execution-policy flag applies only to the launched process. Previous preview scripts are historical; use the package installer for this release.
