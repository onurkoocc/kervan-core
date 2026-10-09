package kervan.core.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.UnsynchronizedAppenderBase;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Writes each event as a JSON line to stdout and, when {@code file} is set (from {@code LOG_FILE}),
 * appends the same line to that file.
 */
public class JsonLinesAppender extends UnsynchronizedAppenderBase<ILoggingEvent> {

    private String service = "core";
    private String file;
    private PrintStream stdout = System.out;
    private JsonLineFormatter formatter;
    private OutputStream fileOut;

    public void setService(String service) {
        this.service = service;
    }

    public void setFile(String file) {
        this.file = file;
    }

    void setStdout(PrintStream stdout) {
        this.stdout = stdout;
    }

    @Override
    public void start() {
        formatter = new JsonLineFormatter(service);
        if (file != null && !file.isBlank()) {
            try {
                Path path = Path.of(file);
                if (path.getParent() != null) {
                    Files.createDirectories(path.getParent());
                }
                fileOut = new FileOutputStream(path.toFile(), true);
            } catch (IOException e) {
                addError("cannot open LOG_FILE " + file + "; logging to stdout only", e);
            }
        }
        super.start();
    }

    @Override
    protected void append(ILoggingEvent event) {
        byte[] line = formatter.format(event).getBytes(StandardCharsets.UTF_8);
        synchronized (this) {
            stdout.write(line, 0, line.length);
            stdout.flush();
            if (fileOut != null) {
                try {
                    fileOut.write(line);
                } catch (IOException e) {
                    addError("cannot write LOG_FILE " + file, e);
                }
            }
        }
    }

    @Override
    public void stop() {
        super.stop();
        synchronized (this) {
            if (fileOut != null) {
                try {
                    fileOut.close();
                } catch (IOException e) {
                    addError("cannot close LOG_FILE " + file, e);
                }
                fileOut = null;
            }
        }
    }
}
