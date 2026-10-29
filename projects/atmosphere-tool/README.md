# Atmosphere Tool Native 2.4.0

Global atmosphere editing for **Hytale 0.7.4**, using a Java server plugin and
ordinary UI/item assets. No client component is required.

Features: global fog ON/OFF (including backdrop), fog density/transparency/color/
brightness, independent moon transparency and size, sky angle/height, night
lighting, exact numeric inputs, and named custom atmosphere saves.
Saves cover global settings only. Zones, global Fullbright and the old localized
client-volume renderer are archived and excluded.

See [installation and controls](docs/INSTALLATION_EN.txt),
[provenance](docs/PROVENANCE_EN.txt), and [final client check](docs/VISUAL_TEST_EN.txt).
License: [All Rights Reserved](LICENSE.txt).

## Build and test on Windows

Requires JDK 25 and legally installed Hytale 0.7.4 server/assets. No game files
or dependencies are included. Set JAVA_HOME or pass -Jdk.

~~~powershell
.\build.ps1 -Jdk 'D:\Tools\jdk-25' -ServerJar 'D:\Hytale\Server\HytaleServer.jar' -AssetsZip 'D:\Hytale\Assets.zip' -RunProbe
$run = (Get-Content .\dist\release\latest-test-run.txt -Raw).Trim()
.\tools\smoke-native.ps1 -Jdk 'D:\Tools\jdk-25' -ServerJar 'D:\Hytale\Server\HytaleServer.jar' -ExistingTestServer "$run\server"
python .\tools\package-native.py
~~~

Tests create isolated server data beneath build/, bind to localhost in offline
mode and shut down automatically. They do not use your live worlds. Test
classes are excluded from the production JAR. To audit the final package, run
python tools/release-audit.py after packaging.

The mod is pinned to Hytale 0.7.4. Do not remove the version guard after a game
update; rebuild and retest against the new release. Marketplace submission and
in-game delivery are not performed by these scripts.

The supplied source matches the release build; ZIP/JAR container timestamps may
differ between rebuilds. Compare compiled class/resource payloads for equivalence.
