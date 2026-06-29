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
    private static final Pattern LEADING_LONG = Pattern.compile("^\\s*(\\d+)");
    private static final Pattern PLAYERS = Pattern.compile(
            "There are (\\d+) of a max of (\\d+) players online:?\\s*(.*)", Pattern.CASE_INSENSITIVE);

    /** Las métricas de disco/conteo son caras; se refrescan a lo sumo cada 30 s. */
    private static final long FS_TTL_MS = 30_000;

    private final CommandRunner runner;

    private volatile FsMetrics fsCache;
    private volatile long fsCacheAt;

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

    /**
     * Detención forzada (Opción A): manda {@code systemctl kill} al servicio,
     * que envía SIGKILL al proceso. Útil cuando el servidor no responde a un
     * stop normal; puede perder datos no guardados (de ahí la confirmación en la UI).
     */
    public Answer kill() {
        CommandResult r = runner.run("sudo systemctl kill " + ServerPaths.SERVICE);
        return r.ok()
                ? Answer.success("Servidor forzado a detenerse.")
                : Answer.failure("No se pudo forzar la detención.", r.trimmed());
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
        boolean active = isActive();
        FsMetrics fs = filesystemMetrics(); // independientes de que el server corra

        ServerStatus.Builder b = ServerStatus.builder()
                .diskUsedMb(fs.diskMb())
                .diskTotalMb(fs.diskTotalMb())
                .worldSizeMb(fs.worldMb())
                .modCount(fs.mods())
                .pluginCount(fs.plugins())
                .serverVersion(fs.version())
                .lastBackupTime(fs.lastBackup());

        if (!active) {
            return b.state(ServerState.OFFLINE).build();
        }

        String out = runner.run("systemctl status " + ServerPaths.SERVICE + " --no-pager").output();
        b.state(ServerState.ONLINE)
                .uptime(parseUptime(out))
                .memoryUsedMb(parseMemoryMb(out))
                .memoryTotalMb(ServerStatus.DEFAULT_TOTAL_MB)
                .cpuPercent(parseCpu());

        applyRuntimeMetrics(b);
        return b.build();
    }

    // ===================== systemctl / ps =====================

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

    /** CPU% instantáneo del proceso vía ps sobre el MainPID del servicio. */
    private double parseCpu() {
        CommandResult r = runner.run(
                "ps -o %cpu= -p \"$(systemctl show -p MainPID --value " + ServerPaths.SERVICE + ")\" 2>/dev/null");
        return parseDouble(r.trimmed(), -1);
    }

    // ===================== filesystem (con caché) =====================

    private FsMetrics filesystemMetrics() {
        long now = System.currentTimeMillis();
        FsMetrics cached = fsCache;
        if (cached != null && now - fsCacheAt < FS_TTL_MS) {
            return cached;
        }
        FsMetrics fresh = new FsMetrics(
                dirSizeMb(ServerPaths.BASE),
                diskTotalMb(ServerPaths.BASE),
                dirSizeMb(ServerPaths.WORLD),
                jarCount(ServerPaths.MODS),
                jarCount(ServerPaths.PLUGINS),
                firstLine(runner.run("cat " + ServerPaths.VERSION_FILE + " 2>/dev/null"), "—"),
                firstLine(runner.run("cat " + ServerPaths.LAST_BACKUP + " 2>/dev/null"), "—"));
        fsCache = fresh;
        fsCacheAt = now;
        return fresh;
    }

    private long dirSizeMb(String path) {
        return parseLeadingLong(runner.run("du -sm \"" + path + "\" 2>/dev/null").trimmed(), -1);
    }

    /** Capacidad total (MB) del sistema de archivos donde vive {@code path}. */
    private long diskTotalMb(String path) {
        String line = runner.run("df -Pm \"" + path + "\" 2>/dev/null | tail -1").trimmed();
        String[] cols = line.split("\\s+");
        return cols.length >= 2 ? parseLeadingLong(cols[1], -1) : -1;
    }

    private int jarCount(String dir) {
        return (int) parseLeadingLong(
                runner.run("ls -1 \"" + dir + "\"/*.jar 2>/dev/null | wc -l").trimmed(), -1);
    }

    // ===================== métricas de la consola del juego =====================

    /**
     * Jugadores, TPS, chunks y entidades. Requieren consultar la consola del
     * juego, así que en DEV se simulan. En PROD se difieren (Etapa 2): sondear
     * estos datos cada 5 s implica inyectar comandos al server y parsear su log
     * con timeout, lo que ensucia la consola; se hará con un mecanismo dedicado
     * de petición/respuesta.
     */
    private void applyRuntimeMetrics(ServerStatus.Builder b) {
        if (!runner.isMock()) {
            return;
        }
        parsePlayers(runner.run("mc:list").output(), b);
        b.tps(parseDouble(runner.run("mc:tps").trimmed(), -1));
        b.loadedChunks((int) parseLeadingLong(runner.run("mc:chunks").trimmed(), -1));
        b.entityCount((int) parseLeadingLong(runner.run("mc:entities").trimmed(), -1));
    }

    private void parsePlayers(String listOutput, ServerStatus.Builder b) {
        Matcher m = PLAYERS.matcher(listOutput);
        if (m.find()) {
            b.players(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)));
            b.onlinePlayerList(m.group(3).trim());
        }
    }

    // ===================== helpers de parseo =====================

    private static long parseLeadingLong(String s, long def) {
        if (s == null) {
            return def;
        }
        Matcher m = LEADING_LONG.matcher(s);
        return m.find() ? Long.parseLong(m.group(1)) : def;
    }

    private static double parseDouble(String s, double def) {
        try {
            return Double.parseDouble(s.trim());
        } catch (NumberFormatException | NullPointerException e) {
            return def;
        }
    }

    private static String firstLine(CommandResult r, String def) {
        String t = r.trimmed();
        if (t.isEmpty()) {
            return def;
        }
        int nl = t.indexOf('\n');
        return nl < 0 ? t : t.substring(0, nl);
    }

    /** Métricas de disco/conteo cacheadas en bloque (ver {@link #FS_TTL_MS}). */
    private record FsMetrics(long diskMb, long diskTotalMb, long worldMb, int mods, int plugins,
                             String version, String lastBackup) {
    }
}
