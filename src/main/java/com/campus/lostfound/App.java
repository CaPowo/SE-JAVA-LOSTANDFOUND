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
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.Optional;

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
            String password = pwdField.getText();
            if (userService.login(username, password)) {
                if (userService.isDefaultPassword(password)
                        && !forceChangeDefaultPassword(username, password)) {
                    return;
                }
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

    private boolean forceChangeDefaultPassword(String username, String currentPassword) {
        while (true) {
            Dialog<ButtonType> dialog = new Dialog<>();
            dialog.setTitle("修改默认密码");
            dialog.setHeaderText("首次使用默认密码登录,请先修改管理员密码。");

            PasswordField newPwd = new PasswordField();
            newPwd.setPromptText("新密码,至少 6 位");
            PasswordField confirmPwd = new PasswordField();
            confirmPwd.setPromptText("再次输入新密码");

            GridPane grid = new GridPane();
            grid.setHgap(10);
            grid.setVgap(12);
            grid.addRow(0, new Label("新密码:"), newPwd);
            grid.addRow(1, new Label("确认密码:"), confirmPwd);
            dialog.getDialogPane().setContent(grid);
            dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

            Optional<ButtonType> result = dialog.showAndWait();
            if (result.isEmpty() || result.get() != ButtonType.OK) {
                warn("必须修改默认密码后才能进入系统。");
                return false;
            }
            if (!newPwd.getText().equals(confirmPwd.getText())) {
                warn("两次输入的新密码不一致。");
                continue;
            }
            try {
                userService.changePassword(username, currentPassword, newPwd.getText());
                info("密码修改成功,请使用新密码继续管理。");
                return true;
            } catch (RuntimeException e) {
                warn(e.getMessage());
            }
        }
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

    private void info(String msg) {
        show(Alert.AlertType.INFORMATION, "提示", msg);
    }

    private void warn(String msg) {
        show(Alert.AlertType.WARNING, "注意", msg);
    }

    private void show(Alert.AlertType type, String title, String msg) {
        Alert alert = new Alert(type, msg);
        alert.setHeaderText(null);
        alert.setTitle(title);
        alert.showAndWait();
    }
}
