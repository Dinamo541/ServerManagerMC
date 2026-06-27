package io.github.dinamo541.servermanagermc.service;

import io.github.dinamo541.corefx.util.Answer;
import io.github.dinamo541.servermanagermc.config.ServerPaths;
import io.github.dinamo541.servermanagermc.model.ServerState;
import io.github.dinamo541.servermanagermc.model.ServerStatus;
import io.github.dinamo541.servermanagermc.service.command.CommandResult;
import io.github.dinamo541.servermanagermc.service.command.CommandRunner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Encendido/apagado/reinicio y estado del servidor, vía systemctl. No gestiona
 * el proceso por su cuenta: ejecuta los mismos comandos que un humano (Opción A).
 */
public final class ServerService {

    private static final Pattern UPTIME = Pattern.compile(";\\s*([^;]+?)\\s+ago");
    private static final Pattern MEMORY = Pattern.compile("Memory:\\s*([0-9.]+)([KMGT])");

    private final CommandRunner runner;

    public ServerService(CommandRunner runner) {
        this.runner = runner;
    }

    public Answer start() {
        return action("start", "Servidor encendido.");
    }

    public Answer stop() {
        return action("stop", "Servidor apagado.");
    }

    public Answer restart() {
        return action("restart", "Servidor reiniciado.");
    }

    private Answer action(String verb, String okMessage) {
        CommandResult r = runner.run("sudo systemctl " + verb + " " + ServerPaths.SERVICE);
        return r.ok()
                ? Answer.success(okMessage)
                : Answer.failure("No se pudo " + verb + " el servidor.", r.trimmed());
    }

    public boolean isActive() {
        return "active".equals(runner.run("systemctl is-active " + ServerPaths.SERVICE).trimmed());
    }

    /** Instantánea de estado para el Dashboard. */
    public ServerStatus status() {
        if (!isActive()) {
            return ServerStatus.offline();
        }
        String out = runner.run("systemctl status " + ServerPaths.SERVICE + " --no-pager").output();
        return new ServerStatus(
                ServerState.ONLINE,
                parseUptime(out),
                parseMemoryMb(out),
                ServerStatus.DEFAULT_TOTAL_MB,
                -1,        // CPU % — se refina en Etapa 1 (leer /proc o ps)
                -1, -1,    // jugadores — vía ConsoleService 'list' (Etapa 1/2)
                -1);       // TPS — 'forge tps' (Etapa 1/2)
    }

    private String parseUptime(String status) {
        Matcher m = UPTIME.matcher(status);
        return m.find() ? m.group(1).trim() : "—";
    }

    private long parseMemoryMb(String status) {
        Matcher m = MEMORY.matcher(status);
        if (!m.find()) {
            return -1;
        }
        double value = Double.parseDouble(m.group(1));
        return switch (m.group(2)) {
            case "K" -> Math.round(value / 1024);
            case "M" -> Math.round(value);
            case "G" -> Math.round(value * 1024);
            case "T" -> Math.round(value * 1024 * 1024);
            default -> -1;
        };
    }
}
