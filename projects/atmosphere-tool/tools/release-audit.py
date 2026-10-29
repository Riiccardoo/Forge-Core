"""Audit the public runtime and its correspondence with the compiled source."""
import hashlib,json,re,zipfile
from pathlib import Path
root=Path(__file__).resolve().parents[1]
version=json.loads((root/'src/main/resources/manifest.json').read_text())['Version']
run=Path((root/'dist/release/latest-test-run.txt').read_text(encoding='utf-8-sig').strip())
jar=root/f'dist/release/Atmosphere-Tool-Native-{version}.jar'
sha=lambda b:hashlib.sha256(b).hexdigest()
entries=[]
with zipfile.ZipFile(jar) as z:
    assert z.testzip() is None
    for name in z.namelist():
        if name.endswith('/'):continue
        payload=z.read(name)
        origin='Original/project-supplied Atmosphere Tool'
        if name.endswith('.class'):
            source=root/'src/main/java'/('/'.join(name.split('/')[:-1])+'/'+name.split('/')[-1].split('$')[0].removesuffix('.class')+'.java')
            compiled=run/'classes'/name
            assert compiled.is_file() and compiled.read_bytes()==payload,name
            assert source.is_file(),name
            reference=source.relative_to(root).as_posix()
            material='compiled Java'
        elif name=='META-INF/MANIFEST.MF':
            reference='JDK jar tool generated metadata';material='metadata'
        elif name in ['LICENSE.txt','NOTICE.txt']:
            assert (root/name).read_bytes()==payload
            reference=name;material='rights notice'
        else:
            source=root/'src/main/resources'/name
            assert source.is_file(),name
            if name=='manifest.json':
                built=json.loads(payload);original=json.loads(source.read_text())
                assert all(built[k]==v for k,v in original.items() if k!='Description')
                assert built['Version']==version and built['ServerVersion']=='=0.7.4'
            else:assert source.read_bytes()==payload,name
            reference=source.relative_to(root).as_posix();material='UI or JSON/text'
        assert not any(bad in name for bad in ['Probe','Regression','WeatherZones','LocalizedMist','SelectionPackets','FullbrightTerrain','LightPacketFloor','ServerFullbright'])
        entries.append({'path':name,'bytes':len(payload),'sha256':sha(payload),'material':material,
                        'source':reference,'origin':origin,'license':'All Rights Reserved (project content)'})
    item=json.loads(z.read('Server/Item/Items/EditorTool/Atmosphere_Tool.json'))
    external=[{'field':f,'reference':item[f],'bundledBytes':0,'rights':'Hytale native asset; referenced only'} for f in ['Model','Texture','Icon']]
    assert item['Interactions']=={'Secondary':'Atmosphere_Tool_Use'}
    assert z.read('LICENSE.txt')==(root/'LICENSE.txt').read_bytes()
out=root/f'docs/evidence/{version}';out.mkdir(parents=True,exist_ok=True)
report={'version':version,'jarSHA256':sha(jar.read_bytes()),'jarBytes':jar.stat().st_size,
 'entries':entries,'externalAssetReferences':external,
 'externalDependencies':'JDK 25 and Hytale 0.7.4 server-provided APIs, Gson and annotations; no implementation bundled',
 'archivedFeaturesExcluded':['Zones','global Fullbright','client-volume renderer'],
 'qaAndPrivatePayloadExcluded':True,'classAndResourcePayloadMatch':True,
 'rightsLimit':'Project owner chose All Rights Reserved. Historical contributor ownership is not independently certified; no third-party runtime bytes identified in this allowlisted package.'}
(out/'runtime-inventory.json').write_text(json.dumps(report,indent=2))
print(json.dumps({k:v for k,v in report.items() if k not in ['entries','externalAssetReferences']},indent=2))
