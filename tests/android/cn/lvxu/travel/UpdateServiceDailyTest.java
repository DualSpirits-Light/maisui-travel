package cn.lvxu.travel;

import android.content.Context;
import android.content.SharedPreferences;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/** Exercises the local, pre-network daily guard used at application launch. */
final class UpdateServiceDailyTest {
    private UpdateServiceDailyTest() {}

    static int run(Context context) {
        SharedPreferences preferences=context.getSharedPreferences("app-prefs-v2",Context.MODE_PRIVATE);
        Map<String,?> saved=new HashMap<>(preferences.getAll());
        int checks=0;
        try {
            check(preferences.edit().remove("lastUpdateDay").commit(),"clear daily update guard");checks++;
            LocalDate first=LocalDate.of(2031,4,5);
            check(UpdateService.markDailyCheck(context,first),"first launch is eligible");checks++;
            check(first.toString().equals(preferences.getString("lastUpdateDay","")),"first launch persists day before checking");checks++;
            check(!UpdateService.markDailyCheck(context,first),"same local day does not check twice");checks++;
            LocalDate tomorrow=first.plusDays(1);
            check(UpdateService.markDailyCheck(context,tomorrow),"next local day is eligible");checks++;
            check(tomorrow.toString().equals(preferences.getString("lastUpdateDay","")),"next day replaces persisted guard");checks++;
            return checks;
        } finally {
            SharedPreferences.Editor restore=preferences.edit().clear();
            for(Map.Entry<String,?> entry:saved.entrySet()){Object value=entry.getValue();if(value instanceof String)restore.putString(entry.getKey(),(String)value);else if(value instanceof Boolean)restore.putBoolean(entry.getKey(),(Boolean)value);else if(value instanceof Long)restore.putLong(entry.getKey(),(Long)value);else if(value instanceof Integer)restore.putInt(entry.getKey(),(Integer)value);else if(value instanceof Float)restore.putFloat(entry.getKey(),(Float)value);}
            restore.commit();
        }
    }
    private static void check(boolean value,String label){if(!value)throw new AssertionError(label);}
}
