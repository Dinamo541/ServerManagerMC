package io.github.dinamo541.servermanagermc.model;

/**
 * Ficha completa de un jugador: identidad, pertenencia (op/whitelist/ban) y
 * estadísticas de juego. Los datos ricos (vida, ubicación, kills…) solo están
 * disponibles en perfil DEV (mock); en PROD se rellenan los que se pueden leer
 * de los {@code *.json} del servidor y el resto queda con su valor por defecto
 * (-1 numérico, "—"/"" texto).
 *
 * <p>Se construye con {@link #builder()} para no depender del orden posicional
 * de los muchos campos (mismo patrón que {@link ServerStatus}).
 */
public record PlayerDetail(
        String name,
        String uuid,
        boolean op,
        int opLevel,
        boolean whitelisted,
        boolean banned,
        String banReason,
        String banSource,
        String bannedBy,
        long firstPlayed,
        long lastPlayed,
        long playTime,
        String gamemode,
        int level,
        float health,
        int hunger,
        double x, double y, double z,
        String ip,
        int deaths,
        int mobKills,
        int playerKills,
        long distanceWalked,
        double damageDealt,
        double damageTaken,
        int itemsEnchanted,
        int fishCaught,
        int trades,
        int timesSinceDeath,
        int ping,
        long blocksMined,
        long blocksPlaced,
        long jumps,
        int advancements,
        long xpTotal) {

    /** Inicial en mayúscula para el avatar; "?" si el nombre viene vacío. */
    public String initial() {
        return name == null || name.isBlank()
                ? "?"
                : name.substring(0, 1).toUpperCase(java.util.Locale.ROOT);
    }

    /** Ratio de bajas/muertes (K/D); -1 si no hay datos. */
    public double killDeathRatio() {
        if (playerKills < 0 || deaths < 0) {
            return -1;
        }
        return deaths == 0 ? playerKills : (double) playerKills / deaths;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static Builder builder(String name) {
        return new Builder().name(name);
    }

    /** Constructor fluido con valores "desconocido" por defecto. */
    public static final class Builder {
        private String name = "—";
        private String uuid = "—";
        private boolean op;
        private int opLevel;
        private boolean whitelisted;
        private boolean banned;
        private String banReason = "";
        private String banSource = "";
        private String bannedBy = "";
        private long firstPlayed = -1;
        private long lastPlayed = -1;
        private long playTime = -1;
        private String gamemode = "—";
        private int level = -1;
        private float health = -1;
        private int hunger = -1;
        private double x = Double.NaN;
        private double y = Double.NaN;
        private double z = Double.NaN;
        private String ip = "—";
        private int deaths = -1;
        private int mobKills = -1;
        private int playerKills = -1;
        private long distanceWalked = -1;
        private double damageDealt = -1;
        private double damageTaken = -1;
        private int itemsEnchanted = -1;
        private int fishCaught = -1;
        private int trades = -1;
        private int timesSinceDeath = -1;
        private int ping = -1;
        private long blocksMined = -1;
        private long blocksPlaced = -1;
        private long jumps = -1;
        private int advancements = -1;
        private long xpTotal = -1;

        public Builder name(String v)            { this.name = v; return this; }
        public Builder uuid(String v)            { this.uuid = v; return this; }
        public Builder op(boolean v)             { this.op = v; return this; }
        public Builder opLevel(int v)            { this.opLevel = v; return this; }
        public Builder whitelisted(boolean v)    { this.whitelisted = v; return this; }
        public Builder banned(boolean v)         { this.banned = v; return this; }
        public Builder banReason(String v)       { this.banReason = v; return this; }
        public Builder banSource(String v)       { this.banSource = v; return this; }
        public Builder bannedBy(String v)        { this.bannedBy = v; return this; }
        public Builder firstPlayed(long v)       { this.firstPlayed = v; return this; }
        public Builder lastPlayed(long v)        { this.lastPlayed = v; return this; }
        public Builder playTime(long v)          { this.playTime = v; return this; }
        public Builder gamemode(String v)        { this.gamemode = v; return this; }
        public Builder level(int v)              { this.level = v; return this; }
        public Builder health(float v)           { this.health = v; return this; }
        public Builder hunger(int v)             { this.hunger = v; return this; }
        public Builder x(double v)               { this.x = v; return this; }
        public Builder y(double v)               { this.y = v; return this; }
        public Builder z(double v)               { this.z = v; return this; }
        public Builder ip(String v)              { this.ip = v; return this; }
        public Builder deaths(int v)             { this.deaths = v; return this; }
        public Builder mobKills(int v)           { this.mobKills = v; return this; }
        public Builder playerKills(int v)        { this.playerKills = v; return this; }
        public Builder distanceWalked(long v)    { this.distanceWalked = v; return this; }
        public Builder damageDealt(double v)     { this.damageDealt = v; return this; }
        public Builder damageTaken(double v)     { this.damageTaken = v; return this; }
        public Builder itemsEnchanted(int v)     { this.itemsEnchanted = v; return this; }
        public Builder fishCaught(int v)         { this.fishCaught = v; return this; }
        public Builder trades(int v)             { this.trades = v; return this; }
        public Builder timesSinceDeath(int v)    { this.timesSinceDeath = v; return this; }
        public Builder ping(int v)               { this.ping = v; return this; }
        public Builder blocksMined(long v)       { this.blocksMined = v; return this; }
        public Builder blocksPlaced(long v)      { this.blocksPlaced = v; return this; }
        public Builder jumps(long v)             { this.jumps = v; return this; }
        public Builder advancements(int v)       { this.advancements = v; return this; }
        public Builder xpTotal(long v)           { this.xpTotal = v; return this; }

        public PlayerDetail build() {
            return new PlayerDetail(name, uuid, op, opLevel, whitelisted, banned,
                    banReason, banSource, bannedBy, firstPlayed, lastPlayed, playTime,
                    gamemode, level, health, hunger, x, y, z, ip, deaths, mobKills,
                    playerKills, distanceWalked, damageDealt, damageTaken,
                    itemsEnchanted, fishCaught, trades, timesSinceDeath,
                    ping, blocksMined, blocksPlaced, jumps, advancements, xpTotal);
        }
    }
}
