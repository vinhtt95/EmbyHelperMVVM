package com.embyhelper.viewmodel;

import com.embyhelper.usecase.BatchProcessUseCase;
import com.embyhelper.usecase.ExportJsonUseCase;
import com.embyhelper.usecase.ImportJsonUseCase;
import java.io.File;
import java.util.ResourceBundle;
import javafx.beans.property.StringProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.concurrent.Task;

public class BatchViewModel extends ViewModelBase {

    private final BatchProcessUseCase batchProcessUseCase;
    private final ExportJsonUseCase exportJsonUseCase;
    private final ImportJsonUseCase importJsonUseCase;
    private final ResourceBundle bundle;

    // --- Properties cho Data Binding ---
    private final StringProperty batchProcessParentId = new SimpleStringProperty();
    private final StringProperty exportParentId = new SimpleStringProperty();
    private final StringProperty importParentId = new SimpleStringProperty();

    public StringProperty batchProcessParentIdProperty() { return batchProcessParentId; }
    public StringProperty exportParentIdProperty() { return exportParentId; }
    public StringProperty importParentIdProperty() { return importParentId; }

    public BatchViewModel(BatchProcessUseCase batchProcessUseCase,
                          ExportJsonUseCase exportJsonUseCase,
                          ImportJsonUseCase importJsonUseCase,
                          ResourceBundle bundle) {
        this.batchProcessUseCase = batchProcessUseCase;
        this.exportJsonUseCase = exportJsonUseCase;
        this.importJsonUseCase = importJsonUseCase;
        this.bundle = bundle;
    }

    // --- Commands (Được gọi từ View) ---

    public void runBatchProcess() {
        String parentId = batchProcessParentId.get();
        if (parentId == null || parentId.trim().isEmpty()) {
            statusText.set("Lỗi: Vui lòng nhập Parent ID cho Batch Process.");
            return;
        }

        Task<String> task = new Task<>() {
            @Override
            protected String call() throws Exception {
                // Cập nhật status từ bên trong UseCase
                batchProcessUseCase.execute(parentId, (progressMessage) ->
                        updateMessage(progressMessage)
                );
                return statusText.get(); // Trả về thông báo cuối cùng
            }
        };
        // Cập nhật statusLabel từ thuộc tính message của Task
        statusText.bind(task.messageProperty());
        isLoading.set(true); // Chỉ set loading, không set text

        task.setOnSucceeded(e -> {
            statusText.unbind();
            isLoading.set(false);
        });
        task.setOnFailed(e -> {
            statusText.unbind();
            statusText.set("Lỗi Batch Process: " + e.getSource().getException().getMessage());
            isLoading.set(false);
        });

        new Thread(task).start();
    }

    public void runExportJson(File directory) {
        String parentId = exportParentId.get();
        if (parentId == null || parentId.trim().isEmpty()) {
            statusText.set("Lỗi: Vui lòng nhập Parent ID để Xuất JSON.");
            return;
        }

        Task<String> task = new Task<>() {
            @Override
            protected String call() throws Exception {
                exportJsonUseCase.execute(parentId, directory, (progressMessage) ->
                        updateMessage(progressMessage)
                );
                return statusText.get();
            }
        };

        statusText.bind(task.messageProperty());
        isLoading.set(true);
        task.setOnSucceeded(e -> { statusText.unbind(); isLoading.set(false); });
        task.setOnFailed(e -> {
            statusText.unbind();
            statusText.set("Lỗi Xuất JSON: " + e.getSource().getException().getMessage());
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

        Task<String> task = new Task<>() {
            @Override
            protected String call() throws Exception {
                importJsonUseCase.execute(finalParentId, directory, (progressMessage) ->
                        updateMessage(progressMessage)
                );
                return statusText.get();
            }
        };

        statusText.bind(task.messageProperty());
        isLoading.set(true);
        task.setOnSucceeded(e -> { statusText.unbind(); isLoading.set(false); });
        task.setOnFailed(e -> {
            statusText.unbind();
            statusText.set("Lỗi Nhập JSON: " + e.getSource().getException().getMessage());
            isLoading.set(false);
        });
        new Thread(task).start();
    }
}