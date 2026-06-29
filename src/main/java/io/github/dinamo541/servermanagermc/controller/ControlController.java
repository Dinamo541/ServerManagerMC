package io.github.dinamo541.servermanagermc.controller;

import io.github.dinamo541.corefx.navigation.StageManager;
import io.github.dinamo541.corefx.util.Answer;
import io.github.dinamo541.servermanagermc.concurrent.AsyncExecutor;
import io.github.dinamo541.servermanagermc.config.AppProfile;
import io.github.dinamo541.servermanagermc.config.Services;
import io.github.dinamo541.servermanagermc.model.ServerStatus;
import io.github.dinamo541.servermanagermc.util.AnimationUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Side;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

/**
 * Control del servidor: panel start/stop/restart/kill con estado de transición,
 * consola en vivo con líneas coloreadas (tail de latest.log), envío de comandos
 * con historial y autocompletado, y un panel lateral de métricas + jugadores +
 * acciones rápidas que se refresca solo.
 *
 * <p>Toda la E/S (systemctl/tmux/lectura de log) va fuera del hilo de la UI por
 * {@link AsyncExecutor}; el tail entrega líneas en un hilo de fondo que aquí se
 * vuelve a marshalar al hilo de JavaFX.
 */
public class ControlController {

    /** Estado de una acción de ciclo de vida aún sin confirmar por el sondeo. */
    private enum Pending { NONE, STARTING, STOPPING }

    /** Tope de líneas retenidas en la consola para no crecer sin límite. */
    private static final int MAX_LINES = 1000;
    /** Tope de comandos recordados durante la sesión (navegables con ↑/↓). */
    private static final int MAX_HISTORY = 50;
    /** Cuántos sondeos rápidos esperar antes de rendirse en una transición. */
    private static final int MAX_FAST_TICKS = 20;

    /** Plantillas para el autocompletado del campo de comandos. */
    private static final List<String> COMMANDS = List.of(
            "list", "say ", "save-all", "stop", "restart", "tps",
            "op ", "deop ", "kick ", "ban ", "pardon ", "gamemode ",
            "weather clear", "weather rain", "weather thunder",
            "time set day", "time set night",
            "difficulty peaceful", "difficulty easy", "difficulty normal", "difficulty hard");

    @FXML private VBox rootBox;

    // ----- Hero de estado -----
    @FXML private Label heroDot;
    @FXML private Label heroState;
    @FXML private Label heroSub;
    @FXML private Label profileBadge;

    // ----- Panel de control -----
    @FXML private Button startBtn;
    @FXML private Button stopBtn;
    @FXML private Button restartBtn;
    @FXML private Button killBtn;
    @FXML private ProgressIndicator actionSpinner;
    @FXML private Label statusLabel;

    // ----- Consola -----
    @FXML private Label liveDot;
    @FXML private Label lineCountLabel;
    @FXML private CheckBox autoScrollCheck;
    @FXML private ListView<String> console;
    @FXML private TextField commandField;

    // ----- Métricas en vivo -----
    @FXML private Label ramLabel;
    @FXML private ProgressBar ramBar;
    @FXML private Label cpuLabel;
    @FXML private ProgressBar cpuBar;
    @FXML private Label tpsLabel;
    @FXML private ProgressBar tpsBar;
    @FXML private Label playersLabel;
    @FXML private ProgressBar playersBar;

    // ----- Jugadores + acciones rápidas -----
    @FXML private Label playersTitle;
    @FXML private FlowPane playerChips;
    @FXML private ComboBox<String> difficultyCombo;

    private final ContextMenu suggestions = new ContextMenu();
    private final List<String> history = new ArrayList<>();
    private int historyIndex = -1;

    private Pending pending = Pending.NONE;
    private Timeline metrics;
    private Timeline fastPoll;
    private int fastTicks;
    private boolean tailStarted;

    // ===================== ciclo de vida de la vista =====================

    @FXML
    private void initialize() {
        profileBadge.setText(AppProfile.current().badge());
        difficultyCombo.getItems().setAll("peaceful", "easy", "normal", "hard");

        setupConsole();
        setupCommandInput();

        liveDot.getStyleClass().setAll("state-dot", "online");
        AnimationUtil.pulse(liveDot);
        AnimationUtil.pulse(heroDot);
        AnimationUtil.staggerIn(rootBox.getChildren());

        startTail();
        startMetricsLoop();
    }

    private void setupConsole() {
        console.setFocusTraversable(false);
        Label placeholder = new Label("Sin actividad todavía…");
        placeholder.getStyleClass().add("muted");
        console.setPlaceholder(placeholder);
        console.setCellFactory(lv -> new ConsoleCell());
        updateLineCount();
    }

    private void setupCommandInput() {
        suggestions.getStyleClass().add("suggest-menu");
        suggestions.setAutoHide(true);
        commandField.textProperty().addListener((obs, old, val) -> updateSuggestions(val));
        commandField.setOnKeyPressed(this::onCommandKey);
    }

    private void startTail() {
        if (tailStarted) {
            return;
        }
        tailStarted = true;
        Services.console().startTail(line ->
                StageManager.getInstance().runOnFxThread(() -> appendLine(line)));
    }

    private void startMetricsLoop() {
        metrics = new Timeline(
                new KeyFrame(Duration.ZERO, e -> reload()),
                new KeyFrame(Duration.seconds(3)));
        metrics.setCycleCount(Animation.INDEFINITE);
        metrics.play();
    }

    private void reload() {
        AsyncExecutor.supply(() -> Services.server().status(), this::renderStatus);
    }

    // ===================== render de estado =====================

    private void renderStatus(ServerStatus s) {
        boolean online = s.online();
        if (pending == Pending.STARTING && online) {
            pending = Pending.NONE;
        } else if (pending == Pending.STOPPING && !online) {
            pending = Pending.NONE;
        }
        boolean busy = pending != Pending.NONE;

        if (busy) {
            heroState.setText(pending == Pending.STARTING ? "INICIANDO…" : "DETENIENDO…");
            heroDot.getStyleClass().setAll("state-dot", "pending");
        } else {
            heroState.setText(online ? "ONLINE" : "OFFLINE");
            heroDot.getStyleClass().setAll("state-dot", online ? "online" : "offline");
        }
        heroSub.setText(buildSub(s, online, busy));

        actionSpinner.setVisible(busy);
        actionSpinner.setManaged(busy);

        startBtn.setDisable(busy || online);
        stopBtn.setDisable(busy || !online);
        restartBtn.setDisable(busy || !online);
        killBtn.setDisable(busy || !online);
        killBtn.setVisible(online);
        killBtn.setManaged(online);

        renderMetrics(s);
        renderPlayers(s);
    }

    private String buildSub(ServerStatus s, boolean online, boolean busy) {
        if (busy) {
            return "Aplicando cambios…";
        }
        return online
                ? "Activo · " + s.uptime() + "   ·   " + s.serverVersion()
                : "Detenido   ·   " + s.serverVersion();
    }

    private void renderMetrics(ServerStatus s) {
        if (s.memoryUsedMb() < 0) {
            ramLabel.setText("—");
            resetBar(ramBar);
        } else {
            ramLabel.setText(fmtMb(s.memoryUsedMb()) + " / " + fmtMb(s.memoryTotalMb()));
            levelHighIsBad(ramBar, ratio(s.memoryUsedMb(), s.memoryTotalMb()));
        }

        if (s.cpuPercent() < 0) {
            cpuLabel.setText("—");
            resetBar(cpuBar);
        } else {
            cpuLabel.setText(String.format(Locale.US, "%.0f %%", s.cpuPercent()));
            levelHighIsBad(cpuBar, s.cpuPercent() / 100.0);
        }

        if (s.tps() < 0) {
            tpsLabel.setText("—");
            resetBar(tpsBar);
        } else {
            tpsLabel.setText(String.format(Locale.US, "%.1f / 20", s.tps()));
            levelHighIsGood(tpsBar, s.tps() / 20.0);
        }

        if (s.playersOnline() < 0) {
            playersLabel.setText("—");
            resetBar(playersBar);
        } else {
            playersLabel.setText(s.playersOnline() + (s.playersMax() < 0 ? "" : " / " + s.playersMax()));
            barNeutral(playersBar, s.playersMax() > 0 ? ratio(s.playersOnline(), s.playersMax()) : 0);
        }
    }

    private void renderPlayers(ServerStatus s) {
        playerChips.getChildren().clear();
        String trimmed = s.onlinePlayerList() == null ? "" : s.onlinePlayerList().trim();
        if (trimmed.isEmpty()) {
            playersTitle.setText("Jugadores conectados");
            Label empty = new Label("Nadie conectado");
            empty.getStyleClass().add("muted");
            playerChips.getChildren().add(empty);
            return;
        }
        String[] names = trimmed.split("\\s*,\\s*");
        playersTitle.setText("Jugadores conectados (" + names.length + ")");
        for (String name : names) {
            Label chip = new Label(name);
            chip.getStyleClass().add("player-chip");
            chip.setCursor(Cursor.HAND);
            chip.setOnMouseClicked(e -> navigateToPlayer(name));
            playerChips.getChildren().add(chip);
        }
    }

    /** Abre la vista Jugadores con este jugador ya seleccionado. */
    private void navigateToPlayer(String name) {
        HomeController home = HomeController.getInstance();
        if (home != null) {
            home.navigateToPlayers(name);
        }
    }

    // ===================== acciones de ciclo de vida =====================

    @FXML private void onStart() {
        pending = Pending.STARTING;
        applyPendingUi();
        runServer(Services.server()::start);
    }

    @FXML private void onStop() {
        pending = Pending.STOPPING;
        applyPendingUi();
        runServer(Services.server()::stop);
    }

    @FXML private void onRestart() {
        pending = Pending.STARTING;
        applyPendingUi();
        runServer(Services.server()::restart);
    }

    @FXML private void onKill() {
        boolean ok = confirm("Forzar detención",
                "¿Forzar la detención del servidor? Se envía una señal de cierre "
                + "inmediato (SIGKILL) y se pueden perder datos no guardados.");
        if (!ok) {
            return;
        }
        pending = Pending.STOPPING;
        applyPendingUi();
        runServer(Services.server()::kill);
    }

    /** Respuesta inmediata de la UI antes de que confirme el sondeo. */
    private void applyPendingUi() {
        heroState.setText(pending == Pending.STARTING ? "INICIANDO…" : "DETENIENDO…");
        heroDot.getStyleClass().setAll("state-dot", "pending");
        heroSub.setText("Aplicando cambios…");
        startBtn.setDisable(true);
        stopBtn.setDisable(true);
        restartBtn.setDisable(true);
        killBtn.setDisable(true);
        actionSpinner.setVisible(true);
        actionSpinner.setManaged(true);
        clearError();
        statusLabel.setText("Ejecutando…");
    }

    private void runServer(Supplier<Answer> action) {
        AsyncExecutor.supply(action, a -> {
            showAnswer(a);
            if (!a.isSuccess()) {
                pending = Pending.NONE;   // la transición no llegó a empezar
            }
            reload();
            startFastPoll();
        });
    }

    /** Sondeo rápido durante una transición hasta confirmar el nuevo estado. */
    private void startFastPoll() {
        if (pending == Pending.NONE) {
            return;
        }
        fastTicks = 0;
        if (fastPoll != null) {
            fastPoll.stop();
        }
        fastPoll = new Timeline(new KeyFrame(Duration.millis(900), e -> {
            if (pending == Pending.NONE || ++fastTicks > MAX_FAST_TICKS) {
                if (pending != Pending.NONE) {
                    pending = Pending.NONE; // se rinde: deja que el sondeo normal mande
                }
                fastPoll.stop();
                reload();
                return;
            }
            reload();
        }));
        fastPoll.setCycleCount(Animation.INDEFINITE);
        fastPoll.play();
    }

    // ===================== consola =====================

    private void appendLine(String line) {
        var items = console.getItems();
        items.add(line);
        if (items.size() > MAX_LINES) {
            items.remove(0, items.size() - MAX_LINES);
        }
        updateLineCount();
        if (autoScrollCheck.isSelected() && !items.isEmpty()) {
            console.scrollTo(items.size() - 1);
        }
    }

    private void updateLineCount() {
        lineCountLabel.setText(console.getItems().size() + " líneas");
    }

    @FXML private void onClear() {
        console.getItems().clear();
        updateLineCount();
    }

    @FXML private void onCopy() {
        ClipboardContent content = new ClipboardContent();
        content.putString(String.join("\n", console.getItems()));
        Clipboard.getSystemClipboard().setContent(content);
        clearError();
        statusLabel.setText("Consola copiada (" + console.getItems().size() + " líneas).");
    }

    /** Celda de consola coloreada según el nivel detectado en la línea. */
    private static final class ConsoleCell extends ListCell<String> {
        @Override
        protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);
            getStyleClass().removeAll("console-line", "line-info", "line-warn", "line-error", "line-debug");
            if (empty || item == null) {
                setText(null);
            } else {
                setText(item);
                getStyleClass().addAll("console-line", levelClass(item));
            }
        }

        private static String levelClass(String line) {
            String u = line.toUpperCase(Locale.ROOT);
            if (u.contains("ERROR") || u.contains("SEVERE") || u.contains("FATAL")) {
                return "line-error";
            }
            if (u.contains("WARN")) {
                return "line-warn";
            }
            if (u.contains("DEBUG") || u.contains("TRACE")) {
                return "line-debug";
            }
            return "line-info";
        }
    }

    // ===================== envío de comandos =====================

    @FXML private void onSend() {
        String cmd = commandField.getText();
        if (cmd == null || cmd.isBlank()) {
            return; // comando vacío: ignorar en silencio
        }
        cmd = cmd.strip();
        if (hasForbidden(cmd)) {
            setError("Comando no permitido (no se admiten \" ` $).");
            return;
        }
        suggestions.hide();
        pushHistory(cmd);
        final String toSend = cmd;
        clearError();
        statusLabel.setText("Enviando…");
        AsyncExecutor.supply(() -> Services.console().sendCommand(toSend), a -> {
            showAnswer(a);
            if (a.isSuccess()) {
                commandField.clear();
            }
        });
    }

    @FXML private void onQuickCommand(ActionEvent e) {
        Object data = ((Node) e.getSource()).getUserData();
        if (data != null) {
            sendRaw(data.toString());
        }
    }

    @FXML private void onSaveAll() {
        sendRaw("save-all");
    }

    @FXML private void onList() {
        sendRaw("list");
    }

    @FXML private void onBroadcast() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Anuncio al servidor");
        dialog.setHeaderText(null);
        dialog.setContentText("Mensaje para todos los jugadores:");
        styleDialog(dialog);
        dialog.showAndWait().ifPresent(msg -> {
            if (!msg.isBlank()) {
                sendRaw("say " + msg.strip());
            }
        });
    }

    @FXML private void onDifficulty() {
        String value = difficultyCombo.getValue();
        if (value != null && !value.isBlank()) {
            sendRaw("difficulty " + value);
        }
    }

    /** Envía un comando fijo (chips/acciones rápidas) sin tocar el campo. */
    private void sendRaw(String cmd) {
        if (cmd == null || cmd.isBlank()) {
            return;
        }
        if (hasForbidden(cmd)) {
            setError("Comando no permitido (no se admiten \" ` $).");
            return;
        }
        clearError();
        statusLabel.setText("Enviando…");
        AsyncExecutor.supply(() -> Services.console().sendCommand(cmd), this::showAnswer);
    }

    private static boolean hasForbidden(String cmd) {
        return cmd.contains("\"") || cmd.contains("`") || cmd.contains("$");
    }

    // ===================== historial + autocompletado =====================

    private void onCommandKey(KeyEvent e) {
        if (suggestions.isShowing()) {
            return; // mientras el popup navega con ↑/↓, no tocar el historial
        }
        if (e.getCode() == KeyCode.UP) {
            historyPrev();
            e.consume();
        } else if (e.getCode() == KeyCode.DOWN) {
            historyNext();
            e.consume();
        }
    }

    private void historyPrev() {
        if (history.isEmpty()) {
            return;
        }
        historyIndex = historyIndex < 0 ? history.size() - 1 : Math.max(0, historyIndex - 1);
        applyHistory();
    }

    private void historyNext() {
        if (historyIndex < 0) {
            return;
        }
        historyIndex++;
        if (historyIndex >= history.size()) {
            historyIndex = -1;
            commandField.clear();
        } else {
            applyHistory();
        }
    }

    private void applyHistory() {
        String value = history.get(historyIndex);
        commandField.setText(value);
        commandField.positionCaret(value.length());
    }

    private void pushHistory(String cmd) {
        if (history.isEmpty() || !history.get(history.size() - 1).equals(cmd)) {
            history.add(cmd);
            if (history.size() > MAX_HISTORY) {
                history.remove(0);
            }
        }
        historyIndex = -1;
    }

    private void updateSuggestions(String value) {
        if (value == null) {
            suggestions.hide();
            return;
        }
        String token = value.strip();
        if (token.isEmpty() || token.contains(" ")) {
            suggestions.hide();
            return; // sólo se sugiere el nombre del comando, no sus argumentos
        }
        String lower = token.toLowerCase(Locale.ROOT);
        List<MenuItem> items = new ArrayList<>();
        for (String cmd : COMMANDS) {
            if (cmd.toLowerCase(Locale.ROOT).startsWith(lower) && !cmd.equalsIgnoreCase(token)) {
                MenuItem item = new MenuItem(cmd);
                item.setOnAction(e -> {
                    commandField.setText(cmd);
                    commandField.positionCaret(cmd.length());
                    suggestions.hide();
                    commandField.requestFocus();
                });
                items.add(item);
                if (items.size() >= 6) {
                    break;
                }
            }
        }
        if (items.isEmpty()) {
            suggestions.hide();
            return;
        }
        suggestions.getItems().setAll(items);
        if (!suggestions.isShowing() && commandField.getScene() != null) {
            suggestions.show(commandField, Side.BOTTOM, 0, 0);
        }
    }

    // ===================== feedback =====================

    private void showAnswer(Answer a) {
        clearError();
        String msg = a.getMessage();
        statusLabel.setText(msg != null ? msg : (a.isSuccess() ? "OK" : "Error"));
        if (!a.isSuccess()) {
            statusLabel.getStyleClass().add("action-error");
        }
    }

    private void setError(String msg) {
        statusLabel.setText(msg);
        if (!statusLabel.getStyleClass().contains("action-error")) {
            statusLabel.getStyleClass().add("action-error");
        }
    }

    private void clearError() {
        statusLabel.getStyleClass().remove("action-error");
    }

    // ===================== diálogos =====================

    private boolean confirm(String title, String message) {
        Alert alert = new Alert(AlertType.CONFIRMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        styleDialog(alert);
        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == ButtonType.OK;
    }

    private void styleDialog(Dialog<?> dialog) {
        var css = getClass().getResource("/io/github/dinamo541/servermanagermc/view/DarkThemeStyle.css");
        if (css != null) {
            dialog.getDialogPane().getStylesheets().add(css.toExternalForm());
        }
        dialog.getDialogPane().getStyleClass().add("content");
    }

    // ===================== barras de nivel =====================

    /** Verde < 60 %, naranja 60–80 %, rojo > 80 % (más alto = peor). */
    private void levelHighIsBad(ProgressBar bar, double r) {
        r = clamp01(r);
        bar.setProgress(r);
        setBarClass(bar, r >= 0.8 ? "bar-crit" : r >= 0.6 ? "bar-warn" : "bar-ok");
    }

    /** Verde cerca del máximo, rojo cuando cae (más alto = mejor; p. ej. TPS). */
    private void levelHighIsGood(ProgressBar bar, double r) {
        r = clamp01(r);
        bar.setProgress(r);
        setBarClass(bar, r >= 0.9 ? "bar-ok" : r >= 0.75 ? "bar-warn" : "bar-crit");
    }

    private void barNeutral(ProgressBar bar, double r) {
        bar.setProgress(clamp01(r));
        setBarClass(bar, "bar-accent");
    }

    private void resetBar(ProgressBar bar) {
        bar.setProgress(0);
        setBarClass(bar, "bar-accent");
    }

    private void setBarClass(ProgressBar bar, String cls) {
        bar.getStyleClass().removeAll("bar-ok", "bar-warn", "bar-crit", "bar-accent");
        bar.getStyleClass().add(cls);
    }

    // ===================== helpers de formato =====================

    private static double ratio(long part, long total) {
        return total <= 0 ? 0 : (double) part / total;
    }

    private static double clamp01(double v) {
        return v < 0 ? 0 : Math.min(v, 1);
    }

    private static String fmtMb(long mb) {
        if (mb < 0) {
            return "—";
        }
        if (mb >= 1024) {
            return String.format(Locale.US, "%.1f GB", mb / 1024.0);
        }
        return mb + " MB";
    }
}
