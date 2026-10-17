# Atmosphere Tool 2.0.1 — Creative Fullbright

Adds one English **Global Fullbright (Creative)** slider above Night lighting.
0 is native normal lighting; 100 is the Creative tools' Fullbright. Values in
between use the game's existing minimum-light curve. No server lighting-command
control is included. The approved fog shaders are byte-identical to 2.0.0.

## Native integration

The inspected Windows Hytale 0.7.2 Creative callback selects a minimum of 0.05
when disabled and 0.5 when enabled. The native renderer builds 16 light levels:
`minimum + (1 - minimum) * pow(level / 15, 1.5)`.

The component redirects two verified world-update calls at startup, in memory
only. Its leaf x64 dispatch retains the original managed caller's return address
and receiver, and tail-jumps to either the ordinary update or the native Creative
setter. It never holds a managed pointer between calls or invokes managed code
from the file-reading or OpenGL thread. The setter stops/restarts the native
meshing workers and invalidates rendered chunks exactly as the Creative callback
does. One mesh-result update is deferred to the next frame when applying a value.

The slider publishes a 20-byte atomic local request. A 500 ms quiet period
coalesces dragging before one native rebuild. Keeping a value unchanged adds no
continuous rebuild, new shader pass, pixel work or texture allocation. A brief
native chunk rebuild can still occur when a value is applied; no game FPS claim
is made. Saved settings are per world, with -1 meaning no override for old saves.
An independent lease releases only a still-owned value. A subsequent native
Creative checkbox change takes precedence. The checkbox remains boolean; the
tool reads the actual lighting minimum rather than rewriting settings.json.

The executable SHA-256 must be
`0406a4c3cfef099ef2dff318cb889726d9c9883a0c12007fdda0563e93a0f80e`.
This is an exact-build native integration, not a public Hytale plugin API.
HytaleClient.exe is not modified on disk or redistributed. The usual local
singleplayer and manual-install restrictions still apply.

## Verification

- Java regression: native status, absent-component rejection, pending slider
  values, range checks, persistence, world change and lease shutdown passed.
- Executed x64 dispatcher against isolated native fixtures: all 101 levels,
  original float argument and receiver, 10,000 unchanged frames with no rebuild,
  native toggle precedence, restoration and new-world baseline passed.
- Native file-reader debounce, no duplicate publication and expiry passed.
- Six native fog shader variants and isolated WGL integration passed, zero GL
  errors; startup image and unchanged-mask handling preserved.
- Existing style/scaling and automatic quality regressions passed. These GPU
  tests are isolated workloads, not in-game FPS or weaker-hardware certification.
- Hytale 0.7.2 / Java 25 isolated JAR boot, one plugin registration, aliases and
  graceful shutdown passed. Test run: `smoke-97057b7cd7a94db7b9fcc4d28d1ca9c2`.
- Windows PowerShell installer fixtures passed fresh/repeat/upgrade/restore,
  injected failure rollback and rejection checks. ZIP CRC/inventory/hashes pass.
- Installed on 2026-10-17 after the user closed Hytale. All four payload hashes
  matched and all 11 saved Atmosphere JSON files stayed unchanged.
- **The Creative adapter's live in-game behavior and visual result still need
  client verification.** Isolated dispatch tests do not establish NativeAOT
  runtime/GC behavior or long-duration stability inside the real game.

The initial native fixture accidentally placed a required protection change
inside a disabled C assert; it failed before executing the dispatch. Assertions
are now explicitly enabled in that fixture and the complete suite passes.
A smoke-test regex also still expected 2.0.0; it was corrected and boot rechecked.

## Artifacts

ZIP: `dist/Atmosphere_Tool-2.0.1-Windows.zip` (289,878 bytes).
SHA-256: `26039ffbd688c5d45884677994ad9829a1bbcc8cbdf4282c8fefc498e2c82c99`.

JAR: `ecfc9400319b597d47afaf2b21848685bcd889a19d8654145aa9ba7d6a385be4`.
DLL: `92afc0db9b719d1a1258dbf81e10301203752c23e2ffc4a80f41c54188ca71ef`.

Installation backup:
`UserData/AtmosphereToolBackups/20261017-024408-3209ef685ad849f490c42532f9ee74ff`.
Close Hytale and use the package's Restore.cmd to restore 2.0.0.
