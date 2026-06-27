module io.github.dinamo541.servermanagermc {
    requires javafx.controls;
    requires javafx.fxml;

    opens io.github.dinamo541.servermanagermc to javafx.fxml;
    exports io.github.dinamo541.servermanagermc;
}
