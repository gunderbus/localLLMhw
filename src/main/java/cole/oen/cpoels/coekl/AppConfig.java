package cole.oen.cpoels.coekl;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;

public class AppConfig {
    public enum BackendType {
        GEMINI,
        OLLAMA
    }

    private BackendType backendType = BackendType.GEMINI;
    private String geminiProjectId = "";
    private String geminiLocation = "";
    private String geminiModel = "gemini-1.5-pro";
    private String ollamaBaseUrl = "http://localhost:11434";
    private String ollamaModel = "llama3";

    public static Path defaultPath() {
        return Path.of("config.properties");
    }

    public static AppConfig load(Path path) {
        AppConfig config = new AppConfig();
        if (path == null || !Files.exists(path)) {
            return config;
        }
        Properties props = new Properties();
        try (InputStream input = Files.newInputStream(path)) {
            props.load(input);
        } catch (IOException e) {
            return config;
        }
        config.backendType = parseBackend(props.getProperty("backend.type"), config.backendType);
        config.geminiProjectId = trimOrDefault(props.getProperty("gemini.projectId"), config.geminiProjectId);
        config.geminiLocation = trimOrDefault(props.getProperty("gemini.location"), config.geminiLocation);
        config.geminiModel = trimOrDefault(props.getProperty("gemini.model"), config.geminiModel);
        config.ollamaBaseUrl = trimOrDefault(props.getProperty("ollama.baseUrl"), config.ollamaBaseUrl);
        config.ollamaModel = trimOrDefault(props.getProperty("ollama.model"), config.ollamaModel);
        return config;
    }

    public void save(Path path) {
        if (path == null) {
            return;
        }
        Properties props = new Properties();
        props.setProperty("backend.type", backendType.name());
        props.setProperty("gemini.projectId", safe(geminiProjectId));
        props.setProperty("gemini.location", safe(geminiLocation));
        props.setProperty("gemini.model", safe(geminiModel));
        props.setProperty("ollama.baseUrl", safe(ollamaBaseUrl));
        props.setProperty("ollama.model", safe(ollamaModel));
        try (OutputStream output = Files.newOutputStream(path)) {
            props.store(output, "AI Settings");
        } catch (IOException e) {
            // Swallow to keep UI responsive; user can re-save.
        }
    }

    public BackendType getBackendType() {
        return backendType;
    }

    public void setBackendType(BackendType backendType) {
        if (backendType != null) {
            this.backendType = backendType;
        }
    }

    public String getGeminiProjectId() {
        return geminiProjectId;
    }

    public void setGeminiProjectId(String geminiProjectId) {
        this.geminiProjectId = safe(geminiProjectId);
    }

    public String getGeminiLocation() {
        return geminiLocation;
    }

    public void setGeminiLocation(String geminiLocation) {
        this.geminiLocation = safe(geminiLocation);
    }

    public String getGeminiModel() {
        return geminiModel;
    }

    public void setGeminiModel(String geminiModel) {
        this.geminiModel = safe(geminiModel);
    }

    public String getOllamaBaseUrl() {
        return ollamaBaseUrl;
    }

    public void setOllamaBaseUrl(String ollamaBaseUrl) {
        this.ollamaBaseUrl = safe(ollamaBaseUrl);
    }

    public String getOllamaModel() {
        return ollamaModel;
    }

    public void setOllamaModel(String ollamaModel) {
        this.ollamaModel = safe(ollamaModel);
    }

    private static BackendType parseBackend(String raw, BackendType fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return BackendType.valueOf(raw.trim().toUpperCase(Locale.US));
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    private static String trimOrDefault(String value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? fallback : trimmed;
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
