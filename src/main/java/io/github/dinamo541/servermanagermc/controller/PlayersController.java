package io.github.dinamo541.servermanagermc.controller;

import io.github.dinamo541.corefx.util.Answer;
import io.github.dinamo541.servermanagermc.concurrent.AsyncExecutor;
import io.github.dinamo541.servermanagermc.config.Services;
import io.github.dinamo541.servermanagermc.model.PlayerDetail;
import io.github.dinamo541.servermanagermc.util.AnimationUtil;
import io.github.dinamo541.servermanagermc.util.Nav;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.fxml.FXML;
import javafx.scene.Cursor;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

/**
 * Vista Jugadores: lista por categorías (conectados / whitelist / operadores /
 * baneados) con búsqueda instantánea a la izquierda, y una ficha de detalle a la
 * derecha con identidad, estadísticas y acciones de moderación (op/deop,
 * whitelist, kick, ban/pardon) sin diálogos: el motivo de kick/ban se pide en
 * una caja inline.
 *
 * <p>Toda la E/S va por {@link AsyncExecutor}; los datos se refrescan cada 5 s y
 * la lista solo se reconstruye cuando su contenido cambia (evita parpadeo).
 */
public class PlayersController {

    private enum Tab { ONLINE, WHITELIST, OPS, BANNED, HISTORY }

    private enum PendingAction { NONE, KICK, BAN }

    /** Resumen de un jugador para pintar una fila (los datos ricos van en la ficha). */
    private record Row(String name, String uuid, boolean op, boolean banned,
                       boolean whitelisted, boolean online, boolean known) {
    }

    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.systemDefault());

    @FXML private VBox rootBox;
    @FXML private TextField searchField;
    @FXML private Button addBtn;

    @FXML private Button tabOnline;
    @FXML private Button tabWhitelist;
    @FXML private Button tabOps;
    @FXML private Button tabBanned;
    @FXML private Button tabHistory;
    @FXML private Label countLabel;
    @FXML private VBox playerList;

    // ----- Resumen general -----
    @FXML private Label statTotal;
    @FXML private Label statOnline;
    @FXML private Label statWhitelist;
    @FXML private Label statOps;
    @FXML private Label statBanned;
    @FXML private Label statHistory;

    // ----- Panel "añadir por nombre" -----
    @FXML private ScrollPane addPane;
    @FXML private VBox addPanel;
    @FXML private TextField addNameField;
    @FXML private Label addStatus;

    @FXML private ScrollPane rightPane;
    @FXML private VBox detailPanel;
    @FXML private Label detailAvatar;
    @FXML private Label detailName;
    @FXML private Label detailUuid;
    @FXML private Label detailSummary;
    @FXML private FlowPane detailBadges;
    @FXML private Label banSectionTitle;
    @FXML private VBox banSection;
    @FXML private VBox infoBox;
    @FXML private VBox statsBox;
    @FXML private Button opBtn;
    @FXML private Button whitelistBtn;
    @FXML private Button kickBtn;
    @FXML private Button banBtn;
    @FXML private VBox liveBox;
    @FXML private ComboBox<String> gamemodeCombo;
    @FXML private VBox reasonBox;
    @FXML private Label reasonTitle;
    @FXML private TextField reasonField;
    @FXML private Button reasonConfirm;
    @FXML private Label actionStatus;

    private Tab activeTab = Tab.ONLINE;
    private PendingAction pendingAction = PendingAction.NONE;

    private final Map<String, Row> allRows = new LinkedHashMap<>();
    private final java.util.Set<String> onlineLower = new java.util.HashSet<>();
    private boolean rosterLoaded;
    private String selectedName;
    private PlayerDetail selectedDetail;
    private String pendingSelection;
    private String lastSignature; // null fuerza el primer render (aunque la lista esté vacía)

    private Timeline refresh;

    // ===================== ciclo de vida =====================

    @FXML
    private void initialize() {
        setActiveTab(Tab.ONLINE);
        gamemodeCombo.getItems().setAll("survival", "creative", "adventure", "spectator");
        searchField.textProperty().addListener((obs, old, val) -> rebuildList(false, true));

        pendingSelection = Nav.getSelectedPlayer();
        Nav.selectedPlayerProperty().addListener((obs, old, val) -> onNavSelect(val));

        AnimationUtil.staggerIn(rootBox.getChildren());

        refresh = new Timeline(
                new KeyFrame(Duration.ZERO, e -> reload()),
                new KeyFrame(Duration.seconds(5)));
        refresh.setCycleCount(Animation.INDEFINITE);
        refresh.play();
    }

    private void onNavSelect(String name) {
        if (name == null || name.isBlank()) {
            return;
        }
        if (rosterLoaded) {
            selectPlayer(name);
        } else {
            pendingSelection = name;
        }
    }

    private void reload() {
        AsyncExecutor.supply(this::loadRoster, this::onRoster);
    }

    // ===================== carga de datos (fuera del hilo UI) =====================

    private Map<String, Row> loadRoster() {
        var service = Services.players();
        List<PlayerDetail> whitelist = service.whitelist();
        List<PlayerDetail> ops = service.ops();
        List<PlayerDetail> banned = service.bannedPlayers();
        List<PlayerDetail> known = service.knownPlayers();
        List<String> online = service.onlinePlayers();

        java.util.Set<String> onlineSet = new java.util.HashSet<>();
        for (String n : online) {
            onlineSet.add(n.toLowerCase(Locale.ROOT));
        }

        Map<String, Row> map = new LinkedHashMap<>();
        mergeAll(map, whitelist, onlineSet, false);
        mergeAll(map, ops, onlineSet, false);
        mergeAll(map, banned, onlineSet, false);
        mergeAll(map, known, onlineSet, true); // estos ya entraron al mundo
        // Conectados que no estuvieran en ninguna lista (raro, pero posible).
        for (String n : online) {
            String key = n.toLowerCase(Locale.ROOT);
            if (!map.containsKey(key)) {
                map.put(key, new Row(n, "—", false, false, false, true, true));
            }
        }
        return map;
    }

    private void mergeAll(Map<String, Row> map, List<PlayerDetail> list,
                          java.util.Set<String> onlineSet, boolean known) {
        for (PlayerDetail p : list) {
            String key = p.name() == null ? "" : p.name().toLowerCase(Locale.ROOT);
            boolean online = onlineSet.contains(key);
            boolean op = p.op() || p.opLevel() > 0;
            Row prev = map.get(key);
            if (prev == null) {
                map.put(key, new Row(p.name(), p.uuid(), op, p.banned(),
                        p.whitelisted(), online, known));
            } else {
                map.put(key, new Row(prev.name(),
                        prev.uuid() != null && !prev.uuid().equals("—") ? prev.uuid() : p.uuid(),
                        prev.op() || op,
                        prev.banned() || p.banned(),
                        prev.whitelisted() || p.whitelisted(),
                        prev.online() || online,
                        prev.known() || known));
            }
        }
    }

    private void onRoster(Map<String, Row> map) {
        allRows.clear();
        allRows.putAll(map);
        onlineLower.clear();
        for (Row r : map.values()) {
            if (r.online()) {
                onlineLower.add(r.name().toLowerCase(Locale.ROOT));
            }
        }
        rosterLoaded = true;
        renderSummary();
        rebuildList(false, false);

        if (pendingSelection != null) {
            String name = pendingSelection;
            pendingSelection = null;
            selectPlayer(name);
        }
    }

    private void renderSummary() {
        int total = allRows.size();
        int online = 0;
        int whitelist = 0;
        int ops = 0;
        int banned = 0;
        int history = 0;
        for (Row r : allRows.values()) {
            if (r.online()) {
                online++;
            }
            if (r.whitelisted()) {
                whitelist++;
            }
            if (r.op()) {
                ops++;
            }
            if (r.banned()) {
                banned++;
            }
            if (isVisitor(r)) {
                history++;
            }
        }
        statTotal.setText(String.valueOf(total));
        statOnline.setText(String.valueOf(online));
        statWhitelist.setText(String.valueOf(whitelist));
        statOps.setText(String.valueOf(ops));
        statBanned.setText(String.valueOf(banned));
        statHistory.setText(String.valueOf(history));
    }

    /** Visitante = ya entró al mundo pero no está en whitelist ni baneado. */
    private static boolean isVisitor(Row r) {
        return r.known() && !r.whitelisted() && !r.banned();
    }

    // ===================== lista (izquierda) =====================

    private List<Row> displayedRows() {
        String q = searchField.getText() == null ? "" : searchField.getText().trim().toLowerCase(Locale.ROOT);
        List<Row> out = new ArrayList<>();
        for (Row r : allRows.values()) {
            boolean inTab = switch (activeTab) {
                case ONLINE -> r.online();
                case WHITELIST -> r.whitelisted();
                case OPS -> r.op();
                case BANNED -> r.banned();
                case HISTORY -> isVisitor(r);
            };
            if (inTab && (q.isEmpty() || r.name().toLowerCase(Locale.ROOT).contains(q))) {
                out.add(r);
            }
        }
        return out;
    }

    private void rebuildList(boolean animate, boolean force) {
        List<Row> rows = displayedRows();
        String signature = signatureOf(rows);
        if (!force && signature.equals(lastSignature)) {
            return; // nada cambió: evitamos reconstruir y el parpadeo
        }
        lastSignature = signature;

        countLabel.setText(String.valueOf(rows.size()));
        playerList.getChildren().clear();
        if (rows.isEmpty()) {
            Label empty = new Label(emptyMessage());
            empty.getStyleClass().add("muted");
            playerList.getChildren().add(empty);
            return;
        }
        for (Row r : rows) {
            playerList.getChildren().add(rowNode(r));
        }
        if (animate) {
            AnimationUtil.staggerIn(playerList.getChildren());
        }
    }

    private String emptyMessage() {
        return switch (activeTab) {
            case ONLINE -> "Nadie conectado";
            case WHITELIST -> "Whitelist vacía";
            case OPS -> "Sin operadores";
            case BANNED -> "Sin baneados";
            case HISTORY -> "Sin visitantes registrados";
        };
    }

    private String signatureOf(List<Row> rows) {
        StringBuilder sb = new StringBuilder();
        for (Row r : rows) {
            sb.append(r.name()).append(r.op() ? 'o' : '-')
              .append(r.banned() ? 'b' : '-').append(r.online() ? 'n' : '-').append('|');
        }
        return sb.toString();
    }

    private HBox rowNode(Row r) {
        HBox row = new HBox(10);
        row.getStyleClass().add("player-row");
        if (r.online()) {
            row.getStyleClass().add("player-row-online");
        }
        if (r.banned()) {
            row.getStyleClass().add("player-row-banned");
        }
        if (r.name().equalsIgnoreCase(selectedName)) {
            row.getStyleClass().add("selected");
        }
        row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        row.setCursor(Cursor.HAND);
        row.setUserData(r.name());
        row.setOnMouseClicked(e -> selectPlayer(r.name()));

        Label dot = new Label("●");
        dot.getStyleClass().setAll("player-status-dot", r.online() ? "online" : "offline");

        Label name = new Label(r.name());
        name.getStyleClass().add("player-row-name");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        row.getChildren().addAll(dot, name, spacer);
        if (r.op()) {
            row.getChildren().add(badge("OP", "badge-op"));
        }
        if (r.banned()) {
            row.getChildren().add(badge("BAN", "badge-banned"));
        }
        return row;
    }

    private void highlightSelectedRow() {
        for (var node : playerList.getChildren()) {
            boolean isSel = selectedName != null && selectedName.equals(node.getUserData());
            node.getStyleClass().remove("selected");
            if (isSel) {
                node.getStyleClass().add("selected");
            }
        }
    }

    // ===================== ficha (derecha) =====================

    private void selectPlayer(String name) {
        if (name == null || name.isBlank()) {
            return;
        }
        selectedName = name;
        highlightSelectedRow();
        resetReason();
        actionStatus.setText("");
        clearActionError();
        hideAddPanel();

        if (!rightPane.isVisible()) {
            rightPane.setVisible(true);
            rightPane.setManaged(true);
        }
        AnimationUtil.slideInRight(detailPanel);
        AsyncExecutor.supply(() -> Services.players().fullDetail(name), this::renderDetail);
    }

    private void renderDetail(PlayerDetail d) {
        selectedDetail = d;
        boolean online = onlineLower.contains(d.name().toLowerCase(Locale.ROOT));

        detailAvatar.setText(d.initial());
        detailName.setText(d.name());
        detailUuid.setText(d.uuid());
        detailSummary.setText(buildSummary(d, online));

        detailBadges.getChildren().clear();
        if (online) {
            detailBadges.getChildren().add(badge("CONECTADO", "badge-online"));
        }
        if (d.op() || d.opLevel() > 0) {
            detailBadges.getChildren().add(badge("OP " + Math.max(d.opLevel(), 1), "badge-op"));
        }
        if (d.whitelisted()) {
            detailBadges.getChildren().add(badge("WHITELIST", "badge-whitelisted"));
        }
        if (d.banned()) {
            detailBadges.getChildren().add(badge("BANEADO", "badge-banned"));
        }

        renderBanSection(d);

        infoBox.getChildren().setAll(
                kvRow("Estado", online ? "Conectado" : "Desconectado"),
                kvRow("Ping", online && d.ping() >= 0 ? d.ping() + " ms" : "—"),
                kvRow("Nivel (XP)", fmtInt(d.level())),
                kvRow("XP total", fmtLong(d.xpTotal())),
                kvRow("Vida", d.health() < 0 ? "—" : String.format(Locale.US, "%.1f / 20", d.health())),
                kvRow("Hambre", d.hunger() < 0 ? "—" : d.hunger() + " / 20"),
                kvRow("Modo de juego", d.gamemode()),
                kvRow("Ubicación", fmtCoords(d)),
                kvRow("IP", d.ip()),
                kvRow("Última conexión", fmtDate(d.lastPlayed())),
                kvRow("Primera conexión", fmtDate(d.firstPlayed())),
                kvRow("Tiempo jugado", fmtPlayTime(d.playTime())));

        statsBox.getChildren().setAll(
                kvRow("K/D", fmtRatio(d.killDeathRatio())),
                kvRow("Muertes", fmtInt(d.deaths())),
                kvRow("Mobs eliminados", fmtInt(d.mobKills())),
                kvRow("Jugadores eliminados", fmtInt(d.playerKills())),
                kvRow("Distancia recorrida", fmtDistance(d.distanceWalked())),
                kvRow("Daño infligido", fmtDouble(d.damageDealt())),
                kvRow("Daño recibido", fmtDouble(d.damageTaken())),
                kvRow("Bloques minados", fmtLong(d.blocksMined())),
                kvRow("Bloques colocados", fmtLong(d.blocksPlaced())),
                kvRow("Saltos", fmtLong(d.jumps())),
                kvRow("Objetos encantados", fmtInt(d.itemsEnchanted())),
                kvRow("Peces pescados", fmtInt(d.fishCaught())),
                kvRow("Comercios", fmtInt(d.trades())),
                kvRow("Logros completados", fmtInt(d.advancements())),
                kvRow("Sin morir (ticks)", fmtInt(d.timesSinceDeath())));

        opBtn.setText(d.op() ? "Quitar OP" : "Dar OP");
        whitelistBtn.setText(d.whitelisted() ? "Quitar whitelist" : "Añadir whitelist");
        kickBtn.setDisable(!online);

        // Acciones que actúan sobre la entidad: solo si está conectado.
        liveBox.setDisable(!online);
        String gm = d.gamemode() == null ? "" : d.gamemode().toLowerCase(Locale.ROOT);
        gamemodeCombo.setValue(gamemodeCombo.getItems().contains(gm) ? gm : null);

        if (d.banned()) {
            banBtn.setText("Perdonar");
            banBtn.getStyleClass().setAll("button", "success");
        } else {
            banBtn.setText("Banear");
            banBtn.getStyleClass().setAll("button", "danger");
        }
    }

    private String buildSummary(PlayerDetail d, boolean online) {
        String mode = d.gamemode() == null || d.gamemode().isBlank() ? "—" : capitalize(d.gamemode());
        String estado = online ? "● Conectado" : "○ Desconectado";
        String nivel = d.level() < 0 ? "" : " · Nivel " + d.level();
        return mode + nivel + " · " + estado;
    }

    /** Sección de sanción: solo visible cuando el jugador está baneado. */
    private void renderBanSection(PlayerDetail d) {
        boolean show = d.banned();
        banSectionTitle.setVisible(show);
        banSectionTitle.setManaged(show);
        banSection.setVisible(show);
        banSection.setManaged(show);
        if (!show) {
            banSection.getChildren().clear();
            return;
        }
        banSection.getChildren().setAll(
                kvRow("Motivo", blankDash(d.banReason())),
                kvRow("Origen", blankDash(d.banSource())),
                kvRow("Sancionado por", blankDash(d.bannedBy())));
    }

    @FXML
    private void onCloseDetail() {
        selectedName = null;
        selectedDetail = null;
        resetReason();
        rightPane.setVisible(false);
        rightPane.setManaged(false);
        highlightSelectedRow();
    }

    // ===================== añadir jugador por nombre =====================

    private void showAddPanel() {
        onCloseDetail(); // cierra la ficha si estaba abierta
        addStatus.setText("");
        clearAddError();
        String prefill = searchField.getText() == null ? "" : searchField.getText().strip();
        addNameField.setText(prefill);
        addPane.setVisible(true);
        addPane.setManaged(true);
        AnimationUtil.slideInRight(addPanel);
        addNameField.requestFocus();
    }

    private void hideAddPanel() {
        addPane.setVisible(false);
        addPane.setManaged(false);
    }

    @FXML private void onCloseAdd() { hideAddPanel(); }

    @FXML private void onAddWhitelistByName() { runAddAction(n -> Services.players().addToWhitelist(n)); }
    @FXML private void onAddOpByName()        { runAddAction(n -> Services.players().op(n)); }
    @FXML private void onAddBanByName()       { runAddAction(n -> Services.players().ban(n, "")); }

    private void runAddAction(java.util.function.Function<String, Answer> action) {
        String name = addNameField.getText() == null ? "" : addNameField.getText().strip();
        if (name.isEmpty()) {
            setAddError("Escribe un nombre.");
            return;
        }
        clearAddError();
        addStatus.setText("Ejecutando…");
        AsyncExecutor.supply(() -> action.apply(name), a -> {
            String msg = a.getMessage();
            addStatus.setText(msg != null ? msg : (a.isSuccess() ? "OK" : "Error"));
            if (a.isSuccess()) {
                clearAddError();
                reload();
            } else {
                markAddError();
            }
        });
    }

    private void setAddError(String msg) {
        addStatus.setText(msg);
        markAddError();
    }

    private void markAddError() {
        if (!addStatus.getStyleClass().contains("action-error")) {
            addStatus.getStyleClass().add("action-error");
        }
    }

    private void clearAddError() {
        addStatus.getStyleClass().remove("action-error");
    }

    // ===================== pestañas y búsqueda =====================

    @FXML private void onTabOnline()    { switchTab(Tab.ONLINE); }
    @FXML private void onTabWhitelist() { switchTab(Tab.WHITELIST); }
    @FXML private void onTabOps()       { switchTab(Tab.OPS); }
    @FXML private void onTabBanned()    { switchTab(Tab.BANNED); }
    @FXML private void onTabHistory()   { switchTab(Tab.HISTORY); }

    private void switchTab(Tab tab) {
        setActiveTab(tab);
        rebuildList(true, true);
    }

    private void setActiveTab(Tab tab) {
        activeTab = tab;
        styleTab(tabOnline, tab == Tab.ONLINE);
        styleTab(tabWhitelist, tab == Tab.WHITELIST);
        styleTab(tabOps, tab == Tab.OPS);
        styleTab(tabBanned, tab == Tab.BANNED);
        styleTab(tabHistory, tab == Tab.HISTORY);
    }

    private void styleTab(Button b, boolean active) {
        b.getStyleClass().remove("active");
        if (active && !b.getStyleClass().contains("active")) {
            b.getStyleClass().add("active");
        }
    }

    // ===================== acciones de moderación =====================

    @FXML
    private void onAddToWhitelist() {
        showAddPanel(); // abre el panel modular "añadir por nombre"
    }

    // ----- Acciones avanzadas (jugador conectado) -----

    @FXML
    private void onApplyGamemode() {
        if (selectedDetail == null) {
            return;
        }
        String mode = gamemodeCombo.getValue();
        if (mode == null) {
            setActionError("Elige un modo de juego.");
            return;
        }
        String name = selectedDetail.name();
        runAction(() -> Services.players().gamemode(name, mode));
    }

    @FXML private void onGrantAdv()   { runSelected(n -> Services.players().grantAdvancements(n)); }
    @FXML private void onRevokeAdv()  { runSelected(n -> Services.players().revokeAdvancements(n)); }
    @FXML private void onGiveXp()     { runSelected(n -> Services.players().giveXp(n, 10)); }
    @FXML private void onHeal()       { runSelected(n -> Services.players().heal(n)); }
    @FXML private void onFeed()       { runSelected(n -> Services.players().feed(n)); }
    @FXML private void onKillPlayer() { runSelected(n -> Services.players().killPlayer(n)); }
    @FXML private void onClearInv()   { runSelected(n -> Services.players().clearInventory(n)); }

    @FXML
    private void onResetStats() {
        if (selectedDetail == null) {
            return;
        }
        String name = selectedDetail.name();
        String uuid = selectedDetail.uuid();
        runAction(() -> Services.players().resetStats(name, uuid));
    }

    private void runSelected(java.util.function.Function<String, Answer> action) {
        if (selectedDetail == null) {
            return;
        }
        String name = selectedDetail.name();
        runAction(() -> action.apply(name));
    }

    @FXML
    private void onToggleOp() {
        if (selectedDetail == null) {
            return;
        }
        String name = selectedDetail.name();
        runAction(selectedDetail.op()
                ? () -> Services.players().deop(name)
                : () -> Services.players().op(name));
    }

    @FXML
    private void onToggleWhitelist() {
        if (selectedDetail == null) {
            return;
        }
        String name = selectedDetail.name();
        runAction(selectedDetail.whitelisted()
                ? () -> Services.players().removeFromWhitelist(name)
                : () -> Services.players().addToWhitelist(name));
    }

    @FXML
    private void onKick() {
        if (selectedDetail == null) {
            return;
        }
        openReason(PendingAction.KICK, "Motivo de expulsión:", "Expulsar");
    }

    @FXML
    private void onBan() {
        if (selectedDetail == null) {
            return;
        }
        if (selectedDetail.banned()) {
            String name = selectedDetail.name();
            runAction(() -> Services.players().pardon(name)); // perdonar: sin motivo
        } else {
            openReason(PendingAction.BAN, "Motivo del baneo:", "Banear");
        }
    }

    private void openReason(PendingAction action, String title, String confirmText) {
        pendingAction = action;
        reasonTitle.setText(title);
        reasonConfirm.setText(confirmText);
        reasonField.clear();
        reasonBox.setVisible(true);
        reasonBox.setManaged(true);
        reasonField.requestFocus();
    }

    @FXML
    private void onConfirmAction() {
        if (selectedDetail == null || pendingAction == PendingAction.NONE) {
            return;
        }
        String name = selectedDetail.name();
        String reason = reasonField.getText();
        PendingAction action = pendingAction;
        resetReason();
        runAction(action == PendingAction.KICK
                ? () -> Services.players().kick(name, reason)
                : () -> Services.players().ban(name, reason));
    }

    @FXML
    private void onCancelAction() {
        resetReason();
    }

    private void resetReason() {
        pendingAction = PendingAction.NONE;
        reasonBox.setVisible(false);
        reasonBox.setManaged(false);
        reasonField.clear();
    }

    /** Ejecuta una acción de servicio fuera del hilo UI y refresca al terminar. */
    private void runAction(Supplier<Answer> action) {
        actionStatus.setText("Ejecutando…");
        clearActionError();
        AsyncExecutor.supply(action, a -> {
            showActionAnswer(a);
            reload();
            if (selectedName != null) {
                AsyncExecutor.supply(() -> Services.players().fullDetail(selectedName), this::renderDetail);
            }
        });
    }

    private void showActionAnswer(Answer a) {
        String msg = a.getMessage();
        actionStatus.setText(msg != null ? msg : (a.isSuccess() ? "OK" : "Error"));
        if (a.isSuccess()) {
            clearActionError();
        } else {
            markActionError();
        }
    }

    private void setActionError(String msg) {
        actionStatus.setText(msg);
        markActionError();
    }

    private void markActionError() {
        if (!actionStatus.getStyleClass().contains("action-error")) {
            actionStatus.getStyleClass().add("action-error");
        }
    }

    private void clearActionError() {
        actionStatus.getStyleClass().remove("action-error");
    }

    // ===================== helpers de UI =====================

    private Label badge(String text, String modifier) {
        Label l = new Label(text);
        l.getStyleClass().addAll("badge", modifier);
        return l;
    }

    private HBox kvRow(String key, String value) {
        Label k = new Label(key);
        k.getStyleClass().add("kv-key");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Label v = new Label(value);
        v.getStyleClass().add("kv-val");
        HBox row = new HBox(8, k, spacer, v);
        row.getStyleClass().add("kv-row");
        row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        return row;
    }

    // ===================== formato =====================

    private static String fmtInt(int v) {
        return v < 0 ? "—" : String.format(Locale.US, "%,d", v);
    }

    private static String fmtLong(long v) {
        return v < 0 ? "—" : String.format(Locale.US, "%,d", v);
    }

    private static String fmtDouble(double v) {
        return v < 0 ? "—" : String.format(Locale.US, "%,.0f", v);
    }

    private static String fmtRatio(double v) {
        return v < 0 ? "—" : String.format(Locale.US, "%.2f", v);
    }

    private static String capitalize(String s) {
        return s == null || s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static String blankDash(String s) {
        return s == null || s.isBlank() ? "—" : s;
    }

    private static String fmtCoords(PlayerDetail d) {
        if (Double.isNaN(d.x()) || Double.isNaN(d.y()) || Double.isNaN(d.z())) {
            return "—";
        }
        return String.format(Locale.US, "%.0f, %.0f, %.0f", d.x(), d.y(), d.z());
    }

    private static String fmtDate(long millis) {
        return millis < 0 ? "—" : DATE.format(Instant.ofEpochMilli(millis));
    }

    private static String fmtPlayTime(long minutes) {
        if (minutes < 0) {
            return "—";
        }
        long h = minutes / 60;
        long m = minutes % 60;
        if (h >= 24) {
            long d = h / 24;
            return d + "d " + (h % 24) + "h";
        }
        return h + "h " + m + "m";
    }

    private static String fmtDistance(long blocks) {
        if (blocks < 0) {
            return "—";
        }
        return blocks >= 1000
                ? String.format(Locale.US, "%.1f km", blocks / 1000.0)
                : blocks + " m";
    }
}
