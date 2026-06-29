package io.github.dinamo541.servermanagermc.controller;

import io.github.dinamo541.corefx.navigation.FlowController;
import io.github.dinamo541.servermanagermc.concurrent.AsyncExecutor;
import io.github.dinamo541.servermanagermc.config.AppProfile;
import io.github.dinamo541.servermanagermc.config.Services;
import io.github.dinamo541.servermanagermc.model.ServerStatus;
import io.github.dinamo541.servermanagermc.util.AnimationUtil;
import io.github.dinamo541.servermanagermc.util.Nav;
import java.util.List;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.util.Duration;

/**
 * Shell de la aplicación: sidebar de navegación (con indicador de sección
 * activa) + header con estado rápido del servidor + región central donde se
 * intercambian las vistas de cada módulo (vía FlowController de CoreFx).
 *
 * @author Dominique
 */
public class HomeController {

    @FXML private BorderPane rootPane;
    @FXML private Label profileLabel;

    @FXML private Label headerDot;
    @FXML private Label headerStateLabel;
    @FXML private Label headerUptimeLabel;
    @FXML private Label headerPlayersLabel;

    @FXML private Button navDashboard;
    @FXML private Button navControl;
    @FXML private Button navPlayers;
    @FXML private Button navProperties;
    @FXML private Button navMods;
    @FXML private Button navBackups;
    @FXML private Button navWorld;

    private final FlowController flow = FlowController.getInstance();
    private List<Button> navButtons;

    /** Acceso al shell ya cargado (FlowController cachea el loader de HomeView). */
    public static HomeController getInstance() {
        return FlowController.getInstance().getController("HomeView");
    }

    /** Navega a Jugadores con un jugador preseleccionado (chips del Dashboard/Consola). */
    public void navigateToPlayers(String playerName) {
        Nav.selectPlayer(playerName);
        setActive(navPlayers);
        show("Players");
    }

    @FXML
    private void initialize() {
        profileLabel.setText(AppProfile.current().badge());
        navButtons = List.of(navDashboard, navControl, navPlayers,
                navProperties, navMods, navBackups, navWorld);

        AnimationUtil.pulse(headerDot);
        startHeaderPolling();

        setActive(navDashboard);
        show("Dashboard");
    }

    private void show(String view) {
        Node node = flow.getLoader(view).getRoot();
        rootPane.setCenter(node);
        AnimationUtil.enter(node);
    }

    private void setActive(Button active) {
        for (Button b : navButtons) {
            b.getStyleClass().remove("active");
        }
        if (!active.getStyleClass().contains("active")) {
            active.getStyleClass().add("active");
        }
    }

    // ===================== estado rápido del header =====================

    private void startHeaderPolling() {
        Timeline poll = new Timeline(
                new KeyFrame(Duration.ZERO, e -> AsyncExecutor.supply(
                        () -> Services.server().status(), this::renderHeader)),
                new KeyFrame(Duration.seconds(5)));
        poll.setCycleCount(Animation.INDEFINITE);
        poll.play();
    }

    private void renderHeader(ServerStatus s) {
        boolean online = s.online();
        headerDot.getStyleClass().setAll("status-dot", online ? "online" : "offline");
        headerStateLabel.setText(online ? "ONLINE" : s.state().name());
        headerUptimeLabel.setText(online ? s.uptime() : "—");
        headerPlayersLabel.setText(s.playersOnline() < 0
                ? "—"
                : s.playersOnline() + (s.playersMax() < 0 ? "" : "/" + s.playersMax()));
    }

    // ===================== navegación =====================

    @FXML private void goDashboard() {
        setActive(navDashboard);  show("Dashboard");
    }

    @FXML private void goControl() {
        setActive(navControl);
        show("Control");
    }

    @FXML private void goPlayers() {
        setActive(navPlayers);
        show("Players");
    }

    @FXML private void goProperties() {
        setActive(navProperties);
        show("Properties");
    }

    @FXML private void goMods() {
        setActive(navMods);
        show("Mods");
    }

    @FXML private void goBackups() {
        setActive(navBackups);
        show("Backups");
    }

    @FXML private void goWorld() {
        setActive(navWorld);
        show("World");
    }

}
