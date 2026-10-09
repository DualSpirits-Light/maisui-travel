package cn.lvxu.travel;

import android.app.TimePickerDialog;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Editable date/time fields with an app calendar and native clock pickers. */
final class DateTimeFields {
    private static final DateTimeFormatter DATE=DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter TIME=DateTimeFormatter.ofPattern("HH:mm");
    /** Editing fields use a space so a visible value never exposes the storage delimiter. */
    private static final DateTimeFormatter DATE_TIME=DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter LEGACY_DATE_TIME=DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
    private static final DateTimeFormatter DISPLAY_DATE_TIME=DateTimeFormatter.ofPattern("yyyy年M月d日 HH:mm");
    private static final Pattern TIME_IN_TEXT=Pattern.compile("(?<!\\d)([01]\\d|2[0-3]):[0-5]\\d(?!\\d)");

    private DateTimeFields() {}

    static EditText date(MainActivity a,LinearLayout form,String label,String value){
        EditText field=field(a,form,label,value,"选择日期");
        field.setInputType(InputType.TYPE_CLASS_TEXT);
        picker(field).setOnClickListener(v->pickDate(a,field,parseDate(field.getText().toString(),LocalDate.now())));
        return field;
    }

    static EditText time(MainActivity a,LinearLayout form,String label,String value){
        EditText field=field(a,form,label,value,"选择时间");
        field.setInputType(InputType.TYPE_CLASS_TEXT);
        picker(field).setOnClickListener(v->pickTime(a,field,parseTime(field.getText().toString(),LocalTime.now())));
        return field;
    }

    static EditText dateTime(MainActivity a,LinearLayout form,String label,String value){
        EditText field=field(a,form,label,normalizeDateTime(value),"选择日期和时间");
        field.setInputType(InputType.TYPE_CLASS_TEXT);
        picker(field).setOnClickListener(v->{
            LocalDateTime base=parseDateTime(field.getText().toString(),LocalDateTime.now());
            RoundedDatePicker.show(a,base.toLocalDate(),selected->{
                new TimePickerDialog(a,(clock,hour,minute)->field.setText(LocalDateTime.of(selected,LocalTime.of(hour,minute)).format(DATE_TIME)),base.getHour(),base.getMinute(),true).show();
            });
        });
        return field;
    }

    /** Offers daily opening hours as a start/end time pair; arbitrary imported text remains editable. */
    static EditText openingHours(MainActivity a,LinearLayout form,String label,String value){
        EditText field=field(a,form,label,value,"选择营业时间");
        field.setInputType(InputType.TYPE_CLASS_TEXT);
        picker(field).setOnClickListener(v->{
            LocalTime[] times=timesIn(field.getText().toString());
            LocalTime start=times[0]==null?LocalTime.of(9,0):times[0];
            LocalTime end=times[1]==null?LocalTime.of(18,0):times[1];
            new TimePickerDialog(a,(startPicker,startHour,startMinute)->new TimePickerDialog(a,(endPicker,endHour,endMinute)->{
                String selected=LocalTime.of(startHour,startMinute).format(TIME)+"–"+LocalTime.of(endHour,endMinute).format(TIME);
                field.setText(selected);
            },end.getHour(),end.getMinute(),true).show(),start.getHour(),start.getMinute(),true).show();
        });
        return field;
    }

    private static EditText field(MainActivity a,LinearLayout form,String label,String value,String pickerDescription){
        form.addView(a.text(label,13,MainActivity.MUTED));
        LinearLayout row=a.row();
        EditText field=new EditText(a);
        field.setText(value==null?"":value);
        field.setTextSize(16);
        field.setMinHeight(a.dp(48));
        field.setPadding(a.dp(14),a.dp(12),a.dp(8),a.dp(12));
        GradientDrawable background=a.shape(MainActivity.SURFACE,12);
        background.setStroke(a.dp(1),MainActivity.LINE);
        row.setBackground(background);
        field.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        field.setTextColor(MainActivity.INK);
        field.setHintTextColor(MainActivity.MUTED);
        field.setSingleLine(true);
        row.addView(field,new LinearLayout.LayoutParams(0,-2,1));
        ImageButton picker=new ImageButton(a);
        picker.setImageResource(R.drawable.ic_calendar);
        picker.setColorFilter(MainActivity.INK);
        picker.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        picker.setBackgroundTintList(null);

        picker.setContentDescription(pickerDescription);
        picker.setFocusable(true);
        picker.setPadding(a.dp(12),a.dp(12),a.dp(12),a.dp(12));
        LinearLayout.LayoutParams buttonParams=new LinearLayout.LayoutParams(a.dp(48),a.dp(48));
        buttonParams.gravity=Gravity.CENTER_VERTICAL;

        row.addView(picker,buttonParams);
        form.addView(row,new LinearLayout.LayoutParams(-1,-2));
        a.space(form,12);
        field.setTag(picker);
        return field;
    }

    private static ImageButton picker(EditText field){return (ImageButton)field.getTag();}
    private static void pickDate(MainActivity a,EditText field,LocalDate base){RoundedDatePicker.show(a,base,selected->field.setText(selected.format(DATE)));}
    private static void pickTime(MainActivity a,EditText field,LocalTime base){new TimePickerDialog(a,(dialog,hour,minute)->field.setText(LocalTime.of(hour,minute).format(TIME)),base.getHour(),base.getMinute(),true).show();}
    private static LocalDate parseDate(String text,LocalDate fallback){try{return LocalDate.parse(text.trim(),DATE);}catch(Exception ignored){return fallback;}}
    private static LocalTime parseTime(String text,LocalTime fallback){try{return LocalTime.parse(text.trim(),TIME);}catch(Exception ignored){return fallback;}}
    static LocalDateTime parseDateTime(String text){return parseDateTime(text,LocalDateTime.now());}
    static LocalDateTime parseDateTime(String text,LocalDateTime fallback){
        return DateTimeValues.parse(text,fallback);
    }
    /** Converts a legacy or editing value to the format shown in an editor. */
    static String normalizeDateTime(String text){
        if(text==null||text.trim().isEmpty())return "";
        return DateTimeValues.normalize(text);
    }
    /** Keeps the existing on-disk delimiter for callers that save a field value. */
    static String storageDateTime(String text){
        if(text==null||text.trim().isEmpty())return "";
        return DateTimeValues.storage(text);
    }
    /** Formats a stored date-time for any user-visible label. */
    static String displayDateTime(String text){
        if(text==null||text.trim().isEmpty())return "";
        return DateTimeValues.display(text);
    }
    private static LocalTime[] timesIn(String text){
        Matcher matcher=TIME_IN_TEXT.matcher(text==null?"":text);LocalTime first=null,second=null;
        if(matcher.find())first=LocalTime.parse(matcher.group(),TIME);
        if(matcher.find())second=LocalTime.parse(matcher.group(),TIME);
        return new LocalTime[]{first,second};
    }
}
