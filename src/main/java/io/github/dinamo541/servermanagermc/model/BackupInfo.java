package io.github.dinamo541.servermanagermc.model;

/** Backup local (.tar.gz) o en Google Drive. */
public record BackupInfo(String name, long sizeBytes, Location location) {

    public enum Location { LOCAL, DRIVE }
}
