package com.campus.lostfound.ui;

import com.campus.lostfound.model.ClaimRecord;
import com.campus.lostfound.model.ItemStatus;
import com.campus.lostfound.model.LostItem;
import com.campus.lostfound.service.LostFoundService;
import com.campus.lostfound.util.ImageStorage;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * 管理主界面:左侧失物列表 + 顶部查询栏 + 右侧录入/编辑表单。
 */
public class MainView extends BorderPane {

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final LostFoundService service = new LostFoundService();
    private final String operator;
    private final ObservableList<LostItem> data = FXCollections.observableArrayList();
    private final TableView<LostItem> table = new TableView<>();

    // 查询条件控件
    private final TextField searchName = new TextField();
    private final TextField searchCategory = new TextField();
    private final TextField searchLocation = new TextField();
    private final ComboBox<String> searchStatus = new ComboBox<>();

    // 录入/编辑表单控件
    private final TextField fName = new TextField();
    private final TextField fCategory = new TextField();
    private final TextField fLocation = new TextField();
    private final TextField fFoundTime = new TextField();
    private final TextField fFinderContact = new TextField();
    private final TextArea fDescription = new TextArea();
    private final ComboBox<ItemStatus> fStatus = new ComboBox<>();
    private final ImageView imagePreview = new ImageView();
    private final Label imageHint = new Label("未选择图片");

    /** 当前正在编辑的记录;为 null 表示处于"新增"状态。 */
    private LostItem editing = null;
    /** 新选择但还未保存的图片。保存时会复制到 data/photos/<物品UUID>.<扩展名>。 */
    private Path selectedImageSource = null;
    /** 当前表单里的图片相对路径。 */
    private String currentImagePath = "";

    public MainView(String operator) {
        this.operator = isBlank(operator) ? "admin" : operator.trim();
        setPadding(new Insets(10));
        setTop(buildSearchBar());
        setCenter(buildTable());
        setRight(buildForm());
        reload();
    }

    public MainView() {
        this("admin");
    }

    // ---------------- 顶部查询栏 ----------------

    private HBox buildSearchBar() {
        searchName.setPromptText("名称");
        searchCategory.setPromptText("类别");
        searchLocation.setPromptText("地点");
        searchStatus.getItems().add("全部");
        for (ItemStatus s : ItemStatus.values()) {
            searchStatus.getItems().add(s.getLabel());
        }
        searchStatus.getSelectionModel().selectFirst();

        Button queryBtn = new Button("查询");
        queryBtn.setOnAction(e -> doQuery());
        Button resetBtn = new Button("重置");
        resetBtn.setOnAction(e -> {
            searchName.clear();
            searchCategory.clear();
            searchLocation.clear();
            searchStatus.getSelectionModel().selectFirst();
            reload();
        });

        HBox box = new HBox(8,
                new Label("名称:"), searchName,
                new Label("类别:"), searchCategory,
                new Label("地点:"), searchLocation,
                new Label("状态:"), searchStatus,
                queryBtn, resetBtn);
        box.setAlignment(Pos.CENTER_LEFT);
        box.setPadding(new Insets(0, 0, 10, 0));
        return box;
    }

    // ---------------- 中间列表 ----------------

    private TableView<LostItem> buildTable() {
        table.setItems(data);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        table.getColumns().add(col("编号", "id", 120));
        table.getColumns().add(col("名称", "name", 90));
        table.getColumns().add(col("类别", "category", 70));
        table.getColumns().add(col("拾取地点", "location", 90));
        table.getColumns().add(col("拾取时间", "foundTime", 130));
        table.getColumns().add(col("拾取人/联系方式", "finderContact", 110));
        table.getColumns().add(col("状态", "status", 70));
        table.getColumns().add(col("认领人", "claimer", 80));
        table.getColumns().add(col("认领时间", "claimTime", 130));

        // 选中某行 -> 把内容填入右侧表单进行编辑
        table.getSelectionModel().selectedItemProperty().addListener((obs, old, sel) -> {
            if (sel != null) {
                fillForm(sel);
            }
        });

        BorderPane.setMargin(table, new Insets(0, 10, 0, 0));
        return table;
    }

    private <T> TableColumn<LostItem, T> col(String title, String prop, double width) {
        TableColumn<LostItem, T> c = new TableColumn<>(title);
        c.setCellValueFactory(new PropertyValueFactory<>(prop));
        c.setPrefWidth(width);
        return c;
    }

    // ---------------- 右侧表单 ----------------

    private VBox buildForm() {
        fDescription.setPrefRowCount(3);
        fDescription.setWrapText(true);
        fStatus.getItems().addAll(ItemStatus.values());
        fStatus.getSelectionModel().select(ItemStatus.PENDING);

        imagePreview.setFitWidth(240);
        imagePreview.setFitHeight(150);
        imagePreview.setPreserveRatio(true);
        imagePreview.setSmooth(true);
        imagePreview.setStyle("-fx-border-color:#ccc; -fx-border-width:1; -fx-background-color:#f7f7f7;");
        imageHint.setStyle("-fx-text-fill:#777; -fx-font-size:11px;");

        Button chooseImageBtn = new Button("选择图片");
        chooseImageBtn.setOnAction(e -> chooseImage());
        Button clearImageBtn = new Button("清除图片");
        clearImageBtn.setOnAction(e -> clearImage());
        HBox imageButtons = new HBox(8, chooseImageBtn, clearImageBtn);
        VBox imageBox = new VBox(6, imagePreview, imageHint, imageButtons);

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        int r = 0;
        grid.addRow(r++, new Label("物品名称*:"), fName);
        grid.addRow(r++, new Label("类  别:"), fCategory);
        grid.addRow(r++, new Label("拾取地点:"), fLocation);
        grid.addRow(r++, new Label("拾取时间:"), fFoundTime);
        grid.addRow(r++, new Label("拾取人/联系:"), fFinderContact);
        grid.addRow(r++, new Label("当前状态:"), fStatus);
        grid.add(new Label("物品描述:"), 0, r);
        grid.add(fDescription, 1, r++);
        grid.add(new Label("图片预览:"), 0, r);
        grid.add(imageBox, 1, r);

        for (TextField field : new TextField[]{
                fName, fCategory, fLocation, fFoundTime, fFinderContact
        }) {
            GridPane.setHgrow(field, Priority.ALWAYS);
        }
        fFoundTime.setPromptText("yyyy-MM-dd HH:mm:ss");

        Button addBtn = new Button("新增");
        addBtn.setOnAction(e -> onAdd());
        Button saveBtn = new Button("保存修改");
        saveBtn.setOnAction(e -> onSave());
        Button deleteBtn = new Button("删除");
        deleteBtn.setOnAction(e -> onDelete());
        Button claimBtn = new Button("办理认领");
        claimBtn.setOnAction(e -> onClaim());
        Button historyBtn = new Button("认领历史");
        historyBtn.setOnAction(e -> showClaimHistory());
        Button clearBtn = new Button("清空表单");
        clearBtn.setOnAction(e -> clearForm());

        for (Button b : new Button[]{addBtn, saveBtn, deleteBtn, claimBtn, historyBtn, clearBtn}) {
            b.setMaxWidth(Double.MAX_VALUE);
        }
        VBox buttons = new VBox(8, addBtn, saveBtn, deleteBtn, claimBtn, historyBtn, clearBtn);

        Label tip = new Label("提示:点击列表某行可载入编辑;\n带 * 为必填项。");
        tip.setStyle("-fx-text-fill:#888; -fx-font-size:11px;");

        VBox box = new VBox(12, new Label("失物信息录入 / 编辑"), grid, buttons, tip);
        box.setPadding(new Insets(0, 0, 0, 10));
        box.setPrefWidth(340);
        VBox.setVgrow(grid, Priority.ALWAYS);
        return box;
    }

    // ---------------- 业务逻辑 ----------------

    /** 加载全部数据。 */
    private void reload() {
        try {
            List<LostItem> list = service.findAll();
            data.setAll(list);
        } catch (RuntimeException e) {
            error("加载数据失败", e);
        }
    }

    /** 按查询条件筛选。 */
    private void doQuery() {
        try {
            String statusText = searchStatus.getValue();
            ItemStatus status = (statusText == null || "全部".equals(statusText))
                    ? null : ItemStatus.fromLabel(statusText);
            List<LostItem> list = service.query(
                    searchName.getText(), searchCategory.getText(),
                    searchLocation.getText(), status);
            data.setAll(list);
        } catch (RuntimeException e) {
            error("查询失败", e);
        }
    }

    /** 新增记录。 */
    private void onAdd() {
        if (!validate()) {
            return;
        }
        LostItem item = new LostItem();
        readForm(item);
        try {
            service.create(item, selectedImageSource);
            reload();
            clearForm();
            info("新增成功!");
        } catch (RuntimeException e) {
            error("新增失败", e);
        }
    }

    /** 保存对选中记录的修改。 */
    private void onSave() {
        if (editing == null) {
            warn("请先在列表中选择要修改的记录。");
            return;
        }
        if (!validate()) {
            return;
        }
        readForm(editing);
        try {
            service.update(editing, selectedImageSource);
            reload();
            info("修改已保存!");
        } catch (RuntimeException e) {
            error("修改失败", e);
        }
    }

    /** 删除选中记录。 */
    private void onDelete() {
        LostItem sel = table.getSelectionModel().getSelectedItem();
        if (sel == null) {
            warn("请先在列表中选择要删除的记录。");
            return;
        }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "确定删除《" + sel.getName() + "》这条记录吗?");
        confirm.setHeaderText(null);
        confirm.setTitle("确认删除");
        Optional<ButtonType> res = confirm.showAndWait();
        if (res.isPresent() && res.get() == ButtonType.OK) {
            try {
                service.delete(sel.getId());
                reload();
                clearForm();
                info("删除成功!");
            } catch (RuntimeException e) {
                error("删除失败", e);
            }
        }
    }

    /** 办理认领登记:输入认领人和联系方式,自动记录认领时间。 */
    private void onClaim() {
        LostItem sel = table.getSelectionModel().getSelectedItem();
        if (sel == null) {
            warn("请先在列表中选择要认领的记录。");
            return;
        }

        TextField claimerField = new TextField();
        claimerField.setPromptText("认领人");
        TextField contactField = new TextField();
        contactField.setPromptText("联系方式");

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        grid.addRow(0, new Label("认领人*:"), claimerField);
        grid.addRow(1, new Label("联系方式:"), contactField);

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("办理认领");
        dialog.setHeaderText("物品:" + sel.getName());
        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isEmpty() || result.get() != ButtonType.OK) {
            return;
        }
        String claimer = claimerField.getText() == null ? "" : claimerField.getText().trim();
        if (claimer.isEmpty()) {
            warn("认领人不能为空。");
            return;
        }
        try {
            service.claim(sel.getId(), claimer, contactField.getText(), operator);
            reload();
            info("认领登记成功!");
        } catch (RuntimeException e) {
            error("认领登记失败", e);
        }
    }

    /** 查看当前物品的认领历史。 */
    private void showClaimHistory() {
        LostItem sel = table.getSelectionModel().getSelectedItem();
        if (sel == null) {
            warn("请先在列表中选择要查看历史的记录。");
            return;
        }
        try {
            List<ClaimRecord> records = service.findClaimHistory(sel.getId());
            if (records.isEmpty()) {
                info("该物品暂无认领历史。");
                return;
            }

            TableView<ClaimRecord> historyTable =
                    new TableView<>(FXCollections.observableArrayList(records));
            historyTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
            historyTable.getColumns().add(historyCol("认领时间", "claimTime", 150));
            historyTable.getColumns().add(historyCol("认领人", "claimer", 100));
            historyTable.getColumns().add(historyCol("联系方式", "contact", 140));
            historyTable.getColumns().add(historyCol("经办人", "operator", 100));
            historyTable.setPrefSize(560, 280);

            Dialog<ButtonType> dialog = new Dialog<>();
            dialog.setTitle("认领历史");
            dialog.setHeaderText("物品:" + sel.getName());
            dialog.getDialogPane().setContent(historyTable);
            dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
            dialog.showAndWait();
        } catch (RuntimeException e) {
            error("查询认领历史失败", e);
        }
    }

    private TableColumn<ClaimRecord, String> historyCol(String title, String prop, double width) {
        TableColumn<ClaimRecord, String> c = new TableColumn<>(title);
        c.setCellValueFactory(new PropertyValueFactory<>(prop));
        c.setPrefWidth(width);
        return c;
    }

    // ---------------- 图片选择与预览 ----------------

    private void chooseImage() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("选择失物图片");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("图片文件", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp"),
                new FileChooser.ExtensionFilter("所有文件", "*.*")
        );
        File file = chooser.showOpenDialog(getScene().getWindow());
        if (file == null) {
            return;
        }
        selectedImageSource = file.toPath();
        showPreview(selectedImageSource);
        imageHint.setText("待保存:" + file.getName());
    }

    private void clearImage() {
        selectedImageSource = null;
        currentImagePath = "";
        imagePreview.setImage(null);
        imageHint.setText("未选择图片");
    }

    private void showPreview(Path path) {
        if (path == null || !Files.exists(path)) {
            imagePreview.setImage(null);
            imageHint.setText("图片文件不存在");
            return;
        }
        Image image = new Image(path.toUri().toString(), 240, 150, true, true);
        if (image.isError()) {
            imagePreview.setImage(null);
            imageHint.setText("图片无法预览");
            return;
        }
        imagePreview.setImage(image);
    }

    private void showPreviewByRelativePath(String relativePath) {
        if (isBlank(relativePath)) {
            imagePreview.setImage(null);
            imageHint.setText("未选择图片");
            return;
        }
        Path path = ImageStorage.resolve(relativePath);
        showPreview(path);
        if (imagePreview.getImage() != null) {
            imageHint.setText(relativePath);
        }
    }

    // ---------------- 表单与数据互转 ----------------

    private void fillForm(LostItem item) {
        editing = item;
        selectedImageSource = null;
        currentImagePath = item.getImagePath();
        fName.setText(item.getName());
        fCategory.setText(item.getCategory());
        fLocation.setText(item.getLocation());
        fFoundTime.setText(item.getFoundTime());
        fFinderContact.setText(item.getFinderContact());
        fDescription.setText(item.getDescription());
        fStatus.getSelectionModel().select(item.getStatus());
        showPreviewByRelativePath(currentImagePath);
    }

    private void readForm(LostItem item) {
        item.setName(fName.getText().trim());
        item.setCategory(fCategory.getText().trim());
        item.setLocation(fLocation.getText().trim());
        item.setFoundTime(fFoundTime.getText().trim());
        item.setFinderContact(fFinderContact.getText().trim());
        item.setDescription(fDescription.getText().trim());
        item.setStatus(fStatus.getValue());
        item.setImagePath(currentImagePath);
    }

    private void clearForm() {
        editing = null;
        selectedImageSource = null;
        currentImagePath = "";
        fName.clear();
        fCategory.clear();
        fLocation.clear();
        fFoundTime.clear();
        fFinderContact.clear();
        fDescription.clear();
        fStatus.getSelectionModel().select(ItemStatus.PENDING);
        imagePreview.setImage(null);
        imageHint.setText("未选择图片");
        table.getSelectionModel().clearSelection();
    }

    /** 输入合法性校验。 */
    private boolean validate() {
        if (fName.getText() == null || fName.getText().trim().isEmpty()) {
            warn("物品名称不能为空。");
            return false;
        }
        String time = fFoundTime.getText().trim();
        if (!time.isEmpty() && !isValidTime(time)) {
            warn("拾取时间格式应为 yyyy-MM-dd HH:mm:ss。");
            return false;
        }
        return true;
    }

    private boolean isValidTime(String text) {
        try {
            LocalDateTime.parse(text, TIME_FMT);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // ---------------- 提示框 ----------------

    private void info(String msg) {
        show(Alert.AlertType.INFORMATION, "提示", msg);
    }

    private void warn(String msg) {
        show(Alert.AlertType.WARNING, "注意", msg);
    }

    private void error(String title, Exception e) {
        show(Alert.AlertType.ERROR, title, e.getMessage());
    }

    private void show(Alert.AlertType type, String title, String msg) {
        Alert alert = new Alert(type, msg);
        alert.setHeaderText(null);
        alert.setTitle(title);
        alert.showAndWait();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
