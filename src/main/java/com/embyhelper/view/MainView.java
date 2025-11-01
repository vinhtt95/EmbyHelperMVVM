package com.embyhelper.view;

import com.embyhelper.di.ServiceLocator; // Sẽ không dùng để lấy VM
import com.embyhelper.model.TagModel;
import com.embyhelper.usecase.metadata_strategy.IMetadataStrategy;
import com.embyhelper.view.controls.TagView;
import com.embyhelper.viewmodel.BatchViewModel;
import com.embyhelper.viewmodel.MainViewModel;
import com.embyhelper.viewmodel.MetadataViewModel;
import embyclient.model.BaseItemDto;
import java.io.File;
import java.util.Optional;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.stage.DirectoryChooser;
import javafx.util.StringConverter;
import embyclient.model.BaseItemDto;
import javafx.scene.control.cell.PropertyValueFactory;

/**
 * Lớp Code-Behind cho MainView.fxml.
 * SỬA LỖI: Lớp này sẽ không tự tạo ViewModel.
 * Thay vào đó, nó nhận ViewModel thông qua setViewModel().
 */
public class MainView {

    // --- ViewModels (Sẽ được tiêm vào) ---
    private MainViewModel mainViewModel;
    private MetadataViewModel metadataViewModel;
    private BatchViewModel batchViewModel;

    // --- FXML Controls (Chung) ---
    @FXML private Label statusLabel;
    @FXML private ProgressIndicator statusIndicator;
    @FXML private HBox statusBar; // Dùng để lấy Scene/Window cho Dialog

    // --- FXML Controls (Tab Metadata) ---
    @FXML private ComboBox<IMetadataStrategy> typeComboBox;
    @FXML private TitledPane copyPane;
    @FXML private TextField txtCopyFromItemID;
    @FXML private TextField txtCopyToParentID;
    @FXML private TitledPane clearParentPane;
    @FXML private Label clearParentLabel;
    @FXML private TextField txtClearByParentID;
    @FXML private Button btnRunClearParent;
    @FXML private TitledPane clearSpecificPane;
    @FXML private Label clearSpecificLabel;
    @FXML private TextField searchField;
    @FXML private FlowPane selectionFlowPane;
    @FXML private Button btnRunClearSpecific;
    @FXML private Button btnRunUpdateSpecific;
    private final ToggleGroup selectionToggleGroup = new ToggleGroup();

    // --- FXML Controls (Tab Batch) ---
    @FXML private TextField txtExportJsonParentID;
    @FXML private TextField txtBatchProcessParentID;
    @FXML private TextField txtImportJsonParentID;
    @FXML private TextField txtCopySourceParentID;
    @FXML private Button btnFindSourceItems;
    @FXML private TableView<BaseItemDto> sourceItemsTableView;
    @FXML private TableColumn<BaseItemDto, String> colSourceId;
    @FXML private TableColumn<BaseItemDto, String> colSourceOriginalTitle;
    @FXML private TableColumn<BaseItemDto, String> colSourceImages;
    @FXML private TableColumn<BaseItemDto, String> colSourcePath;
    @FXML private TableView<BaseItemDto> destinationItemsTableView;

    @FXML private TableColumn<BaseItemDto, String> colDestName;
    @FXML private TableColumn<BaseItemDto, String> colDestOriginalTitle;
    @FXML private TableColumn<BaseItemDto, String> colDestId;
    @FXML private TableColumn<BaseItemDto, String> colDestImages;
    @FXML private TableColumn<BaseItemDto, String> colDestPath;
    @FXML private Button btnRunCopyMetadata;

    /**
     * SỬA LỖI: Hàm initialize() bây giờ rỗng.
     * Toàn bộ logic binding được chuyển sang setViewModel().
     */
    @FXML
    public void initialize() {
        // KHÔNG LÀM GÌ CẢ.
        // Logic sẽ chạy khi setViewModel() được gọi.
    }

    /**
     * SỬA LỖI: Đây là phương thức DI (Injection) mới.
     * NavigationService sẽ gọi hàm này sau khi FXML được tải.
     */
    public void setViewModel(MainViewModel mainViewModel) {
        this.mainViewModel = mainViewModel;
        this.metadataViewModel = mainViewModel.getMetadataViewModel();
        this.batchViewModel = mainViewModel.getBatchViewModel();

        // 2. Binding trạng thái chung
        BooleanProperty metadataLoading = metadataViewModel.isLoadingProperty();
        BooleanProperty batchLoading = batchViewModel.isLoadingProperty();
        statusIndicator.visibleProperty().bind(metadataLoading.or(batchLoading));

        statusLabel.textProperty().bind(
                batchLoading.flatMap(
                        (loading) -> loading ? batchViewModel.statusTextProperty() : metadataViewModel.statusTextProperty()
                )
        );

        // 3. Khởi tạo và Binding Tab Metadata
        initializeMetadataTab();

        // 4. Khởi tạo và Binding Tab Batch
        initializeBatchTab();
    }

    // --- Khởi tạo Tab Metadata ---
    private void initializeMetadataTab() {
        // --- ComboBox (Loại Metadata) ---
        typeComboBox.setItems(metadataViewModel.metadataTypes);
        typeComboBox.valueProperty().bindBidirectional(metadataViewModel.selectedStrategyProperty());

        typeComboBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(IMetadataStrategy strategy) {
                return (strategy != null) ? strategy.getPluralName(mainViewModel.getBundle()) : "";
            }
            @Override
            public IMetadataStrategy fromString(String string) { return null; }
        });

        // --- Copy Pane ---
        copyPane.textProperty().bind(metadataViewModel.copyPaneTitleProperty());
        txtCopyFromItemID.textProperty().bindBidirectional(metadataViewModel.copyFromIdProperty());
        txtCopyToParentID.textProperty().bindBidirectional(metadataViewModel.copyToParentIdProperty());

        // --- Clear Parent Pane ---
        clearParentPane.textProperty().bind(metadataViewModel.clearParentPaneTitleProperty());
        clearParentLabel.textProperty().bind(metadataViewModel.clearParentLabelProperty());
        txtClearByParentID.textProperty().bindBidirectional(metadataViewModel.clearParentIdProperty());
        btnRunClearParent.textProperty().bind(metadataViewModel.clearParentButtonProperty());

        // --- Clear Specific Pane ---
        clearSpecificPane.textProperty().bind(metadataViewModel.clearSpecificPaneTitleProperty());
        clearSpecificLabel.textProperty().bind(metadataViewModel.clearSpecificLabelProperty());
        searchField.textProperty().bindBidirectional(metadataViewModel.filterTextProperty());
        btnRunClearSpecific.textProperty().bind(metadataViewModel.clearSpecificButtonProperty());
        btnRunUpdateSpecific.textProperty().bind(metadataViewModel.updateSpecificButtonProperty());

        btnRunClearSpecific.disableProperty().bind(metadataViewModel.selectedItemProperty().isNull());
        btnRunUpdateSpecific.disableProperty().bind(metadataViewModel.selectedItemProperty().isNull());

        // --- FlowPane (Danh sách items) ---
        metadataViewModel.getFilteredItemsList().addListener((javafx.collections.ListChangeListener.Change<? extends TagModel> c) -> {
            populateFlowPane();
        });
        selectionToggleGroup.selectedToggleProperty().addListener((obs, oldToggle, newToggle) -> {
            if (newToggle != null) {
                metadataViewModel.selectedItemProperty().set((TagModel) newToggle.getUserData());
            } else {
                metadataViewModel.selectedItemProperty().set(null);
            }
        });

        populateFlowPane(); // Tải lần đầu (sẽ rỗng, vì VM chưa load)
    }

    // --- Khởi tạo Tab Batch ---
    private void initializeBatchTab() {
        txtExportJsonParentID.textProperty().bindBidirectional(batchViewModel.exportParentIdProperty());
        txtBatchProcessParentID.textProperty().bindBidirectional(batchViewModel.batchProcessParentIdProperty());
        txtImportJsonParentID.textProperty().bindBidirectional(batchViewModel.importParentIdProperty());

        txtCopySourceParentID.textProperty().bindBidirectional(batchViewModel.copySourceParentIdProperty());

        // 1. Source List View
        sourceItemsTableView.setItems(batchViewModel.getSourceItemsList());
        // Bind item được chọn (View -> ViewModel)
        sourceItemsTableView.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldVal, newVal) -> batchViewModel.selectedSourceItemProperty().set(newVal)
        );
        colSourceId.setCellValueFactory(new PropertyValueFactory<>("Id"));
        colSourceOriginalTitle.setCellValueFactory(new PropertyValueFactory<>("OriginalTitle"));
        colSourcePath.setCellValueFactory(new PropertyValueFactory<>("Path"));
        // Cột tùy chỉnh cho số lượng ảnh
        colSourceImages.setCellValueFactory(cellData -> {
            BaseItemDto item = cellData.getValue();
            if (item == null) return new SimpleStringProperty("-");
            int primary = (item.getImageTags() != null && item.getImageTags().containsKey("Primary")) ? 1 : 0;
            int backdrops = (item.getBackdropImageTags() != null) ? item.getBackdropImageTags().size() : 0;
            return new SimpleStringProperty(primary + " / " + backdrops);
        });

        // 2. Destination Table View
        destinationItemsTableView.setItems(batchViewModel.getDestinationItemsList());
        // Bind item được chọn (View -> ViewModel)
        destinationItemsTableView.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldVal, newVal) -> batchViewModel.selectedDestinationItemProperty().set(newVal)
        );
        // Thiết lập CellValueFactory
        colDestId.setCellValueFactory(new PropertyValueFactory<>("Id"));
        colDestOriginalTitle.setCellValueFactory(new PropertyValueFactory<>("OriginalTitle"));
        colDestPath.setCellValueFactory(new PropertyValueFactory<>("Path"));
        // Cột tùy chỉnh cho số lượng ảnh
        colDestImages.setCellValueFactory(cellData -> {
            BaseItemDto item = cellData.getValue();
            if (item == null) return new SimpleStringProperty("-");
            int primary = (item.getImageTags() != null && item.getImageTags().containsKey("Primary")) ? 1 : 0;
            int backdrops = (item.getBackdropImageTags() != null) ? item.getBackdropImageTags().size() : 0;
            return new SimpleStringProperty(primary + " / " + backdrops);
        });

        // 3. Button "Copy"
        // Vô hiệu hóa nếu chưa chọn đủ 2 item
        btnRunCopyMetadata.disableProperty().bind(
                batchViewModel.selectedSourceItemProperty().isNull()
                        .or(batchViewModel.selectedDestinationItemProperty().isNull())
        );
    }

    private void populateFlowPane() {
        selectionFlowPane.getChildren().clear();
        selectionToggleGroup.getToggles().clear();

        TagModel selectedVMItem = metadataViewModel.selectedItemProperty().get();

        for (TagModel tagModel : metadataViewModel.getFilteredItemsList()) {
            ToggleButton chipButton = new ToggleButton();
            chipButton.setUserData(tagModel);
            chipButton.setToggleGroup(selectionToggleGroup);

            TagView tagView = new TagView(tagModel);
            tagView.getDeleteButton().setVisible(false);
            tagView.getDeleteButton().setManaged(false);

            chipButton.setGraphic(tagView);
            chipButton.getStyleClass().add("chip-toggle-button");
            chipButton.getStyleClass().add(tagModel.isJson() ? "tag-view-json" : "tag-view-simple");

            if (tagModel.equals(selectedVMItem)) {
                chipButton.setSelected(true);
            }

            selectionFlowPane.getChildren().add(chipButton);
        }
    }

    // --- Handlers (View gọi Command trên ViewModel) ---

    // Menu
    @FXML private void onLogoutClick() { mainViewModel.logout(); }
    @FXML private void onSwitchLanguageVI() { mainViewModel.switchLanguage("vi"); }
    @FXML private void onSwitchLanguageEN() { mainViewModel.switchLanguage("en"); }

    // Metadata Tab
    @FXML private void onRunCopyClick() { metadataViewModel.copyCommand(); }
    @FXML private void onRunClearParentClick() { metadataViewModel.clearByParentCommand(); }
    @FXML private void onReloadListClick() { metadataViewModel.loadItems(); }
    @FXML private void onRunClearSpecificClick() { metadataViewModel.clearSpecificCommand(); }

    @FXML
    private void onRunUpdateSpecificClick() {
        Optional<TagModel> result = metadataViewModel.showEditTagDialog(
                statusBar.getScene().getStylesheets().isEmpty() ? null : statusBar.getScene().getStylesheets().get(0)
        );
        result.ifPresent(tagModel -> metadataViewModel.updateSpecificCommand(tagModel));
    }

    // Batch Tab
    @FXML
    private void onRunBatchProcessClick() {
        batchViewModel.runBatchProcess();
    }

    @FXML
    private void onFindSourceItemsClick() {
        batchViewModel.findSourceItems();
    }

    @FXML
    private void onRunCopyMetadataClick() {
        batchViewModel.copyMetadataToSelected();
    }

    @FXML
    private void onRunExportJsonClick() {
        File dir = showDirectoryChooser(mainViewModel.getBundle().getString("chooser.exportJson.title"));
        if (dir != null) {
            batchViewModel.runExportJson(dir);
        }
    }

    @FXML
    private void onRunImportJsonClick() {
        File dir = showDirectoryChooser(mainViewModel.getBundle().getString("chooser.importJson.title"));
        if (dir != null) {
            batchViewModel.runImportJson(dir);
        }
    }

    private File showDirectoryChooser(String title) {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle(title);
        return directoryChooser.showDialog(statusBar.getScene().getWindow());
    }
}