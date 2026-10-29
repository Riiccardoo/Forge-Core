"""Test baseline -> candidate -> baseline with one disposable existing world."""
import argparse,hashlib,json,shutil,subprocess,uuid
from pathlib import Path
p=argparse.ArgumentParser()
for key in ['jdk','server','assets','baseline','seed']:
 p.add_argument('--'+key,type=Path,required=True)
a=p.parse_args();root=Path(__file__).resolve().parents[1]
assert a.seed.resolve().is_relative_to(root/'build')
run=root/'build'/('upgrade-'+uuid.uuid4().hex);run.mkdir(parents=True)
shutil.copytree(a.seed/'universe',run/'universe')
data=run/'mods/AtmosphereTool_AtmosphereTool'
shutil.copytree(a.seed/'mods/AtmosphereTool_AtmosphereTool',data)
(data/'native-weather-zones.json').write_text('{"version":2,"worlds":{}}')
sha=lambda f:hashlib.sha256(f.read_bytes()).hexdigest()
snapshot=lambda:{f.relative_to(data).as_posix():sha(f) for f in data.rglob('*') if f.is_file()}
before=snapshot();candidate=root/'dist/release/Atmosphere-Tool-Native-2.4.0.jar'
stages=[]
for version,artifact in [('2.3.1',a.baseline),('2.4.0',candidate),('2.3.1 rollback',a.baseline)]:
 shutil.copy2(artifact,run/'mods/Atmosphere.jar')
 proc=subprocess.run([str(a.jdk/'bin/java.exe'),'--enable-native-access=ALL-UNNAMED','-Xmx2G','-jar',str(a.server),'--assets',str(a.assets),
  '--disable-sentry','--disable-file-watcher','--auth-mode','offline','--transport','QUICHE','--bind','127.0.0.1:15544',
  '--boot-command','atmo --help','--boot-command','stop'],cwd=run,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,timeout=180)
 log=proc.stdout.decode('utf-8',errors='replace');(run/(version.replace(' ','-')+'.log')).write_text(log,encoding='utf-8')
 assert proc.returncode==0 and 'AtmosphereTool registered' in log and 'Hytale Server Booted!' in log and 'Shutdown completed!' in log
 assert 'Failed to setup' not in log
 assert snapshot()==before,'Settings or saved library changed on '+version
 stages.append({'version':version,'jarSHA256':sha(artifact),'dataUnchanged':True})
 print('PASS '+version,flush=True)
result={'scope':'Existing isolated test world, no graphical client/player. Exact original baseline JAR, candidate, original baseline rollback. All plugin data files hash-identical after each boot.',
 'stages':stages,'dataFileCount':len(before),'files':before}
(run/'result.json').write_text(json.dumps(result,indent=2))
out=root/'docs/evidence/2.4.0';out.mkdir(parents=True,exist_ok=True)
shutil.copy2(run/'result.json',out/'upgrade-rollback.json')
(out/'upgrade-location.txt').write_text(str(run))
