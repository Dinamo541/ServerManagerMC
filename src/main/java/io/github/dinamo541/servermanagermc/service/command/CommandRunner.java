package io.github.dinamo541.servermanagermc.service.command;

/**
 * Abstracción para ejecutar comandos del sistema. Permite intercambiar la
 * implementación real ({@link LinuxCommandRunner}) por una simulada
 * ({@link MockCommandRunner}) para desarrollar en Windows, y a futuro por una
 * que hable con un backend remoto (Nivel 2).
 */
public interface CommandRunner {

    /** Ejecuta {@code command} en un shell y devuelve su resultado. */
    CommandResult run(String command);

    /** {@code true} si esta implementación simula (no ejecuta de verdad). */
    boolean isMock();
}
