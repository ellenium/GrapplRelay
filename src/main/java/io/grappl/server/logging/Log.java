package io.grappl.server.logging;

import java.text.DateFormat;
import java.util.Date;

public class Log {

    public static boolean displayDetailed = true;

    public static void debug(String debug) {
        if(displayDetailed)
            log(debug);
    }

    public static void log(String toBeLogged) {
        String tag = DateFormat.getDateTimeInstance().format(new Date(System.currentTimeMillis()));
        String[] spl = tag.split("\\s+");
        String realTag = spl[3] + " " + spl[4];
        System.out.println("[" + realTag + "] " + toBeLogged);
    }

    public static void raw(String raw) {
        System.out.println(raw);
    }
}
