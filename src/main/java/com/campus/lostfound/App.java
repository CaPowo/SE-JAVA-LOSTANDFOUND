package com.campus.lostfound;

import com.campus.lostfound.db.Database;
import com.campus.lostfound.service.UserService;
import com.campus.lostfound.ui.MainView;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * JavaFX 应用主类:先显示登录界面,登录成功后进入管理主界面。
 */
public class App extends Application {

    private final UserService userService = new UserService();

    @Override
    public void start(Stage stage) {
        // 启动时初始化数据库(建表)
        Database.init();
        userService.initDefaultAdmin();
        showLogin(stage);
    }

    /** 登录界面。 */
    private void showLogin(Stage stage) {
        Label title = new Label("校园失物招领系统");
        title.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");

        TextField userField = new TextField();
        userField.setPromptText("用户名");
        PasswordField pwdField = new PasswordField();
        pwdField.setPromptText("密码");
        Label hint = new Label("默认账号:admin / 123456");
        hint.setStyle("-fx-text-fill: #888;");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(12);
        grid.addRow(0, new Label("用户名:"), userField);
        grid.addRow(1, new Label("密  码:"), pwdField);
        grid.setAlignment(Pos.CENTER);

        Button loginBtn = new Button("登录");
        loginBtn.setPrefWidth(120);
        loginBtn.setDefaultButton(true);
        loginBtn.setOnAction(e -> {
            String username = userField.getText() == null ? "" : userField.getText().trim();
            if (userService.login(username, pwdField.getText())) {
                showMain(stage, username);
            } else {
                Alert alert = new Alert(Alert.AlertType.WARNING, "用户名或密码错误!");
                alert.setHeaderText(null);
                alert.setTitle("登录失败");
                alert.showAndWait();
            }
        });

        VBox root = new VBox(18, title, grid, loginBtn, hint);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(30));

        stage.setTitle("登录 - 校园失物招领系统");
        stage.setScene(new Scene(root, 380, 320));
        stage.show();
    }

    /** 进入管理主界面。 */
    private void showMain(Stage stage, String username) {
        MainView mainView = new MainView(username);
        stage.setTitle("校园失物招领系统 - 管理主界面");
        stage.setScene(new Scene(mainView, 1000, 640));
        stage.centerOnScreen();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
