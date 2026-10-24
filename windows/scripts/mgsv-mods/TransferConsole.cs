using System;
using ArchiveTransferrer;
namespace ArchiveTransferrer { public class FeedbackEventArgs:EventArgs {public string Feedback {get;set;}} }
class TransferConsole {
 static int Main(string[] args) {
  if(args.Length!=3)return 1;
  var t=new TransferManager();
  t.SendFeedback+=(s,e)=>{ if(!e.Feedback.StartsWith("/"))Console.WriteLine(e.Feedback); };
  t.Transfer(args[0],args[1],args[2]);
  Console.WriteLine("Transferred: "+t.GetSuccessfulTransfers());
  if(t.errorOccurred!=""){Console.Error.WriteLine(t.errorOccurred);return 2;}
  return t.successfulTransfers.Count==2?0:3;
 }
}
