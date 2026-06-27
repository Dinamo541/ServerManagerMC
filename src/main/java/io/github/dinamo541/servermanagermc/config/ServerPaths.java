package io.github.dinamo541.servermanagermc.config;

/**
 * Rutas y nombres del servidor en Monica (Linux). Centralizadas aquí para no
 * dispersar literales por el código. En perfil DEV los comandos se simulan, por
 * lo que estas rutas no se tocan en disco; se usan al construir los comandos que
 * se ejecutan en Monica (perfil PROD).
 */
public final class ServerPaths {

    private ServerPaths() {
    }

    public static final String BASE        = "/home/dinamo/servidor";
    public static final String SERVER      = BASE + "/mohist";
    public static final String WORLD       = SERVER + "/world";
    public static final String MODS        = SERVER + "/mods";
    public static final String PLUGINS     = SERVER + "/plugins";
    public static final String LOG         = SERVER + "/logs/latest.log";
    public static final String PROPERTIES  = SERVER + "/server.properties";
    public static final String WHITELIST   = SERVER + "/whitelist.json";
    public static final String OPS         = SERVER + "/ops.json";
    public static final String BANNED      = SERVER + "/banned-players.json";

    public static final String BACKUPS     = BASE + "/backups";
    public static final String BACKUP_SH   = BASE + "/backup.sh";
    public static final String BACKUP_LOG  = BASE + "/backup.log";
    public static final String LAST_BACKUP = BASE + "/.ultimo-backup";

    public static final String SERVICE       = "mohist";
    public static final String TMUX_SESSION  = "mc";
    public static final String RCLONE_REMOTE = "gdrive:backups-servidor";
}
