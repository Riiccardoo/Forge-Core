"""Compare plugin setup and isolated server boot, never connected-client FPS.
The historical archive and JAR are private inputs, not public bundle contents.
"""
import argparse, hashlib, json, re, shutil, statistics, subprocess, time, uuid, zipfile
from pathlib import Path
ap=argparse.ArgumentParser()
ap.add_argument('--jdk',type=Path,required=True)
ap.add_argument('--server',type=Path,required=True)
ap.add_argument('--assets',type=Path,required=True)
ap.add_argument('--baseline-archive',type=Path,required=True)
ap.add_argument('--baseline-jar',type=Path,required=True)
ap.add_argument('--seed',type=Path,required=True)
ap.add_argument('--repeats',type=int,default=3)
args=ap.parse_args()
root=Path(__file__).resolve().parents[1]
run=root/'build'/('startup-'+uuid.uuid4().hex)
run.mkdir(parents=True)
assert args.seed.resolve().is_relative_to(root/'build')
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
java=args.jdk/'bin/java.exe'
javac=args.jdk/'bin/javac.exe'
baseline=run/'baseline-instrumented.jar'
shutil.copy2(args.baseline_jar,baseline)
with zipfile.ZipFile(args.baseline_archive) as z:
    s=z.read('src/main/java/atmospheretool/AtmospherePlugin.java').decode().replace('\r','')
s=s.replace('protected void setup() {','protected void setup() {\n        long started=System.nanoTime();',1)
old='"AtmosphereTool registered (native weather zones and custom atmosphere saves)");'
assert old in s
s=s.replace(old,'"AtmosphereTool registered (baseline benchmark); setupMs=%.3f", (System.nanoTime()-started)/1_000_000.0);')
source=run/'AtmospherePlugin.java';source.write_text(s)
classes=run/'classes';classes.mkdir()
subprocess.run([str(javac),'--release','25','-encoding','UTF-8','-cp',str(args.server)+';'+str(baseline),'-d',str(classes),str(source)],check=True)
subprocess.run([str(args.jdk/'bin/jar.exe'),'--update','--file',str(baseline),'-C',str(classes),'atmospheretool/AtmospherePlugin.class'],check=True)
with zipfile.ZipFile(args.baseline_jar) as a,zipfile.ZipFile(baseline) as b:
    changed=[n for n in a.namelist() if a.read(n)!=b.read(n)]
    assert changed==['atmospheretool/AtmospherePlugin.class'],changed
candidate=root/'dist/release/Atmosphere-Tool-Native-2.4.0.jar'
samples=[]
for i in range(args.repeats):
    order=['2.3.1','2.4.0'] if i%2==0 else ['2.4.0','2.3.1']
    for version in order:
        target=run/f'{i+1}-{version}'
        target.mkdir()
        shutil.copytree(args.seed/'universe',target/'universe')
        data=target/'mods/AtmosphereTool_AtmosphereTool'
        shutil.copytree(args.seed/'mods/AtmosphereTool_AtmosphereTool',data)
        # Identical valid empty archived-zone fixture for both versions.
        (data/'native-weather-zones.json').write_text('{"version":1,"worlds":{}}')
        shutil.copy2(baseline if version=='2.3.1' else candidate,target/'mods/Atmosphere.jar')
        command=[str(java),'--enable-native-access=ALL-UNNAMED','-Xmx2G','-jar',str(args.server),
          '--assets',str(args.assets),'--disable-sentry','--disable-file-watcher',
          '--auth-mode','offline','--transport','QUICHE','--bind','127.0.0.1:15543',
          '--boot-command','atmo --help','--boot-command','stop']
        start=time.perf_counter()
        proc=subprocess.run(command,cwd=target,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,timeout=180)
        elapsed=time.perf_counter()-start
        log=proc.stdout.decode('utf-8',errors='replace')
        (target/'server.log').write_text(log,encoding='utf-8')
        assert proc.returncode==0 and 'Shutdown completed!' in log and 'Hytale Server Booted!' in log
        assert 'Failed to setup' not in log
        match=re.search(r'setupMs=([\d.,]+)',log)
        assert match, target
        boot=re.search(r'Hytale Server Booted!.*?took ([^\r\n]+)',log)
        sample={'version':version,'round':i+1,'setupMs':float(match.group(1).replace(',','.')),
          'processThroughShutdownSeconds':round(elapsed,3),'serverBootReported':boot.group(1) if boot else None}
        samples.append(sample)
        print(json.dumps(sample),flush=True)
summary={}
for version in ['2.3.1','2.4.0']:
    vals=[s['setupMs'] for s in samples if s['version']==version]
    summary[version]={'n':len(vals),'medianSetupMs':statistics.median(vals),'minSetupMs':min(vals),'maxSetupMs':max(vals)}
result={'method':'Alternating separate JVM starts; identical existing isolated world and data seed; empty archived zone set; warm OS cache, not a cold-disk benchmark. Baseline differs only by setup timer/logger. No graphical client or connected player.',
 'baselineJarSHA256':sha(args.baseline_jar),'instrumentedBaselineSHA256':sha(baseline),
 'candidateJarSHA256':sha(candidate),'serverSHA256':sha(args.server),
 'fixtureNote':'Empty version-1 zone file; baseline performs its normal backup, candidate leaves archived data unread.',
 'samples':samples,'summary':summary,'baselineChangedEntries':changed}
(run/'results.json').write_text(json.dumps(result,indent=2))
out=root/'docs/evidence/2.4.0';out.mkdir(parents=True,exist_ok=True)
shutil.copy2(run/'results.json',out/'startup-benchmark.json')
(out/'startup-benchmark-location.txt').write_text(str(run))
print(json.dumps(summary,indent=2),flush=True)
