package com.embyhelper.viewmodel;

import com.embyhelper.repository.IEmbyRepository; // <-- THÊM MỚI
import com.embyhelper.usecase.BatchProcessUseCase;
import com.embyhelper.usecase.CopyMetadataUseCase; // <-- THÊM MỚI
import com.embyhelper.usecase.ExportJsonUseCase;
import com.embyhelper.usecase.ImportJsonUseCase;
import embyclient.model.BaseItemDto; // <-- THÊM MỚI
import java.io.File;
import java.util.List; // <-- THÊM MỚI
import java.util.ResourceBundle;
import java.util.stream.Collectors; // <-- THÊM MỚI
import javafx.beans.property.StringProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.ObjectProperty; // <-- THÊM MỚI
import javafx.beans.property.SimpleObjectProperty; // <-- THÊM MỚI
import javafx.collections.FXCollections; // <-- THÊM MỚI
import javafx.collections.ObservableList; // <-- THÊM MỚI
import javafx.concurrent.Task;

public class BatchViewModel extends ViewModelBase {

    // --- Các UseCase và Repo ---
    private final BatchProcessUseCase batchProcessUseCase;
    private final ExportJsonUseCase exportJsonUseCase;
    private final ImportJsonUseCase importJsonUseCase;
    private final CopyMetadataUseCase copyMetadataUseCase; // <-- THÊM MỚI
    private final IEmbyRepository embyRepo; // <-- THÊM MỚI
    private final ResourceBundle bundle;

    // --- Properties cho Data Binding (Tab cũ) ---
    private final StringProperty batchProcessParentId = new SimpleStringProperty();
    private final StringProperty exportParentId = new SimpleStringProperty();
    private final StringProperty importParentId = new SimpleStringProperty();

    public StringProperty batchProcessParentIdProperty() { return batchProcessParentId; }
    public StringProperty exportParentIdProperty() { return exportParentId; }
    public StringProperty importParentIdProperty() { return importParentId; }

    // --- (THÊM MỚI) Properties cho Chức năng Sao chép Metadata ---
    private final StringProperty copySourceParentId = new SimpleStringProperty();
    private final ObservableList<BaseItemDto> sourceItemsList = FXCollections.observableArrayList();
    private final ObjectProperty<BaseItemDto> selectedSourceItem = new SimpleObjectProperty<>();
    private final ObservableList<BaseItemDto> destinationItemsList = FXCollections.observableArrayList();
    private final ObjectProperty<BaseItemDto> selectedDestinationItem = new SimpleObjectProperty<>();

    public StringProperty copySourceParentIdProperty() { return copySourceParentId; }
    public ObservableList<BaseItemDto> getSourceItemsList() { return sourceItemsList; }
    public ObjectProperty<BaseItemDto> selectedSourceItemProperty() { return selectedSourceItem; }
    public ObservableList<BaseItemDto> getDestinationItemsList() { return destinationItemsList; }
    public ObjectProperty<BaseItemDto> selectedDestinationItemProperty() { return selectedDestinationItem; }
    // --- (Kết thúc) ---


    public BatchViewModel(BatchProcessUseCase batchProcessUseCase,
                          ExportJsonUseCase exportJsonUseCase,
                          ImportJsonUseCase importJsonUseCase,
                          CopyMetadataUseCase copyMetadataUseCase, // <-- THÊM MỚI
                          IEmbyRepository embyRepo, // <-- THÊM MỚI
                          ResourceBundle bundle) {
        this.batchProcessUseCase = batchProcessUseCase;
        this.exportJsonUseCase = exportJsonUseCase;
        this.importJsonUseCase = importJsonUseCase;
        this.copyMetadataUseCase = copyMetadataUseCase; // <-- THÊM MỚI
        this.embyRepo = embyRepo; // <-- THÊM MỚI
        this.bundle = bundle;

        // (THÊM MỚI) Listener tự động tìm item đích khi item nguồn thay đổi
        selectedSourceItem.addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                findDestinationItems(newVal);
            } else {
                destinationItemsList.clear();
            }
        });
    }

    // --- Commands (Tab cũ) ---

    public void runBatchProcess() {
        String parentId = batchProcessParentId.get();
        if (parentId == null || parentId.trim().isEmpty()) {
            statusText.set(bundle.getString("alert.missingInfo.contentBatchProcess"));
            return;
        }

        String taskName = String.format(bundle.getString("task.batchProcess"), parentId);

        Task<String> task = new Task<>() {
            @Override
            protected String call() throws Exception {
                // Cập nhật status từ bên trong UseCase
                BatchProcessUseCase.BatchProcessResult result = batchProcessUseCase.execute(parentId, (progressMessage) ->
                        updateMessage(progressMessage) // Cập nhật statusLabel theo thời gian thực
                );

                // Trả về thông báo cuối cùng
                return String.format(bundle.getString("status.batch.done"),
                        result.successCount, result.skippedCount, result.errorCount);
            }
        };

        // Cập nhật statusLabel từ thuộc tính message() của Task
        statusText.bind(task.messageProperty());
        isLoading.set(true); // Chỉ set loading, không set text

        task.setOnSucceeded(e -> {
            statusText.unbind(); // Ngừng bind
            statusText.set(task.getValue()); // Set giá trị cuối cùng
            isLoading.set(false);
        });
        task.setOnFailed(e -> {
            statusText.unbind();
            String errorMsg = "Lỗi Batch Process: " + e.getSource().getException().getMessage();
            statusText.set(errorMsg);
            System.err.println(errorMsg);
            e.getSource().getException().printStackTrace();
            isLoading.set(false);
        });

        new Thread(task).start();
    }

    public void runExportJson(File directory) {
        String parentId = exportParentId.get();
        if (parentId == null || parentId.trim().isEmpty()) {
            statusText.set(bundle.getString("alert.missingInfo.contentExport"));
            return;
        }

        String taskName = String.format(bundle.getString("task.exportJson"), parentId);

        Task<String> task = new Task<>() {
            @Override
            protected String call() throws Exception {
                ExportJsonUseCase.ExportResult result = exportJsonUseCase.execute(parentId, directory, (progressMessage) ->
                        updateMessage(progressMessage)
                );

                return String.format(bundle.getString("status.export.done"),
                        result.successCount, result.errorCount, directory.getAbsolutePath());
            }
        };

        statusText.bind(task.messageProperty());
        isLoading.set(true);
        task.setOnSucceeded(e -> { statusText.unbind(); statusText.set(task.getValue()); isLoading.set(false); });
        task.setOnFailed(e -> {
            statusText.unbind();
            String errorMsg = "Lỗi Xuất JSON: " + e.getSource().getException().getMessage();
            statusText.set(errorMsg);
            System.err.println(errorMsg);
            e.getSource().getException().printStackTrace();
            isLoading.set(false);
        });
        new Thread(task).start();
    }

    public void runImportJson(File directory) {
        // parentId là tùy chọn (từ MainController cũ)
        String parentId = importParentId.get();
        if (parentId != null && parentId.trim().isEmpty()) {
            parentId = null;
        }
        String finalParentId = parentId;

        String taskName = String.format(bundle.getString("task.importJson"), directory.getName());

        Task<String> task = new Task<>() {
            @Override
            protected String call() throws Exception {
                ImportJsonUseCase.ImportResult result = importJsonUseCase.execute(finalParentId, directory, (progressMessage) ->
                        updateMessage(progressMessage)
                );

                return String.format(bundle.getString("status.import.done"),
                        result.successCount, result.notFoundCount, result.ambiguousCount, result.errorCount);
            }
        };

        statusText.bind(task.messageProperty());
        isLoading.set(true);
        task.setOnSucceeded(e -> { statusText.unbind(); statusText.set(task.getValue()); isLoading.set(false); });
        task.setOnFailed(e -> {
            statusText.unbind();
            String errorMsg = "Lỗi Nhập JSON: " + e.getSource().getException().getMessage();
            statusText.set(errorMsg);
            System.err.println(errorMsg);
            e.getSource().getException().printStackTrace();
            isLoading.set(false);
        });
        new Thread(task).start();
    }

    // --- (THÊM MỚI) Commands cho Chức năng Sao chép Metadata ---

    /**
     * Được gọi bởi View khi nhấn nút "Tìm Item nguồn".
     */
    public void findSourceItems() {
        String parentId = copySourceParentId.get();
        if (parentId == null || parentId.trim().isEmpty()) {
            statusText.set("Lỗi: Vui lòng nhập ID Thư mục nguồn.");
            return;
        }

        isLoading.set(true);
        statusText.set("Đang tìm item con trong Parent ID: " + parentId);
        sourceItemsList.clear(); // Xóa danh sách cũ
        destinationItemsList.clear(); // Xóa danh sách đích

        Task<List<BaseItemDto>> task = new Task<>() {
            @Override
            protected List<BaseItemDto> call() throws Exception {
                // Yêu cầu lấy 'OriginalTitle'
                return embyRepo.getItemsByParentId(parentId, null, null, true, "Movie", "OriginalTitle");
            }
        };

        task.setOnSucceeded(e -> {
            sourceItemsList.setAll(task.getValue());
            statusText.set("Tìm thấy " + sourceItemsList.size() + " item nguồn.");
            isLoading.set(false);
        });
        task.setOnFailed(e -> {
            statusText.set("Lỗi khi tìm item nguồn: " + e.getSource().getException().getMessage());
            isLoading.set(false);
        });
        new Thread(task).start();
    }

    /**
     * Được gọi tự động khi 'selectedSourceItem' thay đổi.
     */
    private void findDestinationItems(BaseItemDto sourceItem) {
        String originalTitle = sourceItem.getOriginalTitle();
        if (originalTitle == null || originalTitle.isEmpty()) {
            statusText.set("Item nguồn không có OriginalTitle, không thể tìm item đích.");
            destinationItemsList.clear();
            return;
        }

        isLoading.set(true);
        statusText.set("Đang tìm item đích khớp với OriginalTitle: " + originalTitle);
        destinationItemsList.clear();

        Task<List<BaseItemDto>> task = new Task<>() {
            @Override
            protected List<BaseItemDto> call() throws Exception {
                // Tìm kiếm toàn bộ server (parentId = null), yêu cầu 'OriginalTitle'
                return embyRepo.searchItems(originalTitle, null, "OriginalTitle", "Movie", true);
            }
        };

        task.setOnSucceeded(e -> {
            // Lọc lại kết quả
            List<BaseItemDto> results = task.getValue().stream()
                    // Khớp chính xác (không phân biệt hoa thường)
                    .filter(item -> item.getOriginalTitle() != null && originalTitle.equalsIgnoreCase(item.getOriginalTitle()))
                    // Loại trừ chính item nguồn
                    .filter(item -> !item.getId().equals(sourceItem.getId()))
                    .collect(Collectors.toList());

            destinationItemsList.setAll(results);
            statusText.set("Tìm thấy " + destinationItemsList.size() + " item đích khớp.");
            isLoading.set(false);
        });
        task.setOnFailed(e -> {
            statusText.set("Lỗi khi tìm item đích: " + e.getSource().getException().getMessage());
            isLoading.set(false);
        });
        new Thread(task).start();
    }

    /**
     * Được gọi bởi View khi nhấn nút "Thực thi sao chép".
     */
    public void copyMetadataToSelected() {
        BaseItemDto source = selectedSourceItem.get();
        BaseItemDto dest = selectedDestinationItem.get();

        if (source == null || dest == null) {
            statusText.set("Lỗi: Vui lòng chọn 1 item nguồn VÀ 1 item đích.");
            return;
        }

        // Sử dụng logic binding giống runBatchProcess
        Task<String> task = new Task<>() {
            @Override
            protected String call() throws Exception {
                copyMetadataUseCase.execute(source.getId(), dest.getId(),
                        (progressMessage) -> updateMessage(progressMessage)
                );
                return statusText.get(); // Trả về thông báo cuối cùng
            }
        };

        statusText.bind(task.messageProperty());
        isLoading.set(true);
        task.setOnSucceeded(e -> {
            statusText.unbind();
            isLoading.set(false);
        });
        task.setOnFailed(e -> {
            statusText.unbind();
            statusText.set("Lỗi Sao chép Metadata: " + e.getSource().getException().getMessage());
            isLoading.set(false);
        });

        new Thread(task).start();
    }
}