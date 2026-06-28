package io.github.dinamo541.servermanagermc.service.command;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Implementación simulada para desarrollar la UI en Windows (perfil DEV).
 * No ejecuta nada real: devuelve salidas verosímiles y mantiene un estado
 * encendido/apagado en memoria para que los botones Start/Stop tengan efecto
 * visible en el Dashboard.
 */
public final class MockCommandRunner implements CommandRunner {

    private static final String[] NAMES = {
        "Steve", "Alex", "Notch", "Herobrine", "Dinamo", "Mariano", "Luna", "Pixel", "Creeper", "Ender"
    };

    private final AtomicBoolean active = new AtomicBoolean(false);
    private final Random rnd = new Random();

    @Override
    public CommandResult run(String command) {
        String c = command.toLowerCase(Locale.ROOT);

        // ----- Ciclo de vida del servicio -----
        if (c.contains("systemctl start"))   { active.set(true);  return new CommandResult(0, ""); }
        if (c.contains("systemctl stop"))    { active.set(false); return new CommandResult(0, ""); }
        if (c.contains("systemctl restart")) { active.set(true);  return new CommandResult(0, ""); }
        if (c.contains("is-active")) {
            return active.get()
                    ? new CommandResult(0, "active")
                    : new CommandResult(3, "inactive");
        }
        if (c.contains("systemctl status")) {
            return new CommandResult(0, mockStatus());
        }
        if (c.contains("ps -o %cpu")) {
            return new CommandResult(0, String.format(Locale.US, "%.1f", 15 + rnd.nextDouble() * 45));
        }

        // ----- Métricas de la consola del juego (protocolo interno mc:*) -----
        if (c.startsWith("mc:list"))     { return new CommandResult(0, mockPlayerList()); }
        if (c.startsWith("mc:tps"))      { return new CommandResult(0, String.format(Locale.US, "%.1f", 18.5 + rnd.nextDouble() * 1.5)); }
        if (c.startsWith("mc:chunks"))   { return new CommandResult(0, String.valueOf(120 + rnd.nextInt(80))); }
        if (c.startsWith("mc:entities")) { return new CommandResult(0, String.valueOf(700 + rnd.nextInt(400))); }

        // ----- Métricas de disco / conteo de archivos -----
        if (c.contains("du -sm")) {
            return c.contains("world")
                    ? new CommandResult(0, "892\t/home/dinamo/servidor/mohist/world")
                    : new CommandResult(0, "2458\t/home/dinamo/servidor");
        }
        if (c.contains("df -pm")) {
            // Filesystem 1M-blocks Used Available Use% Mounted-on  → total = 51200 MB (50 GB)
            return new CommandResult(0, "/dev/sda1 51200 2458 48742 5% /");
        }
        if (c.contains("wc -l")) {
            if (c.contains("plugins")) { return new CommandResult(0, "15"); }
            if (c.contains("mods"))    { return new CommandResult(0, "42"); }
            return new CommandResult(0, "0");
        }
        if (c.contains("version.txt"))   { return new CommandResult(0, "Mohist 1.20.1-JDK25"); }
        if (c.contains("ultimo-backup")) { return new CommandResult(0, "2026-06-27 03:00 (hace 18 h)"); }

        // ----- tmux (envío de comandos al juego) -----
        if (c.startsWith("tmux")) {
            return new CommandResult(0, "");
        }
        return new CommandResult(0, "[mock] " + command);
    }

    private String mockStatus() {
        if (!active.get()) {
            return "* mohist.service - Mohist Minecraft Server\n   Active: inactive (dead)\n";
        }
        return "* mohist.service - Mohist Minecraft Server\n"
             + "   Active: active (running) since Fri 2026-06-27 10:00:00 CST; 2h 15min ago\n"
             + "   Memory: 6.4G\n"
             + "      CPU: 1h 2min 30s\n";
    }

    /** Genera una respuesta tipo comando {@code list} de Minecraft, con jugadores al azar. */
    private String mockPlayerList() {
        int max = 20;
        int n = rnd.nextInt(5); // 0..4 conectados
        if (n == 0) {
            return "There are 0 of a max of " + max + " players online: ";
        }
        List<String> pool = new ArrayList<>(Arrays.asList(NAMES));
        Collections.shuffle(pool, rnd);
        String list = String.join(", ", pool.subList(0, n));
        return "There are " + n + " of a max of " + max + " players online: " + list;
    }

    @Override
    public boolean isMock() {
        return true;
    }
}
