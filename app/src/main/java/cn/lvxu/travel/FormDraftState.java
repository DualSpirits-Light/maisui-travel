package cn.lvxu.travel;
import android.os.Bundle;import android.view.*;import android.widget.*;
/** State scoped to a form with deterministic field order, independent of generated view IDs. */
final class FormDraftState {
 static Bundle capture(View form){Bundle b=new Bundle();walk(form,b,new int[]{0},false);return b;}
 static void restore(View form,Bundle b){if(b!=null)walk(form,b,new int[]{0},true);}
 private static void walk(View v,Bundle b,int[] index,boolean restore){
  if(v instanceof EditText){EditText e=(EditText)v;String key="text."+index[0]++;if(restore){if(b.containsKey(key))e.setText(b.getString(key));int at=b.getInt(key+".cursor",-1);if(at>=0)e.setSelection(Math.min(at,e.length()));if(b.getBoolean(key+".focus"))e.requestFocus();}else{b.putString(key,e.getText().toString());b.putInt(key+".cursor",e.getSelectionStart());b.putBoolean(key+".focus",e.hasFocus());}}
  else if(v instanceof Spinner){Spinner s=(Spinner)v;String key="select."+index[0]++;if(restore)s.setSelection(Math.max(0,Math.min(b.getInt(key),s.getCount()-1)));else b.putInt(key,s.getSelectedItemPosition());}
  if(v instanceof ScrollView){String key="scroll."+index[0]++;if(restore){int y=b.getInt(key);v.post(()->v.scrollTo(0,y));}else b.putInt(key,v.getScrollY());}
  if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++)walk(g.getChildAt(i),b,index,restore);}
 }
}
