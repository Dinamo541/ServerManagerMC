package io.github.dinamo541.servermanagermc.service.command;

/**
 * Resultado crudo de ejecutar un comando del sistema: código de salida y salida
 * combinada (stdout + stderr).
 */
public record CommandResult(int exitCode, String output) {

    public boolean ok() {
        return exitCode == 0;
    }

    public String trimmed() {
        return output == null ? "" : output.strip();
    }
}
