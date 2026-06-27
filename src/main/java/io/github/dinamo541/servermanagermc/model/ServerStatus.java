package io.github.dinamo541.servermanagermc.model;

/**
 * Instantánea del estado del servidor para el Dashboard. Los valores
 * desconocidos/no aplicables se representan con -1 (numéricos) o "—" (uptime).
 */
public record ServerStatus(
        ServerState state,
        String uptime,
        long memoryUsedMb,
        long memoryTotalMb,
        double cpuPercent,
        int playersOnline,
        int playersMax,
        double tps) {

    public static final long DEFAULT_TOTAL_MB = 10_240L; // 10 GB asignados

    public boolean online() {
        return state == ServerState.ONLINE;
    }

    public static ServerStatus offline() {
        return new ServerStatus(ServerState.OFFLINE, "—", -1, DEFAULT_TOTAL_MB, -1, 0, -1, -1);
    }

    public static ServerStatus unknown() {
        return new ServerStatus(ServerState.UNKNOWN, "—", -1, DEFAULT_TOTAL_MB, -1, -1, -1, -1);
    }
}
