package cn.lvxu.travel;
import org.json.*;
/** One-step local undo refuses to overwrite any later trip mutation. */
final class TravelUndo {
 final String label,id,before,after;
 TravelUndo(String label,Trip before,Trip after){try{this.label=label;id=after.id;this.before=before.json().toString();this.after=after.json().toString();}catch(Exception e){throw new IllegalArgumentException("无法保留撤销记录",e);}}
 boolean matches(Trip current){try{return current!=null&&id.equals(current.id)&&after.equals(current.json().toString());}catch(Exception e){return false;}}
 Trip restore(Trip current){if(!matches(current))throw new IllegalArgumentException("旅行已有后续修改，为避免覆盖，请手动调整");try{return Trip.from(new JSONObject(before));}catch(Exception e){throw new IllegalArgumentException("无法恢复该操作",e);}}
}
