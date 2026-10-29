param(
    [string]$Jdk=$env:JAVA_HOME,
    [string]$ServerJar="$env:APPDATA\Hytale\install\release\package\game\latest\Server\HytaleServer.jar",
    [Parameter(Mandatory=$true)][string]$ExistingTestServer,
    [string]$Version='2.4.0',
    [int]$Port=15545
)
$ErrorActionPreference='Stop'
if(!$Jdk){throw 'Set JAVA_HOME to a JDK 25 directory, or pass -Jdk explicitly.'}
$project=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$existing=(Resolve-Path -LiteralPath $ExistingTestServer).Path
if(!$existing.StartsWith($project+'\build\',[StringComparison]::OrdinalIgnoreCase)){throw 'Use only an isolated test server in this project build directory'}
$artifact=Join-Path $project "dist\release\Atmosphere-Tool-Native-$Version.jar"
$run=Join-Path $project ('build\production-'+[guid]::NewGuid().ToString('N'))
$mods=Join-Path $run 'mods';$data=Join-Path $mods 'AtmosphereTool_AtmosphereTool'
New-Item -ItemType Directory -Force -Path $data | Out-Null
Copy-Item -LiteralPath (Join-Path $existing 'universe') -Destination (Join-Path $run 'universe') -Recurse
$staged=Join-Path $mods (Split-Path $artifact -Leaf);Copy-Item -LiteralPath $artifact -Destination $staged
$world=(Get-Content -LiteralPath (Join-Path $existing 'weather-restart-stage.txt') -Raw).Trim()
$legacy=Join-Path $data 'global-atmosphere.json'
$localized=Join-Path $data 'localized-mist.json'
$zones=Join-Path $data 'native-weather-zones.json'
[IO.File]::WriteAllText($legacy,(@{version=1;worlds=@{$world=@{creativeFullbright=82;moonSize=175}}}|ConvertTo-Json -Depth 6))
$fixture=Join-Path (Split-Path $existing -Parent) 'NativeOnlyRegression\zones.json'
Copy-Item -LiteralPath $fixture -Destination $localized
Copy-Item -LiteralPath $fixture -Destination $zones
$library=Join-Path $data 'custom-atmosphere-saves.json'
$sourceLibraries=@(Get-ChildItem -LiteralPath (Join-Path $existing 'mods') -Filter 'custom-atmosphere-saves.json' -Recurse -File)
if($sourceLibraries.Count -ne 1){throw 'Expected exactly one tested custom atmosphere library'}
Copy-Item -LiteralPath $sourceLibraries[0].FullName -Destination $library
$before=@{};foreach($p in @($legacy,$localized,$zones,$library)){$before[$p]=(Get-FileHash -LiteralPath $p).Hash}
$artifactHash=(Get-FileHash -LiteralPath $artifact).Hash
for($boot=1;$boot -le 3;$boot++){
    if($boot -eq 3){Move-Item -LiteralPath $staged -Destination (Join-Path $run 'removed-plugin.jar')}
    $start=[Diagnostics.ProcessStartInfo]::new();$start.FileName=Join-Path $Jdk 'bin\java.exe';$start.WorkingDirectory=$run
    $start.UseShellExecute=$false;$start.CreateNoWindow=$true;$start.RedirectStandardOutput=$true;$start.RedirectStandardError=$true
    $assets=Join-Path (Split-Path (Split-Path $ServerJar -Parent) -Parent) 'Assets.zip'
    $argsList=@('--enable-native-access=ALL-UNNAMED','-Xmx2G','-jar',$ServerJar,'--assets',$assets,'--disable-sentry','--disable-file-watcher','--auth-mode','offline','--transport','QUICHE','--bind',"127.0.0.1:$Port")
    if($boot -lt 3){$argsList+=@('--boot-command','atmo --help')}
    $argsList+=@('--boot-command','stop')
    $start.Arguments=($argsList|ForEach-Object{'"'+$_.Replace('"','\"')+'"'}) -join ' '
    $process=[Diagnostics.Process]::new();$process.StartInfo=$start
    try{
        if(!$process.Start()){throw 'Could not start isolated production test'}
        $outTask=$process.StandardOutput.ReadToEndAsync();$errTask=$process.StandardError.ReadToEndAsync()
        if(!$process.WaitForExit(180000)){$process.Kill();$process.WaitForExit();throw "Server timed out: $run"}
        $out=$outTask.GetAwaiter().GetResult();$err=$errTask.GetAwaiter().GetResult();$log=$out+"`n"+$err
        [IO.File]::WriteAllText((Join-Path $run "stdout-$boot.log"),$out);[IO.File]::WriteAllText((Join-Path $run "stderr-$boot.log"),$err)
        if($process.ExitCode -ne 0 -or !$log.Contains('Shutdown completed!') -or !$log.Contains('Hytale Server Booted!')){throw "Server boot/shutdown failed: $run"}
        if($boot -lt 3){
            foreach($marker in @('Enabled plugin AtmosphereTool:AtmosphereTool','AtmosphereTool registered','Console executed command: atmo --help','Shut down plugin AtmosphereTool:AtmosphereTool')){
                if(!$log.Contains($marker)){throw "Missing evidence: $marker; $run"}
            }
            $native=Get-Content -LiteralPath (Join-Path $data 'native-global-atmosphere.json') -Raw|ConvertFrom-Json
            if($native.worlds.$world.creativeFullbright -ne 0 -or $native.worlds.$world.moonSize -ne 175){throw 'Migration did not preserve expected settings'}
        }elseif($log.Contains('Enabled plugin AtmosphereTool:AtmosphereTool')){throw 'Plugin still enabled after removal'}
        foreach($p in $before.Keys){if((Get-FileHash -LiteralPath $p).Hash -ne $before[$p]){throw "Legacy data changed: $p"}}
        Write-Output "PASS production boot $boot/3; legacy files unchanged"
    }finally{$process.Dispose()}
}
@{artifactSHA256=$artifactHash;sourceTestWorld=$existing;boots=3;legacyFilesPreserved=$true;customLibraryPreserved=$true;scope='Exact production JAR installation, restart, migration and removal; no graphical client or connected player'}|ConvertTo-Json|Set-Content -LiteralPath (Join-Path $run 'result.json') -Encoding UTF8
Set-Content -LiteralPath (Join-Path $project 'dist\release\latest-production-test.txt') -Value $run -Encoding UTF8
Write-Output "EVIDENCE: $run"
