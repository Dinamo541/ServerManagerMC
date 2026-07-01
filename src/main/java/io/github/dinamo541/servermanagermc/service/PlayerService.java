package io.github.dinamo541.servermanagermc.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.dinamo541.corefx.util.Answer;
import io.github.dinamo541.servermanagermc.config.ServerPaths;
import io.github.dinamo541.servermanagermc.model.PlayerDetail;
import io.github.dinamo541.servermanagermc.service.command.CommandResult;
import io.github.dinamo541.servermanagermc.service.command.CommandRunner;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Gestión de jugadores: lee whitelist/operadores/baneados (de los {@code *.json}
 * del servidor en PROD, o de un roster simulado en DEV) y ejecuta acciones de
 * moderación (whitelist add/remove, op/deop, kick, ban, pardon) vía tmux, igual
 * que {@link ConsoleService} (Opción A: ejecutar los mismos comandos que un
 * humano, sin gestionar el proceso).
 */
public final class PlayerService {

    /** Origen del dato según de qué lista proviene (fija el flag correspondiente). */
    private enum Ctx { WHITELIST, OPS, BANNED, DETAIL, KNOWN }

    private static final Pattern NAME_OK = Pattern.compile("^[A-Za-z0-9_]{1,16}$");
    private static final Pattern UUID_OK = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
    private static final Pattern LIST = Pattern.compile(
            "players online:?\\s*(.*)", Pattern.CASE_INSENSITIVE);

    private final CommandRunner runner;

    public PlayerService(CommandRunner runner) {
        this.runner = runner;
    }

    // ===================== lectura de listas =====================

    public List<PlayerDetail> whitelist() {
        return parseArray(readList("mc:whitelist-json", ServerPaths.WHITELIST), Ctx.WHITELIST);
    }

    public List<PlayerDetail> ops() {
        return parseArray(readList("mc:ops-json", ServerPaths.OPS), Ctx.OPS);
    }

    public List<PlayerDetail> bannedPlayers() {
        return parseArray(readList("mc:banned-json", ServerPaths.BANNED), Ctx.BANNED);
    }

    /** Jugadores que ya entraron al mundo alguna vez (usercache.json). */
    public List<PlayerDetail> knownPlayers() {
        return parseArray(readList("mc:known-json", ServerPaths.USERCACHE), Ctx.KNOWN);
    }

    /**
     * Jugadores con advertencia activa (concepto del panel, no de Minecraft).
     * Se guardan en {@link ServerPaths#WARNINGS} (un nombre por línea). Devuelve
     * los nombres en minúscula para comparación directa.
     */
    public java.util.Set<String> warnedPlayers() {
        String out = runner.isMock()
                ? runner.run("mc:warnings").output()
                : runner.run("cat \"" + ServerPaths.WARNINGS + "\" 2>/dev/null").output();
        java.util.Set<String> set = new java.util.HashSet<>();
        if (out != null) {
            for (String line : out.split("\\r?\\n")) {
                String n = line.trim();
                if (!n.isEmpty()) {
                    set.add(n.toLowerCase(Locale.ROOT));
                }
            }
        }
        return set;
    }

    public Answer addWarning(String name) {
        if (name == null || !NAME_OK.matcher(name).matches()) {
            return Answer.failure("Nombre de jugador no válido.");
        }
        CommandResult r = runner.isMock()
                ? runner.run("mc:warn-add " + name)
                : runner.run("grep -qxF \"" + name + "\" \"" + ServerPaths.WARNINGS + "\" 2>/dev/null"
                        + " || echo \"" + name + "\" >> \"" + ServerPaths.WARNINGS + "\"");
        return r.ok()
                ? Answer.success("Advertencia añadida a " + name)
                : Answer.failure("No se pudo añadir la advertencia.", r.trimmed());
    }

    public Answer removeWarning(String name) {
        if (name == null || !NAME_OK.matcher(name).matches()) {
            return Answer.failure("Nombre de jugador no válido.");
        }
        CommandResult r = runner.isMock()
                ? runner.run("mc:warn-remove " + name)
                : runner.run("[ -f \"" + ServerPaths.WARNINGS + "\" ] && grep -vxF \"" + name + "\" \""
                        + ServerPaths.WARNINGS + "\" > \"" + ServerPaths.WARNINGS + ".tmp\" && mv \""
                        + ServerPaths.WARNINGS + ".tmp\" \"" + ServerPaths.WARNINGS + "\"");
        return r.ok()
                ? Answer.success("Advertencia retirada a " + name)
                : Answer.failure("No se pudo retirar la advertencia.", r.trimmed());
    }

    /** Nombres conectados, parseados de la salida del comando {@code list}. */
    public List<String> onlinePlayers() {
        String out = runner.run("mc:list").output();
        Matcher m = LIST.matcher(out == null ? "" : out);
        List<String> names = new ArrayList<>();
        if (m.find()) {
            String tail = m.group(1).trim();
            if (!tail.isEmpty()) {
                for (String n : tail.split("\\s*,\\s*")) {
                    if (!n.isBlank()) {
                        names.add(n.trim());
                    }
                }
            }
        }
        return names;
    }

    /** Ficha completa de un jugador (rica en DEV; derivada de las listas en PROD). */
    public PlayerDetail fullDetail(String name) {
        if (runner.isMock()) {
            String json = runner.run("mc:player-detail " + name).output();
            try {
                JsonElement el = JsonParser.parseString(json == null ? "" : json);
                if (el != null && el.isJsonObject()) {
                    return parseOne(el.getAsJsonObject(), Ctx.DETAIL);
                }
            } catch (RuntimeException ignored) {
                // cae al detalle básico
            }
            return PlayerDetail.builder(name).build();
        }
        return deriveDetail(name);
    }

    /** En PROD se compone con los flags de pertenencia (op/whitelist/ban). */
    private PlayerDetail deriveDetail(String name) {
        PlayerDetail.Builder b = PlayerDetail.builder(name);
        PlayerDetail op = findByName(ops(), name);
        PlayerDetail wl = findByName(whitelist(), name);
        PlayerDetail bn = findByName(bannedPlayers(), name);
        if (wl != null) {
            b.whitelisted(true).uuid(wl.uuid());
        }
        if (op != null) {
            b.op(true).opLevel(op.opLevel()).uuid(op.uuid());
        }
        if (bn != null) {
            b.banned(true).banReason(bn.banReason()).banSource(bn.banSource())
             .bannedBy(bn.bannedBy()).uuid(bn.uuid());
        }
        return b.build();
    }

    private static PlayerDetail findByName(List<PlayerDetail> list, String name) {
        for (PlayerDetail p : list) {
            if (p.name() != null && p.name().equalsIgnoreCase(name)) {
                return p;
            }
        }
        return null;
    }

    // ===================== métodos "mock" (perfil DEV) =====================

    public List<PlayerDetail> mockWhitelist() {
        return parseArray(runner.run("mc:whitelist-json").output(), Ctx.WHITELIST);
    }

    public List<PlayerDetail> mockOps() {
        return parseArray(runner.run("mc:ops-json").output(), Ctx.OPS);
    }

    public List<PlayerDetail> mockBanned() {
        return parseArray(runner.run("mc:banned-json").output(), Ctx.BANNED);
    }

    public PlayerDetail mockDetail(String name) {
        String json = runner.run("mc:player-detail " + name).output();
        JsonElement el = JsonParser.parseString(json == null ? "{}" : json);
        return el.isJsonObject()
                ? parseOne(el.getAsJsonObject(), Ctx.DETAIL)
                : PlayerDetail.builder(name).build();
    }

    // ===================== acciones de moderación (tmux) =====================

    public Answer addToWhitelist(String name) {
        return game(name, "whitelist add " + name, "Añadido a la whitelist: " + name);
    }

    public Answer removeFromWhitelist(String name) {
        return game(name, "whitelist remove " + name, "Quitado de la whitelist: " + name);
    }

    public Answer op(String name) {
        return game(name, "op " + name, name + " ahora es operador.");
    }

    public Answer deop(String name) {
        return game(name, "deop " + name, name + " ya no es operador.");
    }

    public Answer kick(String name, String reason) {
        String r = sanitizeReason(reason);
        return game(name, "kick " + name + (r.isEmpty() ? "" : " " + r),
                "Expulsado: " + name);
    }

    public Answer ban(String name, String reason) {
        String r = sanitizeReason(reason);
        return game(name, "ban " + name + (r.isEmpty() ? "" : " " + r),
                "Baneado: " + name);
    }

    public Answer pardon(String name) {
        return game(name, "pardon " + name, "Baneo retirado: " + name);
    }

    // --- Acciones que requieren al jugador conectado (comandos sobre la entidad) ---

    private static final java.util.Set<String> GAMEMODES =
            java.util.Set.of("survival", "creative", "adventure", "spectator");

    public Answer gamemode(String name, String mode) {
        if (mode == null || !GAMEMODES.contains(mode.toLowerCase(Locale.ROOT))) {
            return Answer.failure("Modo de juego no válido.");
        }
        return game(name, "gamemode " + mode.toLowerCase(Locale.ROOT) + " " + name,
                "Modo de " + name + ": " + mode.toLowerCase(Locale.ROOT));
    }

    public Answer giveXp(String name, int levels) {
        return game(name, "xp add " + name + " " + levels + " levels",
                "XP +" + levels + " niveles a " + name);
    }

    public Answer grantAdvancements(String name) {
        return game(name, "advancement grant " + name + " everything", "Logros otorgados a " + name);
    }

    public Answer revokeAdvancements(String name) {
        return game(name, "advancement revoke " + name + " everything", "Logros retirados a " + name);
    }

    public Answer heal(String name) {
        return game(name, "effect give " + name + " minecraft:instant_health 1 10", "Curado: " + name);
    }

    public Answer feed(String name) {
        return game(name, "effect give " + name + " minecraft:saturation 2 255", "Alimentado: " + name);
    }

    public Answer killPlayer(String name) {
        return game(name, "kill " + name, "Eliminado: " + name);
    }

    public Answer clearInventory(String name) {
        return game(name, "clear " + name, "Inventario vaciado: " + name);
    }

    /**
     * Reinicia las estadísticas del jugador vaciando su {@code world/stats/<uuid>.json}.
     * No pasa por tmux (es de sistema de archivos), por eso funciona aunque esté offline.
     */
    public Answer resetStats(String name, String uuid) {
        if (uuid == null || !UUID_OK.matcher(uuid).matches()) {
            return Answer.failure("UUID desconocido; no se pueden reiniciar las estadísticas.");
        }
        CommandResult r = runner.run(
                "printf '{}' > \"" + ServerPaths.WORLD + "/stats/" + uuid + ".json\"");
        return r.ok()
                ? Answer.success("Estadísticas reiniciadas: " + name)
                : Answer.failure("No se pudieron reiniciar las estadísticas.", r.trimmed());
    }

    /** Envía un comando de juego por tmux tras validar el nombre del jugador. */
    private Answer game(String name, String command, String okMessage) {
        if (name == null || !NAME_OK.matcher(name).matches()) {
            return Answer.failure("Nombre de jugador no válido (solo letras, números y _).");
        }
        CommandResult r = runner.run(
                "tmux send-keys -t " + ServerPaths.TMUX_SESSION + " \"" + command + "\" Enter");
        return r.ok()
                ? Answer.success(okMessage)
                : Answer.failure("No se pudo ejecutar: " + command, r.trimmed());
    }

    private static String sanitizeReason(String reason) {
        if (reason == null) {
            return "";
        }
        return reason.replaceAll("[\"`$]", "").strip();
    }

    // ===================== parseo JSON =====================

    private String readList(String mockCommand, String prodPath) {
        return runner.isMock()
                ? runner.run(mockCommand).output()
                : runner.run("cat \"" + prodPath + "\" 2>/dev/null").output();
    }

    private List<PlayerDetail> parseArray(String json, Ctx ctx) {
        List<PlayerDetail> out = new ArrayList<>();
        if (json == null || json.isBlank()) {
            return out;
        }
        try {
            JsonElement root = JsonParser.parseString(json);
            if (root.isJsonArray()) {
                JsonArray arr = root.getAsJsonArray();
                for (JsonElement e : arr) {
                    if (e.isJsonObject()) {
                        out.add(parseOne(e.getAsJsonObject(), ctx));
                    }
                }
            }
        } catch (RuntimeException ignored) {
            // JSON malformado o archivo ausente: devolvemos lo acumulado
        }
        return out;
    }

    /**
     * Construye un {@link PlayerDetail} desde un objeto JSON. Acepta tanto las
     * claves ricas del mock como las de los archivos reales de Minecraft
     * (ops.json usa {@code level}; banned-players.json usa {@code reason}/{@code source}).
     */
    private PlayerDetail parseOne(JsonObject o, Ctx ctx) {
        PlayerDetail.Builder b = PlayerDetail.builder(str(o, "name", "—"));
        b.uuid(str(o, "uuid", "—"));

        boolean op = bool(o, "op", ctx == Ctx.OPS);
        b.op(op);
        b.opLevel(intOf(o, "opLevel", intOf(o, "level", op ? 1 : 0)));
        b.whitelisted(bool(o, "whitelisted", ctx == Ctx.WHITELIST));
        b.banned(bool(o, "banned", ctx == Ctx.BANNED));
        b.banReason(str(o, "banReason", str(o, "reason", "")));
        b.banSource(str(o, "banSource", str(o, "source", "")));
        b.bannedBy(str(o, "bannedBy", str(o, "source", "")));

        b.firstPlayed(longOf(o, "firstPlayed", -1));
        b.lastPlayed(longOf(o, "lastPlayed", -1));
        b.playTime(longOf(o, "playTime", -1));
        b.gamemode(str(o, "gamemode", "—"));
        b.level(intOf(o, "level", -1));
        b.health((float) doubleOf(o, "health", -1));
        b.hunger(intOf(o, "hunger", -1));
        b.x(doubleOf(o, "x", Double.NaN));
        b.y(doubleOf(o, "y", Double.NaN));
        b.z(doubleOf(o, "z", Double.NaN));
        b.ip(str(o, "ip", "—"));
        b.deaths(intOf(o, "deaths", -1));
        b.mobKills(intOf(o, "mobKills", -1));
        b.playerKills(intOf(o, "playerKills", -1));
        b.distanceWalked(longOf(o, "distanceWalked", -1));
        b.damageDealt(doubleOf(o, "damageDealt", -1));
        b.damageTaken(doubleOf(o, "damageTaken", -1));
        b.itemsEnchanted(intOf(o, "itemsEnchanted", -1));
        b.fishCaught(intOf(o, "fishCaught", -1));
        b.trades(intOf(o, "trades", -1));
        b.timesSinceDeath(intOf(o, "timesSinceDeath", -1));
        b.ping(intOf(o, "ping", -1));
        b.blocksMined(longOf(o, "blocksMined", -1));
        b.blocksPlaced(longOf(o, "blocksPlaced", -1));
        b.jumps(longOf(o, "jumps", -1));
        b.advancements(intOf(o, "advancements", -1));
        b.xpTotal(longOf(o, "xpTotal", -1));
        return b.build();
    }

    private static boolean has(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull();
    }

    private static String str(JsonObject o, String key, String def) {
        try {
            return has(o, key) ? o.get(key).getAsString() : def;
        } catch (RuntimeException e) {
            return def;
        }
    }

    private static int intOf(JsonObject o, String key, int def) {
        try {
            return has(o, key) ? o.get(key).getAsInt() : def;
        } catch (RuntimeException e) {
            return def;
        }
    }

    private static long longOf(JsonObject o, String key, long def) {
        try {
            return has(o, key) ? o.get(key).getAsLong() : def;
        } catch (RuntimeException e) {
            return def;
        }
    }

    private static double doubleOf(JsonObject o, String key, double def) {
        try {
            return has(o, key) ? o.get(key).getAsDouble() : def;
        } catch (RuntimeException e) {
            return def;
        }
    }

    private static boolean bool(JsonObject o, String key, boolean def) {
        try {
            return has(o, key) ? o.get(key).getAsBoolean() : def;
        } catch (RuntimeException e) {
            return def;
        }
    }
}
