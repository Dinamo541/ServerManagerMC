package io.github.dinamo541.servermanagermc.concurrent;

import io.github.dinamo541.corefx.navigation.StageManager;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Ejecuta trabajo de E/S (systemctl, tmux, tar, rclone, lectura de archivos)
 * fuera del hilo de JavaFX usando <b>virtual threads</b> (Java 21+), y devuelve
 * el resultado al hilo de la UI vía {@link StageManager#runOnFxThread(Runnable)}.
 *
 * <p>Regla del proyecto: <b>ningún comando se ejecuta en el hilo de la UI</b>.
 */
public final class AsyncExecutor {

    private static final ExecutorService POOL = Executors.newVirtualThreadPerTaskExecutor();

    private AsyncExecutor() {
    }

    /** Ejecuta una tarea en segundo plano sin retorno. */
    public static void run(Runnable task) {
        POOL.submit(task);
    }

    /**
     * Ejecuta {@code background} fuera del hilo de la UI y entrega su resultado a
     * {@code onFxThread} ya en el hilo de JavaFX.
     */
    public static <T> void supply(Supplier<T> background, Consumer<T> onFxThread) {
        POOL.submit(() -> {
            try {
                T result = background.get();
                StageManager.getInstance().runOnFxThread(() -> onFxThread.accept(result));
            } catch (RuntimeException ex) {
                ex.printStackTrace();
            }
        });
    }

    public static void shutdown() {
        POOL.shutdownNow();
    }
}
