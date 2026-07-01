package io.github.dinamo541.servermanagermc.service.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Implementación simulada para desarrollar la UI en Windows (perfil DEV).
 * No ejecuta nada real: devuelve salidas verosímiles y mantiene un estado
 * encendido/apagado en memoria para que los botones Start/Stop tengan efecto
 * visible en el Dashboard.
 *
 * <p>Mantiene además un <b>roster</b> de jugadores (whitelist/ops/baneados)
 * coherente entre sí y <b>mutable</b>: los comandos {@code op/deop/ban/pardon/
 * whitelist add|remove} enviados por tmux modifican estos conjuntos, de modo que
 * las listas, los conectados ({@code mc:list}) y la ficha individual
 * ({@code mc:player-detail}) reflejan los cambios en el siguiente refresco. Las
 * estadísticas ricas son deterministas (derivadas del nombre) para no variar
 * entre lecturas.
 */
public final class MockCommandRunner implements CommandRunner {

    /** Pool inicial de jugadores conocidos. */
    private static final String[] NAMES = {
        "Steve", "Alex", "Notch", "Herobrine", "Dinamo", "Mariano", "Luna", "Pixel",
        "Creeper", "Ender", "Ghost", "Ninja", "Vikingo", "Tormenta", "Blaze", "Warden",
        "Scout", "Shadow", "Phoenix", "Neo", "Zero"
    };
    /** Jugadores que entraron al mundo alguna vez pero NO están en la whitelist (visitantes). */
    private static final String[] KNOWN_EXTRA = {
        "Guest_99", "RaulMC", "Visitante", "OldMiner", "NoobMaster", "Andrea_", "ElPro2010"
    };
    private static final Set<String> INITIAL_BANNED = Set.of("herobrine", "ghost", "zero");
    private static final Set<String> INITIAL_OPS = Set.of("notch", "dinamo", "mariano", "steve");

    /**
     * Contenido inicial de {@code server.properties} en DEV. Incluye, además de
     * las claves que el editor modela, algunas que no ({@code spawn-protection},
     * {@code force-gamemode}, {@code generator-settings}) para comprobar que
     * guardar conserva intactas las opciones no mostradas.
     */
    private static final String DEFAULT_PROPERTIES = """
            online-mode=true
            pvp=true
            allow-flight=false
            enforce-whitelist=false
            white-list=false
            spawn-animals=true
            spawn-monsters=true
            spawn-npcs=true
            enable-command-block=false
            hardcore=false
            generate-structures=true
            allow-nether=true
            broadcast-console-to-ops=true
            broadcast-rcon-to-ops=true
            enable-rcon=false
            enable-query=false
            enable-jmx-monitoring=false
            enforce-secure-profile=true
            prevent-proxy-connections=false
            use-native-transport=true
            sync-chunk-writes=true
            enable-status=true
            hide-online-players=false
            require-resource-pack=false
            view-distance=10
            simulation-distance=10
            max-players=20
            max-world-size=29999984
            server-port=25565
            rate-limit=0
            max-tick-time=60000
            entity-broadcast-range-percentage=100
            player-idle-timeout=0
            max-chained-neighbor-updates=1000000
            op-permission-level=4
            function-permission-level=2
            network-compression-threshold=256
            region-file-compression=256
            motd=A Minecraft Server
            level-name=world
            level-seed=
            level-type=default
            difficulty=easy
            gamemode=survival
            resource-pack=
            resource-pack-sha1=
            resource-pack-prompt=
            server-ip=
            rcon.password=
            rcon.port=25575
            query.port=25577
            text-filtering-config=
            initial-enabled-packs=vanilla
            initial-disabled-packs=
            bug-report-url=
            spawn-protection=16
            force-gamemode=false
            generator-settings={}
            """;

    /** Instante de referencia fijo para fechas deterministas (~jun 2026). */
    private static final long REF_MILLIS = 1_780_000_000_000L;

    private final AtomicBoolean active = new AtomicBoolean(false);
    private final Random rnd = new Random();

    // --- Estado mutable del roster (claves en minúscula salvo 'roster', canónico) ---
    private final Set<String> roster = ConcurrentHashMap.newKeySet();      // nombres canónicos conocidos
    private final Set<String> whitelist = ConcurrentHashMap.newKeySet();   // lower
    private final Set<String> ops = ConcurrentHashMap.newKeySet();         // lower
    private final Set<String> banned = ConcurrentHashMap.newKeySet();      // lower
    private final Set<String> warned = ConcurrentHashMap.newKeySet();      // lower (advertencias del panel)
    private final Map<String, String> propertiesStore = new ConcurrentHashMap<>(); // server.properties simulado

    // --- Estado de backups simulados (líneas "<nombre> <tamañoBytes>") ---
    private final AtomicBoolean backupRunning = new AtomicBoolean(false);
    private final List<String> mockLocalBackups = new CopyOnWriteArrayList<>();
    private final List<String> mockDriveBackups = new CopyOnWriteArrayList<>();
    private volatile String lastBackupText = "2026-06-27 03:00 (hace 18 h)";

    public MockCommandRunner() {
        for (String name : NAMES) {
            roster.add(name);
            String low = name.toLowerCase(Locale.ROOT);
            if (!INITIAL_BANNED.contains(low)) {
                whitelist.add(low); // la whitelist inicial son todos los no baneados
            }
        }
        // Visitantes: existen en el roster (ya entraron) pero NO en la whitelist.
        for (String name : KNOWN_EXTRA) {
            roster.add(name);
        }
        ops.addAll(INITIAL_OPS);
        banned.addAll(INITIAL_BANNED);
        warned.add("pixel");  // un par de advertidos para la demo en DEV
        warned.add("raulmc");
        parseProperties(DEFAULT_PROPERTIES);

        // Estado inicial de backups (3 locales, 2 en Drive).
        mockLocalBackups.add("mundo-2026-06-27_03-00.tar.gz 456723456");
        mockLocalBackups.add("mundo-2026-06-25_22-30.tar.gz 445123000");
        mockLocalBackups.add("mundo-2026-06-20_15-15.tar.gz 421987654");
        mockDriveBackups.add("mundo-2026-06-27_03-00.tar.gz 456723456");
        mockDriveBackups.add("mundo-2026-06-26_12-00.tar.gz 439876543");
    }

    @Override
    public CommandResult run(String command) {
        String c = command.toLowerCase(Locale.ROOT);

        // ----- Ciclo de vida del servicio -----
        if (c.contains("systemctl start"))    { active.set(true);  return new CommandResult(0, ""); }
        if (c.contains("systemctl stop"))     { active.set(false); return new CommandResult(0, ""); }
        if (c.contains("systemctl restart"))  { active.set(true);  return new CommandResult(0, ""); }
        if (c.contains("systemctl kill"))     { active.set(false); return new CommandResult(0, ""); }
        if (c.contains("is-active")) {
            return active.get()
                    ? new CommandResult(0, "active")
                    : new CommandResult(3, "inactive");
        }
        if (c.contains("systemctl status")) {
            return new CommandResult(0, mockStatus());
        }
        if (c.contains("ps -o %cpu")) {
            return new CommandResult(0, String.format(Locale.US, "%.1f", 15 + rnd.nextDouble() * 45));
        }

        // ----- Roster de jugadores (protocolo interno mc:*-json) -----
        if (c.startsWith("mc:whitelist-json")) { return new CommandResult(0, rosterJson(this::isWhitelisted)); }
        if (c.startsWith("mc:ops-json"))       { return new CommandResult(0, rosterJson(this::isOp)); }
        if (c.startsWith("mc:banned-json"))    { return new CommandResult(0, rosterJson(this::isBanned)); }
        if (c.startsWith("mc:known-json"))     { return new CommandResult(0, rosterJson(name -> true)); }
        if (c.startsWith("mc:player-detail"))  { return new CommandResult(0, playerJson(detailName(command))); }

        // ----- Advertencias del panel (estado en memoria) -----
        if (c.startsWith("mc:warn-add"))    { warned.add(detailName(command).toLowerCase(Locale.ROOT)); return new CommandResult(0, ""); }
        if (c.startsWith("mc:warn-remove")) { warned.remove(detailName(command).toLowerCase(Locale.ROOT)); return new CommandResult(0, ""); }
        if (c.startsWith("mc:warnings"))    { return new CommandResult(0, String.join("\n", warned)); }

        // ----- Editor de server.properties (estado en memoria) -----
        if (c.startsWith("mc:properties-load")) { return new CommandResult(0, propertiesDump()); }
        if (c.startsWith("mc:properties-save")) { propertiesSave(command); return new CommandResult(0, "OK"); }

        // ----- Métricas de la consola del juego (protocolo interno mc:*) -----
        if (c.startsWith("mc:list"))     { return new CommandResult(0, mockPlayerList()); }
        if (c.startsWith("mc:tps"))      { return new CommandResult(0, String.format(Locale.US, "%.1f", 18.5 + rnd.nextDouble() * 1.5)); }
        if (c.startsWith("mc:chunks"))   { return new CommandResult(0, String.valueOf(120 + rnd.nextInt(80))); }
        if (c.startsWith("mc:entities")) { return new CommandResult(0, String.valueOf(700 + rnd.nextInt(400))); }

        // ----- Backups (protocolo interno mc:backup-*) -----
        if (c.startsWith("mc:backup-create"))     { return handleBackupCreate(); }
        if (c.startsWith("mc:backup-list-local")) { return new CommandResult(0, String.join("\n", mockLocalBackups)); }
        if (c.startsWith("mc:backup-list-drive")) { return new CommandResult(0, String.join("\n", mockDriveBackups)); }
        if (c.startsWith("mc:backup-restore"))    { return new CommandResult(0, "restored"); }
        if (c.startsWith("mc:backup-upload"))     { return handleBackupUpload(); }
        if (c.startsWith("mc:backup-download"))   { return handleBackupDownload(command); }
        if (c.startsWith("mc:backup-delete-local")) { return backupDelete(mockLocalBackups, command); }
        if (c.startsWith("mc:backup-delete-drive")) { return backupDelete(mockDriveBackups, command); }
        if (c.startsWith("mc:backup-rename-local")) { return backupRename(mockLocalBackups, command); }
        if (c.startsWith("mc:backup-rename-drive")) { return backupRename(mockDriveBackups, command); }
        if (c.startsWith("mc:backup-move-to-drive")) { return backupMove(mockLocalBackups, mockDriveBackups, command); }
        if (c.startsWith("mc:backup-move-to-local")) { return backupMove(mockDriveBackups, mockLocalBackups, command); }
        if (c.startsWith("mc:backup-status"))     { return new CommandResult(0, backupRunning.get() ? "running" : "idle"); }

        // ----- Métricas de disco / conteo de archivos -----
        if (c.contains("du -sm")) {
            return c.contains("world")
                    ? new CommandResult(0, "892\t/home/dinamo/servidor/mohist/world")
                    : new CommandResult(0, "2458\t/home/dinamo/servidor");
        }
        if (c.contains("df -pm")) {
            // Filesystem 1M-blocks Used Available Use% Mounted-on  → total = 51200 MB (50 GB)
            return new CommandResult(0, "/dev/sda1 51200 2458 48742 5% /");
        }
        if (c.contains("wc -l")) {
            if (c.contains("plugins")) { return new CommandResult(0, "15"); }
            if (c.contains("mods"))    { return new CommandResult(0, "42"); }
            return new CommandResult(0, "0");
        }
        if (c.contains("version.txt"))   { return new CommandResult(0, "Mohist 1.20.1-JDK25"); }
        if (c.contains("ultimo-backup")) { return new CommandResult(0, lastBackupText); }

        // ----- tmux (envío de comandos al juego: whitelist/op/kick/ban/...) -----
        if (c.startsWith("tmux")) {
            applyGameCommand(command);
            return new CommandResult(0, "");
        }
        return new CommandResult(0, "[mock] " + command);
    }

    private String mockStatus() {
        if (!active.get()) {
            return "* mohist.service - Mohist Minecraft Server\n   Active: inactive (dead)\n";
        }
        return "* mohist.service - Mohist Minecraft Server\n"
             + "   Active: active (running) since Fri 2026-06-27 10:00:00 CST; 2h 15min ago\n"
             + "   Memory: 6.4G\n"
             + "      CPU: 1h 2min 30s\n";
    }

    /** Respuesta tipo comando {@code list}; los conectados salen del roster no baneado. */
    private String mockPlayerList() {
        int max = 20;
        List<String> pool = new ArrayList<>();
        for (String name : roster) {
            if (!isBanned(name)) {
                pool.add(name);
            }
        }
        java.util.Collections.shuffle(pool, rnd);
        int n = Math.min(pool.size(), rnd.nextInt(6)); // 0..5 conectados
        if (n == 0) {
            return "There are 0 of a max of " + max + " players online: ";
        }
        String list = String.join(", ", pool.subList(0, n));
        return "There are " + n + " of a max of " + max + " players online: " + list;
    }

    // ===================== mutación del roster vía comandos de juego =====================

    /** Interpreta el comando enviado por tmux y actualiza op/whitelist/ban. */
    private void applyGameCommand(String command) {
        String payload = quoted(command);
        if (payload == null) {
            return;
        }
        String[] t = payload.trim().split("\\s+");
        if (t.length < 2) {
            return;
        }
        String verb = t[0].toLowerCase(Locale.ROOT);
        switch (verb) {
            case "whitelist" -> {
                if (t.length >= 3) {
                    String name = t[2];
                    if ("add".equalsIgnoreCase(t[1])) {
                        roster.add(name);
                        whitelist.add(name.toLowerCase(Locale.ROOT));
                    } else if ("remove".equalsIgnoreCase(t[1])) {
                        whitelist.remove(name.toLowerCase(Locale.ROOT));
                    }
                }
            }
            case "op"     -> { roster.add(t[1]); ops.add(t[1].toLowerCase(Locale.ROOT)); }
            case "deop"   -> ops.remove(t[1].toLowerCase(Locale.ROOT));
            case "ban"    -> { roster.add(t[1]); banned.add(t[1].toLowerCase(Locale.ROOT)); }
            case "pardon" -> banned.remove(t[1].toLowerCase(Locale.ROOT));
            default -> { /* say, kick, save-all, difficulty, ...: sin cambio de estado */ }
        }
    }

    /** Texto entre las primeras y últimas comillas dobles del comando tmux. */
    private static String quoted(String command) {
        int a = command.indexOf('"');
        int b = command.lastIndexOf('"');
        return a >= 0 && b > a ? command.substring(a + 1, b) : null;
    }

    // ===================== roster JSON (whitelist/ops/banned/detail) =====================

    private boolean isWhitelisted(String name) {
        return whitelist.contains(name.toLowerCase(Locale.ROOT));
    }

    private boolean isOp(String name) {
        return ops.contains(name.toLowerCase(Locale.ROOT));
    }

    private boolean isBanned(String name) {
        return banned.contains(name.toLowerCase(Locale.ROOT));
    }

    /** Array JSON con todos los jugadores del roster que cumplen el filtro. */
    private String rosterJson(java.util.function.Predicate<String> include) {
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (String name : roster) {
            if (!include.test(name)) {
                continue;
            }
            if (!first) {
                sb.append(',');
            }
            sb.append(playerJson(name));
            first = false;
        }
        return sb.append(']').toString();
    }

    /** Nombre canónico (case-insensitive) tras {@code mc:player-detail <name>}. */
    private String detailName(String command) {
        String[] parts = command.trim().split("\\s+", 2);
        String raw = parts.length > 1 ? parts[1].trim() : "";
        for (String name : roster) {
            if (name.equalsIgnoreCase(raw)) {
                return name;
            }
        }
        return raw.isEmpty() ? "Desconocido" : raw;
    }

    /** Objeto JSON con la ficha completa (estadísticas deterministas por nombre). */
    private String playerJson(String name) {
        Random r = new Random(name.toLowerCase(Locale.ROOT).hashCode());
        boolean op = isOp(name);
        boolean ban = isBanned(name);
        String uuid = UUID.nameUUIDFromBytes(
                ("OfflinePlayer:" + name).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
        String gamemode = pick(r, "survival", "survival", "survival", "creative", "adventure");
        long firstPlayed = REF_MILLIS - (30L + r.nextInt(300)) * 86_400_000L;
        long lastPlayed = REF_MILLIS - (long) r.nextInt(72) * 3_600_000L;
        long playTimeMin = 60 + r.nextInt(40_000);

        StringBuilder sb = new StringBuilder(256);
        sb.append('{');
        kv(sb, "name", name).append(',');
        kv(sb, "uuid", uuid).append(',');
        kvBool(sb, "op", op).append(',');
        kvNum(sb, "opLevel", op ? opLevel(name) : 0).append(',');
        kvBool(sb, "whitelisted", isWhitelisted(name)).append(',');
        kvBool(sb, "banned", ban).append(',');
        kv(sb, "banReason", ban ? banReason(name) : "").append(',');
        kv(sb, "banSource", ban ? "Consola" : "").append(',');
        kv(sb, "bannedBy", ban ? "Dinamo" : "").append(',');
        kvNum(sb, "firstPlayed", firstPlayed).append(',');
        kvNum(sb, "lastPlayed", lastPlayed).append(',');
        kvNum(sb, "playTime", playTimeMin).append(',');
        kv(sb, "gamemode", gamemode).append(',');
        kvNum(sb, "level", 1 + r.nextInt(80)).append(',');
        kvDec(sb, "health", 0.5 + r.nextInt(40) * 0.5).append(',');
        kvNum(sb, "hunger", r.nextInt(21)).append(',');
        kvDec(sb, "x", -2000 + r.nextInt(4000) + r.nextDouble()).append(',');
        kvDec(sb, "y", 30 + r.nextInt(90) + r.nextDouble()).append(',');
        kvDec(sb, "z", -2000 + r.nextInt(4000) + r.nextDouble()).append(',');
        kv(sb, "ip", "192.168.1." + (2 + r.nextInt(252))).append(',');
        kvNum(sb, "deaths", r.nextInt(60)).append(',');
        kvNum(sb, "mobKills", r.nextInt(2000)).append(',');
        kvNum(sb, "playerKills", r.nextInt(40)).append(',');
        kvNum(sb, "distanceWalked", 1000 + r.nextInt(900_000)).append(',');
        kvDec(sb, "damageDealt", 100 + r.nextInt(50_000) + r.nextDouble()).append(',');
        kvDec(sb, "damageTaken", 100 + r.nextInt(40_000) + r.nextDouble()).append(',');
        kvNum(sb, "itemsEnchanted", r.nextInt(120)).append(',');
        kvNum(sb, "fishCaught", r.nextInt(300)).append(',');
        kvNum(sb, "trades", r.nextInt(200)).append(',');
        kvNum(sb, "timesSinceDeath", r.nextInt(5000)).append(',');
        kvNum(sb, "ping", 10 + r.nextInt(140)).append(',');
        kvNum(sb, "blocksMined", 1000 + r.nextInt(2_000_000)).append(',');
        kvNum(sb, "blocksPlaced", 1000 + r.nextInt(1_500_000)).append(',');
        kvNum(sb, "jumps", 500 + r.nextInt(200_000)).append(',');
        kvNum(sb, "advancements", r.nextInt(110)).append(',');
        kvNum(sb, "xpTotal", r.nextInt(500_000));
        return sb.append('}').toString();
    }

    private static int opLevel(String name) {
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "notch", "dinamo" -> 4;
            case "mariano" -> 3;
            default -> 2;
        };
    }

    private static String banReason(String name) {
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "herobrine" -> "Griefing reiterado";
            case "ghost" -> "Uso de x-ray";
            case "zero" -> "Lenguaje toxico";
            default -> "Incumplimiento de normas";
        };
    }

    // ===================== server.properties simulado =====================

    /** Devuelve el almacén como texto {@code clave=valor} (ordenado, estable). */
    private String propertiesDump() {
        StringBuilder sb = new StringBuilder(2048);
        for (var e : new TreeMap<>(propertiesStore).entrySet()) {
            sb.append(e.getKey()).append('=').append(e.getValue()).append('\n');
        }
        return sb.toString();
    }

    /** Reemplaza el almacén con el contenido recibido tras {@code mc:properties-save}. */
    private void propertiesSave(String command) {
        int sp = command.indexOf(' ');
        String body = sp >= 0 ? command.substring(sp + 1) : "";
        propertiesStore.clear();
        parseProperties(body);
    }

    /** Vuelca líneas {@code clave=valor} en el almacén (ignora vacías y comentarios). */
    private void parseProperties(String body) {
        for (String line : body.split("\\R")) {
            String s = line.strip();
            if (s.isEmpty() || s.charAt(0) == '#' || s.charAt(0) == '!') {
                continue;
            }
            int eq = s.indexOf('=');
            if (eq < 0) {
                continue;
            }
            propertiesStore.put(s.substring(0, eq).strip(), s.substring(eq + 1));
        }
    }

    // ===================== backups simulados =====================

    /** Simula {@code backup.sh}: marca "en curso" ~2 s y añade un archivo nuevo. */
    private CommandResult handleBackupCreate() {
        if (!backupRunning.compareAndSet(false, true)) {
            return new CommandResult(0, "already-running");
        }
        try {
            Thread.sleep(2000); // simula tar + subida a Drive
            String stamp = java.time.LocalDateTime.now()
                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm"));
            long size = 430_000_000L + rnd.nextInt(60_000_000);
            String line = "mundo-" + stamp + ".tar.gz " + size;
            mockLocalBackups.add(0, line);
            mockDriveBackups.add(0, line);
            lastBackupText = java.time.LocalDateTime.now()
                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) + " (recién creado)";
            return new CommandResult(0, "created mundo-" + stamp + ".tar.gz");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new CommandResult(1, "interrupted");
        } finally {
            backupRunning.set(false);
        }
    }

    /** Sube a Drive los locales que aún no estén allí. */
    private CommandResult handleBackupUpload() {
        for (String local : mockLocalBackups) {
            if (!mockDriveBackups.contains(local)) {
                mockDriveBackups.add(0, local);
            }
        }
        return new CommandResult(0, "uploaded");
    }

    /** Descarga de Drive a local el backup indicado (si no está ya en local). */
    private CommandResult handleBackupDownload(String command) {
        String name = secondToken(command);
        for (String drive : mockDriveBackups) {
            if (drive.startsWith(name + " ")
                    && mockLocalBackups.stream().noneMatch(l -> l.startsWith(name + " "))) {
                mockLocalBackups.add(0, drive);
            }
        }
        return new CommandResult(0, "downloaded");
    }

    /** Segundo token del comando (el argumento tras {@code mc:backup-download}). */
    private static String secondToken(String command) {
        String[] p = command.trim().split("\\s+", 2);
        return p.length > 1 ? p[1].trim() : "";
    }

    private CommandResult backupDelete(List<String> list, String command) {
        int i = indexOfBackup(list, secondToken(command));
        if (i >= 0) {
            list.remove(i);
        }
        return new CommandResult(0, "deleted");
    }

    private CommandResult backupRename(List<String> list, String command) {
        String[] p = command.trim().split("\\s+");
        if (p.length >= 3) {
            int i = indexOfBackup(list, p[1]);
            if (i >= 0) {
                list.set(i, p[2] + " " + sizePart(list.get(i)));
            }
        }
        return new CommandResult(0, "renamed");
    }

    private CommandResult backupMove(List<String> from, List<String> to, String command) {
        String name = secondToken(command);
        int i = indexOfBackup(from, name);
        if (i >= 0) {
            String line = from.remove(i);
            if (indexOfBackup(to, name) < 0) {
                to.add(0, line);
            }
        }
        return new CommandResult(0, "moved");
    }

    private static int indexOfBackup(List<String> list, String name) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).startsWith(name + " ")) {
                return i;
            }
        }
        return -1;
    }

    private static String sizePart(String line) {
        String[] p = line.trim().split("\\s+");
        return p.length >= 2 ? p[p.length - 1] : "0";
    }

    // ----- mini-serializador JSON (valores controlados, sin comillas internas) -----

    private static StringBuilder kv(StringBuilder sb, String key, String val) {
        return sb.append('"').append(key).append("\":\"").append(val).append('"');
    }

    private static StringBuilder kvNum(StringBuilder sb, String key, long val) {
        return sb.append('"').append(key).append("\":").append(val);
    }

    private static StringBuilder kvDec(StringBuilder sb, String key, double val) {
        return sb.append('"').append(key).append("\":").append(String.format(Locale.US, "%.1f", val));
    }

    private static StringBuilder kvBool(StringBuilder sb, String key, boolean val) {
        return sb.append('"').append(key).append("\":").append(val);
    }

    private String pick(Random r, String... options) {
        return options[r.nextInt(options.length)];
    }

    @Override
    public boolean isMock() {
        return true;
    }
}
