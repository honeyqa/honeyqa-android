package io.honeyqa.client.collector;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

import android.content.Context;

public class DateCollector {

    public static String GetDateYYMMDDHHMMSS(Context context) {
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        Date currentLocalTime = cal.getTime();
        DateFormat date = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        date.setTimeZone(TimeZone.getTimeZone("UTC"));
        String localTime = date.format(currentLocalTime);
        return localTime;
    }
}
