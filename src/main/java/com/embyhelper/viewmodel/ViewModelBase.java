package com.embyhelper.viewmodel;

import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.StringProperty;
import javafx.concurrent.Task;

/**
 * Lớp ViewModel cơ sở chứa các thuộc tính (Property) chung.
 */
public abstract class ViewModelBase {

    // Đây là các thuộc tính chung mà MainViewModel sẽ "forward"
    protected final StringProperty statusText = new SimpleStringProperty("Sẵn sàng.");
    protected final BooleanProperty isLoading = new SimpleBooleanProperty(false);

    public StringProperty statusTextProperty() { return statusText; }
    public BooleanProperty isLoadingProperty() { return isLoading; }

    /**
     * Chạy một tác vụ (Task) trên một luồng riêng biệt.
     */
    protected void runTask(String taskName, Task<String> task) {
        statusText.set("Đang thực thi: " + taskName + "...");
        isLoading.set(true);

        task.setOnSucceeded(e -> {
            statusText.set(task.getValue()); // Hiển thị thông báo thành công từ task
            isLoading.set(false);
        });

        task.setOnFailed(e -> {
            String errorMsg = "LỖI: " + task.getException().getMessage();
            System.err.println("Lỗi khi chạy task '" + taskName + "':");
            task.getException().printStackTrace();
            statusText.set(errorMsg);
            isLoading.set(false);
            // TODO: Hiển thị Alert (có thể thông qua một service)
        });

        new Thread(task).start();
    }
}