/**
 * ServerManagerMC — panel de control de escritorio (JavaFX) para el servidor
 * Mohist de "Monica". Capa visual sobre systemd + tmux.
 */
module io.github.dinamo541.servermanagermc {

    // --- JavaFX ---
    requires javafx.controls;
    requires javafx.fxml;

    // --- Librería propia del desarrollador (navegación, theming, utilidades) ---
    requires io.github.dinamo541.corefx;

    // NOTA: el theming se hace con DarkThemeStyle.css (paleta propia). AtlantaFX y
    // MaterialFX están en el pom; se añadirán con `requires atlantafx.base;` /
    // `requires MaterialFX;` cuando se usen sus componentes (Etapa 7 — pulido).

    // CoreFx (FlowController) carga los .fxml de este paquete vía App.class.getResource,
    // por eso 'view' debe abrirse (lo lee desde otro módulo).
    opens io.github.dinamo541.servermanagermc.view;

    // javafx.fxml inyecta @FXML y enlaza handlers en los controladores por reflexión.
    opens io.github.dinamo541.servermanagermc.controller to javafx.fxml;

    // javafx.graphics instancia la subclase Application (paquete exportado, clase pública).
    opens io.github.dinamo541.servermanagermc to javafx.fxml;

    exports io.github.dinamo541.servermanagermc;
}
