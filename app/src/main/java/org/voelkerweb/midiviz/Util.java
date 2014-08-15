package org.voelkerweb.midiviz;

import java.text.SimpleDateFormat;
import java.util.Date;

public class Util
{
    public static String formatTime(long timestamp)
    {
        return new SimpleDateFormat("HH:mm:ss.SSS").format(new Date(timestamp));
    }

    public static int byteToUnsignedInt(byte b)
    {
        return (int) b & 0xFF;
    }

    public static float safeDiv(int a, int b) { return ((float) a) / ((float) b); }
    public static float safeDiv(int a, long b) { return ((float) a) / ((float) b); }
    public static float safeDiv(long a, int b) { return ((float) a) / ((float) b); }
    public static float safeDiv(long a, long b) { return ((float) a) / ((float) b); }
}
