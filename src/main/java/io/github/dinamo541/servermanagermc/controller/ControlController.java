package io.github.dinamo541.servermanagermc.controller;

import io.github.dinamo541.corefx.navigation.StageManager;
import io.github.dinamo541.corefx.util.Answer;
import io.github.dinamo541.servermanagermc.concurrent.AsyncExecutor;
import io.github.dinamo541.servermanagermc.config.Services;
import java.util.function.Supplier;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;

/**
 * Control del servidor: botones Start/Stop/Restart, consola en vivo (tail del
 * log) y envío de comandos arbitrarios al juego vía tmux.
 */
public class ControlController {

    @FXML
    private TextArea console;
    @FXML
    private TextField commandField;
    @FXML
    private Label statusLabel;

    private boolean tailStarted;

    @FXML
    private void initialize() {
        if (!tailStarted) {
            tailStarted = true;
            Services.console().startTail(line ->
                    StageManager.getInstance().runOnFxThread(() -> console.appendText(line + "\n")));
        }
    }

    @FXML
    private void onStart()   { runAction(Services.server()::start); }
    @FXML
    private void onStop()    { runAction(Services.server()::stop); }
    @FXML
    private void onRestart() { runAction(Services.server()::restart); }

    private void runAction(Supplier<Answer> action) {
        statusLabel.setText("Ejecutando…");
        AsyncExecutor.supply(action, this::showAnswer);
    }

    @FXML
    private void onSend() {
        Answer a = Services.console().sendCommand(commandField.getText());
        showAnswer(a);
        if (a.isSuccess()) {
            commandField.clear();
        }
    }

    private void showAnswer(Answer a) {
        String msg = a.getMessage();
        statusLabel.setText(msg != null ? msg : (a.isSuccess() ? "OK" : "Error"));
    }
}
