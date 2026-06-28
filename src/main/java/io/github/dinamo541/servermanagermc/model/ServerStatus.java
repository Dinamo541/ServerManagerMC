package io.github.dinamo541.servermanagermc.model;

/**
 * Instantánea del estado del servidor para el Dashboard. Los valores
 * desconocidos/no aplicables se representan con -1 (numéricos), "—" (texto
 * corto) o "" (listas). Se construye con {@link #builder()} para no depender del
 * orden posicional de tantos campos.
 */
public record ServerStatus(
        ServerState state,
        String uptime,
        long memoryUsedMb,
        long memoryTotalMb,
        double cpuPercent,
        int playersOnline,
        int playersMax,
        double tps,
        long diskUsedMb,
        long diskTotalMb,
        long worldSizeMb,
        int modCount,
        int pluginCount,
        String serverVersion,
        String lastBackupTime,
        String onlinePlayerList,
        int loadedChunks,
        int entityCount) {

    public static final long DEFAULT_TOTAL_MB = 10_240L; // 10 GB asignados

    public boolean online() {
        return state == ServerState.ONLINE;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static ServerStatus offline() {
        return builder().state(ServerState.OFFLINE).build();
    }

    public static ServerStatus unknown() {
        return builder().state(ServerState.UNKNOWN).build();
    }

    /** Constructor fluido con valores "desconocido" por defecto. */
    public static final class Builder {
        private ServerState state = ServerState.UNKNOWN;
        private String uptime = "—";
        private long memoryUsedMb = -1;
        private long memoryTotalMb = DEFAULT_TOTAL_MB;
        private double cpuPercent = -1;
        private int playersOnline = -1;
        private int playersMax = -1;
        private double tps = -1;
        private long diskUsedMb = -1;
        private long diskTotalMb = -1;
        private long worldSizeMb = -1;
        private int modCount = -1;
        private int pluginCount = -1;
        private String serverVersion = "—";
        private String lastBackupTime = "—";
        private String onlinePlayerList = "";
        private int loadedChunks = -1;
        private int entityCount = -1;

        public Builder state(ServerState v)        { this.state = v; return this; }
        public Builder uptime(String v)            { this.uptime = v; return this; }
        public Builder memoryUsedMb(long v)        { this.memoryUsedMb = v; return this; }
        public Builder memoryTotalMb(long v)       { this.memoryTotalMb = v; return this; }
        public Builder cpuPercent(double v)        { this.cpuPercent = v; return this; }
        public Builder players(int online, int max){ this.playersOnline = online; this.playersMax = max; return this; }
        public Builder tps(double v)               { this.tps = v; return this; }
        public Builder diskUsedMb(long v)          { this.diskUsedMb = v; return this; }
        public Builder diskTotalMb(long v)         { this.diskTotalMb = v; return this; }
        public Builder worldSizeMb(long v)         { this.worldSizeMb = v; return this; }
        public Builder modCount(int v)             { this.modCount = v; return this; }
        public Builder pluginCount(int v)          { this.pluginCount = v; return this; }
        public Builder serverVersion(String v)     { this.serverVersion = v; return this; }
        public Builder lastBackupTime(String v)    { this.lastBackupTime = v; return this; }
        public Builder onlinePlayerList(String v)  { this.onlinePlayerList = v; return this; }
        public Builder loadedChunks(int v)         { this.loadedChunks = v; return this; }
        public Builder entityCount(int v)          { this.entityCount = v; return this; }

        public ServerStatus build() {
            return new ServerStatus(state, uptime, memoryUsedMb, memoryTotalMb, cpuPercent,
                    playersOnline, playersMax, tps, diskUsedMb, diskTotalMb, worldSizeMb, modCount,
                    pluginCount, serverVersion, lastBackupTime, onlinePlayerList,
                    loadedChunks, entityCount);
        }
    }
}
