package cole.oen.cpoels.coekl;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
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
import cole.oen.cpoels.coekl.AIS.OllamaIO;

public class App extends Application {
    @Override
    public void start(Stage stage) {
        AIIO aiBackend = new OllamaIO("http://localhost:11434", "llama3");
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
            if (selectedFilesView.getItems().isEmpty()) {
                statusLabel.setText("Upload at least one file to generate a plan.");
                return;
            }
            if (promptInput.getText().isBlank()) {
                statusLabel.setText("Enter an AI instruction before generating a plan.");
                return;
            }
            String[] filePaths = selectedFilesView.getItems().toArray(String[]::new);
            generatePlanButton.setDisable(true);
            applyButton.setDisable(true);
            statusLabel.setText("Generating plan with local LLM...");
            CompletableFuture
                .supplyAsync(() -> aiBackend.getTransformationPlan(promptInput.getText(), filePaths))
                .whenComplete((plan, error) -> Platform.runLater(() -> {
                    generatePlanButton.setDisable(false);
                    applyButton.setDisable(false);
                    if (error != null) {
                        statusLabel.setText("Plan failed: " + error.getMessage());
                        return;
                    }
                    planRef.set(plan);
                    statusLabel.setText("Plan generated. Click Apply Changes to write updates.");
                }));
        });

        applyButton.setOnAction(event -> {
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
            generatePlanButton.setDisable(true);
            applyButton.setDisable(true);
            statusLabel.setText("Applying changes with local LLM...");
            CompletableFuture
                .supplyAsync(() -> aiBackend.applyTransformationPlan(plan, filePaths))
                .whenComplete((updatedFiles, error) -> Platform.runLater(() -> {
                    generatePlanButton.setDisable(false);
                    applyButton.setDisable(false);
                    if (error != null) {
                        statusLabel.setText("Apply failed: " + error.getMessage());
                        return;
                    }
                    aiBackend.writeFiles(updatedFiles, filePaths);
                    statusLabel.setText("Changes applied to " + filePaths.length + " file(s).");
                }));
        });

        VBox root = new VBox(panel);
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

    public static void main(String[] args) {
        launch(args);
    }
}
