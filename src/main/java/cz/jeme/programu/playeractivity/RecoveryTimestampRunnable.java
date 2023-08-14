package cz.jeme.programu.playeractivity;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Level;

public class RecoveryTimestampRunnable extends BukkitRunnable {
    private static final String RECOVERY_FILE_NAME = "recovery.yml";
    public static final SimpleDateFormat DATE_FORMATTER = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private FileConfiguration recoveryYml = null;
    private final File recoveryFile;

    public RecoveryTimestampRunnable(File dataFolder) {
        String recoveryFilePath = dataFolder.getAbsolutePath() + File.separator + RECOVERY_FILE_NAME;
        recoveryFile = new File(recoveryFilePath);
        if (!recoveryFile.exists()) {
            try {
                recoveryFile.createNewFile();
            } catch (IOException e) {
                PlayerActivity.serverLog("Couldn't create " + RECOVERY_FILE_NAME + "!", e);
                return;
            }
        }
        recoveryYml = YamlConfiguration.loadConfiguration(recoveryFile);
    }

    public void start() {
        runTaskTimer(PlayerActivity.getPlugin(PlayerActivity.class), 0L, PlayerActivity.config.getLong("recovery.period"));
        if (PlayerActivity.config.getBoolean("logging.log-recovery-runnable")) {
            PlayerActivity.serverLog(Level.INFO, "Started recovery timestamp logging");
        }
    }

    @Override
    public void run() {
        recoveryYml.set("recovery-timestamp", DATE_FORMATTER.format(new Date()));
        try {
            recoveryYml.save(recoveryFile);
        } catch (IOException e) {
            PlayerActivity.serverLog("Couldn't save recovery timestamp to " + RECOVERY_FILE_NAME, e);
        }
    }

    @Override
    public synchronized void cancel() throws IllegalStateException {
        super.cancel();
        if (PlayerActivity.config.getBoolean("logging.log-recovery-runnable")) {
            PlayerActivity.serverLog(Level.INFO, "Stopped recovery timestamp logging");
        }
    }

    public Date getRecoveryDate() {
        if (recoveryYml == null) return null;
        try {
            return DATE_FORMATTER.parse(recoveryYml.getString("recovery-timestamp"));
        } catch (ParseException e) {
            PlayerActivity.serverLog("Couldn't read recovery timestamp from " + RECOVERY_FILE_NAME + "!", e);
            return null;
        }
    }
}
