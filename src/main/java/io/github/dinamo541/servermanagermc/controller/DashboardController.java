package io.github.dinamo541.servermanagermc.controller;

import io.github.dinamo541.corefx.util.Answer;
import io.github.dinamo541.servermanagermc.concurrent.AsyncExecutor;
import io.github.dinamo541.servermanagermc.config.ServerPaths;
import io.github.dinamo541.servermanagermc.config.Services;
import io.github.dinamo541.servermanagermc.model.ServerStatus;
import io.github.dinamo541.servermanagermc.util.AnimationUtil;
import java.util.Locale;
import java.util.function.Supplier;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.FlowPane;
import javafx.util.Duration;

/**
 * Vista de un vistazo del estado del servidor, con refresco automático cada 5 s.
 * La consulta de estado se hace fuera del hilo de la UI (AsyncExecutor). Incluye
 * acciones rápidas (start/stop/restart, save-all, broadcast), barras de nivel y
 * tarjetas de jugadores conectados.
 */
public class DashboardController {

    @FXML
    private Label stateLabel;
    @FXML
    private Label stateDot;
    @FXML
    private Label actionStatus;
    @FXML
    private Button startBtn;
    @FXML
    private Button stopBtn;

    @FXML
    private FlowPane metricGrid;
    @FXML
    private Label ramLabel;
    @FXML
    private ProgressBar ramBar;
    @FXML
    private Label cpuLabel;
    @FXML
    private ProgressBar cpuBar;
    @FXML
    private Label tpsLabel;
    @FXML
    private ProgressBar tpsBar;
    @FXML
    private Label playersLabel;
    @FXML
    private ProgressBar playersBar;
    @FXML
    private Label diskLabel;
    @FXML
    private ProgressBar diskBar;

    @FXML
    private Label uptimeLabel;
    @FXML
    private Label worldLabel;
    @FXML
    private Label modsLabel;
    @FXML
    private Label pluginsLabel;
    @FXML
    private Label chunksLabel;
    @FXML
    private Label entitiesLabel;

    @FXML
    private Label playersTitle;
    @FXML
    private FlowPane playerChips;
    @FXML
    private Label versionLabel;
    @FXML
    private Label backupLabel;
    @FXML
    private Label portLabel;

    private Timeline refresh;

    @FXML
    private void initialize() {
        portLabel.setText(ServerPaths.PORT);
        AnimationUtil.pulse(stateDot);
        AnimationUtil.staggerIn(metricGrid.getChildren());

        refresh = new Timeline(
                new KeyFrame(Duration.ZERO, e -> reload()),
                new KeyFrame(Duration.seconds(5)));
        refresh.setCycleCount(Animation.INDEFINITE);
        refresh.play();
    }

    private void reload() {
        AsyncExecutor.supply(() -> Services.server().status(), this::render);
    }

    private void render(ServerStatus s) {
        boolean online = s.online();
        stateLabel.setText(online ? "ONLINE" : s.state().name());
        stateDot.getStyleClass().setAll("state-dot", online ? "online" : "offline");

        // --- Métricas principales con barra de nivel ---
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

        if (s.diskUsedMb() < 0) {
            diskLabel.setText("—");
            resetBar(diskBar);
        } else {
            diskLabel.setText(s.diskTotalMb() > 0
                    ? fmtMb(s.diskUsedMb()) + " / " + fmtMb(s.diskTotalMb())
                    : fmtMb(s.diskUsedMb()));
            if (s.diskTotalMb() > 0) {
                levelHighIsBad(diskBar, ratio(s.diskUsedMb(), s.diskTotalMb()));
            } else {
                resetBar(diskBar);
            }
        }

        // --- Métricas secundarias ---
        uptimeLabel.setText(s.uptime());
        worldLabel.setText(fmtMb(s.worldSizeMb()));
        modsLabel.setText(s.modCount() < 0 ? "—" : String.valueOf(s.modCount()));
        pluginsLabel.setText(s.pluginCount() < 0 ? "—" : String.valueOf(s.pluginCount()));
        chunksLabel.setText(s.loadedChunks() < 0 ? "—" : String.valueOf(s.loadedChunks()));
        entitiesLabel.setText(s.entityCount() < 0 ? "—" : String.valueOf(s.entityCount()));

        // --- Info + chips de jugadores ---
        versionLabel.setText(s.serverVersion());
        backupLabel.setText(s.lastBackupTime());
        renderPlayerChips(s.onlinePlayerList());

        startBtn.setDisable(online);
        stopBtn.setDisable(!online);
    }

    private void renderPlayerChips(String list) {
        playerChips.getChildren().clear();
        String trimmed = list == null ? "" : list.trim();
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
            playerChips.getChildren().add(chip);
        }
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

    // ===================== acciones rápidas =====================

    @FXML private void onStart() {
        runAction(Services.server()::start);
    }

    @FXML private void onStop() {
        runAction(Services.server()::stop);
    }

    @FXML private void onRestart() {
        runAction(Services.server()::restart);
    }

    @FXML private void onSaveAll() {
        runConsole("save-all", "Guardando mundo…");
    }

    @FXML
    private void onBroadcast() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Mensaje al servidor");
        dialog.setHeaderText(null);
        dialog.setContentText("Mensaje para todos los jugadores:");
        styleDialog(dialog);
        dialog.showAndWait().ifPresent(msg -> {
            if (!msg.isBlank()) {
                runConsole("say " + msg.strip(), "Mensaje enviado.");
            }
        });
    }

    private void runAction(Supplier<Answer> action) {
        actionStatus.setText("Ejecutando…");
        AsyncExecutor.supply(action, a -> {
            showAnswer(a);
            reload();
        });
    }

    private void runConsole(String command, String pending) {
        actionStatus.setText(pending);
        AsyncExecutor.supply(() -> Services.console().sendCommand(command), this::showAnswer);
    }

    private void showAnswer(Answer a) {
        String m = a.getMessage();
        actionStatus.setText(m != null ? m : (a.isSuccess() ? "OK" : "Error"));
    }

    private void styleDialog(Dialog<?> dialog) {
        var css = getClass().getResource("/io/github/dinamo541/servermanagermc/view/DarkThemeStyle.css");
        if (css != null) {
            dialog.getDialogPane().getStylesheets().add(css.toExternalForm());
        }
        dialog.getDialogPane().getStyleClass().add("content");
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
