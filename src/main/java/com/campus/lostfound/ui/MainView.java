package com.campus.lostfound.ui;

import com.campus.lostfound.model.Category;
import com.campus.lostfound.model.ClaimRecord;
import com.campus.lostfound.model.ItemStatus;
import com.campus.lostfound.model.LostItem;
import com.campus.lostfound.service.LostFoundService;
import com.campus.lostfound.service.UserService;
import com.campus.lostfound.util.ImageStorage;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.Spinner;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;

/**
 * 管理主界面:左侧失物列表 + 顶部查询栏 + 右侧录入/编辑表单。
 */
public class MainView extends BorderPane {

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final LostFoundService service = new LostFoundService();
    private final UserService userService = new UserService();
    private final String operator;
    private final ObservableList<LostItem> data = FXCollections.observableArrayList();
    private final ObservableList<Category> categories = FXCollections.observableArrayList();
    private final ObservableList<Category> searchCategories = FXCollections.observableArrayList();
    private final TableView<LostItem> table = new TableView<>();

    // 查询条件控件
    private final TextField searchKeyword = new TextField();
    private final ComboBox<Category> searchCategory = new ComboBox<>();
    private final TextField searchLocation = new TextField();
    private final ComboBox<String> searchStatus = new ComboBox<>();

    // 录入/编辑表单控件
    private final TextField fName = new TextField();
    private final ComboBox<Category> fCategory = new ComboBox<>();
    private final TextField fLocation = new TextField();
    private final DatePicker fFoundDate = new DatePicker();
    private final Spinner<Integer> fFoundHour = new Spinner<>(0, 23, 0);
    private final Spinner<Integer> fFoundMinute = new Spinner<>(0, 59, 0);
    private final Spinner<Integer> fFoundSecond = new Spinner<>(0, 59, 0);
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
        reloadCategories();
        reload();
    }

    public MainView() {
        this("admin");
    }

    // ---------------- 顶部查询栏 ----------------

    private HBox buildSearchBar() {
        searchKeyword.setPromptText("名称/电话/UUID/描述");
        searchCategory.setPromptText("全部");
        searchCategory.setItems(searchCategories);
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
            searchKeyword.clear();
            searchCategory.getSelectionModel().selectFirst();
            searchLocation.clear();
            searchStatus.getSelectionModel().selectFirst();
            reload();
        });
        Button changePasswordBtn = new Button("修改密码");
        changePasswordBtn.setOnAction(e -> changePassword());

        HBox box = new HBox(8,
                new Label("关键词:"), searchKeyword,
                new Label("类别:"), searchCategory,
                new Label("地点:"), searchLocation,
                new Label("状态:"), searchStatus,
                queryBtn, resetBtn, changePasswordBtn);
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
        fCategory.setItems(categories);
        fCategory.setPromptText("选择类别");
        fStatus.getItems().addAll(ItemStatus.values());
        fStatus.getSelectionModel().select(ItemStatus.PENDING);
        configureTimeControls();

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
        Button addCategoryBtn = new Button("新增");
        addCategoryBtn.setOnAction(e -> addCategory());
        HBox categoryBox = new HBox(8, fCategory, addCategoryBtn);
        HBox.setHgrow(fCategory, Priority.ALWAYS);
        HBox timeParts = new HBox(8,
                timePart(fFoundHour, "时"),
                timePart(fFoundMinute, "分"),
                timePart(fFoundSecond, "秒"));
        VBox timeBox = new VBox(6, fFoundDate, timeParts);
        fFoundDate.setMaxWidth(Double.MAX_VALUE);
        VBox.setVgrow(fFoundDate, Priority.NEVER);

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        ColumnConstraints labelColumn = new ColumnConstraints();
        labelColumn.setMinWidth(92);
        labelColumn.setPrefWidth(92);
        labelColumn.setMaxWidth(92);
        ColumnConstraints inputColumn = new ColumnConstraints();
        inputColumn.setHgrow(Priority.ALWAYS);
        inputColumn.setFillWidth(true);
        grid.getColumnConstraints().addAll(labelColumn, inputColumn);
        int r = 0;
        grid.addRow(r++, formLabel("物品名称*:"), fName);
        grid.addRow(r++, formLabel("类  别:"), categoryBox);
        grid.addRow(r++, formLabel("拾取地点:"), fLocation);
        grid.addRow(r++, formLabel("拾取时间:"), timeBox);
        grid.addRow(r++, formLabel("拾取人/联系:"), fFinderContact);
        grid.addRow(r++, formLabel("当前状态:"), fStatus);
        grid.add(formLabel("物品描述:"), 0, r);
        grid.add(fDescription, 1, r++);
        grid.add(formLabel("图片预览:"), 0, r);
        grid.add(imageBox, 1, r);

        for (TextField field : new TextField[]{
                fName, fLocation, fFinderContact
        }) {
            GridPane.setHgrow(field, Priority.ALWAYS);
        }

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

    private Label formLabel(String text) {
        Label label = new Label(text);
        label.setMinWidth(92);
        label.setPrefWidth(92);
        label.setAlignment(Pos.CENTER_RIGHT);
        return label;
    }

    private HBox timePart(Spinner<Integer> spinner, String unit) {
        spinner.setEditable(true);
        spinner.setMinWidth(72);
        spinner.setPrefWidth(72);
        Label label = new Label(unit);
        label.setMinWidth(18);
        return new HBox(4, spinner, label);
    }

    private void configureTimeControls() {
        fFoundDate.setPromptText("选择日期");
        configureSpinner(fFoundHour);
        configureSpinner(fFoundMinute);
        configureSpinner(fFoundSecond);
    }

    private void configureSpinner(Spinner<Integer> spinner) {
        spinner.setEditable(true);
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

    /** 加载类别下拉数据。 */
    private void reloadCategories() {
        try {
            List<Category> list = service.findCategories();
            categories.setAll(list);

            Category all = new Category();
            all.setId("");
            all.setName("全部");
            searchCategories.setAll(all);
            searchCategories.addAll(list);
            searchCategory.getSelectionModel().selectFirst();
        } catch (RuntimeException e) {
            error("加载类别失败", e);
        }
    }

    /** 新增类别并刷新下拉框。 */
    private void addCategory() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("新增类别");
        dialog.setHeaderText(null);
        dialog.setContentText("类别名称:");
        Optional<String> result = dialog.showAndWait();
        if (result.isEmpty()) {
            return;
        }
        String name = result.get() == null ? "" : result.get().trim();
        if (name.isEmpty()) {
            warn("类别名称不能为空。");
            return;
        }
        try {
            Category category = service.createCategory(name);
            reloadCategories();
            selectCategory(fCategory, category.getId(), category.getName());
            info("类别已保存。");
        } catch (RuntimeException e) {
            error("新增类别失败", e);
        }
    }

    /** 长期修改管理员密码:必须校验当前密码。 */
    private void changePassword() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("修改密码");
        dialog.setHeaderText("当前用户:" + operator);

        PasswordField currentPwd = new PasswordField();
        currentPwd.setPromptText("当前密码");
        PasswordField newPwd = new PasswordField();
        newPwd.setPromptText("新密码,至少 6 位");
        PasswordField confirmPwd = new PasswordField();
        confirmPwd.setPromptText("再次输入新密码");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(12);
        grid.addRow(0, new Label("当前密码:"), currentPwd);
        grid.addRow(1, new Label("新密码:"), newPwd);
        grid.addRow(2, new Label("确认密码:"), confirmPwd);
        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isEmpty() || result.get() != ButtonType.OK) {
            return;
        }
        if (!newPwd.getText().equals(confirmPwd.getText())) {
            warn("两次输入的新密码不一致。");
            return;
        }
        try {
            userService.changePassword(operator, currentPwd.getText(), newPwd.getText());
            info("密码修改成功。");
        } catch (RuntimeException e) {
            warn(e.getMessage());
        }
    }

    /** 按查询条件筛选。 */
    private void doQuery() {
        try {
            String statusText = searchStatus.getValue();
            ItemStatus status = (statusText == null || "全部".equals(statusText))
                    ? null : ItemStatus.fromLabel(statusText);
            Category category = searchCategory.getSelectionModel().getSelectedItem();
            List<LostItem> list = service.query(
                    searchKeyword.getText(), category == null ? null : category.getName(),
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
        selectCategory(fCategory, item.getCategoryId(), item.getCategory());
        fLocation.setText(item.getLocation());
        setFoundTime(item.getFoundTime());
        fFinderContact.setText(item.getFinderContact());
        fDescription.setText(item.getDescription());
        fStatus.getSelectionModel().select(item.getStatus());
        showPreviewByRelativePath(currentImagePath);
    }

    private void readForm(LostItem item) {
        item.setName(fName.getText().trim());
        Category category = fCategory.getSelectionModel().getSelectedItem();
        item.setCategoryId(category == null ? "" : category.getId());
        item.setCategory(category == null ? "" : category.getName());
        item.setLocation(fLocation.getText().trim());
        item.setFoundTime(formatFoundTime());
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
        fCategory.getSelectionModel().clearSelection();
        fLocation.clear();
        clearFoundTime();
        fFinderContact.clear();
        fDescription.clear();
        fStatus.getSelectionModel().select(ItemStatus.PENDING);
        imagePreview.setImage(null);
        imageHint.setText("未选择图片");
        table.getSelectionModel().clearSelection();
    }

    private void selectCategory(ComboBox<Category> comboBox, String categoryId, String categoryName) {
        for (Category category : comboBox.getItems()) {
            boolean idMatches = !isBlank(categoryId) && categoryId.equals(category.getId());
            boolean nameMatches = !isBlank(categoryName) && categoryName.equals(category.getName());
            if (idMatches || nameMatches) {
                comboBox.getSelectionModel().select(category);
                return;
            }
        }
        comboBox.getSelectionModel().clearSelection();
    }

    private String formatFoundTime() {
        LocalDate date = fFoundDate.getValue();
        if (date == null) {
            return "";
        }
        LocalDateTime time = date.atTime(
                fFoundHour.getValue(),
                fFoundMinute.getValue(),
                fFoundSecond.getValue());
        return time.format(TIME_FMT);
    }

    private void setFoundTime(String text) {
        if (isBlank(text)) {
            clearFoundTime();
            return;
        }
        try {
            LocalDateTime time = LocalDateTime.parse(text, TIME_FMT);
            fFoundDate.setValue(time.toLocalDate());
            fFoundHour.getValueFactory().setValue(time.getHour());
            fFoundMinute.getValueFactory().setValue(time.getMinute());
            fFoundSecond.getValueFactory().setValue(time.getSecond());
        } catch (DateTimeParseException e) {
            clearFoundTime();
        }
    }

    private void clearFoundTime() {
        fFoundDate.setValue(null);
        fFoundHour.getValueFactory().setValue(0);
        fFoundMinute.getValueFactory().setValue(0);
        fFoundSecond.getValueFactory().setValue(0);
    }

    /** 输入合法性校验。 */
    private boolean validate() {
        if (fName.getText() == null || fName.getText().trim().isEmpty()) {
            warn("物品名称不能为空。");
            return false;
        }
        String time = formatFoundTime();
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
