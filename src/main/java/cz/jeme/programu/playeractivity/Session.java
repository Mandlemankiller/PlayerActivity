package cz.jeme.programu.playeractivity;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;

public class Session {
    public static final SimpleDateFormat DATE_FORMATTER = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    public UUID uuid;
    public Date startStamp;
    public Date endStamp = null;

    public Session(UUID uuid, Date startStamp) {
        this.uuid = uuid;
        this.startStamp = startStamp;
    }
    public Session(UUID uuid) {
        this(uuid, new Date());
    }
    public Session(UUID uuid, String startStamp) throws ParseException {
        this(uuid, parseDate(startStamp));
    }

    public void end() {
        endStamp = new Date();
//        playTime = (endStamp.getTime() - startStamp.getTime()) / 1000;
    }
    public static String formatDate(Date date) {
        return DATE_FORMATTER.format(date);
    }
    public static Date parseDate(String date) throws ParseException {
        return DATE_FORMATTER.parse(date);
    }
}
