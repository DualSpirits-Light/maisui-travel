package cn.lvxu.travel;
import android.app.*;
import android.text.InputType;
import android.widget.*;

final class AdvancedSettingsUi {
 private final MainActivity a;private final ApiConfig config;
 AdvancedSettingsUi(MainActivity a){this.a=a;config=new ApiConfig(a);}
 void show(){PageUi page=new PageUi(a,"高级设置");LinearLayout box=page.body;
  box.addView(a.bold("地图服务",20,MainActivity.INK));Spinner provider=a.select(box,"地图服务商",MapService.NAMES,MapService.name(config.mapProvider()));
  box.addView(a.action("应用地图服务商",false,()->{try{config.put("mapProvider",MapService.IDS[provider.getSelectedItemPosition()]);a.toast("地图服务已切换");a.render();}catch(Exception e){a.toast("设置未能保存，请重试");}}));
  box.addView(a.text("请配置服务端 / Web 服务类型的 Key。地图搜索与路线请求使用所选服务，未配置时仍保留已有地点和离线动线。",13,MainActivity.MUTED));
  for(int i=0;i<MapService.IDS.length;i++){final String id=MapService.IDS[i];box.addView(a.action(MapService.NAMES[i]+" API Key  ›",false,()->mapKey(id)));}
  a.space(box,14);Switch coords=new Switch(a);coords.setText("显示地点坐标信息");coords.setTextColor(MainActivity.INK);coords.setChecked(a.prefs.showPlaceCoordinates());coords.setOnCheckedChangeListener((v,on)->a.prefs.setShowPlaceCoordinates(on));box.addView(coords);
  box.addView(a.action("首页随机风景接口  ›",false,this::scenery));
  box.addView(a.action("百度智能搜索配置  ›",false,()->new AiSettingsUi(a).showSearchConfig()));
  box.addView(a.action("AI 服务商  ›",false,()->new AiSettingsUi(a).showProviders()));
  box.addView(a.action("AI 规划默认提示词  ›",false,this::prompt));
  box.addView(a.action("恢复默认设置",false,()->reset(page)));page.show();
 }
 static EditText secret(MainActivity a,LinearLayout f,String label,String value){EditText input=a.field(f,label,value,InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);CheckBox show=new CheckBox(a);show.setText("显示密钥");show.setTextColor(MainActivity.MUTED);show.setOnCheckedChangeListener((v,on)->{input.setTransformationMethod(on?null:android.text.method.PasswordTransformationMethod.getInstance());input.setSelection(input.length());});f.addView(show);return input;}
 private void mapKey(String id){LinearLayout f=a.col();a.pad(f,20);EditText key=secret(a,f,"Web 服务 Key / AK",config.mapKey(id));AlertDialog d=new AlertDialog.Builder(a).setTitle(MapService.name(id)).setView(f).setNegativeButton("取消",null).setNeutralButton("清除配置",(x,w)->{try{config.put("mapKey."+id,"");a.toast("配置已清除");}catch(Exception e){a.toast("清除失败");}}).setPositiveButton("验证并保存",null).create();d.setOnShowListener(v->d.getButton(-1).setOnClickListener(vv->{String candidate=key.getText().toString().trim();if(candidate.isEmpty()){a.toast("请输入地图 API Key");return;}final long startRevision=config.revision();d.getButton(-1).setEnabled(false);new Thread(()->{String error=null;try{MapService.validate(id,candidate);}catch(Exception e){error=e.getMessage()==null?"验证失败，请重试":e.getMessage();}final String result=error;a.runOnUiThread(()->{if(!d.isShowing()||a.isDestroyed()||a.isFinishing())return;d.getButton(-1).setEnabled(true);if(result!=null){a.toast(result);return;}try{if(!config.putIfRevision(startRevision,"mapKey."+id,candidate)){a.toast("配置已变更，请重新验证后保存");return;}d.dismiss();a.toast("验证成功，已保存");}catch(Exception e){a.toast("保存失败");}});},"map-config-check").start();}));d.show();}
 private void scenery(){LinearLayout f=a.col();EditText url=a.field(f,"HTTPS 图片地址（留空使用默认）",config.scenery(),InputType.TYPE_TEXT_VARIATION_URI);a.dialog("首页随机风景接口",f,()->{String value=url.getText().toString().trim();if(!value.isEmpty())ApiHttp.url(value);try{config.put("scenery",value);a.toast("风景接口已保存");}catch(Exception e){throw new IllegalArgumentException("设置未能保存");}},null);}
 private void prompt(){LinearLayout f=a.col();EditText value=a.field(f,"默认提示词",config.prompt(),InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE);value.setMinLines(8);f.addView(a.action("恢复默认提示词",false,()->value.setText(ApiConfig.DEFAULT_PROMPT)));a.dialog("AI 规划默认提示词",f,()->{try{config.put("prompt",a.required(value,8000));a.toast("提示词已保存");}catch(Exception e){throw new IllegalArgumentException("请输入不超过 8000 字的提示词");}},null);}
 private void reset(PageUi page){LinearLayout f=a.col();a.pad(f,20);f.addView(a.text("将恢复主题、首页、导航和高级服务配置，并清除 WebDAV 连接配置。旅行、照片、授权及备份文件会保留。请输入：我已知悉",14,MainActivity.INK));EditText input=a.field(f,"确认文字","",InputType.TYPE_CLASS_TEXT);AlertDialog d=new AlertDialog.Builder(a).setTitle("恢复默认设置？").setView(f).setNegativeButton("取消",null).setPositiveButton("恢复默认",null).create();d.setOnShowListener(v->d.getButton(-1).setOnClickListener(vv->{if(!"我已知悉".equals(input.getText().toString())){a.toast("请输入完整的“我已知悉”");return;}try{config.reset();a.prefs.resetSettings();new WebDavService(a).clear();a.getSharedPreferences("about-support-v1",0).edit().clear().apply();d.dismiss();page.dialog.dismiss();a.render();a.toast("设置已恢复，旅行数据已保留");}catch(Exception e){a.toast("部分设置未能恢复，请重试");}}));d.show();}
}
