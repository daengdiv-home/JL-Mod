package javax.microedition.shell;


import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.Keep;

import java.util.HashMap;
import java.util.Map;

/** {@link java.lang.System} Delegate for Midlet */
@Keep
public final class MidletSystem {
    private static final String TAG = MidletSystem.class.getSimpleName();

    private static final Map<String, String> PROPERTY = new HashMap<>();

    /** When fake time is active: offset = fakeStart - realStart, so that
     *  {@code currentTimeMillis()} = {@code System.currentTimeMillis() + timeOffset}
     *  and the clock continues ticking from the configured point. */
    private static volatile long timeOffset = 0;
    private static volatile boolean fakeTimeEnabled = false;

    static void setProperty(String key, String value) {
        PROPERTY.put(key, value);
    }

    /**
     * Configure the fake time.
     * @param enabled  whether to use fake time
     * @param fakeStartMillis  the epoch-millis the in-game clock should read
     *                         at the moment this method is called
     */
    public static void setFakeTime(boolean enabled, long fakeStartMillis) {
        fakeTimeEnabled = enabled;
        timeOffset = enabled ? fakeStartMillis - System.currentTimeMillis() : 0;
    }

    /** Drop-in replacement for {@link System#currentTimeMillis()} injected by the ASM transformer. */
    public static long currentTimeMillis() {
        return fakeTimeEnabled
                ? System.currentTimeMillis() + timeOffset
                : System.currentTimeMillis();
    }


    public static String getProperty(String key) {
        String value = PROPERTY.get(key);
        if (TextUtils.isEmpty(value)) value = System.getProperty(key);
        Log.d(TAG, "System.getProperty: " + key + "=" + value);
        return value;
    }

    public static String getProperty(String key, String def) {
        String value = PROPERTY.get(key);
        if (TextUtils.isEmpty(value)) value = System.getProperty(key, def);
        Log.d(TAG, "System.getProperty: " + key + "=" + value);
        return value;
    }

}
