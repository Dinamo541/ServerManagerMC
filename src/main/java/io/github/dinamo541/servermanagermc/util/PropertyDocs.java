package io.github.dinamo541.servermanagermc.util;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Documentación en español de las claves de {@code server.properties}: un título
 * legible y una explicación de para qué sirve cada una. Es la única fuente de
 * verdad para la etiqueta amigable (título) y para el texto de la ventana de
 * ayuda del editor de propiedades.
 */
public final class PropertyDocs {

    /** Título humano (español) y descripción de una clave de {@code server.properties}. */
    public record Doc(String titulo, String descripcion) {
    }

    private static final Map<String, Doc> DOCS = new LinkedHashMap<>();

    private PropertyDocs() {
    }

    /** Devuelve la documentación de {@code key}, o una genérica si no está registrada. */
    public static Doc get(String key) {
        Doc d = DOCS.get(key);
        return d != null ? d : new Doc(key, "Sin descripción disponible para esta propiedad.");
    }

    private static void d(String key, String titulo, String descripcion) {
        DOCS.put(key, new Doc(titulo, descripcion));
    }

    static {
        // ----- Booleanos -----
        d("online-mode", "Modo en línea",
                "Verifica cada cuenta contra los servidores de Mojang. Actívalo en servidores "
                + "públicos para evitar suplantaciones; desactívalo solo para permitir clientes "
                + "sin licencia (offline).");
        d("pvp", "Combate entre jugadores (PvP)",
                "Permite que los jugadores se hagan daño entre sí. Si se desactiva, los golpes "
                + "entre jugadores no causan daño.");
        d("allow-flight", "Permitir vuelo",
                "Permite volar en supervivencia a quien tenga mods/plugins que lo habiliten. No "
                + "afecta al modo creativo. Evita expulsiones por 'flying is not enabled'.");
        d("enforce-whitelist", "Forzar whitelist",
                "Al activarse, expulsa de inmediato a cualquier jugador que no esté en la "
                + "whitelist. Requiere que la lista blanca esté activada.");
        d("white-list", "Lista blanca (whitelist)",
                "Si está activa, solo pueden entrar los jugadores incluidos en la whitelist.");
        d("spawn-animals", "Generar animales",
                "Permite que aparezcan de forma natural animales pacíficos (vacas, ovejas, etc.).");
        d("spawn-monsters", "Generar monstruos",
                "Permite que aparezcan criaturas hostiles (zombis, esqueletos…). Es independiente "
                + "de la dificultad.");
        d("spawn-npcs", "Generar aldeanos",
                "Permite que aparezcan aldeanos (NPCs) en las aldeas.");
        d("enable-command-block", "Bloques de comandos",
                "Habilita el uso de bloques de comandos dentro del mundo.");
        d("hardcore", "Modo hardcore",
                "Fija la dificultad en difícil y, al morir, el jugador queda como espectador de "
                + "forma permanente. Úsalo con cuidado.");
        d("generate-structures", "Generar estructuras",
                "Permite que se generen estructuras (aldeas, mazmorras, templos…) al crear "
                + "terreno nuevo.");
        d("allow-nether", "Permitir el Nether",
                "Habilita el acceso a la dimensión del Nether mediante portales.");
        d("broadcast-console-to-ops", "Difundir consola a operadores",
                "Muestra a los operadores conectados los comandos ejecutados desde la consola.");
        d("broadcast-rcon-to-ops", "Difundir RCON a operadores",
                "Muestra a los operadores los comandos ejecutados a través de RCON.");
        d("enable-rcon", "Habilitar RCON",
                "Activa el acceso remoto por consola (RCON). Requiere una contraseña. Mantenlo "
                + "apagado si no lo usas.");
        d("enable-query", "Habilitar Query",
                "Activa el protocolo Query (GameSpy4) para consultar el estado del servidor desde "
                + "fuera.");
        d("enable-jmx-monitoring", "Monitoreo JMX",
                "Expone métricas de rendimiento del servidor vía JMX para herramientas externas de "
                + "monitoreo.");
        d("enforce-secure-profile", "Perfiles seguros",
                "Exige a los jugadores un perfil de chat firmado por Mojang. Desactívalo solo si "
                + "sabes lo que haces.");
        d("prevent-proxy-connections", "Bloquear proxies",
                "Rechaza las conexiones que llegan a través de un proxy/VPN distinto al de la "
                + "cuenta.");
        d("use-native-transport", "Transporte nativo (Linux)",
                "Usa optimizaciones de red nativas del sistema (epoll) en Linux, mejorando el "
                + "rendimiento de red.");
        d("sync-chunk-writes", "Escritura sincrónica de chunks",
                "Guarda los chunks en disco de forma sincrónica: más seguro ante caídas, pero "
                + "puede impactar el rendimiento.");
        d("enable-status", "Responder al estado",
                "Permite que el servidor aparezca como 'en línea' en la lista de servidores y "
                + "responda a los pings.");
        d("hide-online-players", "Ocultar jugadores conectados",
                "Oculta la lista de jugadores conectados en la información del ping del servidor.");
        d("require-resource-pack", "Exigir resource pack",
                "Obliga a aceptar el paquete de recursos para poder jugar. Si el jugador lo "
                + "rechaza, es expulsado.");

        // ----- Numéricos -----
        d("view-distance", "Distancia de visión",
                "Radio de chunks que el servidor envía a cada jugador. Más alto = se ve más lejos, "
                + "pero consume más CPU, RAM y ancho de banda.");
        d("simulation-distance", "Distancia de simulación",
                "Radio de chunks donde hay actividad (mobs, cultivos, redstone). Es uno de los "
                + "ajustes que más influye en el rendimiento.");
        d("max-players", "Jugadores máximos",
                "Número máximo de jugadores conectados a la vez.");
        d("max-world-size", "Tamaño máximo del mundo",
                "Radio máximo del mundo, en bloques, desde el punto de aparición. Limita hasta "
                + "dónde puede generarse terreno.");
        d("server-port", "Puerto del servidor",
                "Puerto de red en el que escucha el servidor. El estándar de Minecraft es 25565.");
        d("rate-limit", "Límite de paquetes",
                "Máximo de paquetes por segundo por jugador antes de ser desconectado. 0 = sin "
                + "límite.");
        d("max-tick-time", "Tiempo máximo por tick",
                "Milisegundos que puede tardar un tick antes de que el watchdog considere colgado "
                + "el servidor y lo reinicie. Súbelo para evitar cierres en picos de carga.");
        d("entity-broadcast-range-percentage", "Rango de difusión de entidades",
                "Porcentaje del rango normal al que se envían las entidades a los clientes. Menor "
                + "= menos ancho de banda, pero se ven más tarde.");
        d("player-idle-timeout", "Tiempo de inactividad",
                "Minutos de inactividad tras los cuales se expulsa al jugador. 0 = nunca.");
        d("max-chained-neighbor-updates", "Actualizaciones encadenadas máximas",
                "Límite de actualizaciones de bloques vecinos encadenadas antes de descartarlas. "
                + "Previene ciertas 'lag machines'.");
        d("op-permission-level", "Nivel de permiso de OP",
                "Nivel (1–4) que reciben los operadores. 4 concede acceso total, incluyendo "
                + "comandos como /stop.");
        d("function-permission-level", "Nivel de permiso de funciones",
                "Nivel de permisos (0–4) con el que se ejecutan las funciones y datapacks.");
        d("network-compression-threshold", "Umbral de compresión de red",
                "Tamaño en bytes a partir del cual se comprimen los paquetes. -1 desactiva la "
                + "compresión; 0 comprime todo.");
        d("region-file-compression", "Compresión de archivos de región",
                "Nivel de compresión de los archivos de región del mundo guardados en disco.");

        // ----- Cadenas -----
        d("motd", "Mensaje del día (MOTD)",
                "Texto que aparece bajo el nombre del servidor en la lista de servidores. Admite "
                + "códigos de color.");
        d("level-name", "Nombre del mundo",
                "Carpeta/nombre del mundo que carga el servidor. Cambiarlo carga (o crea) otro "
                + "mundo distinto.");
        d("level-seed", "Semilla del mundo",
                "Semilla para generar el terreno de un mundo nuevo. Vacío = aleatoria. No afecta a "
                + "mundos ya generados.");
        d("level-type", "Tipo de mundo",
                "Generador del terreno: normal (default), plano (flat), biomas grandes, "
                + "amplificado, etc.");
        d("difficulty", "Dificultad",
                "Dificultad del juego: pacífico, fácil, normal o difícil. Afecta al daño y a la "
                + "aparición de hostiles.");
        d("gamemode", "Modo de juego",
                "Modo por defecto para quien entra: supervivencia, creativo, aventura o "
                + "espectador.");
        d("resource-pack", "Resource pack (URL)",
                "URL del paquete de recursos que se ofrece u obliga a los jugadores al conectarse.");
        d("resource-pack-sha1", "SHA-1 del resource pack",
                "Hash SHA-1 del resource pack para verificar su integridad. Muy recomendado si "
                + "usas un resource pack.");
        d("resource-pack-prompt", "Mensaje del resource pack",
                "Texto que se muestra al jugador al pedirle que acepte el resource pack.");
        d("server-ip", "IP del servidor",
                "Interfaz de red a la que se enlaza el servidor. Déjalo vacío para escuchar en "
                + "todas.");
        d("rcon.password", "Contraseña RCON",
                "Contraseña para el acceso remoto por consola (RCON). Solo se usa si RCON está "
                + "activado.");
        d("rcon.port", "Puerto RCON",
                "Puerto en el que escucha el servicio RCON.");
        d("query.port", "Puerto Query",
                "Puerto en el que escucha el protocolo Query.");
        d("text-filtering-config", "Filtrado de texto",
                "Configuración de un servicio externo de filtrado de chat. Normalmente se deja "
                + "vacío.");
        d("initial-enabled-packs", "Datapacks activados al inicio",
                "Datapacks del núcleo habilitados al crear el mundo (por defecto 'vanilla').");
        d("initial-disabled-packs", "Datapacks desactivados al inicio",
                "Datapacks que quedan deshabilitados al crear el mundo.");
        d("bug-report-url", "URL de reporte de errores",
                "Dirección a la que se enlazan los reportes de fallos del cliente.");
    }
}
