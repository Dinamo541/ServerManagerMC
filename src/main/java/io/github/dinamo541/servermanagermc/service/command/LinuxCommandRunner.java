package io.github.dinamo541.servermanagermc.service.command;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Implementación real: ejecuta cada comando con {@code bash -c} en Monica
 * (perfil PROD) y captura su salida combinada.
 */
public final class LinuxCommandRunner implements CommandRunner {

    @Override
    public CommandResult run(String command) {
        try {
            ProcessBuilder pb = new ProcessBuilder("bash", "-c", command);
            pb.redirectErrorStream(true);
            Process p = pb.start();

            StringBuilder sb = new StringBuilder();
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    sb.append(line).append('\n');
                }
            }
            int code = p.waitFor();
            return new CommandResult(code, sb.toString());
        } catch (IOException ex) {
            return new CommandResult(-1, "Error de E/S: " + ex.getMessage());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return new CommandResult(-1, "Interrumpido: " + ex.getMessage());
        }
    }

    @Override
    public boolean isMock() {
        return false;
    }
}
