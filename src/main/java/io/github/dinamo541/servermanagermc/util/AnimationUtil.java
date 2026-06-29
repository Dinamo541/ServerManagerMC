package io.github.dinamo541.servermanagermc.util;

import java.util.List;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.ParallelTransition;
import javafx.animation.TranslateTransition;
import javafx.scene.Node;
import javafx.util.Duration;

/**
 * Transiciones de la UI. JavaFX no soporta animaciones declarativas en CSS, así
 * que los efectos de entrada/pulso se implementan aquí con la API de animación.
 */
public final class AnimationUtil {

    private AnimationUtil() {
    }

    /**
     * Entrada de una vista al navegar: aparece con un leve deslizamiento hacia
     * arriba + fundido. Se fija el centro de inmediato (no bloquea reclics).
     */
    public static void enter(Node node) {
        if (node == null) {
            return;
        }
        node.setOpacity(0);
        node.setTranslateY(16);

        FadeTransition fade = new FadeTransition(Duration.millis(260), node);
        fade.setFromValue(0);
        fade.setToValue(1);

        TranslateTransition slide = new TranslateTransition(Duration.millis(260), node);
        slide.setFromY(16);
        slide.setToY(0);
        slide.setInterpolator(Interpolator.EASE_OUT);

        new ParallelTransition(node, fade, slide).play();
    }

    /**
     * Entrada escalonada de una lista de nodos (p. ej. las tarjetas del
     * dashboard): cada uno entra con un pequeño retardo incremental.
     */
    public static void staggerIn(List<? extends Node> nodes) {
        int i = 0;
        for (Node n : nodes) {
            n.setOpacity(0);
            n.setTranslateY(14);

            FadeTransition fade = new FadeTransition(Duration.millis(240), n);
            fade.setFromValue(0);
            fade.setToValue(1);

            TranslateTransition slide = new TranslateTransition(Duration.millis(240), n);
            slide.setFromY(14);
            slide.setToY(0);
            slide.setInterpolator(Interpolator.EASE_OUT);

            ParallelTransition pt = new ParallelTransition(n, fade, slide);
            pt.setDelay(Duration.millis(40L * i++));
            pt.play();
        }
    }

    /**
     * Entrada lateral desde la derecha + fundido. Pensada para paneles que
     * aparecen al costado (p. ej. la ficha de jugador al seleccionarlo).
     */
    public static void slideInRight(Node node) {
        if (node == null) {
            return;
        }
        node.setOpacity(0);
        node.setTranslateX(28);

        FadeTransition fade = new FadeTransition(Duration.millis(220), node);
        fade.setFromValue(0);
        fade.setToValue(1);

        TranslateTransition slide = new TranslateTransition(Duration.millis(220), node);
        slide.setFromX(28);
        slide.setToX(0);
        slide.setInterpolator(Interpolator.EASE_OUT);

        new ParallelTransition(node, fade, slide).play();
    }

    /** Pulso infinito (latido) para el indicador de estado online. */
    public static FadeTransition pulse(Node node) {
        FadeTransition pulse = new FadeTransition(Duration.millis(900), node);
        pulse.setFromValue(1.0);
        pulse.setToValue(0.35);
        pulse.setCycleCount(FadeTransition.INDEFINITE);
        pulse.setAutoReverse(true);
        pulse.play();
        return pulse;
    }
}
