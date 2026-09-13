package cn.lvxu.travel;

import java.util.List;

/** Regression coverage for the public donations feed parser. */
public final class DonationsServiceTest {
    private static int checks;
    private static void check(boolean value,String name){if(!value)throw new AssertionError(name);checks++;}
    private static void rejects(String json,String name){try{DonationsService.parseRecords(json);throw new AssertionError(name);}catch(java.io.IOException expected){checks++;}}
    public static void main(String[] args)throws Exception {
        List<DonationsService.Record> records=DonationsService.parseRecords("{\"records\":[{\"name\":\"旧\",\"date\":\"2024-01-01\"},{\"name\":\"新\",\"date\":\"2025-02-03\",\"message\":\"谢谢\"},{\"name\":\"坏\"}]}");
        check(records.size()==2,"invalid rows are skipped");check(records.get(0).name.equals("新"),"records sorted newest first");check(records.get(0).message.equals("谢谢"),"message retained");
        rejects("{}","missing records rejected");rejects("{\"records\":null}","null records rejected");
        StringBuilder many=new StringBuilder("{\"records\":[");for(int i=0;i<205;i++){if(i>0)many.append(',');many.append("{\"name\":\"n\",\"date\":\"").append(String.format("%04d-01-01",i)).append("\"}");}many.append("]}");
        check(DonationsService.parseRecords(many.toString()).size()==200,"feed is bounded");
        System.out.println("PASS: "+checks+" donations assertions");
    }
}
