package cn.lvxu.travel;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;

/** Activation controls shared by settings and the activation entry point. */
final class LicenseUi {
    private final MainActivity a;
    LicenseUi(MainActivity activity){a=activity;}

    void show(LinearLayout parent){
        a.space(parent,16);parent.addView(a.bold("授权与激活",18,MainActivity.INK));a.space(parent,10);
        LinearLayout content=a.card(parent);populate(content);
    }

    private void populate(LinearLayout content){
        content.removeAllViews();CloudLicenseService service=a.prefs.cloud;
        if(service.hasLicense()){
            content.addView(a.bold(service.isValid()?"已激活":"需要联网验证",18,MainActivity.GREEN));
            content.addView(a.text(service.statusText(),13,MainActivity.MUTED));
            a.space(content,10);content.addView(a.action("立即联网验证",true,()->a.runJob("正在验证授权",()->{
                try{service.refresh();return "授权验证成功";}
                finally{a.runOnUiThread(()->populate(content));}
            },()->{populate(content);a.render();})));
        }else if(a.prefs.paid()){
            content.addView(a.bold("已激活 · 离线授权",18,MainActivity.GREEN));
            content.addView(a.text(a.prefs.activationSubject(),13,MainActivity.MUTED));
        }else{
            content.addView(a.text("输入开发者提供的授权码，联网激活当前设备。离线使用期限由授权策略决定。",13,MainActivity.MUTED));
        }
        a.space(content,10);content.addView(a.action(service.hasLicense()?"输入其他授权码":"输入授权码",!service.hasLicense(),()->enter(()->{populate(content);a.render();})));
        a.space(content,8);content.addView(a.action("复制本机设备编号",false,()->{
            ClipboardManager clipboard=(ClipboardManager)a.getSystemService(Context.CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(ClipData.newPlainText("麦穗旅序设备编号",service.deviceId()));a.toast("设备编号已复制，可提供给开发者处理换机");
        }));
        a.space(content,8);content.addView(a.text("换机请联系开发者解绑原设备。授权信息不会随旅行备份转移。",12,MainActivity.MUTED));
    }

    void enter(Runnable success){
        LinearLayout form=a.col();a.pad(form,22);
        EditText code=a.field(form,"授权码","",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        code.setSingleLine(false);code.setMaxLines(5);
        AlertDialog dialog=new AlertDialog.Builder(a).setTitle("激活麦穗旅序").setView(form).setNegativeButton("取消",null).setPositiveButton("激活",null).create();
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            String value=code.getText().toString().trim();
            if(value.isEmpty()||value.length()>12000){code.setError("请输入有效的授权码");return;}
            a.runJob("正在激活",()->{
                if(value.startsWith("LVX1.")){
                    if(a.prefs.cloud.hasLicense())throw new IllegalArgumentException("此设备已使用在线授权，请输入在线授权码");
                    new ActivationService(a.prefs).activate(value);
                }else a.prefs.cloud.activate(value);
                return "激活成功";
            },()->{code.setText("");dialog.dismiss();if(success!=null)success.run();});
        }));dialog.show();
    }
}
