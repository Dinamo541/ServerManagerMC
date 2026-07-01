package io.github.dinamo541.servermanagermc.model;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import javafx.beans.InvalidationListener;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

/**
 * Modelo editable de {@code server.properties} con propiedades de JavaFX para
 * enlace bidireccional con los controles del editor (interruptores, deslizadores,
 * campos). Cada campo conoce su clave en el archivo (con guiones) y su valor por
 * defecto.
 *
 * <p>Las claves del archivo que el editor <b>no</b> modela (p. ej. {@code
 * spawn-protection} o ajustes propios de Paper/Mohist) se conservan en
 * {@link #extras} y se vuelcan intactas en {@link #toProperties()}, de modo que
 * guardar nunca destruye opciones que el panel no muestra.
 */
public final class ServerPropertiesModel {

    private final Map<String, BooleanProperty> bools = new LinkedHashMap<>();
    private final Map<String, IntegerProperty> ints = new LinkedHashMap<>();
    private final Map<String, StringProperty> strs = new LinkedHashMap<>();
    private final Map<String, Boolean> boolDefaults = new LinkedHashMap<>();
    private final Map<String, Integer> intDefaults = new LinkedHashMap<>();
    private final Map<String, String> strDefaults = new LinkedHashMap<>();
    private final Map<String, String> extras = new LinkedHashMap<>();

    // ----- Booleanos -----
    public final BooleanProperty onlineMode = bool("online-mode", true);
    public final BooleanProperty pvp = bool("pvp", true);
    public final BooleanProperty allowFlight = bool("allow-flight", false);
    public final BooleanProperty enforceWhitelist = bool("enforce-whitelist", false);
    public final BooleanProperty whiteList = bool("white-list", false);
    public final BooleanProperty spawnAnimals = bool("spawn-animals", true);
    public final BooleanProperty spawnMonsters = bool("spawn-monsters", true);
    public final BooleanProperty spawnNpcs = bool("spawn-npcs", true);
    public final BooleanProperty enableCommandBlock = bool("enable-command-block", false);
    public final BooleanProperty hardcore = bool("hardcore", false);
    public final BooleanProperty generateStructures = bool("generate-structures", true);
    public final BooleanProperty allowNether = bool("allow-nether", true);
    public final BooleanProperty broadcastConsoleToOps = bool("broadcast-console-to-ops", true);
    public final BooleanProperty broadcastRconToOps = bool("broadcast-rcon-to-ops", true);
    public final BooleanProperty enableRcon = bool("enable-rcon", false);
    public final BooleanProperty enableQuery = bool("enable-query", false);
    public final BooleanProperty enableJmxMonitoring = bool("enable-jmx-monitoring", false);
    public final BooleanProperty enforceSecureProfile = bool("enforce-secure-profile", true);
    public final BooleanProperty preventProxyConnections = bool("prevent-proxy-connections", false);
    public final BooleanProperty useNativeTransport = bool("use-native-transport", true);
    public final BooleanProperty syncChunkWrites = bool("sync-chunk-writes", true);
    public final BooleanProperty enableStatus = bool("enable-status", true);
    public final BooleanProperty hideOnlinePlayers = bool("hide-online-players", false);
    public final BooleanProperty requireResourcePack = bool("require-resource-pack", false);

    // ----- Numéricos -----
    public final IntegerProperty viewDistance = integer("view-distance", 10);
    public final IntegerProperty simulationDistance = integer("simulation-distance", 10);
    public final IntegerProperty maxPlayers = integer("max-players", 20);
    public final IntegerProperty maxWorldSize = integer("max-world-size", 29999984);
    public final IntegerProperty serverPort = integer("server-port", 25565);
    public final IntegerProperty rateLimit = integer("rate-limit", 0);
    public final IntegerProperty maxTickTime = integer("max-tick-time", 60000);
    public final IntegerProperty entityBroadcastRangePercentage = integer("entity-broadcast-range-percentage", 100);
    public final IntegerProperty playerIdleTimeout = integer("player-idle-timeout", 0);
    public final IntegerProperty maxChainedNeighborUpdates = integer("max-chained-neighbor-updates", 1000000);
    public final IntegerProperty opPermissionLevel = integer("op-permission-level", 4);
    public final IntegerProperty functionPermissionLevel = integer("function-permission-level", 2);
    public final IntegerProperty networkCompressionThreshold = integer("network-compression-threshold", 256);
    public final IntegerProperty regionFileCompression = integer("region-file-compression", 256);

    // ----- Cadenas -----
    public final StringProperty motd = string("motd", "A Minecraft Server");
    public final StringProperty levelName = string("level-name", "world");
    public final StringProperty levelSeed = string("level-seed", "");
    public final StringProperty levelType = string("level-type", "default");
    public final StringProperty difficulty = string("difficulty", "easy");
    public final StringProperty gamemode = string("gamemode", "survival");
    public final StringProperty resourcePack = string("resource-pack", "");
    public final StringProperty resourcePackSha1 = string("resource-pack-sha1", "");
    public final StringProperty resourcePackPrompt = string("resource-pack-prompt", "");
    public final StringProperty serverIp = string("server-ip", "");
    public final StringProperty rconPassword = string("rcon.password", "");
    public final StringProperty rconPort = string("rcon.port", "25575");
    public final StringProperty queryPort = string("query.port", "25577");
    public final StringProperty textFilteringConfig = string("text-filtering-config", "");
    public final StringProperty initialEnabledPacks = string("initial-enabled-packs", "vanilla");
    public final StringProperty initialDisabledPacks = string("initial-disabled-packs", "");
    public final StringProperty bugReportUrl = string("bug-report-url", "");

    /** Modelo con todos los valores por defecto y sin claves extra. */
    public static ServerPropertiesModel defaults() {
        return new ServerPropertiesModel();
    }

    /** Modelo poblado a partir de un {@link Properties} (p. ej. el archivo real). */
    public static ServerPropertiesModel fromProperties(Properties p) {
        ServerPropertiesModel m = new ServerPropertiesModel();
        m.setFrom(p);
        return m;
    }

    /**
     * Sobrescribe los valores del modelo con los de {@code p}. Las claves ausentes
     * vuelven a su valor por defecto y las no modeladas se guardan como extras.
     */
    public void setFrom(Properties p) {
        for (var e : bools.entrySet()) {
            e.getValue().set(parseBool(p.getProperty(e.getKey()), boolDefaults.get(e.getKey())));
        }
        for (var e : ints.entrySet()) {
            e.getValue().set(parseInt(p.getProperty(e.getKey()), intDefaults.get(e.getKey())));
        }
        for (var e : strs.entrySet()) {
            String raw = p.getProperty(e.getKey());
            e.getValue().set(raw != null ? raw : strDefaults.get(e.getKey()));
        }
        extras.clear();
        for (String key : p.stringPropertyNames()) {
            if (!isModeled(key)) {
                extras.put(key, p.getProperty(key));
            }
        }
    }

    /** Vuelca el modelo a un {@link Properties} (claves con guiones), extras incluidos. */
    public Properties toProperties() {
        Properties p = new Properties();
        for (var e : extras.entrySet()) {
            p.setProperty(e.getKey(), e.getValue());
        }
        bools.forEach((k, v) -> p.setProperty(k, String.valueOf(v.get())));
        ints.forEach((k, v) -> p.setProperty(k, String.valueOf(v.get())));
        strs.forEach((k, v) -> p.setProperty(k, v.get() == null ? "" : v.get()));
        return p;
    }

    /** Copia independiente (mismo estado y mismos extras) para detectar cambios. */
    public ServerPropertiesModel copy() {
        ServerPropertiesModel m = new ServerPropertiesModel();
        m.setFrom(this.toProperties());
        return m;
    }

    /** {@code true} si algún valor difiere de {@code original}. */
    public boolean isChanged(ServerPropertiesModel original) {
        return original == null || !toProperties().equals(original.toProperties());
    }

    /** Registra un oyente en todas las propiedades (para recalcular el estado "modificado"). */
    public void addListener(InvalidationListener listener) {
        bools.values().forEach(p -> p.addListener(listener));
        ints.values().forEach(p -> p.addListener(listener));
        strs.values().forEach(p -> p.addListener(listener));
    }

    private boolean isModeled(String key) {
        return bools.containsKey(key) || ints.containsKey(key) || strs.containsKey(key);
    }

    private BooleanProperty bool(String key, boolean def) {
        BooleanProperty p = new SimpleBooleanProperty(def);
        bools.put(key, p);
        boolDefaults.put(key, def);
        return p;
    }

    private IntegerProperty integer(String key, int def) {
        IntegerProperty p = new SimpleIntegerProperty(def);
        ints.put(key, p);
        intDefaults.put(key, def);
        return p;
    }

    private StringProperty string(String key, String def) {
        StringProperty p = new SimpleStringProperty(def);
        strs.put(key, p);
        strDefaults.put(key, def);
        return p;
    }

    private static boolean parseBool(String raw, boolean def) {
        if (raw == null) {
            return def;
        }
        String s = raw.trim().toLowerCase(Locale.ROOT);
        return switch (s) {
            case "true" -> true;
            case "false" -> false;
            default -> def;
        };
    }

    private static int parseInt(String raw, int def) {
        if (raw == null) {
            return def;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
