package io.github.dinamo541.servermanagermc.service;

import io.github.dinamo541.corefx.util.Answer;
import io.github.dinamo541.servermanagermc.config.ServerPaths;
import io.github.dinamo541.servermanagermc.model.ServerPropertiesModel;
import io.github.dinamo541.servermanagermc.service.command.CommandResult;
import io.github.dinamo541.servermanagermc.service.command.CommandRunner;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

/**
 * Lectura y escritura de {@code server.properties}. En PROD lee el archivo con
 * {@code cat} y lo reescribe con un here-doc; en DEV se apoya en el protocolo
 * interno {@code mc:properties-load|save} del {@link MockCommandRunner}, que
 * mantiene el contenido en memoria. Bifurca con {@link CommandRunner#isMock()}
 * igual que {@link PlayerService}.
 *
 * <p>Al guardar se serializa el {@link Properties} completo (incluidas las claves
 * que el editor no modela), de modo que no se pierde ninguna opción del archivo.
 */
public final class PropertiesService {

    private final CommandRunner runner;

    public PropertiesService(CommandRunner runner) {
        this.runner = runner;
    }

    /** Lee el archivo y lo devuelve como {@link Properties}. */
    public Properties load() {
        String raw = runner.isMock()
                ? runner.run("mc:properties-load").output()
                : runner.run("cat \"" + ServerPaths.PROPERTIES + "\" 2>/dev/null").output();
        return parse(raw);
    }

    /** Reescribe el archivo con el contenido de {@code props}. */
    public Answer save(Properties props) {
        String body = serialize(props);
        CommandResult r = runner.isMock()
                ? runner.run("mc:properties-save " + body)
                : runner.run("cat > \"" + ServerPaths.PROPERTIES + "\" << 'EOF'\n" + body + "EOF");
        return r.ok()
                ? Answer.success("Propiedades guardadas en el servidor.")
                : Answer.failure("No se pudieron guardar las propiedades.", r.trimmed());
    }

    /** Valores por defecto razonables para un servidor Mohist/Paper. */
    public Properties defaults() {
        return ServerPropertiesModel.defaults().toProperties();
    }

    private static Properties parse(String raw) {
        Properties p = new Properties();
        if (raw != null && !raw.isBlank()) {
            try {
                p.load(new StringReader(raw));
            } catch (IOException ignored) {
                // contenido ilegible: devolvemos lo que se haya podido cargar
            }
        }
        return p;
    }

    private static String serialize(Properties props) {
        List<String> keys = new ArrayList<>(props.stringPropertyNames());
        Collections.sort(keys);
        StringBuilder sb = new StringBuilder();
        for (String key : keys) {
            sb.append(key).append('=').append(props.getProperty(key, "")).append('\n');
        }
        return sb.toString();
    }
}
