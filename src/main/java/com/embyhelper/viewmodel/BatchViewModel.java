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
import java.util.Map; // <-- THÊM MỚI
import java.util.ArrayList; // <-- THÊM MỚI
import java.util.concurrent.ConcurrentHashMap; // <-- THÊM MỚI
import javafx.application.Platform; // <-- THÊM MỚI
import javafx.collections.transformation.FilteredList; // <-- THÊM MỚI

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

    // --- (SỬA ĐỔI) Properties cho Chức năng Sao chép Metadata ---
    private final StringProperty copySourceParentId = new SimpleStringProperty();

    // Danh sách gốc chứa TẤT CẢ item nguồn
    private final ObservableList<BaseItemDto> allSourceItemsList = FXCollections.observableArrayList();
    // Map lưu trữ các cặp khớp (SourceID -> List<DestItem>)
    private final Map<String, List<BaseItemDto>> sourceToDestMap = new ConcurrentHashMap<>();
    // Bộ lọc tìm kiếm (Yêu cầu 2)
    private final StringProperty sourceFilterText = new SimpleStringProperty("");
    // Danh sách đã lọc (Yêu cầu 1 & 2)
    private final FilteredList<BaseItemDto> filteredSourceItemsList;

    private final ObjectProperty<BaseItemDto> selectedSourceItem = new SimpleObjectProperty<>();
    private final ObservableList<BaseItemDto> destinationItemsList = FXCollections.observableArrayList();
    private final ObjectProperty<BaseItemDto> selectedDestinationItem = new SimpleObjectProperty<>();

    public StringProperty copySourceParentIdProperty() { return copySourceParentId; }
    // Getter cho danh sách đã lọc (cho View)
    public FilteredList<BaseItemDto> getFilteredSourceItemsList() { return filteredSourceItemsList; }
    public StringProperty sourceFilterTextProperty() { return sourceFilterText; }

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

        // (SỬA ĐỔI) Khởi tạo FilteredList
        this.filteredSourceItemsList = new FilteredList<>(allSourceItemsList, p -> false); // Mặc định ẩn tất cả

        // (SỬA ĐỔI) Listener tự động tìm item đích (đọc từ Map)
        selectedSourceItem.addListener((obs, oldVal, newVal) -> {
            findDestinationItemsFromMap(newVal);
        });

        // (THÊM MỚI) Listener cho bộ lọc text
        sourceFilterText.addListener((obs, oldVal, newVal) -> {
            updateSourceFilterPredicate(newVal);
        });
    }

    /**
     * (THÊM MỚI) Cập nhật bộ lọc cho danh sách nguồn (Yêu cầu 1 & 2)
     */
    private void updateSourceFilterPredicate(String filterText) {
        String lowerCaseFilter = (filterText == null) ? "" : filterText.toLowerCase();

        filteredSourceItemsList.setPredicate(item -> {
            // Yêu cầu 1: Phải có ít nhất 1 item đích
            boolean hasDestination = sourceToDestMap.containsKey(item.getId()) &&
                    !sourceToDestMap.get(item.getId()).isEmpty();
            if (!hasDestination) {
                return false;
            }

            // Yêu cầu 2: Lọc theo OriginalTitle
            if (lowerCaseFilter.isEmpty()) {
                return true; // Không có text filter, chỉ cần qua Yêu cầu 1
            }

            return item.getOriginalTitle() != null &&
                    item.getOriginalTitle().toLowerCase().contains(lowerCaseFilter);
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

    // --- (SỬA ĐỔI) Commands cho Chức năng Sao chép Metadata ---

    /**
     * (SỬA ĐỔI) Được gọi bởi View khi nhấn nút "Tìm Item nguồn".
     * Sẽ tìm nguồn VÀ tìm tất cả các cặp đích (Yêu cầu 1).
     */
    public void findSourceItems() {
        String parentId = copySourceParentId.get();
        if (parentId == null || parentId.trim().isEmpty()) {
            statusText.set("Lỗi: Vui lòng nhập ID Thư mục nguồn.");
            return;
        }

        isLoading.set(true);
        statusText.set("Đang tìm item con trong Parent ID: " + parentId);
        allSourceItemsList.clear(); // Xóa danh sách gốc
        destinationItemsList.clear(); // Xóa danh sách đích
        sourceToDestMap.clear(); // Xóa map

        Task<String> task = new Task<>() {
            @Override
            protected String call() throws Exception {
                // --- GIAI ĐOẠN 1: LẤY NGUỒN ---
                updateMessage("Đang tìm item con trong Parent ID: " + parentId);
                // Yêu cầu lấy 'OriginalTitle'
                List<BaseItemDto> sourceItems = embyRepo.getItemsByParentId(parentId, null, null, true, "Movie", "OriginalTitle");

                Platform.runLater(() -> allSourceItemsList.setAll(sourceItems));

                if (sourceItems.isEmpty()) {
                    return "Không tìm thấy item nguồn nào.";
                }

                // --- GIAI ĐOẠN 2: TÌM ĐÍCH CHO TẤT CẢ (Yêu cầu 1) ---
                int total = sourceItems.size();
                for (int i = 0; i < total; i++) {
                    BaseItemDto sourceItem = sourceItems.get(i);
                    updateMessage(String.format(bundle.getString("status.copy.findingMatches"), (i + 1), total, sourceItem.getName()));

                    String originalTitle = sourceItem.getOriginalTitle();
                    if (originalTitle == null || originalTitle.isEmpty()) {
                        continue; // Bỏ qua nếu không có OriginalTitle
                    }

                    // Tìm kiếm toàn bộ server (parentId = null), yêu cầu 'OriginalTitle'
                    List<BaseItemDto> results = embyRepo.searchItems(originalTitle, null, "OriginalTitle", "Movie", true);

                    // Lọc lại kết quả
                    List<BaseItemDto> destinations = results.stream()
                            // Khớp chính xác (không phân biệt hoa thường)
                            .filter(item -> item.getOriginalTitle() != null && originalTitle.equalsIgnoreCase(item.getOriginalTitle()))
                            // Loại trừ chính item nguồn
                            .filter(item -> !item.getId().equals(sourceItem.getId()))
                            .collect(Collectors.toList());

                    if (!destinations.isEmpty()) {
                        sourceToDestMap.put(sourceItem.getId(), destinations);
                    }
                }

                // Cập nhật lại bộ lọc trên UI Thread
                Platform.runLater(() -> {
                    updateSourceFilterPredicate(sourceFilterText.get());
                });

                return String.format(bundle.getString("status.copy.foundMatches"), sourceToDestMap.size());
            }
        };

        statusText.bind(task.messageProperty());

        task.setOnSucceeded(e -> {
            statusText.unbind();
            statusText.set(task.getValue()); // Set thông báo cuối cùng
            isLoading.set(false);
        });
        task.setOnFailed(e -> {
            statusText.unbind();
            statusText.set("Lỗi khi tìm item nguồn: " + e.getSource().getException().getMessage());
            isLoading.set(false);
        });
        new Thread(task).start();
    }

    /**
     * (SỬA ĐỔI) Được gọi tự động khi 'selectedSourceItem' thay đổi.
     * Lấy danh sách đích từ Map đã có.
     */
    private void findDestinationItemsFromMap(BaseItemDto sourceItem) {
        destinationItemsList.clear();
        if (sourceItem != null && sourceToDestMap.containsKey(sourceItem.getId())) {
            destinationItemsList.setAll(sourceToDestMap.get(sourceItem.getId()));
        }
    }

    // --- HÀM CŨ (Không còn dùng, logic đã chuyển vào findSourceItems) ---
    /*
    private void findDestinationItems(BaseItemDto sourceItem) {
        // ... (logic cũ)
    }
    */

    /**
     * Được gọi bởi View khi nhấn nút "Thực thi sao chép" (1-1).
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

    /**
     * (THÊM MỚI) Được gọi bởi View khi nhấn nút "Áp dụng Tất cả" (Yêu cầu 3).
     */
    public void copyAllMetadata() {
        if (sourceToDestMap.isEmpty()) {
            statusText.set("Không có cặp item nào để áp dụng.");
            return;
        }

        Task<String> task = new Task<>() {
            @Override
            protected String call() throws Exception {
                List<Map.Entry<String, List<BaseItemDto>>> jobs = new ArrayList<>(sourceToDestMap.entrySet());
                int totalSourceItems = jobs.size();
                updateMessage(String.format(bundle.getString("task.copyAll.start"), totalSourceItems));

                long totalJobs = sourceToDestMap.values().stream().mapToLong(List::size).sum();
                long currentJob = 0;

                for (Map.Entry<String, List<BaseItemDto>> entry : jobs) {
                    String sourceId = entry.getKey();
                    List<BaseItemDto> destItems = entry.getValue();

                    for (BaseItemDto destItem : destItems) {
                        currentJob++;
                        String destId = destItem.getId();

                        // Cập nhật tiến độ cho UI
                        updateMessage(String.format(bundle.getString("task.copyAll.progress"),
                                currentJob, totalJobs, sourceId, destId));

                        try {
                            // Chạy sao chép (đồng bộ), không cần callback con
                            copyMetadataUseCase.execute(sourceId, destId, null);
                        } catch (Exception e) {
                            System.err.println("Lỗi khi sao chép " + sourceId + " -> " + destId + ": " + e.getMessage());
                            // (Tùy chọn: có thể đếm lỗi)
                        }
                    }
                }

                return String.format(bundle.getString("task.copyAll.done"), totalJobs);
            }
        };

        statusText.bind(task.messageProperty());
        isLoading.set(true);
        task.setOnSucceeded(e -> {
            statusText.unbind();
            statusText.set(task.getValue()); // Set thông báo cuối cùng
            isLoading.set(false);
        });
        task.setOnFailed(e -> {
            statusText.unbind();
            statusText.set("Lỗi 'Áp dụng Tất cả': " + e.getSource().getException().getMessage());
            isLoading.set(false);
        });

        new Thread(task).start();
    }
}