using System;
using System.IO;
using System.Linq;
using System.Collections.Generic;
using System.ComponentModel;
using System.Reflection;
class CreteModSetup {
 static Assembly lib;
 static object Call(string type,string method,params object[] args) {
  return lib.GetType(type,true).GetMethods(BindingFlags.Public|BindingFlags.NonPublic|BindingFlags.Static).Single(m=>m.Name==method && m.GetParameters().Length==args.Length).Invoke(null,args);
 }
 [STAThread] static int Main(string[] args) {
  try {
   string baseDir=AppDomain.CurrentDomain.BaseDirectory;Directory.SetCurrentDirectory(baseDir);
   lib=Assembly.LoadFrom(Path.Combine(baseDir,"SnakeBite.exe"));
   Console.WriteLine("SnakeBite "+lib.GetName().Version);
   if(args.Length==0 || args[0]=="inspect") {
    foreach(string n in new[]{"SnakeBite.ModManager","SnakeBite.BackupManager","SnakeBite.InstallManager","SnakeBite.Properties.Settings"}) {
     Type t=lib.GetType(n,true);Console.WriteLine(n);
     foreach(var m in t.GetMethods(BindingFlags.Public|BindingFlags.NonPublic|BindingFlags.Static).Where(m=>m.Name.Contains("Merge")||m.Name.Contains("InstallMods")||m.Name.Contains("Default")||m.Name.Contains("Version")))Console.WriteLine(m);
    }
    return 0;
   }
   string game=Path.GetFullPath(args[1]);
   if(!File.Exists(Path.Combine(game,"mgsvtpp.exe")))throw new Exception("Game executable missing");
   Type st=lib.GetType("SnakeBite.Properties.Settings",true);
   object settings=st.GetProperty("Default",BindingFlags.Public|BindingFlags.Static).GetValue(null,null);
   st.GetProperty("InstallPath").SetValue(settings,game,null);
   st.GetMethod("Save").Invoke(settings,null);
   Console.WriteLine("Game "+game);
   if(args[0]=="probe") {Console.WriteLine("GameVersion "+Call("SnakeBite.ModManager","GetMGSVersion"));return 0;}
   string xml=Path.Combine(game,"snakebite.xml");
   Type mt=lib.GetType("SnakeBite.SettingsManager",true);
   object manager=Activator.CreateInstance(mt,new object[]{xml});
   if(args[0]=="prepare") {
    if(File.Exists(xml))throw new Exception("Already managed; preparation refused");
    bool stock=(bool)mt.GetMethod("IsVanilla0001Size").Invoke(manager,null);
    if(!stock)throw new Exception("Unexpected game archives; preparation refused");
    BackgroundWorker worker=new BackgroundWorker();worker.WorkerReportsProgress=true;
    worker.ProgressChanged+=(s,e)=>Console.WriteLine("Progress "+e.UserState);
    DoWorkEventArgs ev=new DoWorkEventArgs(null);
    Call("SnakeBite.BackupManager","backgroundWorker_CopyBackupFiles",worker,ev);
    Call("SnakeBite.ModManager","backgroundWorker_MergeAndCleanup",worker,ev);
    if(ev.Cancel)throw new Exception("SnakeBite preparation cancelled; inspect log");
   } else if(args[0]=="install") {
    if(!File.Exists(xml))throw new Exception("Prepare game before installing mods");
    var mods=args.Skip(2).ToList();if(mods.Count==0)throw new Exception("No mods provided");
    foreach(string mod in mods)if(!File.Exists(mod))throw new Exception("Missing mod "+mod);
    if(!(bool)Call("SnakeBite.InstallManager","InstallMods",mods,false))throw new Exception("Install failed; inspect SnakeBite log");
   } else if(args[0]!="verify") throw new Exception("Unknown operation");
   manager=Activator.CreateInstance(mt,new object[]{xml});
   bool valid=(bool)mt.GetMethod("ValidateDatHash",BindingFlags.Public|BindingFlags.NonPublic|BindingFlags.Instance).Invoke(manager,null); Console.WriteLine("HashValid "+valid); if(!valid)throw new Exception("Managed archive hash validation failed");
   Console.WriteLine("Complete "+args[0]);return 0;
  } catch(Exception e) {Console.Error.WriteLine(e.ToString());return 1;}
 }
}


