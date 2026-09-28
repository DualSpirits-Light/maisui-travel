package cn.lvxu.travel;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.TreeSet;

/** Read-only memory projections: no heuristic writes to old records or device album scans. */
final class CheckinMemories {
    static final String UNKNOWN="unknown";
    private CheckinMemories() {}
    static Trip.Stop linkedStop(Trip trip, Trip.Checkin record) {
        if(trip==null||record==null||empty(record.stopId))return null;
        Trip.Stop found=null;
        for(Trip.Stop stop:trip.stops)if(record.stopId.equals(stop.id)){if(found!=null)return null;found=stop;}
        return found;
    }
    static String linkLabel(Trip trip,Trip.Checkin record) {
        Trip.Stop linked=linkedStop(trip,record);
        if(linked!=null)return "关联地点："+linked.name+" · 第 "+(linked.day+1)+" 天";
        if(record!=null&&!empty(record.stopId))return "未关联 · 原地点已删除或标识重复，请重新选择";
        int matches=0;
        if(trip!=null&&record!=null&&!empty(record.place))for(Trip.Stop stop:trip.stops)
            if(record.place.trim().equals(stop.name==null?"":stop.name.trim()))matches++;
        if(matches==1)return "未关联 · 有同名行程地点，编辑时选择关联（待确认）";
        if(matches>1)return "未关联 · 多个同名地点，请编辑后选择具体地点";
        return "未关联行程地点 · 可在编辑中选择";
    }
    static ArrayList<Trip.Checkin> forStop(Trip trip,Trip.Stop stop) {
        ArrayList<Trip.Checkin> result=new ArrayList<>();
        if(trip!=null&&stop!=null)for(Trip.Checkin record:CheckinGroups.records(trip))
            if(linkedStop(trip,record)==stop)result.add(record);
        return result;
    }
    static String date(Trip.Checkin record) {
        try{return LocalDateTime.parse(record.time.trim().replace(' ','T')).toLocalDate().toString();}
        catch(Exception ignored){return UNKNOWN;}
    }
    static ArrayList<String> days(Trip trip) {
        TreeSet<String> keys=new TreeSet<>();boolean unknown=false;
        if(trip!=null){
            try{LocalDate start=LocalDate.parse(trip.start);for(int i=0;i<Math.min(60,Math.max(0,trip.days));i++)keys.add(start.plusDays(i).toString());}catch(Exception ignored){}
            for(Trip.Checkin record:trip.checkins){String key=date(record);if(UNKNOWN.equals(key))unknown=true;else keys.add(key);}
        }
        ArrayList<String> result=new ArrayList<>(keys);if(unknown)result.add(UNKNOWN);return result;
    }
    static ArrayList<Trip.Checkin> records(Trip trip,String day) {
        ArrayList<Trip.Checkin> result=new ArrayList<>();
        for(Trip.Checkin record:CheckinGroups.records(trip))if(record!=null&&(empty(day)||day.equals(date(record))))result.add(record);
        return result;
    }
    static String dayLabel(Trip trip,String date) {
        if(UNKNOWN.equals(date))return "日期未识别";
        try{long day=java.time.temporal.ChronoUnit.DAYS.between(LocalDate.parse(trip.start),LocalDate.parse(date));
            return date+(day>=0&&day<trip.days?" · 第 "+(day+1)+" 天":" · 行程日期外");
        }catch(Exception ignored){return date;}
    }
    static ArrayList<String> photos(Trip.Checkin record) {
        LinkedHashSet<String> paths=new LinkedHashSet<>();
        if(record!=null){for(String path:record.groupPhotos)if(!empty(path))paths.add(path);
            for(String path:record.sceneryPhotos)if(!empty(path))paths.add(path);if(!empty(record.photo))paths.add(record.photo);}
        return new ArrayList<>(paths);
    }
    private static boolean empty(String value){return value==null||value.trim().isEmpty();}
}
