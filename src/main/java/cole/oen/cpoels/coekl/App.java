package cole.oen.cpoels.coekl;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class App extends Application {
    @Override
    public void start(Stage stage) {
        Label cardTitle = new Label("Hover Box");
        cardTitle.getStyleClass().add("hover-title");

        Label cardBody = new Label("This box works like a div container.\nPut labels, buttons, inputs, and layouts inside it.");
        cardBody.getStyleClass().add("hover-body");

        Button cardButton = new Button("Inside Box Button");
        cardButton.getStyleClass().add("card-button");

        VBox hoverBox = new VBox(10, cardTitle, cardBody, cardButton);
        hoverBox.getStyleClass().add("hover-box");
        hoverBox.setMaxWidth(360);

        Button actionButton = new Button("Click Me");
        actionButton.getStyleClass().add("bottom-button");

        StackPane root = new StackPane(hoverBox, actionButton);
        root.getStyleClass().add("app-root");
        StackPane.setAlignment(actionButton, Pos.BOTTOM_CENTER);
        StackPane.setMargin(actionButton, new Insets(0, 0, 24, 0));

        Scene scene = new Scene(root, 640, 400);
        scene.getStylesheets().add(
            App.class.getResource("/styles.css").toExternalForm()
        );

        stage.setTitle("test application");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
