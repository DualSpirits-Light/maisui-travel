package cn.lvxu.travel;

import android.app.*;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.util.*;

/** Native configuration screens. Candidate values are tested before encrypted replacement. */
final class AiSettingsUi {
 interface SearchVerifier {void verify(String key)throws Exception;}
 private final MainActivity a; private final ApiConfig config;private final SearchVerifier searchVerifier;
 private Runnable afterProviderSave;
 AiSettingsUi(MainActivity a){this(a,key->AiExploreUi.searchRequest(key,"北京博物馆"));}
 AiSettingsUi(MainActivity a,SearchVerifier verifier){this.a=a;config=new ApiConfig(a);searchVerifier=verifier;}
 void showProviders(Runnable afterSave){afterProviderSave=afterSave;showProviders();}
 void showProviders(){PageUi page=new PageUi(a,"AI 服务商");renderProviders(page);page.show();}
 private void renderProviders(PageUi page){LinearLayout box=page.body;box.removeAllViews();ArrayList<AiProviders.Profile> saved=AiProviders.read(a);String selected=AiProviders.selected(a);box.addView(a.text("密钥会加密保存在本机。每次保存前都会发送一次短请求验证，验证失败不会覆盖已保存的配置。",13,MainActivity.MUTED));a.space(box,12);for(AiProviders.Profile p:saved){String label=(p.id.equals(selected)?"✓ ":"")+p.name+" · "+p.model;box.addView(a.action(label,false,()->edit(page,p)));}a.space(box,8);box.addView(a.action("＋ 添加预设或自定义服务",true,()->choosePreset(page)));if(!saved.isEmpty())box.addView(a.text("点按配置可编辑、设为当前服务或删除。",12,MainActivity.MUTED));}
 private void choosePreset(PageUi page){String[] names=new String[AiProviders.presets().length];AiProviders.Profile[] presets=AiProviders.presets();for(int i=0;i<names.length;i++)names[i]=presets[i].name;new RoundedDialogs.Builder(a).setTitle("选择服务").setItems(names,(d,which)->{AiProviders.Profile p=presets[which];for(AiProviders.Profile old:AiProviders.read(a))if(old.id.equals(p.id)){p=new AiProviders.Profile(p.id+"-"+System.currentTimeMillis(),p.name,p.baseUrl,p.model,p.protocol);break;}edit(page,p);}).show();}
 private void edit(PageUi page,AiProviders.Profile original){LinearLayout f=a.col();a.pad(f,20);EditText name=a.field(f,"名称",original.name,InputType.TYPE_CLASS_TEXT);EditText base=a.field(f,"HTTPS 基础地址",original.baseUrl,InputType.TYPE_TEXT_VARIATION_URI);EditText model=a.field(f,"模型",original.model,InputType.TYPE_CLASS_TEXT);Spinner protocol=a.select(f,"接口协议",new String[]{"OpenAI 兼容 Chat Completions","Gemini GenerateContent"},AiProviders.GEMINI.equals(original.protocol)?"Gemini GenerateContent":"OpenAI 兼容 Chat Completions");EditText key=AdvancedSettingsUi.secret(a,f,"API Key",original.key==null?"":original.key);EditText headers=a.field(f,"额外请求头（JSON 对象，可留空）",original.headers,InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE);headers.setMinLines(3);TextView error=a.text("",13,MainActivity.MUTED);error.setVisibility(View.GONE);f.addView(error);View[] fields=new View[]{name,base,model,protocol,key,headers};
  ScrollView formScroll=new ScrollView(a);formScroll.addView(f);AlertDialog d=new RoundedDialogs.Builder(a).setTitle("AI 配置").setView(formScroll).setNegativeButton("取消",null).setNeutralButton("删除",null).setPositiveButton("验证并保存",null).create();
  d.setOnShowListener(v->{d.getButton(-3).setOnClickListener(x->{ArrayList<AiProviders.Profile> all=AiProviders.read(a);all.removeIf(p->p.id.equals(original.id));try{AiProviders.save(a,all,AiProviders.selected(a).equals(original.id)?"":AiProviders.selected(a));d.dismiss();renderProviders(page);a.toast("配置已删除");}catch(Exception e){a.toast("删除失败");}});d.getButton(-1).setOnClickListener(x->{AiProviders.Profile candidate=new AiProviders.Profile(original.id,name.getText().toString().trim(),base.getText().toString().trim(),model.getText().toString().trim(),protocol.getSelectedItemPosition()==1?AiProviders.GEMINI:AiProviders.OPENAI);candidate.key=key.getText().toString().trim();candidate.headers=headers.getText().toString().trim();final long startRevision=config.revision();error.setText("正在验证，请稍候…");error.setVisibility(View.VISIBLE);for(View field:fields)field.setEnabled(false);d.getButton(-1).setEnabled(false);d.getButton(-3).setEnabled(false);new Thread(()->{String failure=null;try{AiProviders.verify(candidate);}catch(Exception e){failure=e.getMessage()==null?"验证失败，请重试":e.getMessage();}String result=failure;a.runOnUiThread(()->{if(!d.isShowing()||!page.alive()||a.isDestroyed()||a.isFinishing())return;for(View field:fields)field.setEnabled(true);d.getButton(-1).setEnabled(true);d.getButton(-3).setEnabled(true);if(result!=null){error.setText(result);error.setVisibility(View.VISIBLE);a.toast(result);return;}try{ArrayList<AiProviders.Profile> all=AiProviders.read(a);boolean replaced=false;for(int i=0;i<all.size();i++)if(all.get(i).id.equals(candidate.id)){all.set(i,candidate);replaced=true;}if(!replaced)all.add(candidate);if(!AiProviders.saveIfRevision(a,all,candidate.id,startRevision)){error.setText("配置已变更，请重新验证后保存");error.setVisibility(View.VISIBLE);a.toast("配置已变更，请重新验证后保存");return;}d.dismiss();renderProviders(page);a.toast("验证成功，已保存并设为当前服务");if(afterProviderSave!=null){Runnable resume=afterProviderSave;afterProviderSave=null;page.dialog.dismiss();resume.run();}}catch(Exception e){String message=e.getMessage()==null?"保存失败":e.getMessage();error.setText(message);error.setVisibility(View.VISIBLE);a.toast(message);}});},"ai-provider-verify").start();});});d.show();}
 void showSearchConfig(){showSearchConfig(null);}
 AlertDialog showSearchConfig(Runnable afterSave){
  LinearLayout f=a.col();a.pad(f,20);
  EditText key=AdvancedSettingsUi.secret(a,f,"百度智能搜索 API Key",config.searchKey());
  f.addView(a.text("用于百度千帆 AI 搜索。保存前会执行一次实际搜索请求。",13,MainActivity.MUTED));
  TextView error=a.text("",13,MainActivity.MUTED);error.setVisibility(View.GONE);f.addView(error);
  AlertDialog d=new RoundedDialogs.Builder(a).setTitle("百度智能搜索").setView(f).setNegativeButton("取消",null).setNeutralButton("清除配置",null).setPositiveButton("验证并保存",null).create();
  d.setOnShowListener(v->{
   d.getButton(-3).setOnClickListener(x->{try{config.put("baiduSearchKey","");d.dismiss();a.toast("配置已清除");}catch(Exception e){error.setText("清除失败，请重试");error.setVisibility(View.VISIBLE);}});
   d.getButton(-1).setOnClickListener(x->{
    String candidate=key.getText().toString().trim();
    if(candidate.isEmpty()){error.setText("请输入 API Key");error.setVisibility(View.VISIBLE);return;}
    final long startRevision=config.revision();
    error.setText("正在验证，请稍候…");error.setVisibility(View.VISIBLE);
    key.setEnabled(false);d.getButton(-1).setEnabled(false);d.getButton(-3).setEnabled(false);
    new Thread(()->{
     String failure=null;try{searchVerifier.verify(candidate);}catch(Exception e){failure=e.getMessage()==null?"验证失败，请重试":e.getMessage();}
     String result=failure;a.runOnUiThread(()->{
      if(!d.isShowing()||a.isDestroyed()||a.isFinishing())return;
      key.setEnabled(true);d.getButton(-1).setEnabled(true);d.getButton(-3).setEnabled(true);
      if(result!=null){error.setText(result);error.setVisibility(View.VISIBLE);return;}
      try{
       if(!config.putIfRevision(startRevision,"baiduSearchKey",candidate)){error.setText("配置已变更，请重新验证后保存");error.setVisibility(View.VISIBLE);return;}
       d.dismiss();a.toast("验证成功，已保存");if(afterSave!=null)afterSave.run();
      }catch(Exception e){error.setText("保存失败，请重试");error.setVisibility(View.VISIBLE);}
     });
    },"baidu-search-verify").start();
   });
  });d.show();return d;
 }
}
