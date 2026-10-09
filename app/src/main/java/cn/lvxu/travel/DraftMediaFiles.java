package cn.lvxu.travel;
final class DraftMediaFiles {
 static boolean referenced(MainActivity a,String path){if(path==null||path.isEmpty())return true;if(path.equals(a.prefs.avatar())||path.equals(a.prefs.background()))return true;for(Trip t:a.trips){for(Trip.Item i:t.items)if(path.equals(i.photo))return true;for(Trip.Expense e:t.expenses)if(path.equals(e.photo))return true;for(Trip.Stop s:t.stops)if(path.equals(s.previewPhoto)||s.notePhotos.contains(path))return true;for(Trip.Checkin c:t.checkins)if(path.equals(c.photo)||path.equals(c.card)||c.groupPhotos.contains(path)||c.sceneryPhotos.contains(path))return true;}return false;}
 static void discard(MainActivity a,String path){if(referenced(a,path))return;try{java.io.File file=a.mediaFile(path);if(file!=null)file.delete();}catch(IllegalArgumentException ignored){}}
}
