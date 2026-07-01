package io.github.dinamo541.servermanagermc.config;

import io.github.dinamo541.servermanagermc.service.BackupService;
import io.github.dinamo541.servermanagermc.service.ConsoleService;
import io.github.dinamo541.servermanagermc.service.PlayerService;
import io.github.dinamo541.servermanagermc.service.PropertiesService;
import io.github.dinamo541.servermanagermc.service.ServerService;
import io.github.dinamo541.servermanagermc.service.command.CommandRunner;
import io.github.dinamo541.servermanagermc.service.command.LinuxCommandRunner;
import io.github.dinamo541.servermanagermc.service.command.MockCommandRunner;

/**
 * Localizador de servicios. Construye el {@link CommandRunner} adecuado según el
 * {@link AppProfile} y expone instancias únicas de los servicios para que los
 * controladores los consuman sin conocer cómo se ejecutan los comandos.
 *
 * <p>Mantener este punto único de creación facilita el Nivel 2 (remoto): bastará
 * con sustituir el {@code CommandRunner} por uno que hable con un backend REST.
 */
public final class Services {

    private static CommandRunner runner;
    private static ServerService server;
    private static ConsoleService console;
    private static PlayerService players;
    private static PropertiesService properties;
    private static BackupService backups;

    private Services() {
    }

    public static synchronized CommandRunner runner() {
        if (runner == null) {
            runner = AppProfile.current().isDev()
                    ? new MockCommandRunner()
                    : new LinuxCommandRunner();
        }
        return runner;
    }

    public static synchronized ServerService server() {
        if (server == null) {
            server = new ServerService(runner());
        }
        return server;
    }

    public static synchronized ConsoleService console() {
        if (console == null) {
            console = new ConsoleService(runner());
        }
        return console;
    }

    public static synchronized PlayerService players() {
        if (players == null) {
            players = new PlayerService(runner());
        }
        return players;
    }

    public static synchronized PropertiesService properties() {
        if (properties == null) {
            properties = new PropertiesService(runner());
        }
        return properties;
    }

    public static synchronized BackupService backups() {
        if (backups == null) {
            backups = new BackupService(runner());
        }
        return backups;
    }
}
