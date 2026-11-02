import pathlib,json,hashlib,shutil,datetime,sys,ctypes
pack=pathlib.Path(sys.argv[1]).resolve();drive=pathlib.Path(sys.argv[2]).resolve()
assert drive==pathlib.Path(drive.anchor),'Pass the inspected USB volume root'
assert ctypes.windll.kernel32.GetDriveTypeW(ctypes.c_wchar_p(str(drive).rstrip('\\/')+'\\'))==2,'Expected the inspected removable USB volume'
legacy=drive/'CreteOS-G1/ARTIFACTS.json'
assert json.loads(legacy.read_text(encoding='utf-8-sig'))['source']=='d17320cb418c0ab624b8bf8c3dd5c7160c8dcf5d'
dest=drive/pack.name;dest.mkdir(exist_ok=True)
sha=lambda p:hashlib.file_digest(p.open('rb'),'sha256').hexdigest()
manifest=json.loads((pack/'MANIFEST.json').read_text(encoding='utf-8'))
for item in manifest['files']:
 src=(pack/item['path']).resolve();target=(dest/item['path']).resolve()
 assert src.is_relative_to(pack) and target.is_relative_to(dest)
 assert sha(src)==item['sha256']
 target.parent.mkdir(parents=True,exist_ok=True)
 if not target.is_file() or sha(target)!=item['sha256']:shutil.copy2(src,target)
 assert sha(target)==item['sha256'],item['path']
for name in ['MANIFEST.json','MANIFEST.sha256','PACK-VERIFICATION.json']:
 if (pack/name).is_file():shutil.copy2(pack/name,dest/name);assert sha(pack/name)==sha(dest/name)
receipt={'verified':True,'time':datetime.datetime.now().astimezone().isoformat(),'folder':str(dest),'filesVerified':len(manifest['files']),'manifestSHA256':sha(dest/'MANIFEST.json'),'profileArchiveSHA256':sha(dest/'Profile-Backup.zip'),'executableSHA256':sha(dest/'Recovery/creteos.exe'),'sourceBundleCommit':json.loads((dest/'PACKAGE.json').read_text())['sourceBundleCommit'],'method':'Each source and destination file SHA-256 checked, USB bytes read back after copy','legacyPreserved':str(drive/'CreteOS-G1'),'freshWindowsInstallTested':False}
(dest/'USB-COPY-RECEIPT.json').write_text(json.dumps(receipt,indent=2)+'\n',encoding='utf-8')
index=drive/'START-HERE.txt'
if index.exists():
 old=dest/'Legacy-entry-points'/('START-HERE-'+datetime.datetime.now().strftime('%Y%m%d-%H%M%S')+'.txt');old.parent.mkdir(exist_ok=True);shutil.copy2(index,old)
index.write_text('LATEST CRETEOS REPLACEMENT WINDOWS SETUP\n\nOpen '+pack.name+'\\START-HERE.txt first.\nThe older CreteOS-G1 folder is preserved October history.\nThe current pack contains the current executable, verified private profiles, source and setup/recovery instructions.\nRead the documented account/pairing and hardware-specific steps before setup.\nWi-Fi fault on the original G1 remains unresolved; its diagnostic pause/driver must not be blindly applied to a replacement.\n',encoding='utf-8')
(pack/'USB-COPY-RECEIPT.json').write_text(json.dumps(receipt,indent=2)+'\n',encoding='utf-8')
(pack.parent/'CreteOS-G1-USB-copy-latest.json').write_text(json.dumps(receipt,indent=2)+'\n',encoding='utf-8')
print(json.dumps(receipt))
