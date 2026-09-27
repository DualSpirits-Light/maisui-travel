package cn.lvxu.travel;
import android.app.*;
import android.text.InputType;
import android.widget.*;

final class AdvancedSettingsUi {
 interface MapVerifier {void verify(String id,String key)throws Exception;}
 private final MainActivity a;private final ApiConfig config;private final MapVerifier verifier;
 AdvancedSettingsUi(MainActivity a){this(a,MapService::validate);}
 AdvancedSettingsUi(MainActivity a,MapVerifier verifier){this.a=a;config=new ApiConfig(a);this.verifier=verifier;}
 void show(){PageUi page=new PageUi(a,"高级设置");LinearLayout box=page.body;
  box.addView(a.bold("地图服务",20,MainActivity.INK));Spinner provider=a.select(box,"地图服务商",MapService.NAMES,MapService.name(config.mapProvider()));
  box.addView(a.action("应用地图服务商",false,()->{try{config.put("mapProvider",MapService.IDS[provider.getSelectedItemPosition()]);a.toast("地图服务已切换");a.render();}catch(Exception e){a.toast("设置未能保存，请重试");}}));
  box.addView(a.text("高德地图与地点搜索使用 Android SDK Key。请在本机填写您自己的 Android 平台 Key；下方 Web 服务 Key 用于独立的 Web 接口，不能互换。",13,MainActivity.MUTED));
  box.addView(a.action("高德 Android SDK Key  ›",false,()->androidKey(null)));
  for(int i=0;i<MapService.IDS.length;i++){final String id=MapService.IDS[i];box.addView(a.action(MapService.NAMES[i]+" Web 服务配置  ›",false,()->mapKey(id)));}
  a.space(box,14);Switch coords=new Switch(a);coords.setText("显示地点坐标信息");coords.setTextColor(MainActivity.INK);coords.setChecked(a.prefs.showPlaceCoordinates());coords.setOnCheckedChangeListener((v,on)->a.prefs.setShowPlaceCoordinates(on));box.addView(coords);
  box.addView(a.action("首页随机风景接口  ›",false,this::scenery));
  box.addView(a.action("百度智能搜索配置  ›",false,()->new AiSettingsUi(a).showSearchConfig()));
  box.addView(a.action("AI 服务商  ›",false,()->new AiSettingsUi(a).showProviders()));
  box.addView(a.action("AI 规划默认提示词  ›",false,this::prompt));
  box.addView(a.action("恢复默认设置",false,()->reset(page)));page.show();
 }
 static EditText secret(MainActivity a,LinearLayout f,String label,String value){EditText input=a.field(f,label,value,InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);CheckBox show=new CheckBox(a);show.setText("显示密钥");show.setTextColor(MainActivity.MUTED);show.setOnCheckedChangeListener((v,on)->{input.setTransformationMethod(on?null:android.text.method.PasswordTransformationMethod.getInstance());input.setSelection(input.length());});f.addView(show);return input;}
 AlertDialog androidKey(Runnable afterSave){
  LinearLayout f=a.col();a.pad(f,20);
  f.addView(a.text("填写高德开放平台为本应用包名和安装证书创建的 Android 平台 Key。此处只保存在本机加密配置中；保存时无法在线验证 Android Key 是否有效。请勿填写 Web 服务 Key。",13,MainActivity.MUTED));
  TextView identity=a.text(AmapIdentity.description(a),12,MainActivity.MUTED);identity.setTextIsSelectable(true);f.addView(identity);
  EditText key=secret(a,f,"Android SDK Key",config.amapAndroidKey());
  TextView error=a.text("",13,MainActivity.ORANGE);f.addView(error);
  AlertDialog d=new RoundedDialogs.Builder(a).setTitle("高德 Android SDK Key").setView(scroll(f)).setNegativeButton("取消",null)
    .setNeutralButton("清除 Key",null).setPositiveButton("保存",null).create();
  d.setOnShowListener(v->{d.getButton(-3).setOnClickListener(w->new RoundedDialogs.Builder(a)
    .setTitle("清除高德 Android Key？")
    .setMessage("清除后，高德原生地图和地点搜索需要重新配置。")
    .setNegativeButton("取消",null).setPositiveButton("清除",(dialog,which)->{
      try{config.put("amapAndroidKey","");d.dismiss();a.invalidateAndroidMap();a.toast("Android Key 已清除");
       if(AmapRuntime.needsRestart(a))new RoundedDialogs.Builder(a).setTitle("需要重启应用")
         .setMessage("当前进程已初始化高德 SDK，请关闭并重新打开应用。")
         .setPositiveButton("知道了",null).show();
      }catch(Exception e){error.setText("清除失败，请重试");}
    }).show());
   d.getButton(-1).setOnClickListener(w->{String candidate=key.getText().toString().trim();
   if(candidate.isEmpty()){error.setText("请输入 Android 平台 Key");return;}
   if(candidate.length()>256||candidate.matches("(?s).*\\s.*")){error.setText("Key 格式有误，请检查输入");return;}
   try{boolean changed=!candidate.equals(config.amapAndroidKey());config.put("amapAndroidKey",candidate);d.dismiss();if(changed&&AmapRuntime.needsRestart(a))a.invalidateAndroidMap();
    if(AmapRuntime.needsRestart(a))new RoundedDialogs.Builder(a).setTitle("需要重启应用")
      .setMessage("当前进程已经使用过另一个高德 Android Key。请关闭并重新打开应用后，再使用高德地图或搜索。")
      .setPositiveButton("知道了",null).show();
    else {a.toast("Android Key 已保存");if(afterSave!=null)afterSave.run();}
   }catch(Exception e){error.setText("保存失败，请重试");}
  });});d.show();return d;
 }
 private android.widget.ScrollView scroll(LinearLayout form){android.widget.ScrollView view=new android.widget.ScrollView(a);view.addView(form);return view;}
 void mapKey(String id){mapKey(id,null);}
 AlertDialog mapKey(String id,Runnable afterSave){
  LinearLayout f=a.col();a.pad(f,20);
  f.addView(a.text("amap".equals(id)?"此处是可选的 Web 服务密钥，与高级设置中的高德 Android SDK Key 分开保存。请勿填写 Android 平台密钥。":"当前接入使用 Web 服务接口，需要服务端 / Web 服务类型的密钥。Android 平台密钥不适用于此处。",13,MainActivity.MUTED));
  EditText key=secret(a,f,"Web 服务 Key / AK",config.mapKey(id));
  TextView error=a.text("",13,MainActivity.MUTED);error.setVisibility(android.view.View.GONE);f.addView(error);
  AlertDialog d=new RoundedDialogs.Builder(a).setTitle(MapService.name(id)+" · Web 服务").setView(f).setNegativeButton("取消",null).setNeutralButton("清除 Web 配置",(x,w)->{try{config.put("mapKey."+id,"");a.toast("Web 配置已清除");}catch(Exception e){a.toast("清除失败");}}).setPositiveButton("验证并保存",null).create();
  d.setOnShowListener(v->d.getButton(-1).setOnClickListener(vv->{
   String candidate=key.getText().toString().trim();error.setVisibility(android.view.View.VISIBLE);
   if(candidate.isEmpty()){error.setText("请输入 Web 服务 Key / AK");return;}
   final long startRevision=config.revision();key.setEnabled(false);d.getButton(-1).setEnabled(false);d.getButton(-3).setEnabled(false);error.setText("正在验证 Web 服务…");
   new Thread(()->{String failure=null;try{verifier.verify(id,candidate);}catch(Exception e){failure=e.getMessage()==null?"验证失败，请重试":e.getMessage();}final String result=failure;
    a.runOnUiThread(()->{if(!d.isShowing()||a.isDestroyed()||a.isFinishing())return;key.setEnabled(true);d.getButton(-1).setEnabled(true);d.getButton(-3).setEnabled(true);
     if(result!=null){error.setText(result);return;}
     try{if(!config.putIfRevision(startRevision,"mapKey."+id,candidate)){error.setText("配置已变更，请重新验证后保存");return;}d.dismiss();a.toast("验证成功，已保存");if(afterSave!=null)afterSave.run();}catch(Exception e){error.setText("保存失败，请重试");}
    });
   },"map-config-check").start();
  }));d.show();return d;
 }
 private void scenery(){LinearLayout f=a.col();EditText url=a.field(f,"HTTPS 图片地址（留空使用默认）",config.scenery(),InputType.TYPE_TEXT_VARIATION_URI);a.dialog("首页随机风景接口",f,()->{String value=url.getText().toString().trim();if(!value.isEmpty())ApiHttp.url(value);try{config.put("scenery",value);a.toast("风景接口已保存");}catch(Exception e){throw new IllegalArgumentException("设置未能保存");}},null);}
 private void prompt(){LinearLayout f=a.col();EditText value=a.field(f,"默认提示词",config.prompt(),InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE);value.setMinLines(8);f.addView(a.action("恢复默认提示词",false,()->value.setText(ApiConfig.DEFAULT_PROMPT)));a.dialog("AI 规划默认提示词",f,()->{try{config.put("prompt",a.required(value,8000));a.toast("提示词已保存");}catch(Exception e){throw new IllegalArgumentException("请输入不超过 8000 字的提示词");}},null);}
 private void reset(PageUi page){LinearLayout f=a.col();a.pad(f,20);f.addView(a.text("将恢复主题、首页、导航和高级服务配置，并清除 WebDAV 连接配置。旅行、照片、授权及备份文件会保留。请输入：我已知悉",14,MainActivity.INK));EditText input=a.field(f,"确认文字","",InputType.TYPE_CLASS_TEXT);AlertDialog d=new RoundedDialogs.Builder(a).setTitle("恢复默认设置？").setView(f).setNegativeButton("取消",null).setPositiveButton("恢复默认",null).create();d.setOnShowListener(v->d.getButton(-1).setOnClickListener(vv->{if(!"我已知悉".equals(input.getText().toString())){a.toast("请输入完整的“我已知悉”");return;}try{config.reset();a.prefs.resetSettings();new WebDavService(a).clear();a.getSharedPreferences("about-support-v1",0).edit().clear().apply();d.dismiss();page.dialog.dismiss();a.invalidateAndroidMap();a.toast("设置已恢复，旅行数据已保留");}catch(Exception e){a.toast("部分设置未能恢复，请重试");}}));d.show();}
}
