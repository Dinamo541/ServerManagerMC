package io.github.dinamo541.servermanagermc.service;

import io.github.dinamo541.corefx.util.Answer;
import io.github.dinamo541.servermanagermc.config.ServerPaths;
import io.github.dinamo541.servermanagermc.model.BackupInfo;
import io.github.dinamo541.servermanagermc.service.command.CommandResult;
import io.github.dinamo541.servermanagermc.service.command.CommandRunner;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Backups del mundo: crea (ejecutando {@code backup.sh} en Monica), lista los
 * archivos locales y los de Google Drive (vía {@code rclone}), restaura, sube y
 * descarga. Es una capa fina (Opción A): dispara los comandos y parsea su salida;
 * la lógica real de qué se respalda y cómo vive en {@code backup.sh}.
 *
 * <p>Las órdenes de listado, tanto en PROD como en DEV, emiten una línea por
 * archivo con el formato {@code <nombre> <tamañoBytes>}, de modo que
 * {@link #parseBackups} sirve para ambos perfiles.
 */
public final class BackupService {

    private static final Pattern NAME_OK = Pattern.compile("^[A-Za-z0-9._-]+\\.tar\\.gz$");

    private final CommandRunner runner;

    /** Rutas configurables en caliente (arrancan de {@link ServerPaths}). */
    private String backupsDir = ServerPaths.BACKUPS;
    private String driveRemote = ServerPaths.RCLONE_REMOTE;

    public BackupService(CommandRunner runner) {
        this.runner = runner;
    }

    public String backupsDir() {
        return backupsDir;
    }

    public String driveRemote() {
        return driveRemote;
    }

    public void setBackupsDir(String dir) {
        if (dir != null && !dir.isBlank()) {
            backupsDir = dir.strip();
        }
    }

    public void setDriveRemote(String remote) {
        if (remote != null && !remote.isBlank()) {
            driveRemote = remote.strip();
        }
    }

    /** Ejecuta {@code backup.sh}. Puede tardar minutos (tar + subida a Drive). */
    public Answer createBackup() {
        CommandResult r = runner.isMock()
                ? runner.run("mc:backup-create")
                : runner.run("bash \"" + ServerPaths.BACKUP_SH + "\"");
        return r.ok()
                ? Answer.success("Backup creado correctamente.")
                : Answer.failure("No se pudo crear el backup.", r.trimmed());
    }

    public List<BackupInfo> listLocalBackups() {
        String out = runner.isMock()
                ? runner.run("mc:backup-list-local").output()
                : runner.run("find \"" + backupsDir + "\" -maxdepth 1 -name '*.tar.gz' "
                        + "-printf '%f %s\\n' 2>/dev/null | sort -r").output();
        return parseBackups(out, BackupInfo.Location.LOCAL);
    }

    public List<BackupInfo> listDriveBackups() {
        String out = runner.isMock()
                ? runner.run("mc:backup-list-drive").output()
                : runner.run("rclone ls \"" + driveRemote + "\" 2>/dev/null "
                        + "| awk '{print $2\" \"$1}'").output();
        return parseBackups(out, BackupInfo.Location.DRIVE);
    }

    /** Restaura un backup local sobre la carpeta del servidor (destructivo). */
    public Answer restoreBackup(String name) {
        if (!validName(name)) {
            return Answer.failure("Nombre de backup no válido.");
        }
        CommandResult r = runner.isMock()
                ? runner.run("mc:backup-restore " + name)
                : runner.run("[ -f \"" + backupsDir + "/" + name + "\" ] && tar -xzf \""
                        + backupsDir + "/" + name + "\" -C \"" + ServerPaths.SERVER + "\"");
        return r.ok()
                ? Answer.success("Backup restaurado: " + name)
                : Answer.failure("No se pudo restaurar el backup.", r.trimmed());
    }

    /** Sube (sincroniza) los backups locales a Google Drive con rclone. */
    public Answer uploadToDrive() {
        CommandResult r = runner.isMock()
                ? runner.run("mc:backup-upload")
                : runner.run("rclone copy \"" + backupsDir + "\" \""
                        + driveRemote + "\" 2>&1");
        return r.ok()
                ? Answer.success("Backups sincronizados con Drive.")
                : Answer.failure("No se pudieron subir los backups.", r.trimmed());
    }

    /** Descarga un backup desde Drive a la carpeta local de backups. */
    public Answer downloadBackup(String name) {
        if (!validName(name)) {
            return Answer.failure("Nombre de backup no válido.");
        }
        CommandResult r = runner.isMock()
                ? runner.run("mc:backup-download " + name)
                : runner.run("rclone copy \"" + driveRemote + "/" + name + "\" \""
                        + backupsDir + "/\" 2>&1");
        return r.ok()
                ? Answer.success("Backup descargado: " + name)
                : Answer.failure("No se pudo descargar el backup.", r.trimmed());
    }

    /** Borra un backup (local con {@code rm}; en Drive con {@code rclone delete}). */
    public Answer deleteBackup(BackupInfo b) {
        String name = b.name();
        if (!validName(name)) {
            return Answer.failure("Nombre de backup no válido.");
        }
        boolean drive = b.location() == BackupInfo.Location.DRIVE;
        CommandResult r;
        if (runner.isMock()) {
            r = runner.run((drive ? "mc:backup-delete-drive " : "mc:backup-delete-local ") + name);
        } else if (drive) {
            r = runner.run("rclone delete \"" + driveRemote + "/" + name + "\" 2>&1");
        } else {
            r = runner.run("rm -f \"" + backupsDir + "/" + name + "\"");
        }
        return r.ok()
                ? Answer.success("Backup borrado: " + name)
                : Answer.failure("No se pudo borrar el backup.", r.trimmed());
    }

    /** Renombra un backup ({@code mv} en local; {@code rclone moveto} en Drive). */
    public Answer renameBackup(BackupInfo b, String newName) {
        String old = b.name();
        if (!validName(old) || !validName(newName)) {
            return Answer.failure("El nombre debe ser válido y terminar en .tar.gz");
        }
        boolean drive = b.location() == BackupInfo.Location.DRIVE;
        CommandResult r;
        if (runner.isMock()) {
            r = runner.run((drive ? "mc:backup-rename-drive " : "mc:backup-rename-local ") + old + " " + newName);
        } else if (drive) {
            r = runner.run("rclone moveto \"" + driveRemote + "/" + old + "\" \""
                    + driveRemote + "/" + newName + "\" 2>&1");
        } else {
            r = runner.run("mv \"" + backupsDir + "/" + old + "\" \"" + backupsDir + "/" + newName + "\"");
        }
        return r.ok()
                ? Answer.success("Renombrado a: " + newName)
                : Answer.failure("No se pudo renombrar el backup.", r.trimmed());
    }

    /** Mueve el backup al lado opuesto (local ↔ Drive) con {@code rclone move}. */
    public Answer moveBackup(BackupInfo b) {
        String name = b.name();
        if (!validName(name)) {
            return Answer.failure("Nombre de backup no válido.");
        }
        boolean drive = b.location() == BackupInfo.Location.DRIVE;
        CommandResult r;
        if (runner.isMock()) {
            r = runner.run((drive ? "mc:backup-move-to-local " : "mc:backup-move-to-drive ") + name);
        } else if (drive) {
            r = runner.run("rclone move \"" + driveRemote + "/" + name + "\" \"" + backupsDir + "/\" 2>&1");
        } else {
            r = runner.run("rclone move \"" + backupsDir + "/" + name + "\" \"" + driveRemote + "/\" 2>&1");
        }
        return r.ok()
                ? Answer.success(drive ? "Movido a local: " + name : "Movido a Drive: " + name)
                : Answer.failure("No se pudo mover el backup.", r.trimmed());
    }

    /** Fecha del último backup ({@code .ultimo-backup}), o "—" si no hay. */
    public String lastBackupTime() {
        String out = runner.run("cat \"" + ServerPaths.LAST_BACKUP + "\" 2>/dev/null").output();
        if (out == null || out.isBlank()) {
            return "—";
        }
        return out.strip().split("\\R", 2)[0];
    }

    /** {@code true} si hay un backup en curso ({@code backup.sh} corriendo). */
    public boolean isBackupRunning() {
        if (runner.isMock()) {
            return "running".equals(runner.run("mc:backup-status").trimmed());
        }
        return runner.run("pgrep -f backup.sh >/dev/null 2>&1").ok();
    }

    // ===================== parseo =====================

    private static List<BackupInfo> parseBackups(String out, BackupInfo.Location loc) {
        List<BackupInfo> list = new ArrayList<>();
        if (out == null || out.isBlank()) {
            return list;
        }
        for (String line : out.split("\\R")) {
            String[] parts = line.strip().split("\\s+");
            if (parts.length < 2) {
                continue;
            }
            try {
                long size = Long.parseLong(parts[parts.length - 1]);
                list.add(new BackupInfo(parts[0], size, loc));
            } catch (NumberFormatException ignored) {
                // línea sin tamaño válido: se omite
            }
        }
        return list;
    }

    private static boolean validName(String name) {
        return name != null && NAME_OK.matcher(name).matches();
    }
}
