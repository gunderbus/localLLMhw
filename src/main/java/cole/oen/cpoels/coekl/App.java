package cole.oen.cpoels.coekl;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.PasswordField;
import javafx.scene.control.Separator;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import cole.oen.cpoels.coekl.AIS.AIIO;
import cole.oen.cpoels.coekl.AIS.ClaudeIO;
import cole.oen.cpoels.coekl.AIS.GeminiIO;
import cole.oen.cpoels.coekl.AIS.OllamaIO;

public class App extends Application {
    @Override
    public void start(Stage stage) {
        AppConfig config = AppConfig.load(AppConfig.defaultPath());
        ObjectProperty<AIIO> aiBackend = new SimpleObjectProperty<>();
        BooleanProperty busy = new SimpleBooleanProperty(false);
        AtomicReference<String> planRef = new AtomicReference<>();

        Label title = new Label("AI File Transformer");
        title.getStyleClass().add("screen-title");

        Label subtitle = new Label("Upload files, describe the edits you want, then apply AI-generated changes.");
        subtitle.getStyleClass().add("screen-subtitle");

        Label uploadLabel = new Label("Files");
        uploadLabel.getStyleClass().add("section-label");

        ListView<String> selectedFilesView = new ListView<>();
        selectedFilesView.getStyleClass().add("file-list");
        selectedFilesView.setPrefHeight(140);

        Button uploadButton = new Button("Upload Files");
        uploadButton.getStyleClass().add("primary-button");

        Button clearFilesButton = new Button("Clear");
        clearFilesButton.getStyleClass().add("secondary-button");

        HBox uploadActions = new HBox(10, uploadButton, clearFilesButton);

        Label promptLabel = new Label("AI Instruction");
        promptLabel.getStyleClass().add("section-label");

        TextArea promptInput = new TextArea();
        promptInput.setPromptText("Example: Rename variables to camelCase and add comments explaining each method.");
        promptInput.setWrapText(true);
        promptInput.setPrefRowCount(6);
        promptInput.getStyleClass().add("prompt-area");

        Button generatePlanButton = new Button("Generate Change Plan");
        generatePlanButton.getStyleClass().add("secondary-button");

        Button applyButton = new Button("Apply Changes");
        applyButton.getStyleClass().add("primary-button");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox actionRow = new HBox(10, generatePlanButton, spacer, applyButton);

        Label statusLabel = new Label("Ready. Upload files to begin.");
        statusLabel.getStyleClass().add("status-label");

        VBox panel = new VBox(
            12,
            title,
            subtitle,
            uploadLabel,
            selectedFilesView,
            uploadActions,
            promptLabel,
            promptInput,
            actionRow,
            statusLabel
        );
        panel.getStyleClass().add("main-panel");
        panel.setMaxWidth(760);
        panel.setPadding(new Insets(24));

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select Files to Transform");
        fileChooser.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("Text & Code", "*.txt", "*.md", "*.java", "*.js", "*.ts", "*.json", "*.xml", "*.yaml", "*.yml", "*.py", "*.css", "*.html"),
            new FileChooser.ExtensionFilter("All Files", "*.*")
        );

        generatePlanButton.disableProperty().bind(busy.or(aiBackend.isNull()));
        applyButton.disableProperty().bind(busy.or(aiBackend.isNull()));

        uploadButton.setOnAction(event -> {
            List<File> files = fileChooser.showOpenMultipleDialog(stage);
            if (files == null || files.isEmpty()) {
                statusLabel.setText("No files selected.");
                return;
            }
            selectedFilesView.getItems().clear();
            files.forEach(file -> selectedFilesView.getItems().add(file.getAbsolutePath()));
            statusLabel.setText("Loaded " + files.size() + " file(s). Add instructions for AI changes.");
        });

        clearFilesButton.setOnAction(event -> {
            selectedFilesView.getItems().clear();
            statusLabel.setText("File selection cleared.");
        });

        generatePlanButton.setOnAction(event -> {
            if (aiBackend.get() == null) {
                statusLabel.setText("Configure AI settings before generating a plan.");
                return;
            }
            if (selectedFilesView.getItems().isEmpty()) {
                statusLabel.setText("Upload at least one file to generate a plan.");
                return;
            }
            if (promptInput.getText().isBlank()) {
                statusLabel.setText("Enter an AI instruction before generating a plan.");
                return;
            }
            String[] filePaths = selectedFilesView.getItems().toArray(String[]::new);
            busy.set(true);
            statusLabel.setText("Generating plan with local LLM...");
            CompletableFuture
                .supplyAsync(() -> aiBackend.get().getTransformationPlan(promptInput.getText(), filePaths))
                .whenComplete((plan, error) -> Platform.runLater(() -> {
                    busy.set(false);
                    if (error != null) {
                        statusLabel.setText("Plan failed: " + error.getMessage());
                        return;
                    }
                    planRef.set(plan);
                    statusLabel.setText("Plan generated. Click Apply Changes to write updates.");
                }));
        });

        applyButton.setOnAction(event -> {
            if (aiBackend.get() == null) {
                statusLabel.setText("Configure AI settings before applying changes.");
                return;
            }
            if (selectedFilesView.getItems().isEmpty()) {
                statusLabel.setText("No files uploaded.");
                return;
            }
            if (promptInput.getText().isBlank()) {
                statusLabel.setText("No AI instruction provided.");
                return;
            }
            String plan = planRef.get();
            if (plan == null || plan.isBlank()) {
                statusLabel.setText("Generate a plan before applying changes.");
                return;
            }
            String[] filePaths = selectedFilesView.getItems().toArray(String[]::new);
            busy.set(true);
            statusLabel.setText("Applying changes with local LLM...");
            CompletableFuture
                .supplyAsync(() -> aiBackend.get().applyTransformationPlan(plan, filePaths))
                .whenComplete((updatedFiles, error) -> Platform.runLater(() -> {
                    busy.set(false);
                    if (error != null) {
                        statusLabel.setText("Apply failed: " + error.getMessage());
                        return;
                    }
                    aiBackend.get().writeFiles(updatedFiles, filePaths);
                    statusLabel.setText("Changes applied to " + filePaths.length + " file(s).");
                }));
        });

        VBox settingsPanel = buildSettingsPanel(config, aiBackend, statusLabel);
        TabPane tabs = new TabPane();
        Tab settingsTab = new Tab("AI Settings", settingsPanel);
        Tab transformerTab = new Tab("Transformer", panel);
        settingsTab.setClosable(false);
        transformerTab.setClosable(false);
        tabs.getTabs().addAll(settingsTab, transformerTab);
        tabs.getSelectionModel().select(settingsTab);

        String startupValidation = validateConfig(config);
        if (startupValidation == null) {
            try {
                aiBackend.set(buildBackend(config));
                statusLabel.setText("Loaded AI settings. Active backend: " + config.getBackendType().name().toLowerCase() + ".");
            } catch (RuntimeException e) {
                statusLabel.setText("AI settings need attention: " + e.getMessage());
            }
        } else {
            statusLabel.setText("Configure AI settings before generating changes.");
        }

        VBox root = new VBox(tabs);
        root.getStyleClass().add("app-root");
        root.setPadding(new Insets(18));

        Scene scene = new Scene(root, 920, 680);
        scene.getStylesheets().add(
            App.class.getResource("/styles.css").toExternalForm()
        );

        stage.setTitle("AI File Transformer");
        stage.setMinWidth(860);
        stage.setMinHeight(620);
        stage.setScene(scene);
        stage.show();
    }

    private VBox buildSettingsPanel(AppConfig config, ObjectProperty<AIIO> aiBackend, Label statusLabel) {
        Label title = new Label("AI Interface Settings");
        title.getStyleClass().add("screen-title");

        Label subtitle = new Label("Select a backend and configure credentials or endpoints.");
        subtitle.getStyleClass().add("screen-subtitle");

        Label backendLabel = new Label("Backend");
        backendLabel.getStyleClass().add("section-label");

        ChoiceBox<AppConfig.BackendType> backendChoice = new ChoiceBox<>(
            FXCollections.observableArrayList(AppConfig.BackendType.values())
        );
        backendChoice.setValue(config.getBackendType());

        Label geminiLabel = new Label("Gemini (Vertex AI)");
        geminiLabel.getStyleClass().add("section-label");

        TextField geminiProjectField = new TextField(config.getGeminiProjectId());
        geminiProjectField.setPromptText("Project ID (e.g., my-gcp-project)");

        TextField geminiLocationField = new TextField(config.getGeminiLocation());
        geminiLocationField.setPromptText("Location (e.g., us-central1)");

        TextField geminiModelField = new TextField(config.getGeminiModel());
        geminiModelField.setPromptText("Model (default: gemini-1.5-pro)");

        Label ollamaLabel = new Label("Ollama");
        ollamaLabel.getStyleClass().add("section-label");

        TextField ollamaBaseUrlField = new TextField(config.getOllamaBaseUrl());
        ollamaBaseUrlField.setPromptText("Base URL (default: http://localhost:11434)");

        TextField ollamaModelField = new TextField(config.getOllamaModel());
        ollamaModelField.setPromptText("Model (e.g., llama3)");

        Label claudeLabel = new Label("Claude");
        claudeLabel.getStyleClass().add("section-label");

        PasswordField claudeApiKeyField = new PasswordField();
        claudeApiKeyField.setText(config.getClaudeApiKey());
        claudeApiKeyField.setPromptText("API Key (starts with sk-...)");

        TextField claudeBaseUrlField = new TextField(config.getClaudeBaseUrl());
        claudeBaseUrlField.setPromptText("Base URL (default: https://api.anthropic.com)");

        TextField claudeModelField = new TextField(config.getClaudeModel());
        claudeModelField.setPromptText("Model (e.g., claude-sonnet-4-20250514)");

        TextField claudeVersionField = new TextField(config.getClaudeVersion());
        claudeVersionField.setPromptText("API Version (default: 2023-06-01)");

        TextField claudeMaxTokensField = new TextField(Integer.toString(config.getClaudeMaxTokens()));
        claudeMaxTokensField.setPromptText("Max tokens (default: 1024)");

        Button saveButton = new Button("Save & Use");
        saveButton.getStyleClass().add("primary-button");

        saveButton.setOnAction(event -> {
            AppConfig.BackendType backendType = backendChoice.getValue();
            if (backendType == null) {
                statusLabel.setText("Select an AI backend before saving.");
                return;
            }

            config.setBackendType(backendType);
            config.setGeminiProjectId(geminiProjectField.getText());
            config.setGeminiLocation(geminiLocationField.getText());
            config.setGeminiModel(geminiModelField.getText());
            config.setOllamaBaseUrl(ollamaBaseUrlField.getText());
            config.setOllamaModel(ollamaModelField.getText());
            config.setClaudeApiKey(claudeApiKeyField.getText());
            config.setClaudeBaseUrl(claudeBaseUrlField.getText());
            config.setClaudeModel(claudeModelField.getText());
            config.setClaudeVersion(claudeVersionField.getText());
            config.setClaudeMaxTokens(parseIntOrFallback(claudeMaxTokensField.getText(), config.getClaudeMaxTokens()));

            String validationError = validateConfig(config);
            if (validationError != null) {
                statusLabel.setText(validationError);
                return;
            }

            try {
                aiBackend.set(buildBackend(config));
                config.save(AppConfig.defaultPath());
                statusLabel.setText("AI settings saved. Active backend: " + backendType.name().toLowerCase() + ".");
            } catch (RuntimeException e) {
                statusLabel.setText("Failed to initialize backend: " + e.getMessage());
            }
        });

        VBox settingsPanel = new VBox(
            12,
            title,
            subtitle,
            backendLabel,
            backendChoice,
            new Separator(),
            geminiLabel,
            geminiProjectField,
            geminiLocationField,
            geminiModelField,
            new Separator(),
            ollamaLabel,
            ollamaBaseUrlField,
            ollamaModelField,
            new Separator(),
            claudeLabel,
            claudeApiKeyField,
            claudeBaseUrlField,
            claudeModelField,
            claudeVersionField,
            claudeMaxTokensField,
            saveButton
        );
        settingsPanel.getStyleClass().add("main-panel");
        settingsPanel.setMaxWidth(760);
        settingsPanel.setPadding(new Insets(24));

        return settingsPanel;
    }

    private AIIO buildBackend(AppConfig config) {
        if (config.getBackendType() == AppConfig.BackendType.OLLAMA) {
            return new OllamaIO(config.getOllamaBaseUrl(), config.getOllamaModel());
        }
        if (config.getBackendType() == AppConfig.BackendType.CLAUDE) {
            return new ClaudeIO(
                config.getClaudeApiKey(),
                config.getClaudeBaseUrl(),
                config.getClaudeModel(),
                config.getClaudeVersion(),
                config.getClaudeMaxTokens()
            );
        }
        return new GeminiIO(config.getGeminiProjectId(), config.getGeminiLocation(), config.getGeminiModel());
    }

    private String validateConfig(AppConfig config) {
        if (config.getBackendType() == AppConfig.BackendType.GEMINI) {
            if (config.getGeminiProjectId().isBlank()) {
                return "Gemini project ID is required.";
            }
            if (config.getGeminiLocation().isBlank()) {
                return "Gemini location is required.";
            }
        }
        if (config.getBackendType() == AppConfig.BackendType.OLLAMA) {
            if (config.getOllamaBaseUrl().isBlank()) {
                return "Ollama base URL is required.";
            }
            if (config.getOllamaModel().isBlank()) {
                return "Ollama model name is required.";
            }
        }
        if (config.getBackendType() == AppConfig.BackendType.CLAUDE) {
            if (config.getClaudeApiKey().isBlank()) {
                return "Claude API key is required.";
            }
            if (config.getClaudeBaseUrl().isBlank()) {
                return "Claude base URL is required.";
            }
            if (config.getClaudeModel().isBlank()) {
                return "Claude model name is required.";
            }
            if (config.getClaudeVersion().isBlank()) {
                return "Claude API version is required.";
            }
            if (config.getClaudeMaxTokens() <= 0) {
                return "Claude max tokens must be greater than 0.";
            }
        }
        return null;
    }

    private int parseIntOrFallback(String raw, int fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
