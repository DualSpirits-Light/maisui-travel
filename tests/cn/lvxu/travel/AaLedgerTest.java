package cn.lvxu.travel;
import org.json.*;import java.util.*;
public final class AaLedgerTest {
 static int checks;static void check(boolean x,String s){if(!x)throw new AssertionError(s);checks++;}
 public static void main(String[] args)throws Exception {
  JSONObject raw=Trip.demo().json();raw.put("members",new JSONArray().put(new JSONObject().put("id","a").put("name","小王")).put(new JSONObject().put("id","b").put("name","小王")).put(new JSONObject().put("id","c").put("name","我")));
  Trip t=Trip.from(raw);check(t.json().getJSONArray("members").length()==3,"stable same-name members must survive snapshot");
  run(t);System.out.println("PASS: "+checks+" AA assertions");
 }
 static void run(Trip t)throws Exception {
  check(AaLedger.equal(100,Arrays.asList("a","b","c")).values().toString().equals("[34, 33, 33]"),"integer-cent remainder");
  Trip.Expense dinner=new Trip.Expense();dinner.amount=9000;dinner.payerId="a";dinner.shares.putAll(AaLedger.equal(9000,Arrays.asList("a","b","c")));t.expenses.add(dinner);
  Trip.Expense tickets=new Trip.Expense();tickets.amount=6000;tickets.payerId="b";tickets.shares.putAll(AaLedger.equal(6000,Arrays.asList("a","b")));t.expenses.add(tickets);
  check(AaLedger.balances(t).get("b")==0&&AaLedger.balances(t).get("a")==3000,"partial participation netting");
  check(AaLedger.payments(t).size()==1&&AaLedger.payments(t).get(0).fromId.equals("c")&&AaLedger.payments(t).get(0).amount==3000,"net settlement example");
  Trip.Settlement payment=new Trip.Settlement();payment.fromId="c";payment.toId="a";payment.amount=1000;payment.occurredAt="2026-10-06T10:00";t.settlements.add(payment);
  check(AaLedger.payments(t).get(0).amount==2000&&t.spent()==15000,"partial actual transfer without double spending");
  Trip full=Trip.from(t.json());check(full.settlements.size()==1&&full.expenses.get(0).shares.size()==3,"ledger roundtrip");
  Trip legacy=Trip.demo();Trip.Expense personal=new Trip.Expense();personal.amount=99;legacy.expenses.add(personal);check(Trip.from(legacy.json()).expenses.get(0).shares.isEmpty(),"old expense stays personal");
  Trip pure=TripLinkCodec.copyForShare(t,false,true);check(pure.members.isEmpty()&&pure.settlements.isEmpty(),"pure share removes ledger members and transfers");
  Trip local=Trip.from(t.json());local.title="local changed";BackupMergePlan conflict=new BackupMergePlan(Collections.singletonList(local),Collections.singletonList(t));Trip copy=conflict.added.get(0);
  check(!copy.members.get(0).id.equals(t.members.get(0).id)&&AaLedger.payments(copy).get(0).amount==2000,"restore copy remaps every ledger reference");
  check(new BackupMergePlan(conflict.merged,Collections.singletonList(t)).added.isEmpty(),"repeated ledger backup skips restored copy");
  JSONObject bad=t.json();bad.getJSONArray("expenses").getJSONObject(0).put("payerId","missing");boolean rejected=false;try{Trip.from(bad);}catch(IllegalArgumentException ex){rejected=true;}check(rejected,"missing payer is rejected");
  full.expenses.get(0).amount=12000;full.expenses.get(0).shares.clear();full.expenses.get(0).shares.putAll(AaLedger.equal(12000,Arrays.asList("a","b","c")));check(AaLedger.balances(full).get("c")==-3000,"bill edits preserve transfers and recompute remaining net");
  t.stops.get(0).timeLocked=true;check(Trip.from(t.json()).stops.get(0).timeLocked,"appointment lock roundtrip");
  Trip independent=Trip.from(t.json());String originalMember=independent.members.get(0).id;AaLedger.regenerate(independent);check(!independent.members.get(0).id.equals(originalMember)&&AaLedger.payments(independent).get(0).amount==2000,"shared regeneration keeps net and remaps transfers");
  JSONObject decimal=t.json();decimal.getJSONArray("expenses").getJSONObject(0).put("amount",9000.5);rejected=false;try{Trip.from(decimal);}catch(IllegalArgumentException ex){rejected=true;}check(rejected,"AA cents must not silently truncate decimals");
  JSONObject duplicated=t.json();duplicated.getJSONArray("members").getJSONObject(1).put("id","a");rejected=false;try{Trip.from(duplicated);}catch(IllegalArgumentException ex){rejected=true;}check(rejected,"duplicate member IDs must be rejected");
  JSONObject missing=t.json();missing.getJSONArray("settlements").getJSONObject(0).put("toId","gone");rejected=false;try{Trip.from(missing);}catch(IllegalArgumentException ex){rejected=true;}check(rejected,"missing transfer member rejected");
  JSONObject mismatch=t.json();mismatch.getJSONArray("expenses").getJSONObject(0).getJSONArray("shares").getJSONObject(0).put("amount",10);rejected=false;try{Trip.from(mismatch);}catch(IllegalArgumentException ex){rejected=true;}check(rejected,"allocation sum mismatch rejected");
  JSONObject malformed=t.json().put("members","broken");rejected=false;try{Trip.from(malformed);}catch(IllegalArgumentException ex){rejected=true;}check(rejected,"malformed ledger array rejected");
  Trip payerOutside=Trip.from(t.json());payerOutside.expenses.get(0).shares.clear();payerOutside.expenses.get(0).shares.putAll(AaLedger.equal(9000,Arrays.asList("b","c")));check(AaLedger.balances(payerOutside).get("a")==5000,"payer need not participate");
  Trip.Member first=t.members.get(0);String id=first.id;first.name="改名";first.archived=true;check(Trip.from(t.json()).members.get(0).id.equals(id)&&AaLedger.payments(t).get(0).amount==2000,"rename and archive preserve ID and ledger");
 }
}
