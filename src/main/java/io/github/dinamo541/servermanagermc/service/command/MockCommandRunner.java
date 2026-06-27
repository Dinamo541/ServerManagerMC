package io.github.dinamo541.servermanagermc.service.command;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Implementación simulada para desarrollar la UI en Windows (perfil DEV).
 * No ejecuta nada real: devuelve salidas verosímiles y mantiene un estado
 * encendido/apagado en memoria para que los botones Start/Stop tengan efecto
 * visible en el Dashboard.
 */
public final class MockCommandRunner implements CommandRunner {

    private final AtomicBoolean active = new AtomicBoolean(false);

    @Override
    public CommandResult run(String command) {
        String c = command.toLowerCase();

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
        if (c.contains("tmux send-keys") || c.startsWith("tmux")) {
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

    @Override
    public boolean isMock() {
        return true;
    }
}
