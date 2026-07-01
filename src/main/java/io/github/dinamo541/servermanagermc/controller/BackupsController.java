package io.github.dinamo541.servermanagermc.controller;

import io.github.dinamo541.corefx.util.Answer;
import io.github.dinamo541.servermanagermc.concurrent.AsyncExecutor;
import io.github.dinamo541.servermanagermc.config.Services;
import io.github.dinamo541.servermanagermc.model.BackupInfo;
import io.github.dinamo541.servermanagermc.util.AnimationUtil;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Duration;

/**
 * Vista Backups: lista los backups locales y los de Google Drive en dos pestañas,
 * con una pequeña cronología (línea de tiempo) que muestra su orden. La ficha
 * lateral reúne toda la información del backup y sus acciones (descargar,
 * restaurar, renombrar, mover local↔Drive, borrar). Las rutas (carpeta local y
 * remoto de rclone) se pueden cambiar en caliente desde un diálogo modal.
 *
 * <p>Sigue el patrón de {@link PlayersController}: refresco cada 5 s vía
 * {@link AsyncExecutor} y reconstrucción solo cuando el contenido cambia. La
 * restauración —lo más destructivo— exige escribir el nombre exacto del backup.
 */
public class BackupsController {

    private enum Tab { LOCAL, DRIVE }

    private enum PendingAction { NONE, DELETE, MOVE }

    /** Datos leídos en bloque fuera del hilo de UI. */
    private record Snapshot(List<BackupInfo> local, List<BackupInfo> drive, String last, boolean running) {
    }

    private static final Pattern DATE_IN_NAME =
            Pattern.compile("(\\d{4})-(\\d{2})-(\\d{2})_(\\d{2})-(\\d{2})");
    private static final DateTimeFormatter FULL = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter SHORT = DateTimeFormatter.ofPattern("dd/MM");

    @FXML private VBox rootBox;
    @FXML private Label lastBackupLabel;
    @FXML private Label backupStatusLabel;
    @FXML private Button configBtn;

    @FXML private Button tabLocal;
    @FXML private Button tabDrive;
    @FXML private ProgressIndicator backupSpinner;
    @FXML private Label countLabel;
    @FXML private HBox timelineBox;
    @FXML private Label timelineHint;
    @FXML private VBox backupList;

    @FXML private Button createBtn;
    @FXML private Button uploadBtn;

    @FXML private ScrollPane rightPane;
    @FXML private VBox detailPanel;
    @FXML private Label detailName;
    @FXML private Label detailLocation;
    @FXML private VBox infoBox;
    @FXML private Button downloadBtn;
    @FXML private Button restoreBtn;
    @FXML private Button renameBtn;
    @FXML private Button moveBtn;
    @FXML private Button deleteBtn;

    @FXML private VBox confirmBox;
    @FXML private TextField confirmField;
    @FXML private Button confirmRestoreBtn;
    @FXML private Label restoreStatus;
    @FXML private VBox renameBox;
    @FXML private TextField renameField;
    @FXML private Button renameConfirmBtn;
    @FXML private Label renameStatus;
    @FXML private VBox simpleConfirmBox;
    @FXML private Label simpleConfirmLabel;
    @FXML private Button simpleConfirmBtn;
    @FXML private Label statusMessage;

    private Tab activeTab = Tab.LOCAL;
    private PendingAction pendingAction = PendingAction.NONE;
    private boolean backupInProgress;
    private List<BackupInfo> currentLocal = List.of();
    private List<BackupInfo> currentDrive = List.of();
    private BackupInfo selectedBackup;
    private String lastSignature;

    private Timeline refresh;

    // ===================== ciclo de vida =====================

    @FXML
    private void initialize() {
        setActiveTab(Tab.LOCAL);
        AnimationUtil.staggerIn(rootBox.getChildren());

        refresh = new Timeline(
                new KeyFrame(Duration.ZERO, e -> reload()),
                new KeyFrame(Duration.seconds(5)));
        refresh.setCycleCount(Animation.INDEFINITE);
        refresh.play();
    }

    private void reload() {
        AsyncExecutor.supply(() -> {
            var svc = Services.backups();
            return new Snapshot(svc.listLocalBackups(), svc.listDriveBackups(),
                    svc.lastBackupTime(), svc.isBackupRunning());
        }, this::onSnapshot);
    }

    private void onSnapshot(Snapshot s) {
        currentLocal = s.local();
        currentDrive = s.drive();
        backupInProgress = s.running();
        lastBackupLabel.setText("Último: " + s.last());
        backupSpinner.setVisible(s.running());
        backupSpinner.setManaged(s.running());
        createBtn.setDisable(s.running());
        rebuildList(false);
        refreshDetailIfNeeded();
    }

    // ===================== pestañas =====================

    @FXML private void onTabLocal() { switchTab(Tab.LOCAL); }
    @FXML private void onTabDrive() { switchTab(Tab.DRIVE); }

    private void switchTab(Tab tab) {
        setActiveTab(tab);
        rebuildList(true);
    }

    private void setActiveTab(Tab tab) {
        activeTab = tab;
        styleTab(tabLocal, tab == Tab.LOCAL);
        styleTab(tabDrive, tab == Tab.DRIVE);
        uploadBtn.setVisible(tab == Tab.LOCAL);
        uploadBtn.setManaged(tab == Tab.LOCAL);
    }

    private void styleTab(Button b, boolean active) {
        b.getStyleClass().remove("active");
        if (active) {
            b.getStyleClass().add("active");
        }
    }

    // ===================== lista + cronología =====================

    private List<BackupInfo> displayedList() {
        return activeTab == Tab.LOCAL ? currentLocal : currentDrive;
    }

    private void rebuildList(boolean force) {
        List<BackupInfo> rows = displayedList();
        String signature = signatureOf(rows);
        if (!force && signature.equals(lastSignature)) {
            return;
        }
        lastSignature = signature;

        countLabel.setText(String.valueOf(rows.size()));
        backupList.getChildren().clear();
        if (rows.isEmpty()) {
            Label empty = new Label(activeTab == Tab.LOCAL ? "Sin backups locales" : "Sin backups en Drive");
            empty.getStyleClass().add("muted");
            backupList.getChildren().add(empty);
        } else {
            for (BackupInfo b : rows) {
                backupList.getChildren().add(rowNode(b));
            }
            if (force) {
                AnimationUtil.staggerIn(backupList.getChildren());
            }
        }
        refreshTimeline();
    }

    private String signatureOf(List<BackupInfo> rows) {
        StringBuilder sb = new StringBuilder();
        for (BackupInfo b : rows) {
            sb.append(b.name()).append(':').append(b.sizeBytes()).append('|');
        }
        return sb.toString();
    }

    private HBox rowNode(BackupInfo b) {
        HBox row = new HBox(10);
        row.getStyleClass().add("backup-row");
        if (selectedBackup != null && selectedBackup.name().equals(b.name())) {
            row.getStyleClass().add("selected");
        }
        row.setAlignment(Pos.CENTER_LEFT);
        row.setCursor(Cursor.HAND);
        row.setUserData(b.name());
        row.setOnMouseClicked(e -> selectBackup(b));

        Label icon = new Label(b.location() == BackupInfo.Location.DRIVE ? "☁" : "▤");
        icon.getStyleClass().add("backup-row-icon");

        VBox info = new VBox(1);
        Label name = new Label(b.name());
        name.getStyleClass().add("backup-row-name");
        Label meta = new Label(formatSize(b.sizeBytes()) + rowDate(b));
        meta.getStyleClass().add("backup-row-size");
        info.getChildren().addAll(name, meta);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        row.getChildren().addAll(icon, info, spacer);
        return row;
    }

    private static String rowDate(BackupInfo b) {
        LocalDateTime d = parseDate(b.name());
        return d != null ? "  ·  " + FULL.format(d) : "";
    }

    private void highlightSelectedRow() {
        for (var node : backupList.getChildren()) {
            boolean sel = selectedBackup != null && selectedBackup.name().equals(node.getUserData());
            node.getStyleClass().remove("selected");
            if (sel) {
                node.getStyleClass().add("selected");
            }
        }
    }

    /** Dibuja la línea de tiempo del tab actual, ordenada de más antiguo a más reciente. */
    private void refreshTimeline() {
        List<BackupInfo> rows = displayedList();
        timelineBox.getChildren().clear();
        if (rows.isEmpty()) {
            timelineHint.setText("");
            return;
        }
        List<BackupInfo> sorted = new ArrayList<>(rows);
        sorted.sort(Comparator.comparing(b -> dateKey(b.name())));

        for (int i = 0; i < sorted.size(); i++) {
            if (i > 0) {
                Region line = new Region();
                line.getStyleClass().add("timeline-line");
                HBox.setHgrow(line, Priority.ALWAYS);
                timelineBox.getChildren().add(line);
            }
            BackupInfo b = sorted.get(i);
            LocalDateTime d = parseDate(b.name());
            Label dot = new Label("●");
            dot.getStyleClass().add("timeline-dot");
            if (i == sorted.size() - 1) {
                dot.getStyleClass().add("latest");
            }
            if (selectedBackup != null && selectedBackup.name().equals(b.name())) {
                dot.getStyleClass().add("selected");
            }
            dot.setCursor(Cursor.HAND);
            Tooltip.install(dot, new Tooltip(b.name() + (d != null ? "\n" + FULL.format(d) : "")));
            dot.setOnMouseClicked(e -> selectBackup(b));
            dot.setOnMouseEntered(e ->
                    timelineHint.setText(b.name() + (d != null ? "  ·  " + FULL.format(d) : "")));
            timelineBox.getChildren().add(dot);
        }

        LocalDateTime od = parseDate(sorted.get(0).name());
        LocalDateTime nd = parseDate(sorted.get(sorted.size() - 1).name());
        String range = "más antiguo" + (od != null ? " (" + SHORT.format(od) + ")" : "")
                + "  →  más reciente" + (nd != null ? " (" + SHORT.format(nd) + ")" : "");
        timelineHint.setText(range);
        timelineBox.setOnMouseExited(e -> timelineHint.setText(range));
    }

    // ===================== ficha (derecha) =====================

    private void selectBackup(BackupInfo b) {
        selectedBackup = b;
        highlightSelectedRow();
        refreshTimeline();
        closeAllPanels();
        statusMessage.setText("");
        clearError(statusMessage);

        boolean drive = b.location() == BackupInfo.Location.DRIVE;
        LocalDateTime d = parseDate(b.name());

        detailName.setText(b.name());
        detailLocation.setText((drive ? "Google Drive" : "Local") + "  ·  " + formatSize(b.sizeBytes()));

        infoBox.getChildren().setAll(
                kvRow("Fecha", d != null ? FULL.format(d) : "—"),
                kvRow("Tamaño", formatSize(b.sizeBytes())),
                kvRow("Ubicación", drive ? "Google Drive" : "Local"),
                kvRow("También en", crossLocation(b)));

        downloadBtn.setVisible(drive);
        downloadBtn.setManaged(drive);
        restoreBtn.setVisible(!drive);
        restoreBtn.setManaged(!drive);
        moveBtn.setText(drive ? "Mover a local" : "Mover a Drive");

        if (!rightPane.isVisible()) {
            rightPane.setVisible(true);
            rightPane.setManaged(true);
        }
        AnimationUtil.slideInRight(detailPanel);
    }

    private String crossLocation(BackupInfo b) {
        List<BackupInfo> other = b.location() == BackupInfo.Location.LOCAL ? currentDrive : currentLocal;
        boolean also = other.stream().anyMatch(x -> x.name().equals(b.name()));
        return also ? "Sí (en local y Drive)" : "No, solo aquí";
    }

    @FXML
    private void onCloseDetail() {
        selectedBackup = null;
        closeAllPanels();
        rightPane.setVisible(false);
        rightPane.setManaged(false);
        highlightSelectedRow();
        refreshTimeline();
    }

    /** Si el backup seleccionado ya no existe tras refrescar, cierra la ficha. */
    private void refreshDetailIfNeeded() {
        if (selectedBackup == null) {
            return;
        }
        List<BackupInfo> list = selectedBackup.location() == BackupInfo.Location.LOCAL
                ? currentLocal : currentDrive;
        boolean present = list.stream().anyMatch(b -> b.name().equals(selectedBackup.name()));
        if (!present) {
            onCloseDetail();
        } else {
            highlightSelectedRow();
        }
    }

    // ===================== acciones =====================

    @FXML
    private void onCreate() {
        if (backupInProgress) {
            return;
        }
        backupInProgress = true;
        createBtn.setDisable(true);
        backupSpinner.setVisible(true);
        backupSpinner.setManaged(true);
        setStatus("Creando backup… (puede tardar varios minutos)");
        AsyncExecutor.supply(() -> Services.backups().createBackup(), a -> {
            showActionAnswer(a);
            reload();
        });
    }

    @FXML
    private void onUpload() {
        setStatus("Subiendo a Drive…");
        AsyncExecutor.supply(() -> Services.backups().uploadToDrive(), a -> {
            showActionAnswer(a);
            reload();
        });
    }

    @FXML
    private void onDownload() {
        if (selectedBackup == null) {
            return;
        }
        String name = selectedBackup.name();
        setStatus("Descargando " + name + "…");
        AsyncExecutor.supply(() -> Services.backups().downloadBackup(name), a -> {
            showActionAnswer(a);
            reload();
        });
    }

    @FXML
    private void onRestore() {
        if (selectedBackup == null) {
            return;
        }
        closeAllPanels();
        confirmBox.setVisible(true);
        confirmBox.setManaged(true);
        confirmField.clear();
        confirmField.requestFocus();
    }

    @FXML
    private void onConfirmRestore() {
        if (selectedBackup == null) {
            return;
        }
        String typed = confirmField.getText();
        if (typed == null || !typed.equals(selectedBackup.name())) {
            restoreStatus.setText("El nombre no coincide. Escribe exactamente: " + selectedBackup.name());
            markError(restoreStatus);
            return;
        }
        BackupInfo target = selectedBackup;
        confirmRestoreBtn.setDisable(true);
        restoreStatus.setText("Restaurando…");
        clearError(restoreStatus);
        AsyncExecutor.supply(() -> Services.backups().restoreBackup(target.name()), a -> {
            showActionAnswer(a);
            closeAllPanels();
            reload();
        });
    }

    @FXML
    private void onRename() {
        if (selectedBackup == null) {
            return;
        }
        closeAllPanels();
        renameBox.setVisible(true);
        renameBox.setManaged(true);
        renameField.setText(selectedBackup.name());
        renameField.requestFocus();
    }

    @FXML
    private void onConfirmRename() {
        if (selectedBackup == null) {
            return;
        }
        String newName = renameField.getText() == null ? "" : renameField.getText().strip();
        if (newName.isEmpty() || newName.equals(selectedBackup.name())) {
            renameStatus.setText("Escribe un nombre nuevo distinto.");
            markError(renameStatus);
            return;
        }
        BackupInfo target = selectedBackup;
        renameConfirmBtn.setDisable(true);
        renameStatus.setText("Renombrando…");
        clearError(renameStatus);
        AsyncExecutor.supply(() -> Services.backups().renameBackup(target, newName), a -> {
            showActionAnswer(a);
            closeAllPanels();
            reload();
        });
    }

    @FXML
    private void onMove() {
        if (selectedBackup == null) {
            return;
        }
        boolean drive = selectedBackup.location() == BackupInfo.Location.DRIVE;
        closeAllPanels();
        pendingAction = PendingAction.MOVE;
        simpleConfirmLabel.setText(drive
                ? "¿Mover “" + selectedBackup.name() + "” de Drive a la carpeta local?"
                : "¿Mover “" + selectedBackup.name() + "” de local a Drive?");
        simpleConfirmBtn.setText("Mover");
        simpleConfirmBtn.getStyleClass().setAll("button", "accent");
        simpleConfirmBox.setVisible(true);
        simpleConfirmBox.setManaged(true);
    }

    @FXML
    private void onDelete() {
        if (selectedBackup == null) {
            return;
        }
        closeAllPanels();
        pendingAction = PendingAction.DELETE;
        simpleConfirmLabel.setText("¿Borrar definitivamente “" + selectedBackup.name()
                + "”? Esta acción no se puede deshacer.");
        simpleConfirmBtn.setText("Borrar");
        simpleConfirmBtn.getStyleClass().setAll("button", "danger");
        simpleConfirmBox.setVisible(true);
        simpleConfirmBox.setManaged(true);
    }

    @FXML
    private void onSimpleConfirm() {
        if (selectedBackup == null || pendingAction == PendingAction.NONE) {
            return;
        }
        BackupInfo target = selectedBackup;
        PendingAction action = pendingAction;
        simpleConfirmBtn.setDisable(true);
        setStatus(action == PendingAction.DELETE ? "Borrando…" : "Moviendo…");
        AsyncExecutor.supply(
                () -> action == PendingAction.DELETE
                        ? Services.backups().deleteBackup(target)
                        : Services.backups().moveBackup(target),
                a -> {
                    showActionAnswer(a);
                    closeAllPanels();
                    reload();
                });
    }

    @FXML
    private void onCancelPanels() {
        closeAllPanels();
    }

    private void closeAllPanels() {
        hidePanel(confirmBox);
        confirmField.clear();
        restoreStatus.setText("");
        clearError(restoreStatus);
        confirmRestoreBtn.setDisable(false);

        hidePanel(renameBox);
        renameField.clear();
        renameStatus.setText("");
        clearError(renameStatus);
        renameConfirmBtn.setDisable(false);

        hidePanel(simpleConfirmBox);
        simpleConfirmBtn.setDisable(false);
        pendingAction = PendingAction.NONE;
    }

    private static void hidePanel(VBox box) {
        box.setVisible(false);
        box.setManaged(false);
    }

    // ===================== configuración de rutas (modal) =====================

    @FXML
    private void onConfigPaths() {
        var svc = Services.backups();

        Label title = new Label("Configurar rutas de backup");
        title.getStyleClass().add("help-modal-title");

        Label l1 = new Label("Carpeta de backups en el servidor:");
        l1.getStyleClass().add("muted");
        TextField dirField = new TextField(svc.backupsDir());

        Label l2 = new Label("Remoto de rclone (Google Drive):");
        l2.getStyleClass().add("muted");
        TextField remoteField = new TextField(svc.driveRemote());

        Label note = new Label("Se usa para listar, restaurar, subir y descargar. "
                + "El cambio se aplica durante esta sesión.");
        note.getStyleClass().add("help-modal-desc");
        note.setWrapText(true);

        Button cancel = new Button("Cancelar");
        cancel.getStyleClass().add("ghost");
        Button save = new Button("Guardar");
        save.getStyleClass().add("accent");
        HBox actions = new HBox(8, cancel, save);
        actions.setAlignment(Pos.CENTER_RIGHT);

        VBox box = new VBox(10, title, l1, dirField, l2, remoteField, note, actions);
        box.getStyleClass().add("help-modal");
        box.setPadding(new Insets(22));
        box.setPrefWidth(480);

        Stage modal = new Stage();
        modal.initModality(Modality.APPLICATION_MODAL);
        if (rootBox.getScene() != null) {
            modal.initOwner(rootBox.getScene().getWindow());
        }
        modal.setResizable(false);
        modal.setTitle("Rutas de backup");

        Scene scene = new Scene(box);
        var css = getClass().getResource("/io/github/dinamo541/servermanagermc/view/DarkThemeStyle.css");
        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
        }
        modal.setScene(scene);

        cancel.setOnAction(e -> modal.close());
        save.setOnAction(e -> {
            svc.setBackupsDir(dirField.getText());
            svc.setDriveRemote(remoteField.getText());
            modal.close();
            setStatus("Rutas actualizadas.");
            lastSignature = null;
            reload();
        });
        modal.showAndWait();
    }

    // ===================== feedback =====================

    private void showActionAnswer(Answer a) {
        String msg = a.getMessage();
        setStatus(msg != null ? msg : (a.isSuccess() ? "OK" : "Error"));
        if (a.isSuccess()) {
            clearError(statusMessage);
        } else {
            markError(statusMessage);
        }
    }

    private void setStatus(String msg) {
        statusMessage.setText(msg);
        backupStatusLabel.setText(msg);
    }

    private static void markError(Label label) {
        if (!label.getStyleClass().contains("action-error")) {
            label.getStyleClass().add("action-error");
        }
    }

    private static void clearError(Label label) {
        label.getStyleClass().remove("action-error");
    }

    // ===================== helpers =====================

    private HBox kvRow(String key, String value) {
        Label k = new Label(key);
        k.getStyleClass().add("kv-key");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Label v = new Label(value);
        v.getStyleClass().add("kv-val");
        v.setWrapText(true);
        HBox row = new HBox(8, k, spacer, v);
        row.getStyleClass().add("kv-row");
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private static LocalDateTime parseDate(String name) {
        Matcher m = DATE_IN_NAME.matcher(name);
        if (m.find()) {
            try {
                return LocalDateTime.of(
                        Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)),
                        Integer.parseInt(m.group(3)), Integer.parseInt(m.group(4)),
                        Integer.parseInt(m.group(5)));
            } catch (RuntimeException ignored) {
                // fecha fuera de rango: se trata como sin fecha
            }
        }
        return null;
    }

    private static LocalDateTime dateKey(String name) {
        LocalDateTime d = parseDate(name);
        return d != null ? d : LocalDateTime.MIN;
    }

    private static String formatSize(long bytes) {
        if (bytes < 0) {
            return "—";
        }
        double kb = bytes / 1024.0;
        double mb = kb / 1024.0;
        double gb = mb / 1024.0;
        if (gb >= 1) {
            return String.format(Locale.US, "%.1f GB", gb);
        }
        if (mb >= 1) {
            return String.format(Locale.US, "%.1f MB", mb);
        }
        if (kb >= 1) {
            return String.format(Locale.US, "%.0f KB", kb);
        }
        return bytes + " B";
    }
}
