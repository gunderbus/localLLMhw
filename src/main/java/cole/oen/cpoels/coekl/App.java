package cole.oen.cpoels.coekl;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

public class App extends Application {
    @Override
    public void start(Stage stage) {
        Label title = new Label("Hello JavaFX");
        title.getStyleClass().add("title-label");

        Button actionButton = new Button("Click Me");
        actionButton.getStyleClass().add("bottom-button");

        StackPane root = new StackPane(title, actionButton);
        root.getStyleClass().add("app-root");
        StackPane.setAlignment(actionButton, Pos.BOTTOM_CENTER);
        StackPane.setMargin(actionButton, new Insets(0, 0, 24, 0));

        Scene scene = new Scene(root, 640, 400);
        scene.getStylesheets().add(
            App.class.getResource("/styles.css").toExternalForm()
        );

        stage.setTitle("tuoeis");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
