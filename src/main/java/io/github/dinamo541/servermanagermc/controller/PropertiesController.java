package io.github.dinamo541.servermanagermc.controller;

import io.github.dinamo541.corefx.util.Answer;
import io.github.dinamo541.servermanagermc.App;
import io.github.dinamo541.servermanagermc.concurrent.AsyncExecutor;
import io.github.dinamo541.servermanagermc.config.ServerPaths;
import io.github.dinamo541.servermanagermc.config.Services;
import io.github.dinamo541.servermanagermc.model.ServerPropertiesModel;
import io.github.dinamo541.servermanagermc.util.AnimationUtil;
import io.github.dinamo541.servermanagermc.util.PropertyDocs;
import java.util.Locale;
import java.util.Optional;
import java.util.Properties;
import javafx.application.HostServices;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.StringProperty;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.converter.NumberStringConverter;

/**
 * Editor visual de {@code server.properties}: interruptores (booleanos),
 * deslizadores (numéricos) y campos/listas (texto) enlazados de forma
 * bidireccional a un {@link ServerPropertiesModel}. El botón Guardar solo se
 * habilita cuando hay cambios respecto al estado cargado, y al guardar se avisa
 * de que el servidor debe reiniciarse para aplicarlos.
 *
 * <p>A diferencia de las demás vistas, esta <b>no</b> auto-refresca con un
 * {@code Timeline}: recargar mientras se edita pisaría lo que el usuario está
 * escribiendo. Los datos se cargan una vez y se recargan solo a petición.
 */
public class PropertiesController {

    @FXML private VBox rootBox;
    @FXML private Label pathLabel;
    @FXML private Button reloadBtn;
    @FXML private Button saveBtn;
    @FXML private Label actionStatus;

    // ----- General -----
    @FXML private CheckBox onlineModeToggle;
    @FXML private TextField motdField;
    @FXML private TextField levelNameField;
    @FXML private TextField levelSeedField;
    @FXML private ComboBox<String> difficultyCombo;
    @FXML private ComboBox<String> gamemodeCombo;
    @FXML private Slider serverPortSlider;
    @FXML private Label serverPortValue;
    @FXML private Slider maxPlayersSlider;
    @FXML private Label maxPlayersValue;
    @FXML private CheckBox hardcoreToggle;
    @FXML private CheckBox enableStatusToggle;

    // ----- Jugabilidad -----
    @FXML private CheckBox pvpToggle;
    @FXML private CheckBox allowFlightToggle;
    @FXML private CheckBox spawnAnimalsToggle;
    @FXML private CheckBox spawnMonstersToggle;
    @FXML private CheckBox spawnNpcsToggle;
    @FXML private CheckBox allowNetherToggle;
    @FXML private CheckBox generateStructuresToggle;
    @FXML private Slider playerIdleTimeoutSlider;
    @FXML private Label playerIdleTimeoutValue;
    @FXML private CheckBox hideOnlinePlayersToggle;

    // ----- Mundo -----
    @FXML private Slider viewDistanceSlider;
    @FXML private Label viewDistanceValue;
    @FXML private Slider simulationDistanceSlider;
    @FXML private Label simulationDistanceValue;
    @FXML private Slider maxWorldSizeSlider;
    @FXML private TextField maxWorldSizeField;
    @FXML private ComboBox<String> levelTypeCombo;

    // ----- Seguridad -----
    @FXML private CheckBox enforceWhitelistToggle;
    @FXML private CheckBox whiteListToggle;
    @FXML private CheckBox enforceSecureProfileToggle;
    @FXML private CheckBox preventProxyConnectionsToggle;
    @FXML private Slider opPermissionLevelSlider;
    @FXML private Label opPermissionLevelValue;
    @FXML private Slider functionPermissionLevelSlider;
    @FXML private Label functionPermissionLevelValue;
    @FXML private CheckBox enableCommandBlockToggle;
    @FXML private CheckBox broadcastConsoleToOpsToggle;
    @FXML private TextField textFilteringConfigField;

    // ----- Red -----
    @FXML private TextField serverIpField;
    @FXML private Slider rateLimitSlider;
    @FXML private Label rateLimitValue;
    @FXML private Slider networkCompressionThresholdSlider;
    @FXML private Label networkCompressionThresholdValue;
    @FXML private CheckBox useNativeTransportToggle;
    @FXML private CheckBox enableRconToggle;
    @FXML private PasswordField rconPasswordField;
    @FXML private TextField rconPortField;
    @FXML private CheckBox enableQueryToggle;
    @FXML private TextField queryPortField;
    @FXML private CheckBox broadcastRconToOpsToggle;

    // ----- Rendimiento -----
    @FXML private Slider maxTickTimeSlider;
    @FXML private Label maxTickTimeValue;
    @FXML private Slider entityBroadcastRangeSlider;
    @FXML private Label entityBroadcastRangeValue;
    @FXML private Slider maxChainedNeighborUpdatesSlider;
    @FXML private Label maxChainedNeighborUpdatesValue;
    @FXML private CheckBox syncChunkWritesToggle;
    @FXML private Slider regionFileCompressionSlider;
    @FXML private Label regionFileCompressionValue;
    @FXML private CheckBox enableJmxMonitoringToggle;

    // ----- Recursos -----
    @FXML private TextField resourcePackField;
    @FXML private Button testPackBtn;
    @FXML private TextField resourcePackSha1Field;
    @FXML private TextField resourcePackPromptField;
    @FXML private CheckBox requireResourcePackToggle;
    @FXML private TextField initialEnabledPacksField;
    @FXML private TextField initialDisabledPacksField;
    @FXML private TextField bugReportUrlField;

    private final ServerPropertiesModel model = new ServerPropertiesModel();
    private final Properties defaults = ServerPropertiesModel.defaults().toProperties();
    private ServerPropertiesModel original;

    // ===================== ciclo de vida =====================

    @FXML
    private void initialize() {
        pathLabel.setText(ServerPaths.PROPERTIES);
        setupCombos();
        bindControls();
        enhanceLabels(rootBox);
        model.addListener(obs -> onModelChanged());
        original = model.copy();
        saveBtn.setDisable(true);
        AnimationUtil.staggerIn(rootBox.getChildren());
        load();
    }

    private void setupCombos() {
        difficultyCombo.getItems().setAll("peaceful", "easy", "normal", "hard");
        gamemodeCombo.getItems().setAll("survival", "creative", "adventure", "spectator");
        levelTypeCombo.getItems().setAll("default", "flat", "largebiomes", "amplified", "buffet", "custom");
    }

    private void bindControls() {
        // General
        bindToggle(onlineModeToggle, model.onlineMode);
        bindField(motdField, model.motd);
        bindField(levelNameField, model.levelName);
        bindField(levelSeedField, model.levelSeed);
        bindCombo(difficultyCombo, model.difficulty);
        bindCombo(gamemodeCombo, model.gamemode);
        bindSlider(serverPortSlider, serverPortValue, model.serverPort);
        bindSlider(maxPlayersSlider, maxPlayersValue, model.maxPlayers);
        bindToggle(hardcoreToggle, model.hardcore);
        bindToggle(enableStatusToggle, model.enableStatus);

        // Jugabilidad
        bindToggle(pvpToggle, model.pvp);
        bindToggle(allowFlightToggle, model.allowFlight);
        bindToggle(spawnAnimalsToggle, model.spawnAnimals);
        bindToggle(spawnMonstersToggle, model.spawnMonsters);
        bindToggle(spawnNpcsToggle, model.spawnNpcs);
        bindToggle(allowNetherToggle, model.allowNether);
        bindToggle(generateStructuresToggle, model.generateStructures);
        bindSlider(playerIdleTimeoutSlider, playerIdleTimeoutValue, model.playerIdleTimeout);
        bindToggle(hideOnlinePlayersToggle, model.hideOnlinePlayers);

        // Mundo
        bindSlider(viewDistanceSlider, viewDistanceValue, model.viewDistance);
        bindSlider(simulationDistanceSlider, simulationDistanceValue, model.simulationDistance);
        maxWorldSizeSlider.valueProperty().bindBidirectional(model.maxWorldSize);
        Bindings.bindBidirectional(maxWorldSizeField.textProperty(), model.maxWorldSize,
                new NumberStringConverter(Locale.US, "0"));
        bindCombo(levelTypeCombo, model.levelType);

        // Seguridad
        bindToggle(enforceWhitelistToggle, model.enforceWhitelist);
        bindToggle(whiteListToggle, model.whiteList);
        bindToggle(enforceSecureProfileToggle, model.enforceSecureProfile);
        bindToggle(preventProxyConnectionsToggle, model.preventProxyConnections);
        bindSlider(opPermissionLevelSlider, opPermissionLevelValue, model.opPermissionLevel);
        bindSlider(functionPermissionLevelSlider, functionPermissionLevelValue, model.functionPermissionLevel);
        bindToggle(enableCommandBlockToggle, model.enableCommandBlock);
        bindToggle(broadcastConsoleToOpsToggle, model.broadcastConsoleToOps);
        bindField(textFilteringConfigField, model.textFilteringConfig);

        // Red
        bindField(serverIpField, model.serverIp);
        bindSlider(rateLimitSlider, rateLimitValue, model.rateLimit);
        bindSlider(networkCompressionThresholdSlider, networkCompressionThresholdValue,
                model.networkCompressionThreshold);
        bindToggle(useNativeTransportToggle, model.useNativeTransport);
        bindToggle(enableRconToggle, model.enableRcon);
        rconPasswordField.textProperty().bindBidirectional(model.rconPassword);
        bindField(rconPortField, model.rconPort);
        bindToggle(enableQueryToggle, model.enableQuery);
        bindField(queryPortField, model.queryPort);
        bindToggle(broadcastRconToOpsToggle, model.broadcastRconToOps);
        // Los campos de RCON/Query solo tienen sentido si su servicio está activo.
        rconPasswordField.disableProperty().bind(model.enableRcon.not());
        rconPortField.disableProperty().bind(model.enableRcon.not());
        queryPortField.disableProperty().bind(model.enableQuery.not());

        // Rendimiento
        bindSlider(maxTickTimeSlider, maxTickTimeValue, model.maxTickTime);
        bindSlider(entityBroadcastRangeSlider, entityBroadcastRangeValue, model.entityBroadcastRangePercentage);
        bindSlider(maxChainedNeighborUpdatesSlider, maxChainedNeighborUpdatesValue,
                model.maxChainedNeighborUpdates);
        bindToggle(syncChunkWritesToggle, model.syncChunkWrites);
        bindSlider(regionFileCompressionSlider, regionFileCompressionValue, model.regionFileCompression);
        bindToggle(enableJmxMonitoringToggle, model.enableJmxMonitoring);

        // Recursos
        bindField(resourcePackField, model.resourcePack);
        bindField(resourcePackSha1Field, model.resourcePackSha1);
        bindField(resourcePackPromptField, model.resourcePackPrompt);
        bindToggle(requireResourcePackToggle, model.requireResourcePack);
        bindField(initialEnabledPacksField, model.initialEnabledPacks);
        bindField(initialDisabledPacksField, model.initialDisabledPacks);
        bindField(bugReportUrlField, model.bugReportUrl);
    }

    private static void bindToggle(CheckBox box, BooleanProperty prop) {
        box.selectedProperty().bindBidirectional(prop);
    }

    private static void bindField(TextField field, StringProperty prop) {
        field.textProperty().bindBidirectional(prop);
    }

    private static void bindCombo(ComboBox<String> combo, StringProperty prop) {
        combo.valueProperty().bindBidirectional(prop);
    }

    private static void bindSlider(Slider slider, Label value, IntegerProperty prop) {
        slider.valueProperty().bindBidirectional(prop);
        value.textProperty().bind(prop.asString());
    }

    // ===================== carga / guardado =====================

    private void load() {
        actionStatus.setText("Cargando…");
        clearActionError();
        AsyncExecutor.supply(Services.properties()::load, this::onLoaded);
    }

    private void onLoaded(Properties props) {
        model.setFrom(props);
        original = model.copy();
        onModelChanged();
        actionStatus.setText("Propiedades cargadas.");
    }

    private void onModelChanged() {
        saveBtn.setDisable(!model.isChanged(original));
    }

    @FXML
    private void onReload() {
        if (model.isChanged(original) && !confirmDiscard()) {
            return;
        }
        load();
    }

    @FXML
    private void onSave() {
        if (!model.isChanged(original)) {
            actionStatus.setText("Sin cambios que guardar.");
            return;
        }
        Properties props = model.toProperties();
        actionStatus.setText("Guardando…");
        clearActionError();
        saveBtn.setDisable(true);
        AsyncExecutor.supply(() -> Services.properties().save(props), this::onSaved);
    }

    private void onSaved(Answer a) {
        String msg = a.getMessage();
        actionStatus.setText(msg != null ? msg : (a.isSuccess() ? "Guardado." : "Error al guardar."));
        if (a.isSuccess()) {
            clearActionError();
            original = model.copy();
            onModelChanged();
            showRestartAlert();
        } else {
            markActionError();
            onModelChanged();
        }
    }

    @FXML
    private void onTestResourcePack() {
        String url = model.resourcePack.get();
        if (url == null || url.isBlank()) {
            setActionError("No hay URL de resource pack que probar.");
            return;
        }
        HostServices host = App.hostServices();
        if (host == null) {
            setActionError("No se pudo abrir el navegador.");
            return;
        }
        host.showDocument(url.trim());
        actionStatus.setText("Abriendo el resource pack en el navegador…");
        clearActionError();
    }

    // ===================== diálogos =====================

    private void showRestartAlert() {
        Alert alert = new Alert(AlertType.INFORMATION);
        alert.setTitle("Propiedades guardadas");
        alert.setHeaderText("Los cambios se guardaron correctamente");
        alert.setContentText("Es necesario reiniciar el servidor para que los cambios surtan efecto.\n"
                + "Usa la sección Control > Reiniciar para aplicar los cambios.");
        alert.getButtonTypes().setAll(new ButtonType("Entendido", ButtonBar.ButtonData.OK_DONE));
        styleDialog(alert);
        alert.showAndWait();
    }

    private boolean confirmDiscard() {
        Alert alert = new Alert(AlertType.CONFIRMATION);
        alert.setTitle("Descartar cambios");
        alert.setHeaderText("Hay cambios sin guardar");
        alert.setContentText("¿Descartar los cambios y recargar desde el servidor?");
        ButtonType discard = new ButtonType("Descartar", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancel = new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE);
        alert.getButtonTypes().setAll(discard, cancel);
        styleDialog(alert);
        Optional<ButtonType> res = alert.showAndWait();
        return res.isPresent() && res.get() == discard;
    }

    private void styleDialog(Dialog<?> dialog) {
        var css = getClass().getResource("/io/github/dinamo541/servermanagermc/view/DarkThemeStyle.css");
        if (css != null) {
            dialog.getDialogPane().getStylesheets().add(css.toExternalForm());
        }
        dialog.getDialogPane().getStyleClass().add("content");
    }

    // ===================== etiquetas amigables y ayuda =====================

    /**
     * Sustituye la etiqueta cruda de cada fila (la clave del archivo) por una
     * celda con título en español, la clave original como subtítulo y un botón
     * discreto de ayuda que abre la explicación en una ventana modal.
     */
    private void enhanceLabels(Parent parent) {
        for (Node child : parent.getChildrenUnmodifiable()) {
            if (child instanceof HBox row && row.getStyleClass().contains("prop-row")) {
                enhanceRow(row);
            } else if (child instanceof Parent p) {
                enhanceLabels(p);
            }
        }
    }

    private void enhanceRow(HBox row) {
        if (row.getChildren().isEmpty()) {
            return;
        }
        Node first = row.getChildren().get(0);
        if (first instanceof Label label && label.getStyleClass().contains("prop-label")) {
            row.getChildren().set(0, labelCell(label.getText()));
        }
    }

    private VBox labelCell(String rawKey) {
        PropertyDocs.Doc doc = PropertyDocs.get(rawKey);

        Label title = new Label(doc.titulo());
        title.getStyleClass().add("prop-title");

        Label subtitle = new Label(rawKey);
        subtitle.getStyleClass().add("prop-subtitle");

        Button help = new Button("?");
        help.getStyleClass().add("prop-help");
        help.setFocusTraversable(false);
        help.setOnAction(e -> showHelp(rawKey));

        HBox subRow = new HBox(6, subtitle, help);
        subRow.setAlignment(Pos.CENTER_LEFT);

        VBox cell = new VBox(1, title, subRow);
        cell.getStyleClass().add("prop-labelbox");
        cell.setAlignment(Pos.CENTER_LEFT);
        return cell;
    }

    /** Ventana modal (bloquea la pantalla) que explica una propiedad. */
    private void showHelp(String rawKey) {
        PropertyDocs.Doc doc = PropertyDocs.get(rawKey);
        String def = defaults.getProperty(rawKey, "");

        Label title = new Label(doc.titulo());
        title.getStyleClass().add("help-modal-title");
        title.setWrapText(true);

        Label sub = new Label(rawKey + "   ·   por defecto: " + (def.isEmpty() ? "(vacío)" : def));
        sub.getStyleClass().add("help-modal-sub");
        sub.setWrapText(true);

        Label desc = new Label(doc.descripcion());
        desc.getStyleClass().add("help-modal-desc");
        desc.setWrapText(true);

        Button close = new Button("Cerrar");
        close.getStyleClass().add("accent");
        HBox actions = new HBox(close);
        actions.setAlignment(Pos.CENTER_RIGHT);

        VBox box = new VBox(12, title, sub, desc, actions);
        box.getStyleClass().add("help-modal");
        box.setPadding(new Insets(22));
        box.setPrefWidth(440);

        Stage modal = new Stage();
        modal.initModality(Modality.APPLICATION_MODAL);
        if (rootBox.getScene() != null) {
            modal.initOwner(rootBox.getScene().getWindow());
        }
        modal.setResizable(false);
        modal.setTitle(doc.titulo());

        Scene scene = new Scene(box);
        var css = getClass().getResource("/io/github/dinamo541/servermanagermc/view/DarkThemeStyle.css");
        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
        }
        modal.setScene(scene);
        close.setOnAction(e -> modal.close());
        modal.showAndWait();
    }

    // ===================== estado del feedback =====================

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
}
