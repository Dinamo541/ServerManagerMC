# PROMPT PARA CLAUDE CODE — Implementar Etapa 2: Vista Jugadores

Eres un ingeniero de software experto en JavaFX 25, FXML, CSS moderno (glassmorphism, gradientes), y patrones de UI como los de Aternos. Vas a implementar la vista **Jugadores** completa para ServerManagerMC, una app JavaFX 25 + Maven que administra un servidor Minecraft Mohist.

## REGLAS CRÍTICAS

1. **NO uses modales ni diálogos** para mostrar info de jugadores. Todo debe ser inline, en paneles que aparecen/desaparecen.
2. **Toda I/O debe ir fuera del FX thread** usando `AsyncExecutor` (virtual threads).
3. **Sigue el estilo existente**: violeta/naranja oscuro, glassmorphism, gradientes, `.metric-card`, `.info-card`, `.player-chip`, `.btn-accent`, `.btn-danger`, `.btn-success`, `.btn-ghost`. Revisa DarkThemeStyle.css antes de escribir CSS nuevo.
4. **Mock mode**: todo debe funcionar en DEV (sin servidor real). Las operaciones player en mock deben devolver datos realistas.
5. **NO uses emojis en el código ni comentarios** (a menos que sean iconos tipo "☺", "◆", "▤" como los existentes).
6. **Respeta module-info.java**: los paquetes que necesites abrir deben declararse.

## CONTEXTO DEL PROYECTO

- **Stack**: Java 25, JavaFX 25.0.2, Maven, CoreFx (nav), AtlantaFX, MaterialFX
- **Perfiles**: `AppProfile.DEV` (Windows, mock) / `AppProfile.PROD` (Linux, real)
- **Patrón datos**: `AsyncExecutor.supply(() -> servicio(), this::renderEnFxThread)`
- **Nav**: `HomeController` carga vistas al centro de un BorderPane. Cada vista es FXML + Controller. Los botones del sidebar llaman `go*()` → `show("ViewName")`.
- **Estado servidor**: `ServerService.status()` → `ServerStatus` record con `onlinePlayerList` (string separado por comas)
- **Paths definidos en `ServerPaths`**: WHITELIST, OPS, BANNED ya existen apuntando a `/home/dinamo/servidor/mohist/*.json`
- **Player model actual** (en `model/Player.java`): `record Player(String name, String uuid, boolean op)` — básico, hay que extenderlo

## LO QUE DEBES CREAR/MODIFICAR

---

### 1. `model/PlayerDetail.java` (CREAR)

Record enriquecido con todos estos campos:

```java
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
    int timesSinceDeath
)
```

Incluye un builder interno (como ServerStatus tiene) para construirlo pieza por pieza.

---

### 2. `service/PlayerService.java` (CREAR)

```java
public final class PlayerService {
    private final CommandRunner runner;

    public PlayerService(CommandRunner runner);

    // ===== LECTURA DESDE JSON =====
    // En PROD: runner.run("cat /ruta/whitelist.json")
    // En MOCK: runner.run("mc:whitelist-json") devuelve JSON simulado
    // Usa Gson para parsear

    public List<PlayerDetail> whitelist();
    public List<PlayerDetail> ops();
    public List<PlayerDetail> bannedPlayers();
    public List<String> onlinePlayers(); // desde ServerStatus

    // ===== COMANDOS VÍA TMUX =====
    public Answer addToWhitelist(String name);
    public Answer removeFromWhitelist(String name);
    public Answer op(String name);
    public Answer deop(String name);
    public Answer kick(String name, String reason);
    public Answer ban(String name, String reason);
    public Answer pardon(String name);

    // ===== MOCK =====
    public List<PlayerDetail> mockWhitelist();
    public List<PlayerDetail> mockOps();
    public List<PlayerDetail> mockBanned();
    public PlayerDetail mockDetail(String name);

    // Helper
    public PlayerDetail fullDetail(String name);
}
```

**Mock**: 20+ nombres (Steve, Alex, Notch, Herobrine, Dinamo, Mariano, Luna, Pixel, Creeper, Ender, Ghost, Ninja, Vikingo, Tormenta, Blaze, Warden, Scout, Shadow, Phoenix, Neo, Zero). UUIDs con formato válido. Stats variadas. Algunos OP, algunos baneados. Los nombres deben ser consistentes entre whitelist, ops, banned y online.

---

### 3. `util/Nav.java` (CREAR)

Helper simple para navegación cross-view:

```java
public final class Nav {
    private Nav() {}

    private static final StringProperty SELECTED_PLAYER = new SimpleStringProperty();

    public static StringProperty selectedPlayerProperty() { return SELECTED_PLAYER; }
    public static String getSelectedPlayer() { return SELECTED_PLAYER.get(); }
    public static void selectPlayer(String name) { SELECTED_PLAYER.set(name); }
    public static void clearSelection() { SELECTED_PLAYER.set(null); }
}
```

---

### 4. `view/Players.fxml` (REWRITE COMPLETO)

Layout Aternos-like con split vertical:

```
ScrollPane
  VBox (padding 24, spacing 18)
    ─ Header row ─
    HBox: "Jugadores" (title) + Region spacer + TextField(search) + Button(+Añadir)
    
    ─ Main 2-column ─
    HBox (spacing 16)
    
      ─ LEFT (~60%): Lists ─
      VBox (HBox.hgrow=ALWAYS, minWidth=380)
        Category tabs: HBox con 4 botones (Conectados | Whitelist | Baneados | Operadores) + count label
        ScrollPane (VBox.vgrow=ALWAYS)
          VBox fx:id="playerList" (spacing=6) ← filas generadas dinámicamente
      
      ─ RIGHT (~40%): Detail panel ─
      VBox fx:id="detailPanel" (prefWidth=380, managed=false, visible=false)
        - Avatar + nombre + UUID
        - FlowPane de badges (OP, Whitelisted, Banned, Online)
        - Sección Información (grid 2-col: nivel, vida, hambre, modo, ubicación, IP, última vez, tiempo jugado)
        - Sección Estadísticas (grid 2-col: muertes, mob kills, player kills, distancia, daño, objetos encantados, peces, veces sin morir)
        - Sección Acciones (OP toggle, Whitelist toggle, Kick, Ban, Pardon)
        - HBox reasonBox (TextField + Confirmar + Cancelar, oculto por defecto)
```

NO uses componentes personalizados como `KVRow` — usa HBox + Label con styleClass.

---

### 5. `controller/PlayersController.java` (REWRITE COMPLETO ~600+ líneas)

- Lee `Nav.selectedPlayerProperty()` en initialize — si hay pre-selección
- Timeline refresh cada 5s
- Filtros: filterOnline, filterWhitelist, filterBanned, filterOps con tabs activos
- Search: filtra en tiempo real por nombre (sin llamadas al server)
- Player rows: HBox clickeables con status dot + nombre + badges (OP, banned)
- selectPlayer(String name) → renderDetail(PlayerDetail)
- Acciones: onToggleOp, onToggleWhitelist, onKick (muestra reasonBox), onBan (muestra reasonBox), onPardon, onConfirmAction, onCancelAction, onAddToWhitelist
- El detail panel al seleccionar debe animarse (fade in + slide from right)
- Al deseleccionar, el panel se oculta

---

### 6. `service/command/MockCommandRunner.java` (MODIFICAR)

Agregar handlers para:
- `mc:whitelist-json` → JSON array simulado
- `mc:ops-json` → JSON array simulado
- `mc:banned-json` → JSON array simulado
- `mc:player-detail <name>` → JSON detalle de ese jugador
- `tmux send-keys ... whitelist add ...` → siempre éxito
- `tmux send-keys ... whitelist remove ...` → siempre éxito
- `tmux send-keys ... op ...` → siempre éxito
- `tmux send-keys ... deop ...` → siempre éxito
- `tmux send-keys ... kick ...` → siempre éxito
- `tmux send-keys ... ban ...` → siempre éxito
- `tmux send-keys ... pardon ...` → siempre éxito

Los métodos mock deben generar JSON con StringBuilder (sin Gson en MockCommandRunner). Crea un pool interno de jugadores predefinidos.

---

### 7. `config/Services.java` (MODIFICAR)

Agregar:
```java
private static PlayerService players;

public static synchronized PlayerService players() {
    if (players == null) {
        players = new PlayerService(runner());
    }
    return players;
}
```

---

### 8. `pom.xml` (MODIFICAR)

Agregar dependencia:
```xml
<dependency>
    <groupId>com.google.code.gson</groupId>
    <artifactId>gson</artifactId>
    <version>2.11.0</version>
</dependency>
```

---

### 9. `module-info.java` (MODIFICAR)

```java
requires com.google.gson;
opens io.github.dinamo541.servermanagermc.model to com.google.gson;
```

---

### 10. `view/DarkThemeStyle.css` (MODIFICAR)

Agregar CSS para:
- `.category-tabs`, `.tab-btn`, `.tab-btn.active` (tabs de categoría)
- `.player-list`, `.player-row`, `.player-row:hover`, `.player-row.selected`
- `.player-status-dot` (circulito verde/gris)
- `.player-row-online`, `.player-row-whitelisted`, `.player-row-banned`
- `.player-avatar` (círculo morado 42px con inicial blanca)
- `.detail-card` (panel derecho, similar a info-card pero más ancho)
- `.detail-name` (22px bold), `.detail-uuid` (11px muted monospace)
- `.detail-section`, `.detail-section-title`
- `.badge`, `.badge-op` (naranja), `.badge-whitelisted` (verde), `.badge-banned` (rojo), `.badge-online` (verde con glow)
- `.reason-box`, `.reason-box .text-field`
- Transiciones suaves para detail panel aparecer/desaparecer

Usa las mismas variables `-fx-color-*` existentes. No introduzcas colores nuevos.

---

### 11. `controller/HomeController.java` (MODIFICAR)

Agregar:
```java
public static HomeController getInstance() {
    return (HomeController) FlowController.getInstance().getLoader("HomeView").getController();
}

public void navigateToPlayers(String playerName) {
    Nav.selectPlayer(playerName);
    // Llama a goPlayers() pero con el jugador preseleccionado
    setActive(navPlayers);
    show("Players");
}
```

---

### 12. `controller/DashboardController.java` (MODIFICAR)

En `renderPlayerChips`:
```java
chip.setCursor(Cursor.HAND);
chip.setOnMouseClicked(e -> navigateToPlayer(name));

// Método nuevo:
private void navigateToPlayer(String name) {
    HomeController.getInstance().navigateToPlayers(name);
}
```

---

### 13. `controller/ControlController.java` (MODIFICAR)

Mismo cambio que Dashboard en `renderPlayers()`.

---

## CONSIDERACIONES TÉCNICAS

1. **AsyncExecutor siempre**: Jamás llames servicios en el FX thread.
2. **Detail panel dinámico**: `managed=false, visible=false` cuando no hay selección. Al seleccionar, animación suave (opacity + translateX).
3. **Search instantáneo**: Filtra por nombre en la lista ya cargada, sin ir al server.
4. **Consistencia mock**: mismos nombres en whitelist, ops, banned y online.
5. **ReasonBox**: Al hacer Kick o Ban, aparece HBox con TextField + Confirmar/Cancelar. Solo una acción pendiente a la vez.
6. **Sin modales**: cero diálogos ni popups para info de jugadores. Si necesitas input (razón para kick/ban), usa el reasonBox inline.

## VERIFICACIÓN

Después de implementar todo, ejecuta:
```bash
mvn clean compile -q
```

Corrige cualquier error. No dejes TODOs ni placeholders. Los datos mock deben demostrar todas las funcionalidades.
