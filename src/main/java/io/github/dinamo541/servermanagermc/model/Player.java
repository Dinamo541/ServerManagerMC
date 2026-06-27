package io.github.dinamo541.servermanagermc.model;

/** Jugador (whitelist / conectado / op). */
public record Player(String name, String uuid, boolean op) {

    public Player(String name) {
        this(name, null, false);
    }
}
