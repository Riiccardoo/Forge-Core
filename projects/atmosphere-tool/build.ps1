param(
    [string]$Jdk=$env:JAVA_HOME,
    [string]$ServerJar="$env:APPDATA\Hytale\install\release\package\game\latest\Server\HytaleServer.jar",
    [string]$AssetsZip,
    [string]$Version='2.4.0',
    [switch]$RunProbe,
    [int]$Port=15527
)
$ErrorActionPreference='Stop'
if(!$Jdk){throw 'Set JAVA_HOME to a JDK 25 directory, or pass -Jdk explicitly.'}
if(!$AssetsZip){$AssetsZip=Join-Path (Split-Path (Split-Path $ServerJar -Parent) -Parent) 'Assets.zip'}
$java=Join-Path $Jdk 'bin\java.exe'; $javac=Join-Path $Jdk 'bin\javac.exe'; $jarTool=Join-Path $Jdk 'bin\jar.exe'
foreach($f in @($java,$javac,$jarTool,$ServerJar,$AssetsZip)){if(!(Test-Path -LiteralPath $f)){throw "Missing input: $f"}}
$run=Join-Path $PSScriptRoot ('build\native-weather-'+[guid]::NewGuid().ToString('N'))
$classes=Join-Path $run 'classes';$tests=Join-Path $run 'tests';$runtime=Join-Path $run 'resources';$qa=Join-Path $run 'server'
$isPreview=$Version -match '-preview'
$dist=Join-Path $PSScriptRoot $(if($isPreview){'dist\native-weather-preview'}else{'dist\release'})
foreach($p in @($classes,$tests,$runtime,$qa,$dist)){New-Item -ItemType Directory -Force -Path $p | Out-Null}
$sources=@(Get-ChildItem (Join-Path $PSScriptRoot 'src\main\java') -Recurse -Filter '*.java' | Select-Object -ExpandProperty FullName)
& $javac --release 25 -encoding UTF-8 -cp $ServerJar -d $classes @sources
if($LASTEXITCODE){throw 'Compilation failed'}
$testSources=@(Get-ChildItem (Join-Path $PSScriptRoot 'src\test\java') -Recurse -Filter '*.java' | Select-Object -ExpandProperty FullName)
& $javac --release 25 -encoding UTF-8 -cp "$ServerJar;$classes" -d $tests @testSources
if($LASTEXITCODE){throw 'Test compilation failed'}
foreach($test in @('NativeOnlyRegression')){
    & $java "-Datmosphere.client.directory=$run\client-bridge" "-Dhytale.assets=$AssetsZip" -ea -cp "$ServerJar;$classes;$tests;$PSScriptRoot\src\main\resources" "atmospheretool.$test" (Join-Path $run $test) | Tee-Object -FilePath (Join-Path $run "$test.log")
    if($LASTEXITCODE){throw "Regression failed: $test. Evidence: $run"}
}
foreach($relative in @('manifest.json','atmosphere-presets.json','Common\UI','Server\Item','Server\Languages')){
    $dest=Join-Path $runtime $relative
    New-Item -ItemType Directory -Force -Path (Split-Path $dest -Parent) | Out-Null
    Copy-Item -LiteralPath (Join-Path "$PSScriptRoot\src\main\resources" $relative) -Destination $dest -Recurse
}
foreach($notice in @('LICENSE.txt','NOTICE.txt')){
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot $notice) -Destination (Join-Path $runtime $notice)
}
$manifest=Get-Content -LiteralPath (Join-Path $runtime 'manifest.json') -Raw | ConvertFrom-Json
$manifest.Version=$Version
$manifest.Description="Native global weather, sky, moon and custom atmosphere saves. No client component."
[IO.File]::WriteAllText((Join-Path $runtime 'manifest.json'),($manifest | ConvertTo-Json -Depth 10),[Text.UTF8Encoding]::new($false))
$artifact=Join-Path $dist "Atmosphere-Tool-Native-$Version.jar"
& $jarTool --create --file $artifact -C $classes . -C $runtime .
if($LASTEXITCODE){throw 'Packaging failed'}
if($RunProbe){
    $mods=Join-Path $qa 'mods';New-Item -ItemType Directory -Path $mods | Out-Null
    $probeJar=Join-Path $mods 'Atmosphere-NativeWeather-QA.jar';Copy-Item -LiteralPath $artifact -Destination $probeJar
    $probeManifest=Join-Path $run 'probe-manifest';New-Item -ItemType Directory -Path $probeManifest | Out-Null
    $manifest.Group='AtmosphereNativeQA';$manifest.Main='atmospheretool.NativeOnlyProbe'
    [IO.File]::WriteAllText((Join-Path $probeManifest 'manifest.json'),($manifest | ConvertTo-Json -Depth 10),[Text.UTF8Encoding]::new($false))
    & $jarTool --update --file $probeJar -C $tests . -C $probeManifest manifest.json
    if($LASTEXITCODE){throw 'Probe packaging failed'}
    for($boot=1;$boot -le 2;$boot++){
        $start=[Diagnostics.ProcessStartInfo]::new();$start.FileName=$java;$start.WorkingDirectory=$qa
        $start.UseShellExecute=$false;$start.CreateNoWindow=$true;$start.RedirectStandardOutput=$true;$start.RedirectStandardError=$true
        $arguments=@('--enable-native-access=ALL-UNNAMED',"-Datmosphere.client.directory=$qa\client-bridge",'-Xmx2G','-jar',$ServerJar,'--assets',$AssetsZip,'--disable-sentry','--disable-file-watcher','--auth-mode','offline','--transport','QUICHE','--bind',"127.0.0.1:$Port",'--boot-command','weatherqa','--boot-command','stop')
        $start.Arguments=($arguments | ForEach-Object {'"'+$_.Replace('"','\"')+'"'}) -join ' '
        $process=[Diagnostics.Process]::new();$process.StartInfo=$start
        try{
            if(!$process.Start()){throw 'Failed to start isolated server'}
            $stdout=$process.StandardOutput.ReadToEndAsync();$stderr=$process.StandardError.ReadToEndAsync()
            if(!$process.WaitForExit(180000)){$process.Kill();$process.WaitForExit();throw "Probe timed out: $qa"}
            $out=$stdout.GetAwaiter().GetResult();$err=$stderr.GetAwaiter().GetResult()
            [IO.File]::WriteAllText((Join-Path $qa "stdout-$boot.log"),$out);[IO.File]::WriteAllText((Join-Path $qa "stderr-$boot.log"),$err)
            if($process.ExitCode -ne 0 -or !(Test-Path (Join-Path $qa 'weather-qa-pass.txt')) -or (Test-Path (Join-Path $qa 'weather-qa-fail.txt'))){throw "Native weather integration failed: $qa"}
            if(!$out.Contains('Shutdown completed!') -and !$err.Contains('Shutdown completed!')){throw "Incomplete shutdown: $qa"}
            Write-Output "PASS isolated native-weather server boot $boot/2: $qa"
        }finally{$process.Dispose()}
    }
    if(!(Test-Path (Join-Path $qa 'weather-second-boot-pass.txt'))){throw 'Second-boot evidence missing'}
}
Get-FileHash -LiteralPath $artifact,$ServerJar -Algorithm SHA256 | Format-List
Set-Content -LiteralPath (Join-Path $dist 'latest-test-run.txt') -Value $run -Encoding UTF8
Write-Output "CANDIDATE: $artifact"
Write-Output "EVIDENCE: $run"
