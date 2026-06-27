package io.github.dinamo541.servermanagermc.model;

/** Mod de Forge o plugin de Bukkit (un archivo .jar en mods/ o plugins/). */
public record ModInfo(String fileName, boolean enabled, long sizeBytes, Kind kind) {

    public enum Kind { MOD, PLUGIN }
}
