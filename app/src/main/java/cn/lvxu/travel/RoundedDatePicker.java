package cn.lvxu.travel;

import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.*;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.function.Consumer;

/** App-owned calendar: all seven columns and the header share the dialog's measured width. */
final class RoundedDatePicker {
 private RoundedDatePicker() {}
 static AlertDialog show(MainActivity a,LocalDate initial,Consumer<LocalDate> confirmed){
  final LocalDate[] selected={initial};final YearMonth[] month={YearMonth.from(initial)};
  LinearLayout content=a.col();content.setPadding(0,0,0,0);
  LinearLayout header=a.col();header.setPadding(a.dp(20),a.dp(16),a.dp(20),a.dp(16));
  GradientDrawable top=a.shape(MainActivity.GREEN,0);float r=a.dp(24);top.setCornerRadii(new float[]{r,r,r,r,0,0,0,0});header.setBackground(top);
  TextView year=a.text(initial.getYear()+"年 ▾",16,MainActivity.readable(Color.WHITE,MainActivity.GREEN));year.setPadding(0,a.dp(6),0,a.dp(6));year.setContentDescription("选择年份");
  TextView chosen=a.text("",24,MainActivity.readable(Color.WHITE,MainActivity.GREEN));header.addView(year);header.addView(chosen);content.addView(header,new LinearLayout.LayoutParams(-1,-2));
  LinearLayout body=a.col();body.setPadding(a.dp(12),a.dp(8),a.dp(12),a.dp(4));content.addView(body,new LinearLayout.LayoutParams(-1,-2));
  LinearLayout navigation=a.row();TextView previous=control(a,"‹","上个月"),caption=control(a,"","当前月份"),next=control(a,"›","下个月");
  navigation.addView(previous,new LinearLayout.LayoutParams(a.dp(48),a.dp(48)));navigation.addView(caption,new LinearLayout.LayoutParams(0,a.dp(48),1));navigation.addView(next,new LinearLayout.LayoutParams(a.dp(48),a.dp(48)));body.addView(navigation);
  LinearLayout weekdays=a.row();for(String day:new String[]{"一","二","三","四","五","六","日"})weekdays.addView(control(a,day,"星期"+day),new LinearLayout.LayoutParams(0,a.dp(32),1));body.addView(weekdays);
  LinearLayout days=a.col();body.addView(days,new LinearLayout.LayoutParams(-1,-2));
  Runnable[] render={null};render[0]=()->{
   year.setText(selected[0].getYear()+"年 ▾");chosen.setText(selected[0].getMonthValue()+"月"+selected[0].getDayOfMonth()+"日");caption.setText(month[0].getYear()+"年"+month[0].getMonthValue()+"月");days.removeAllViews();
   int offset=month[0].atDay(1).getDayOfWeek().getValue()-1;
   for(int row=0;row<6;row++){LinearLayout week=a.row();for(int col=0;col<7;col++){
    int day=row*7+col-offset+1;TextView cell=control(a,"","");
    if(day>=1&&day<=month[0].lengthOfMonth()){LocalDate date=month[0].atDay(day);cell.setText(""+day);cell.setContentDescription(date.toString());cell.setSelected(date.equals(selected[0]));
     if(date.equals(selected[0])){cell.setBackground(a.shape(MainActivity.GREEN,24));cell.setTextColor(MainActivity.readable(Color.WHITE,MainActivity.GREEN));}
     cell.setOnClickListener(v->{selected[0]=date;render[0].run();});
    }else cell.setImportantForAccessibility(android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    week.addView(cell,new LinearLayout.LayoutParams(0,a.dp(42),1));
   }days.addView(week,new LinearLayout.LayoutParams(-1,-2));}
   ThemeViews.apply(content,MainActivity.SURFACE);
  };
  previous.setOnClickListener(v->{if(month[0].getYear()>1||month[0].getMonthValue()>1){month[0]=month[0].minusMonths(1);render[0].run();}});
  next.setOnClickListener(v->{if(month[0].getYear()<9999||month[0].getMonthValue()<12){month[0]=month[0].plusMonths(1);render[0].run();}});
  year.setOnClickListener(v->{LinearLayout f=a.col();a.pad(f,16);EditText input=a.field(f,"年份（1–9999）",""+month[0].getYear(),android.text.InputType.TYPE_CLASS_NUMBER);
   AlertDialog years=new RoundedDialogs.Builder(a).setTitle("选择年份").setView(f).setNegativeButton("取消",null).setPositiveButton("确定",null).create();years.setOnShowListener(d->years.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(vv->{try{int y=Integer.parseInt(input.getText().toString());if(y<1||y>9999)throw new IllegalArgumentException();month[0]=month[0].withYear(y);selected[0]=selected[0].withYear(y);render[0].run();years.dismiss();}catch(Exception e){input.setError("请输入 1–9999 年");}}));years.show();});
  render[0].run();
  ScrollView scroll=new ScrollView(a);scroll.setFillViewport(true);scroll.addView(content,new android.view.ViewGroup.LayoutParams(-1,-2));
  AlertDialog dialog=new RoundedDialogs.Builder(a).setNegativeButton("取消",null).setPositiveButton("确定",(d,w)->confirmed.accept(selected[0])).create();
  dialog.setView(scroll,0,0,0,0);dialog.show();int width=Math.min(a.getResources().getDisplayMetrics().widthPixels-a.dp(24),a.dp(400));dialog.getWindow().setLayout(width,-2);return dialog;
 }
 private static TextView control(MainActivity a,String text,String description){TextView v=a.text(text,16,MainActivity.INK);v.setGravity(Gravity.CENTER);v.setContentDescription(description);v.setMinWidth(0);v.setPadding(0,0,0,0);return v;}
}
