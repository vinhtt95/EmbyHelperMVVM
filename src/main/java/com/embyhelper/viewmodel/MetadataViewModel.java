package com.embyhelper.viewmodel;

import com.embyhelper.model.MetadataType;
import com.embyhelper.model.TagModel;
import com.embyhelper.repository.IEmbyRepository;
import com.embyhelper.usecase.metadata_strategy.*;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.function.Function;
import java.util.stream.Collectors;
import javafx.application.Platform;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.concurrent.Task;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import java.util.Optional;
import javafx.geometry.Insets;

public class MetadataViewModel extends ViewModelBase {

    private final IEmbyRepository embyRepo;
    private final ResourceBundle bundle;
    private final Map<MetadataType, IMetadataStrategy> strategyMap;

    // --- Properties cho Data Binding ---
    public final ObservableList<IMetadataStrategy> metadataTypes = FXCollections.observableArrayList();
    public final ObjectProperty<IMetadataStrategy> selectedStrategy = new SimpleObjectProperty<>();

    private final ObservableList<TagModel> itemsList = FXCollections.observableArrayList();
    private final FilteredList<TagModel> filteredItemsList;
    public final StringProperty filterText = new SimpleStringProperty("");

    public final ObjectProperty<TagModel> selectedItem = new SimpleObjectProperty<>();

    public final StringProperty copyFromId = new SimpleStringProperty();
    public final StringProperty copyToParentId = new SimpleStringProperty();
    public final StringProperty clearParentId = new SimpleStringProperty();

    // --- Dynamic Text Properties ---
    public final StringProperty copyPaneTitle = new SimpleStringProperty();
    public final StringProperty clearParentPaneTitle = new SimpleStringProperty();
    public final StringProperty clearSpecificPaneTitle = new SimpleStringProperty();
    public final StringProperty clearParentLabel = new SimpleStringProperty();
    public final StringProperty clearParentButton = new SimpleStringProperty();
    public final StringProperty clearSpecificLabel = new SimpleStringProperty();
    public final StringProperty clearSpecificButton = new SimpleStringProperty();
    public final StringProperty updateSpecificButton = new SimpleStringProperty();

    // --- Getters cho Properties (cho View) ---
    public ObjectProperty<IMetadataStrategy> selectedStrategyProperty() { return selectedStrategy; }
    public StringProperty filterTextProperty() { return filterText; }
    public ObjectProperty<TagModel> selectedItemProperty() { return selectedItem; }
    public StringProperty copyFromIdProperty() { return copyFromId; }
    public StringProperty copyToParentIdProperty() { return copyToParentId; }
    public StringProperty clearParentIdProperty() { return clearParentId; }
    public StringProperty copyPaneTitleProperty() { return copyPaneTitle; }
    public StringProperty clearParentPaneTitleProperty() { return clearParentPaneTitle; }
    public StringProperty clearSpecificPaneTitleProperty() { return clearSpecificPaneTitle; }
    public StringProperty clearParentLabelProperty() { return clearParentLabel; }
    public StringProperty clearParentButtonProperty() { return clearParentButton; }
    public StringProperty clearSpecificLabelProperty() { return clearSpecificLabel; }
    public StringProperty clearSpecificButtonProperty() { return clearSpecificButton; }
    public StringProperty updateSpecificButtonProperty() { return updateSpecificButton; }


    public MetadataViewModel(IEmbyRepository embyRepo, ResourceBundle bundle) {
        this.embyRepo = embyRepo;
        this.bundle = bundle;

        // Khởi tạo và nạp các chiến lược
        List<IMetadataStrategy> strategies = List.of(
                new StudioStrategy(), new GenreStrategy(), new PeopleStrategy(), new TagStrategy()
        );
        this.strategyMap = strategies.stream()
                .collect(Collectors.toMap(IMetadataStrategy::getType, Function.identity()));

        metadataTypes.setAll(strategies);

        // Thiết lập bộ lọc
        filteredItemsList = new FilteredList<>(itemsList, p -> true);
        filterText.addListener((obs, oldV, newV) ->
                filteredItemsList.setPredicate(tagModel ->
                        newV == null || newV.isEmpty() ||
                                tagModel.getDisplayName().toLowerCase().contains(newV.toLowerCase())
                )
        );

        // Khi chiến lược thay đổi
        selectedStrategy.addListener((obs, oldS, newS) -> {
            if (newS != null) {
                loadItems();
                updateDynamicLabels(newS);
            }
        });

        selectedStrategy.set(strategies.get(0)); // Chọn Studio làm mặc định
    }

    public FilteredList<TagModel> getFilteredItemsList() {
        return filteredItemsList;
    }

    private void updateDynamicLabels(IMetadataStrategy strategy) {
        String singular = strategy.getSingularName(bundle);
        String plural = strategy.getPluralName(bundle);

        copyPaneTitle.set(String.format(bundle.getString("pane.copy.title"), plural));
        clearParentPaneTitle.set(String.format(bundle.getString("pane.clearParent.title"), plural));
        clearSpecificPaneTitle.set(String.format(bundle.getString("pane.clearSpecific.title"), plural));

        clearParentLabel.set(String.format(bundle.getString("label.clearParentDynamic"), singular));
        clearParentButton.set(String.format(bundle.getString("button.clearParentDynamic"), plural));
        clearSpecificLabel.set(String.format(bundle.getString("label.clearSpecificDynamic"), singular, singular));
        clearSpecificButton.set(String.format(bundle.getString("button.clearSpecificDynamic"), singular));
        updateSpecificButton.set(String.format(bundle.getString("button.updateSpecificDynamic"), singular));
    }

    public void loadItems() {
        IMetadataStrategy strategy = selectedStrategy.get();
        if (strategy == null) return;

        Task<String> task = new Task<>() {
            @Override
            protected String call() throws Exception {
                List<?> rawItems = strategy.loadItems(embyRepo);

                List<TagModel> tagModels = rawItems.stream()
                        .map(item -> {
                            String name = strategy.getItemName(item);
                            String id = strategy.getItemIdentifier(item);
                            TagModel tm = TagModel.parse(name);
                            tm.setUserData(id); // Gắn ID hoặc Tên (cho Genre/Tag) vào TagModel
                            return tm;
                        })
                        .collect(Collectors.toList());

                Platform.runLater(() -> itemsList.setAll(tagModels));

                return String.format(bundle.getString("status.loaded"),
                        strategy.getPluralName(bundle),
                        tagModels.size());
            }
        };
        runTask(String.format(bundle.getString("task.loading"), strategy.getPluralName(bundle)), task);
    }

    // --- COMMANDS (Được gọi từ View) ---

    public void copyCommand() {
        IMetadataStrategy strategy = selectedStrategy.get();
        String from = copyFromId.get();
        String to = copyToParentId.get();

        if (from == null || from.isEmpty() || to == null || to.isEmpty()) {
            statusText.set(bundle.getString("alert.missingInfo.contentCopy"));
            return;
        }

        String taskName = String.format(bundle.getString("task.copy"), strategy.getPluralName(bundle));
        Task<String> task = new Task<>() {
            @Override protected String call() {
                strategy.copy(embyRepo, from, to);
                return String.format(bundle.getString("status.task.success"), taskName);
            }
        };
        runTask(taskName, task);
    }

    public void clearByParentCommand() {
        IMetadataStrategy strategy = selectedStrategy.get();
        String parentId = clearParentId.get();

        if (parentId == null || parentId.isEmpty()) {
            statusText.set(bundle.getString("alert.missingInfo.contentClearParent"));
            return;
        }

        String taskName = String.format(bundle.getString("task.clearParent"), strategy.getPluralName(bundle));
        Task<String> task = new Task<>() {
            @Override protected String call() {
                strategy.clearByParent(embyRepo, parentId);
                return String.format(bundle.getString("status.task.success"), taskName);
            }
        };
        runTask(taskName, task);
    }

    public void clearSpecificCommand() {
        IMetadataStrategy strategy = selectedStrategy.get();
        TagModel selected = selectedItem.get();

        if (selected == null) {
            statusText.set(bundle.getString("alert.notSelected.content"));
            return;
        }

        String idOrName = (String) selected.getUserData();
        String displayName = selected.getDisplayName();
        String taskName = String.format(bundle.getString("task.clearSpecific"), strategy.getSingularName(bundle), displayName);

        Task<String> task = new Task<>() {
            @Override protected String call() {
                strategy.clearSpecific(embyRepo, idOrName);
                Platform.runLater(MetadataViewModel.this::loadItems); // Tải lại danh sách sau khi xóa
                return String.format(bundle.getString("status.task.success"), taskName);
            }
        };
        runTask(taskName, task);
    }

    /**
     * View sẽ gọi hàm này trước, sau đó gọi updateSpecificCommand.
     */
    public Optional<TagModel> showEditTagDialog(String stylesheetPath) {
        TagModel oldModel = selectedItem.get();
        if (oldModel == null) return Optional.empty();

        // Logic tạo Dialog (từ MainController cũ)
        Dialog<TagModel> dialog = new Dialog<>();
        dialog.setTitle(String.format(bundle.getString("dialog.rename.title"), selectedStrategy.get().getSingularName(bundle)));
        dialog.setHeaderText(bundle.getString("dialog.rename.header"));

        if (stylesheetPath != null) {
            dialog.getDialogPane().getStylesheets().add(stylesheetPath);
        }

        ButtonType okButtonType = new ButtonType("OK", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(okButtonType, ButtonType.CANCEL);

        VBox mainLayout = new VBox(15);
        mainLayout.setPadding(new Insets(20));
        CheckBox isJsonCheckbox = new CheckBox(bundle.getString("dialog.rename.isJson"));
        VBox simpleBox = new VBox(5);
        Label simpleLabel = new Label(bundle.getString("dialog.rename.simpleName"));
        TextField simpleField = new TextField();
        simpleBox.getChildren().addAll(simpleLabel, simpleField);
        GridPane jsonGrid = new GridPane();
        jsonGrid.setHgap(10); jsonGrid.setVgap(10);
        Label keyLabel = new Label(bundle.getString("dialog.rename.key"));
        TextField keyField = new TextField();
        Label valueLabel = new Label(bundle.getString("dialog.rename.value"));
        TextField valueField = new TextField();
        jsonGrid.add(keyLabel, 0, 0); jsonGrid.add(keyField, 1, 0);
        jsonGrid.add(valueLabel, 0, 1); jsonGrid.add(valueField, 1, 1);
        GridPane.setHgrow(keyField, Priority.ALWAYS); GridPane.setHgrow(valueField, Priority.ALWAYS);
        mainLayout.getChildren().addAll(isJsonCheckbox, simpleBox, jsonGrid);
        dialog.getDialogPane().setContent(mainLayout);

        isJsonCheckbox.selectedProperty().addListener((obs, oldVal, newVal) -> {
            simpleBox.setVisible(!newVal); simpleBox.setManaged(!newVal);
            jsonGrid.setVisible(newVal); jsonGrid.setManaged(newVal);
        });

        // Điền dữ liệu cũ
        if (oldModel.isJson()) {
            isJsonCheckbox.setSelected(true);
            keyField.setText(oldModel.getKey());
            valueField.setText(oldModel.getValue());
            simpleBox.setVisible(false); simpleBox.setManaged(false);
        } else {
            isJsonCheckbox.setSelected(false);
            simpleField.setText(oldModel.getDisplayName());
            jsonGrid.setVisible(false); jsonGrid.setManaged(false);
        }

        dialog.getDialogPane().lookupButton(okButtonType).setDisable(false);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == okButtonType) {
                if (isJsonCheckbox.isSelected()) {
                    String key = keyField.getText(); String value = valueField.getText();
                    if (key != null && !key.isEmpty() && value != null && !value.isEmpty()) {
                        return new TagModel(key, value);
                    }
                } else {
                    String simple = simpleField.getText();
                    if (simple != null && !simple.isEmpty()) {
                        return new TagModel(simple);
                    }
                }
                return null;
            }
            return null;
        });

        return dialog.showAndWait();
    }

    public void updateSpecificCommand(TagModel newModel) {
        IMetadataStrategy strategy = selectedStrategy.get();
        TagModel oldModel = selectedItem.get();

        if (oldModel.serialize().equals(newModel.serialize())) {
            statusText.set("Không có thay đổi.");
            return;
        }

        String oldIdOrName = (String) oldModel.getUserData();
        String newSerializedName = newModel.serialize();
        String taskName = String.format(bundle.getString("task.updateSpecific"),
                strategy.getSingularName(bundle),
                oldModel.getDisplayName(),
                newModel.getDisplayName());

        Task<String> task = new Task<>() {
            @Override protected String call() {
                strategy.updateSpecific(embyRepo, oldIdOrName, newSerializedName);
                Platform.runLater(MetadataViewModel.this::loadItems); // Tải lại
                return String.format(bundle.getString("status.task.success"), taskName);
            }
        };
        runTask(taskName, task);
    }
}