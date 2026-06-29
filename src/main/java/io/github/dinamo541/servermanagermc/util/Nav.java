package io.github.dinamo541.servermanagermc.util;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

/**
 * Estado de navegación compartido entre vistas. Permite que, por ejemplo, al
 * pulsar el chip de un jugador en el Dashboard o la Consola, la vista Jugadores
 * abra ya ese jugador seleccionado. Las vistas se cachean (FlowController), por
 * lo que el destino observa esta propiedad para reaccionar también cuando ya
 * estaba cargado.
 */
public final class Nav {

    private static final StringProperty SELECTED_PLAYER = new SimpleStringProperty();

    private Nav() {
    }

    public static StringProperty selectedPlayerProperty() {
        return SELECTED_PLAYER;
    }

    public static String getSelectedPlayer() {
        return SELECTED_PLAYER.get();
    }

    public static void selectPlayer(String name) {
        SELECTED_PLAYER.set(name);
    }

    public static void clearSelection() {
        SELECTED_PLAYER.set(null);
    }
}
