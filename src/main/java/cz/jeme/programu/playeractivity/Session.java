package cz.jeme.programu.playeractivity;

import java.util.Date;
import java.util.UUID;

public class Session {
    public UUID uuid;
    public Date startDate;
    public Date endDate = null;
    public Integer playTime = null;

    public Session(UUID uuid, Date startDate) {
        this.uuid = uuid;
        this.startDate = startDate;
    }

    public Session(UUID uuid) {
        this(uuid, new Date());
    }

    public void close(Date endDate) {
        this.endDate = endDate;
        playTime = (int) (endDate.getTime() - startDate.getTime()) / 1000;
    }

    public static String translatePlayTime(int playTime) {
        long hours = playTime / 3600;
        long minutes = (playTime - hours * 3600) / 60;
        long seconds = playTime - hours * 3600 - minutes * 60;

        String hoursStr = "";
        String minutesStr = "";
        String secondsStr = "";
        if (hours > 0) {
            hoursStr = hours + "hrs ";
        }
        if (minutes > 0) {
            minutesStr = minutes + "min ";
        }
        if (seconds > 0) {
            secondsStr = seconds + "sec";
        }
        return hoursStr + minutesStr + secondsStr;
    }
}
