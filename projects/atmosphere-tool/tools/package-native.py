"""Package only audited runtime files and user instructions; keep QA evidence separate."""
from pathlib import Path
import hashlib, json, re, shutil, zipfile

ROOT = Path(__file__).resolve().parents[1]
DIST = ROOT / 'dist/release'
VERSION = json.loads((ROOT/'src/main/resources/manifest.json').read_text())['Version']
jar = DIST / f'Atmosphere-Tool-Native-{VERSION}.jar'
run = Path((DIST / 'latest-test-run.txt').read_text(encoding='utf-8-sig').strip())
production = Path((DIST / 'latest-production-test.txt').read_text(encoding='utf-8-sig').strip())
def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()
def under_root(path):
    if not path.resolve().is_relative_to(ROOT):
        raise ValueError(f'Outside project: {path}')
under_root(run); under_root(production)
assert json.loads((production/'result.json').read_text(encoding='utf-8-sig'))['artifactSHA256'].lower() == sha(jar)
assert (run/'server/weather-second-boot-pass.txt').is_file()
assert not (run/'server/weather-qa-fail.txt').exists()

main_classes = {p.stem for p in (ROOT/'src/main/java/atmospheretool').glob('*.java')}
with zipfile.ZipFile(jar) as archive:
    names = [n for n in archive.namelist() if not n.endswith('/')]
    for name in names:
        if name.startswith('atmospheretool/') and name.endswith('.class'):
            assert name.split('/')[-1].split('$')[0].removesuffix('.class') in main_classes, name
        else:
            assert name in {'META-INF/MANIFEST.MF','manifest.json','atmosphere-presets.json','LICENSE.txt','NOTICE.txt'} or name.startswith(('Common/UI/','Server/Item/','Server/Languages/')), name
        assert not name.lower().endswith(('.dll','.exe','.glsl','.log','.png','.jpg','.mp4','.zip')), name
    manifest=json.loads(archive.read('manifest.json'))
    assert manifest['Main']=='atmospheretool.AtmospherePlugin' and manifest['ServerVersion']=='=0.7.4'
    assert manifest['IncludesAssetPack'] is True
    ui=archive.read('Common/UI/Custom/Pages/AtmosphereToolPage.ui').decode()
    ids=set(re.findall(r'#([A-Za-z][A-Za-z0-9_]*)',ui))
    assert {'GlobalOn','GlobalOff','GlobalBrightness','GlobalBrightnessInput','MoonTransparency','MoonTransparencyInput'} <= ids
    assert not {'BackgroundOn','BackgroundOff','BackgroundStatus','CreativeFullbright','CreativeFullbrightInput','CreativeFullbrightStatus','TabZones','PanelZones'} & ids
    assert {'SavedAtmospheres','SaveName','SaveNew','SaveApply','SaveRename','SaveDelete','SaveDeleteConfirm','PanelSaves','PanelFog','PanelSky'} <= ids
    assert not any('ServerFullbright' in n or 'LightPacketFloor' in n or 'FullbrightTerrainProbe' in n or 'WeatherZones' in n or 'LocalizedMistService' in n or 'NativeSelectionPackets' in n for n in names)
    for file in ['menu-commands.json','menu-events.json']:
        data=json.loads((run/'server'/file).read_text())
        for selector in re.findall(r'"selector":\s*"([^"]+)"',json.dumps(data)):
            for identifier in re.findall(r'#([A-Za-z][A-Za-z0-9_]*)',selector):
                assert identifier in ids, (file,selector)
    assert 'Atmosphere_Tool_Client_Component' not in archive.read('Server/Languages/en-US/atmosphere.lang').decode()

evidence=ROOT/f'docs/evidence/{VERSION}'
evidence.mkdir(parents=True,exist_ok=True)
for relative in ['server/custom-saves-checks.txt','server/moon-transparency-packets.json','server/moon-transparency-latency.json','server/fog-brightness-latency.json','server/fog-brightness-sample.json','server/fog-off-packet.json','server/weather-latency.json','server/weather-second-boot-pass.txt','NativeOnlyRegression.log']:
    shutil.copy2(run/relative,evidence/Path(relative).name)
shutil.copy2(production/'result.json',evidence/'production-result.json')
first=(run/'server/stdout-1.log').read_text(encoding='utf-8',errors='replace')
second=(run/'server/stdout-2.log').read_text(encoding='utf-8',errors='replace')
checks=re.findall(r'WEATHER_QA PASS ([^\r\n\x1b]+)',first+second)
(evidence/'server-checks.txt').write_text('\n'.join(checks)+'\n',encoding='utf-8')

files={jar.name:jar,'LICENSE.txt':ROOT/'LICENSE.txt','NOTICE.txt':ROOT/'NOTICE.txt'}
for name in ['INSTALLATION_EN.txt','PROVENANCE_EN.txt','VISUAL_TEST_EN.txt']:
    files[name]=ROOT/'docs'/name
checksums=''.join(f'{sha(path)}  {name}\n' for name,path in files.items())
bundle=DIST/f'Atmosphere-Tool-Native-{VERSION}.zip'
with zipfile.ZipFile(bundle,'w',zipfile.ZIP_DEFLATED,compresslevel=9) as archive:
    for name,path in files.items(): archive.write(path,name)
    archive.writestr('SHA256SUMS.txt',checksums)
with zipfile.ZipFile(bundle) as archive:
    assert archive.testzip() is None
    assert set(archive.namelist())==set(files)|{'SHA256SUMS.txt'}
    for name,path in files.items(): assert hashlib.sha256(archive.read(name)).hexdigest()==sha(path)
(DIST/'SHA256SUMS.txt').write_text(checksums+f'{sha(bundle)}  {bundle.name}\n',encoding='utf-8')
sources={str(p.relative_to(ROOT)).replace('\\','/'):sha(p) for p in (ROOT/'src').rglob('*') if p.is_file()}
for p in [ROOT/'LICENSE.txt',ROOT/'NOTICE.txt',ROOT/'build.ps1',*sorted((ROOT/'tools').glob('*'))]:
    if p.is_file():sources[str(p.relative_to(ROOT)).replace('\\','/')]=sha(p)
(evidence/'source-hashes.json').write_text(json.dumps(sources,indent=2),encoding='utf-8')
regression_checks=int(re.search(r'PASS (\d+) checks', (run/'NativeOnlyRegression.log').read_text()).group(1))
result={'jarSHA256':sha(jar),'zipSHA256':sha(bundle),'jarBytes':jar.stat().st_size,'zipBytes':bundle.stat().st_size,'jarEntries':len(names),'regressionChecks':regression_checks,'serverChecks':len(checks),'productionBoots':3,'probeRun':str(run),'productionRun':str(production),'visualClient':'NOT RUN','packageContents':list(files)+['SHA256SUMS.txt']}
(evidence/'package-check.json').write_text(json.dumps(result,indent=2),encoding='utf-8')
print(json.dumps(result,indent=2))
