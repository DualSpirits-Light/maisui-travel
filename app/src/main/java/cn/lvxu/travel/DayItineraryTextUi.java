package cn.lvxu.travel;
import android.content.*;import android.widget.*;
/** Explicit preview before a clipboard copy or system share chooser. */
final class DayItineraryTextUi {
 static PageUi show(MainActivity a,Trip trip,int day){
  PageUi page=new PageUi(a,"复制当日日程");page.body.addView(a.text("默认只包含日期、地点、计划时间和交通，不包含费用、同行人、票号及图片。",13,MainActivity.MUTED));
  CheckBox notes=new CheckBox(a);notes.setText("包含地点地址和备注");notes.setTextColor(MainActivity.INK);notes.setMinHeight(a.dp(48));page.body.addView(notes);
  LinearLayout box=a.card(page.body);TextView preview=a.text(DayItineraryText.format(trip,day,false),15,MainActivity.INK);preview.setTextIsSelectable(true);box.addView(preview);
  notes.setOnCheckedChangeListener((v,checked)->preview.setText(DayItineraryText.format(trip,day,checked)));
  a.pair(page.body,a.action("复制文字",true,()->{ClipboardManager clipboard=(ClipboardManager)a.getSystemService(Context.CLIPBOARD_SERVICE);clipboard.setPrimaryClip(ClipData.newPlainText("麦穗旅序当日日程",preview.getText()));a.toast("已复制当日日程");}),a.action("发送给同行人",false,()->{try{a.startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,preview.getText().toString()),"发送当日日程"));}catch(ActivityNotFoundException e){a.toast("没有可用的分享应用，请复制文字");}}));
  page.show();return page;
 }
}
