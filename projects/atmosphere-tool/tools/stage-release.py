"""Stage explicit public files. Never copies build trees, raw logs or worlds."""
import argparse,hashlib,json,re,shutil,zipfile
from pathlib import Path
root=Path(__file__).resolve().parents[1]
ap=argparse.ArgumentParser();ap.add_argument('--destination',type=Path,required=True)
args=ap.parse_args()
v=json.loads((root/'src/main/resources/manifest.json').read_text())['Version']
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
dist=root/'dist/release'
names=['README.md','LICENSE.txt','NOTICE.txt','.gitignore','build.ps1']
for folder in ['src','tools']:
 names += [p.relative_to(root).as_posix() for p in (root/folder).rglob('*') if p.is_file() and p.suffix not in ['.pyc'] and '__pycache__' not in p.parts]
names += ['docs/'+n for n in ['INSTALLATION_EN.txt','PROVENANCE_EN.txt','VISUAL_TEST_EN.txt','HISTORY_EN.txt','LISTING_EN.txt',f'RELEASE_ASSESSMENT_{v}.txt']]
for folder in [f'docs/release-{v}','docs/branding']:
 names += [p.relative_to(root).as_posix() for p in (root/folder).rglob('*') if p.is_file()]
names=sorted(set(names))
hashes={n:sha(root/n) for n in names}
for n in names:
 if Path(n).suffix!='.png':
  text=(root/n).read_text(encoding='utf-8-sig')
  assert not re.search(r'[A-Za-z]:[\\/]Users[\\/](?!Public|Default)[^\\/\r\n]+',text),n
source=dist/f'Atmosphere-Tool-Native-{v}-Source.zip'
with zipfile.ZipFile(source,'w',zipfile.ZIP_DEFLATED,compresslevel=9) as z:
 for n in names:z.write(root/n,n)
 z.writestr('SOURCE_SHA256.json',json.dumps(hashes,indent=2))
with zipfile.ZipFile(source) as z:
 assert z.testzip() is None
 for n,h in hashes.items():assert hashlib.sha256(z.read(n)).hexdigest()==h
out=args.destination.resolve()
assert not out.exists(),f'Refusing to overwrite existing staged release: {out}'
for sub in ['DA_CARICARE','TESTI_E_IMMAGINI','LICENZA_E_ATTRIBUZIONI','PACCHETTI_E_SORGENTI']:
 (out/sub).mkdir(parents=True,exist_ok=True)
jar=dist/f'Atmosphere-Tool-Native-{v}.jar'
shutil.copy2(jar,out/'DA_CARICARE'/jar.name)
for name in ['LISTING_EN.txt']:
 shutil.copy2(root/'docs'/name,out/'TESTI_E_IMMAGINI'/name)
for p in (root/'docs/branding').iterdir():shutil.copy2(p,out/'TESTI_E_IMMAGINI'/p.name)
for name in ['LICENSE.txt','NOTICE.txt']:shutil.copy2(root/name,out/'LICENZA_E_ATTRIBUZIONI'/name)
shutil.copy2(root/'docs/PROVENANCE_EN.txt',out/'LICENZA_E_ATTRIBUZIONI/PROVENANCE_EN.txt')
for p in [source,dist/f'Atmosphere-Tool-Native-{v}.zip']:shutil.copy2(p,out/'PACCHETTI_E_SORGENTI'/p.name)
(out/'LEGGIMI_PRIMA_DI_PUBBLICARE.txt').write_text(f"""Atmosphere Tool Native {v} - Hytale 0.7.4
File da caricare: DA_CARICARE/{jar.name}
Categoria: Mods / plugin Java. Non caricare lo ZIP dei sorgenti come mod.

Licenza: All Rights Reserved. Istruzioni e descrizione sono in inglese.
I pacchetti ZIP sono materiale di consegna; il plugin richiede solo il JAR.
TESTI_E_IMMAGINI contiene una bozza e un logo originale, non screenshot di gioco.

Prima dell'invio: breve controllo visivo del menu 2.4.0 (tre schede) nel client.
Il risultato 2.3.1 e stato approvato; il nuovo menu ha superato i test server.
La moderazione CurseForge e la distribuzione tramite browser non sono state
eseguite. Nessuna release o pagina marketplace e stata pubblicata.
Il rapporto tecnico completo e nel progetto sorgente:
docs/RELEASE_ASSESSMENT_{v}.txt.
Non attribuire compatibilita ad altre versioni Hytale senza verificarla.
""",encoding='utf-8')
out_hashes={p.relative_to(out).as_posix():sha(p) for p in out.rglob('*') if p.is_file()}
(out/'SHA256SUMS.txt').write_text(''.join(f'{h}  {n}\n' for n,h in sorted(out_hashes.items())),encoding='utf-8')
assert sha(out/'DA_CARICARE'/jar.name)==sha(jar)
e=root/f'docs/evidence/{v}'
(e/'public-source-manifest.json').write_text(json.dumps(hashes,indent=2))
(e/'publication-artifacts.json').write_text(json.dumps({
 'directory':str(out),'files':out_hashes,'sourceZipSHA256':sha(source),'jarSHA256':sha(jar)
},indent=2))
print(json.dumps({'publicDirectory':str(out),'sourceFiles':len(names),'sourceZipBytes':source.stat().st_size,'sourceZipSHA256':sha(source),'jarSHA256':sha(jar)},indent=2))
