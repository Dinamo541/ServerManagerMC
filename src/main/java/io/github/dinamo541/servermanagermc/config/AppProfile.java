package io.github.dinamo541.servermanagermc.config;

/**
 * Perfil de ejecución. Decide qué implementación de comandos se usa:
 * <ul>
 *   <li>{@link #DEV}  — desarrollo en "Lady" (Windows): comandos simulados (mock).</li>
 *   <li>{@link #PROD} — ejecución real en "Monica" (Linux): systemctl/tmux/etc.</li>
 *   <li>{@link #REMOTE} — ejecución remota en "Monica" (Linux): comunicación via SSH/REST.</li>
 * </ul>
 * Resolución: propiedad de sistema {@code -Dmonica.profile=dev|prod|remote} si está
 * presente; si no, se autodetecta por el sistema operativo (Windows → DEV).
 */
public enum AppProfile {
    DEV,    // Windows local: MockCommandRunner
    PROD,   // Linux local (Monica): LinuxCommandRunner
    REMOTE; // Cualquier SO: habla con Monica via SSH/REST (Desarrollar después de terminar la app local)

    private static volatile AppProfile current;

    public static AppProfile current() {
        AppProfile p = current;
        if (p == null) {
            synchronized (AppProfile.class) {
                p = current;
                if (p == null) {
                    p = resolve();
                    current = p;
                }
            }
        }
        return p;
    }

    private static AppProfile resolve() {
        String override = System.getProperty("monica.profile");
        if (override != null && !override.isBlank()) {
            switch (override.toLowerCase()) {
                case "remote":
                    return REMOTE;
                case "prod":
                    return PROD;
                default:
                    return DEV;
            }
        }
        String os = System.getProperty("os.name", "").toLowerCase();
        return os.contains("win") ? DEV : PROD;
    }

    public boolean isDev() {
        return this == DEV;
    }

    /** Etiqueta corta para mostrar en la UI (badge del sidebar). */
    public String badge() {
        switch (this) {
            case DEV:
                return "DEV · mock";
            case PROD:
                return "PROD · Monica";
            case REMOTE:
                return "REMOTE · Monica";
            default:
                return "UNKNOWN";
        }
    }

}
