import pathlib,json,hashlib,shutil,datetime,subprocess,zipfile,argparse,re
parser=argparse.ArgumentParser(description='Build a private replacement pack from the verified latest native snapshot and current checkout. Never commit the generated pack.')
parser.add_argument('--workspace',type=pathlib.Path,required=True)
parser.add_argument('--legacy-pack',type=pathlib.Path,required=True)
parser.add_argument('--app-source',required=True)
parser.add_argument('--output',type=pathlib.Path,required=True)
args=parser.parse_args()
assert re.fullmatch(r'[0-9a-f]{40}',args.app_source)
w=args.workspace.resolve();r=pathlib.Path(__file__).resolve().parents[3]
out=args.output.resolve();out.mkdir(exist_ok=True)
load=lambda p:json.loads(p.read_text(encoding='utf-8-sig'))
sha=lambda p:hashlib.file_digest(p.open('rb'),'sha256').hexdigest()
def cp(src,rel):
 p=out/rel;p.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(src,p);assert sha(src)==sha(p);return p
d=load(w/'outputs/CreteOS-G1-profile-backup-latest.json');assert not d.get('stale') and d['archiveContentsVerified']
archive=pathlib.Path(d['archive']);assert sha(archive)==d['archiveSHA256']
cp(archive,'Profile-Backup.zip')
with zipfile.ZipFile(archive) as z:
 assert isinstance(json.loads(z.read('Supporting/WiFiSmart/control.json'))['Paused'],bool)
 for name in ['creteos.exe','LICENSE','UPSTREAM.md']:
  p=out/'Recovery'/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(z.read('Recovery/'+name))
 assert sha(out/'Recovery/creteos.exe')==next(f['sha256'] for f in json.loads(z.read('manifest.json'))['files'] if f['path']=='Recovery/creteos.exe')
old=args.legacy_pack.resolve();art=load(old/'ARTIFACTS.json')
for item in art['artifacts']:
 src=old/item['path'];assert sha(src)==item['sha256']
 name=src.name if 'd17320c' not in src.name else 'CreteOS-bootstrap-only.exe'
 cp(src,'Installers/'+name)
for name in ['Install-ReplacementDevice.ps1','Verify-RecoveryPack.ps1']:
 cp(r/'windows/scripts/recovery'/name,'Scripts/'+name)
for folder in ['device-records','journal']:
 shutil.copytree(r/'docs'/folder,out/'Documentation'/folder,dirs_exist_ok=True)
cp(r/'docs/G1_SETUP_AND_RECOVERY.md','Documentation/G1_SETUP_AND_RECOVERY.md')
cp(r/'docs/APP_JOURNAL.md','Documentation/APP_JOURNAL.md')
cp(r/'docs/LUTRIS_HOST_INTEGRATION.md','Documentation/LUTRIS_HOST_INTEGRATION.md')
shutil.copytree(r/'windows/scripts/wifi-smart',out/'Supporting/WiFiSmart-source',dirs_exist_ok=True)
head=subprocess.check_output(['git','-C',str(r),'rev-parse','HEAD'],text=True).strip()
bundle=w/'outputs/CreteOS-G1-complete-20261023.bundle'
heads=subprocess.check_output(['git','bundle','list-heads',str(bundle)],text=True)
assert any(line.split()==[head,'HEAD'] for line in heads.splitlines()),'Refresh/verify source bundle to current HEAD before packaging'
cp(bundle,'Source/CreteOS.bundle')
subprocess.run(['git','-C',str(r),'archive','--format=zip','--output='+str(out/'Source/SourceSnapshot.zip'),head],check=True)
start='''CRETEOS — REPLACEMENT WINDOWS DEVICE — 2 NOVEMBER 2026

Use THIS folder. The older CreteOS-G1 folder is preserved October history.
Keep this USB connected until verification/setup finishes. No installed game
downloads or game saves, passwords, private SSH keys or pairing identities are
in this pack. No BIOS, driver, TDP or security settings are applied automatically.

1. Open CODEX-PROMPT.txt and give it to Codex on the NEW Windows machine.
   Or read Documentation/G1_SETUP_AND_RECOVERY.md and use the scripts yourself.
2. Run Scripts/Verify-RecoveryPack.ps1 in native Windows PowerShell.
3. SETUP-NEW-WINDOWS.cmd verifies the required files, installs the old base
   installer ONLY as bootstrap if needed, replaces it with the exact current
   G1 executable, and restores the saved preferences/onboarding/art/library.
   It does not launch CreteOS or a game. It refuses the source OXPG1.
   On a different display, old G1 display profiles are skipped by default.
   Use -UseG1DisplayProfiles only after checking the new panel; adjust native
   stream resolution/FPS/refresh and client paths for that device.
4. Finish the checklist below with Codex, then open CreteOS and test it.

CURRENT APP: source1faa909; SHA256D2C4C3109193B19DF88ACAA3515B3817FE8D673981BDEF4439BA0F26579A4FD6.
Full identities/hashes are in PACKAGE.json, MANIFEST.json and the guide.
The source bundle/snapshot include current source/docs. Exact installed app
source and source-bundle HEAD may differ because later commits record setup.

RESTORED BY CORE SETUP: onboarding and preferences, artwork/metadata/review-video
caches, hidden/pinned items, library copies, recent history, device stream-time
files when present, saved provider preferences. Account-bound progress is
reference only. This is not a Windows disk image and is not fully unattended.

FINISH ON THE NEW DEVICE:
- Install Steam, Moonlight, GeForce NOW, Chrome/Luna, AnyFSE, ASOBI (Steam4505440),
  Lossless Scaling if owned, and the correct vendor controller/power utility.
  The original verified Moonlight/AnyFSE installers are supplied as a fallback;
  review current official versions. Sign in afresh to CreteOS store accounts.
- Restore reviewed Moonlight/NVIDIA/AnyFSE/OneXConsole/Lossless Scaling settings
  from the private ZIP Supporting folder; paths and displays can differ.
  Fresh Moonlight/Buddy and ASOBI PS5 pairing are required; re-export ASOBI
  shortcuts through its own Add to Steam/Restart Steam and refresh CreteOS.
  Use safe ASOBI preferences/library reference exports, never consoles.json.
- Install the preserved Luna Companion folder in Chrome with explicit user
  approval, then verify live launch/quit and playtime. Browser sign-ins are fresh.
- Configure native Wi-Fi helper for the NEW adapter GUID and saved home profiles.
  G1 automatic switching was restored on 2 November at the user's request;
  review diagnosis and never copy an old adapter GUID. Moonlight/PS5 default Extra;
  GFN/Luna/PS Cloud default FibreE433; hold during play, away networks untouched.
- Generate a NEW SSH key on the new device, authorize it on Bosgame and restore
  the reviewed alias/host identity. No old private SSH key is included.
- Check vendor TDP/controller mappings/AnyFSE, choose desired startup behavior,
  and review power/wake settings locally. Never import another model's hardware
  power database or force the G1 AX210 driver onto unrelated hardware.
- Test startup/hibernate/controller, stream start/B-cancel/quit/return covers,
  playtime, networking and dock/unplug. PS live transitions remain pending user
  acceptance. Host GPU/Lutris setup is backed up as reference; do not replay it
  on an already configured Bosgame or restart models/games unnecessarily.

WIFI FAULT ON SOURCE G1: repeated AX210 fatal failures on both24.80.0.2 and
23.150.0.4. Rollback did NOT fix it. Switching paused13:51 and restored14:30 on2November.
Heat on a blanket is possible, not proven; use clear vents/hard surface for
observation. Drivers are G1-only recovery references, not new-device defaults.
The current guide outside Profile-Backup.zip supersedes its packaging-time copy.
'''
start=start.replace('source1faa909', 'source '+args.app_source).replace('SHA256D2C4C3109193B19DF88ACAA3515B3817FE8D673981BDEF4439BA0F26579A4FD6','SHA256 '+sha(out/'Recovery/creteos.exe'))
(out/'START-HERE.txt').write_text(start,encoding='utf-8')
(out/'CODEX-PROMPT.txt').write_text('''Set up CreteOS on this replacement Windows device using THIS recovery folder. First read START-HERE.txt, PACKAGE.json and Documentation/G1_SETUP_AND_RECOVERY.md, then verify MANIFEST.json with Scripts/Verify-RecoveryPack.ps1. Preserve this source USB and any destination profiles. Use the exact current recovery executable, not the October bootstrap as the final app. Core profile restore should preserve saved onboarding; use the native Windows AppData profile. Inspect the new hardware/display/client paths, install required clients and restore reviewed supporting preferences. Ask me to perform fresh account sign-ins and pairing; no credentials/private keys are portable. Do not blindly apply G1 drivers, BIOS, vendor power settings, Windows security/login settings, or its temporary diagnostic Wi-Fi pause to this device. Do not launch games without my instruction. Check each setup/test result and record exact installed hashes and remaining steps. Source bundle/snapshot and setup history are included; keep future changes documented and refresh verified private backups. Hardware/account-dependent steps must be verified, not assumed from the G1.\n''',encoding='utf-8')
(out/'SETUP-NEW-WINDOWS.cmd').write_text('@echo off\r\npowershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0Scripts\\Install-ReplacementDevice.ps1"\r\nif errorlevel 1 echo Setup stopped. Read the error above before retrying.\r\npause\r\n',encoding='ascii')
required=['Profile-Backup.zip','Recovery/creteos.exe','Installers/CreteOS-bootstrap-only.exe','Scripts/Install-ReplacementDevice.ps1']
info={'prepared':datetime.datetime.now().astimezone().isoformat(),'appSourceCommit':args.app_source,'sourceBundleCommit':head,'executableSHA256':sha(out/'Recovery/creteos.exe'),'profileArchiveSHA256':sha(out/'Profile-Backup.zip'),'profileSnapshotName':archive.name,'required':[{'path':n,'sha256':sha(out/n)} for n in required],'bootstrapOnly':'October installer is used for base installation only; final executable is exact D2 build','newWindowsInstallAcceptance':'Not performed; hash/read-only/fixture checks recorded separately','wifiFault':'Unresolved; automatic switching restored at user request, rollback did not fix recurrence'}
(out/'PACKAGE.json').write_text(json.dumps(info,indent=2)+'\n',encoding='utf-8')
files=[{'path':p.relative_to(out).as_posix(),'bytes':p.stat().st_size,'sha256':sha(p)} for p in sorted(out.rglob('*')) if p.is_file() and p.name not in ('MANIFEST.json','MANIFEST.sha256','USB-COPY-RECEIPT.json','PACK-VERIFICATION.json')]
(out/'MANIFEST.json').write_text(json.dumps({'created':info['prepared'],'files':files},indent=2)+'\n',encoding='utf-8')
(out/'MANIFEST.sha256').write_text(sha(out/'MANIFEST.json')+'  MANIFEST.json\n',encoding='ascii')
print(json.dumps({'folder':str(out),'files':len(files),'bytes':sum(f['bytes'] for f in files),'manifestSHA256':sha(out/'MANIFEST.json'),'sourceCommit':head}))
