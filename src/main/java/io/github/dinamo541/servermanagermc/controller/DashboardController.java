package io.github.dinamo541.servermanagermc.controller;

import io.github.dinamo541.servermanagermc.concurrent.AsyncExecutor;
import io.github.dinamo541.servermanagermc.config.Services;
import io.github.dinamo541.servermanagermc.model.ServerStatus;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.util.Duration;

/**
 * Vista de un vistazo del estado del servidor, con refresco automático cada 5 s.
 * La consulta de estado se hace fuera del hilo de la UI (AsyncExecutor).
 */
public class DashboardController {

    @FXML
    private Label stateLabel;
    @FXML
    private Label stateDot;
    @FXML
    private Label uptimeLabel;
    @FXML
    private Label ramLabel;
    @FXML
    private Label cpuLabel;
    @FXML
    private Label playersLabel;
    @FXML
    private Label tpsLabel;

    private Timeline refresh;

    @FXML
    private void initialize() {
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
        uptimeLabel.setText(s.uptime());
        ramLabel.setText(s.memoryUsedMb() < 0
                ? "—"
                : s.memoryUsedMb() + " / " + s.memoryTotalMb() + " MB");
        cpuLabel.setText(s.cpuPercent() < 0 ? "—" : String.format("%.0f %%", s.cpuPercent()));
        playersLabel.setText(s.playersOnline() < 0
                ? "—"
                : s.playersOnline() + (s.playersMax() < 0 ? "" : " / " + s.playersMax()));
        tpsLabel.setText(s.tps() < 0 ? "—" : String.format("%.1f", s.tps()));
    }
}
