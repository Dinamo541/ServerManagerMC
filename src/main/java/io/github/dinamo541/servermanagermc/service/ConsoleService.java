package io.github.dinamo541.servermanagermc.service;

import io.github.dinamo541.corefx.util.Answer;
import io.github.dinamo541.corefx.util.Validator;
import io.github.dinamo541.servermanagermc.config.ServerPaths;
import io.github.dinamo541.servermanagermc.service.command.CommandResult;
import io.github.dinamo541.servermanagermc.service.command.CommandRunner;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * Consola en vivo: hace "tail" de {@code latest.log} en un hilo de fondo y envía
 * comandos al juego vía {@code tmux send-keys}. El tail es resistente a la
 * rotación del log (al reiniciar el server, latest.log se recrea).
 */
public final class ConsoleService {

    private final CommandRunner runner;
    private volatile boolean tailing;
    private Thread tailThread;

    public ConsoleService(CommandRunner runner) {
        this.runner = runner;
    }

    /**
     * Envía un comando a la consola del juego vía tmux. Sanea la entrada para que
     * no pueda romper la cadena entre comillas ni inyectar comandos de shell.
     */
    public Answer sendCommand(String raw) {
        Validator v = Validator.getInstance();
        if (v.isBlank(raw)) {
            return Answer.failure("El comando está vacío.");
        }
        String cmd = raw.strip();
        if (cmd.contains("\"") || cmd.contains("`") || cmd.contains("$(")) {
            return Answer.failure("El comando contiene caracteres no permitidos (\" ` $().");
        }
        CommandResult r = runner.run(
                "tmux send-keys -t " + ServerPaths.TMUX_SESSION + " \"" + cmd + "\" Enter");
        return r.ok()
                ? Answer.success("Comando enviado: " + cmd)
                : Answer.failure("No se pudo enviar el comando.", r.trimmed());
    }

    /** Arranca el tail. {@code onLine} se invoca en un hilo de fondo. */
    public void startTail(Consumer<String> onLine) {
        if (tailing) {
            return;
        }
        tailing = true;
        tailThread = Thread.ofVirtual().name("mc-log-tail").start(() -> tailLoop(onLine));
    }

    public void stopTail() {
        tailing = false;
        if (tailThread != null) {
            tailThread.interrupt();
        }
    }

    private void tailLoop(Consumer<String> onLine) {
        if (runner.isMock()) {
            mockTail(onLine);
            return;
        }
        Path log = Path.of(ServerPaths.LOG);
        long position = 0;
        while (tailing) {
            try {
                if (Files.exists(log)) {
                    try (RandomAccessFile raf = new RandomAccessFile(log.toFile(), "r")) {
                        long length = raf.length();
                        if (length < position) {
                            position = 0; // log truncado/recreado (rotación)
                        }
                        raf.seek(position);
                        String line;
                        while ((line = raf.readLine()) != null) {
                            // readLine() lee bytes como ISO-8859-1; reinterpretar como UTF-8.
                            onLine.accept(new String(
                                    line.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8));
                        }
                        position = raf.getFilePointer();
                    }
                }
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (IOException e) {
                sleepQuiet(1000);
            }
        }
    }

    private void mockTail(Consumer<String> onLine) {
        int i = 0;
        while (tailing) {
            onLine.accept(String.format(
                    "[12:00:%02d] [Server thread/INFO]: [mock] línea de log simulada #%d", i % 60, i));
            i++;
            if (!sleepQuiet(1200)) {
                break;
            }
        }
    }

    private boolean sleepQuiet(long ms) {
        try {
            Thread.sleep(ms);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
