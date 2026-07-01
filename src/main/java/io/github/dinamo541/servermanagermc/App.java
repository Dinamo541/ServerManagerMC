package io.github.dinamo541.servermanagermc;

import io.github.dinamo541.corefx.navigation.FlowController;
import io.github.dinamo541.corefx.ui.ThemeManager;
import io.github.dinamo541.servermanagermc.concurrent.AsyncExecutor;
import io.github.dinamo541.servermanagermc.config.AppProfile;
import javafx.application.Application;
import javafx.application.HostServices;
import javafx.stage.Stage;

/**
 * Punto de entrada JavaFX. Cablea el theming (AtlantaFX + ThemeManager de CoreFx)
 * y la navegación (FlowController), y muestra el shell (HomeView = BorderPane con
 * sidebar). Toda la lógica vive en la capa de servicios.
 */
public class App extends Application {

    public static final String APP_NAME = "Server Minecraft Manager";

    private static HostServices hostServices;

    /** Servicios de la aplicación (p. ej. abrir URLs en el navegador del sistema). */
    public static HostServices hostServices() {
        return hostServices;
    }

    @Override
    public void init() {
        // Resuelve el perfil temprano: DEV (mock) en Windows / PROD (real) en Monica.
        System.out.println("[ServerManagerMC] Perfil activo: " + AppProfile.current());
    }

    @Override
    public void start(Stage stage) {
        hostServices = getHostServices();

        // 1) Tema oscuro propio (DarkThemeStyle.css) registrado en el ThemeManager
        //    de CoreFx y aplicado a cada escena vía themeApplier.
        ThemeManager theme = ThemeManager.getInstance();
        var darkCss = getClass().getResource("view/DarkThemeStyle.css");
        if (darkCss != null) {
            theme.registerTheme("dark", darkCss.toExternalForm());
            theme.setActiveTheme("dark");
        }

        // 2) Bootstrap de CoreFx (convención del proyecto: view/, resources/, icon.png).
        FlowController flow = FlowController.getInstance();
        flow.initialize(
            stage,
            APP_NAME,
            "/io/github/dinamo541/servermanagermc/view/",
            "/io/github/dinamo541/servermanagermc/resources/",
            "/io/github/dinamo541/servermanagermc/icon.png",
            App.class
        );
        flow.setThemeApplier(theme.asApplier());

        // 3) Ventana + mostrar el shell.
        stage.setTitle(APP_NAME + " — Panel de Monica");
        flow.goViewMain("HomeView");
        flow.setStageMinSize(1024, 640);
    }

    @Override
    public void stop() {
        AsyncExecutor.shutdown();
    }

    public static void main(String[] args) {
        launch();
    }
}
