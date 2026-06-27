package io.github.dinamo541.servermanagermc.controller;

import io.github.dinamo541.corefx.navigation.FlowController;
import io.github.dinamo541.servermanagermc.config.AppProfile;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;

/**
 * Shell de la aplicación: sidebar de navegación + región central donde se
 * intercambian las vistas de cada módulo (vía FlowController de CoreFx).
 */
public class HomeController {

    @FXML
    private BorderPane rootPane;
    @FXML
    private Label profileLabel;

    private final FlowController flow = FlowController.getInstance();

    @FXML
    private void initialize() {
        profileLabel.setText(AppProfile.current().badge());
        show("Dashboard");
    }

    private void show(String view) {
        // Fijamos el centro con el nodo (cacheado) de la vista. No usamos
        // changeViewInBorderPane porque, en reclics, su lógica intentaría anidar
        // la vista dentro de sí misma (el loader está cacheado) → "cycle detected".
        rootPane.setCenter(flow.getLoader(view).getRoot());
    }

    @FXML
    private void goDashboard() {
        show("Dashboard");
    }
    @FXML
    private void goControl() {
        show("Control");
    }
    @FXML
    private void goPlayers() {
        show("Players");
    }
    @FXML
    private void goProperties() {
        show("Properties");
    }
    @FXML
    private void goMods() {
        show("Mods");
    }
    @FXML
    private void goBackups() {
        show("Backups");
    }
    @FXML
    private void goWorld() {
        show("World");
    }
}
