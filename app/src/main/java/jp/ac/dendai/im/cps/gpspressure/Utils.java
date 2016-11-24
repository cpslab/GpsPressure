package jp.ac.dendai.im.cps.gpspressure;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class Utils {

    public static String parseDate(long timeMillis) {
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd_HH:mm:ss", Locale.JAPAN);
        Date date = new Date(timeMillis);
        return df.format(date);
    }

    public static float rad2deg(float rad) {
        return (float) Math.toDegrees(rad);
    }
}
