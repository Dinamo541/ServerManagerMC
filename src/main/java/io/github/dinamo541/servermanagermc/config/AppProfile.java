package io.github.dinamo541.servermanagermc.config;

/**
 * Perfil de ejecución. Decide qué implementación de comandos se usa:
 * <ul>
 *   <li>{@link #DEV}  — desarrollo en "Lady" (Windows): comandos simulados (mock).</li>
 *   <li>{@link #PROD} — ejecución real en "Monica" (Linux): systemctl/tmux/etc.</li>
 * </ul>
 * Resolución: propiedad de sistema {@code -Dmonica.profile=dev|prod} si está
 * presente; si no, se autodetecta por el sistema operativo (Windows → DEV).
 */
public enum AppProfile {
    DEV, PROD;

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
            return override.equalsIgnoreCase("prod") ? PROD : DEV;
        }
        String os = System.getProperty("os.name", "").toLowerCase();
        return os.contains("win") ? DEV : PROD;
    }

    public boolean isDev() {
        return this == DEV;
    }

    /** Etiqueta corta para mostrar en la UI (badge del sidebar). */
    public String badge() {
        return this == DEV ? "DEV · mock" : "PROD · Monica";
    }
}
