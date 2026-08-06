package gg.vape.lifecycle;

import gg.vape.Vape;
import gg.vape.config.VapeStorage;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

public class ClientLifecycleLogWriter
implements ClientLifecycleCallback {
    private File logFile;
    private final SimpleDateFormat timestampFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private PrintWriter logWriter;

    private void closeFromShutdownHook() {
        this.close();
    }

    public ClientLifecycleLogWriter() {
        try {
            String clientDirectoryPath = VapeStorage.logsDirectory().toString();
            File clientDirectory = VapeStorage.logsDirectory().toFile();
            if (!clientDirectory.exists() && !clientDirectory.mkdirs() && !clientDirectory.isDirectory()) {
                throw new IOException("Unable to create " + clientDirectory);
            }
            String logFilePath = clientDirectoryPath + File.separator
                    + new SimpleDateFormat("yyyy-MM-dd-HH-mm").format(new Date()) + ".logs";
            Vape.debugLog("Creating log file at: " + logFilePath);
            this.logFile = new File(logFilePath);
            FileWriter fileWriter = new FileWriter(this.logFile, true);
            this.logWriter = new PrintWriter(fileWriter);
            Runtime.getRuntime().addShutdownHook(new Thread(this::closeFromShutdownHook));
        }
        catch (IOException iOException) {
            Vape.logThrowable(iOException);
        }
    }

    private static IOException propagateIOException(IOException exception) {
        return exception;
    }

    @Override
    public void log(String message) {
        try {
            String timestamp = this.timestampFormat.format(new Date());
            this.logWriter.printf("%s: %s%n", timestamp, message);
            this.logWriter.flush();
        }
        catch (Exception exception) {
            Vape.logThrowable(exception);
        }
    }

    @Override
    public void close() {
        try {
            if (this.logWriter != null) {
                this.logWriter.close();
            }
        }
        catch (Exception exception) {
            Vape.logThrowable(exception);
        }
    }
}
