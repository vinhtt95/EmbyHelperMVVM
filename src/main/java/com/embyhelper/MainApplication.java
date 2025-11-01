package com.embyhelper;

import com.embyhelper.di.ServiceLocator;
import com.embyhelper.repository.IConfigRepository;
import com.embyhelper.service.IAuthService;
import com.embyhelper.service.ILocalizationService;
import com.embyhelper.service.INavigationService;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;

public class MainApplication extends Application {

    @Override
    public void start(Stage stage) {
        // 1. Khởi tạo ServiceLocator
        ServiceLocator locator = ServiceLocator.getInstance();

        // 2. Lấy các service cần thiết
        ILocalizationService locService = locator.getLocalizationService();
        IAuthService authService = locator.getAuthService();
        INavigationService navService = locator.getNavigationService();
        IConfigRepository configRepo = locator.getConfigRepository();

        // 3. SỬA LỖI: Cung cấp Stage chính cho NavigationService
        // Dòng 19 cũ (locator.setPrimaryStage(stage);) là sai.
        // Phải lấy service trước, sau đó gọi phương thức trên service đó.
        navService.setPrimaryStage(stage);

        // 4. Đặt kích thước cửa sổ
        stage.setWidth(configRepo.getLoginWindowWidth());
        stage.setHeight(configRepo.getLoginWindowHeight());

        // 5. Lưu kích thước cửa sổ khi thay đổi (cho cửa sổ hiện tại, Login)
        // (Chúng ta sẽ gán lại listener này trong NavigationService khi chuyển view)
        stage.widthProperty().addListener((obs, oldVal, newVal) -> configRepo.saveLoginWindowSize(stage.getWidth(), stage.getHeight()));
        stage.heightProperty().addListener((obs, oldVal, newVal) -> configRepo.saveLoginWindowSize(stage.getWidth(), stage.getHeight()));

        // 6. Đóng ứng dụng hoàn toàn khi đóng cửa sổ
        stage.setOnCloseRequest(e -> {
            Platform.exit();
            System.exit(0);
        });

        // 7. Kiểm tra session đã lưu (chạy nền)
        authService.validateSavedSessionAsync(
                // onSuccess (userId hợp lệ)
                (userId) -> {
                    System.out.println("Session hợp lệ. Mở màn hình chính.");
                    Platform.runLater(() -> navService.showMainView(userId));
                },
                // onFailure
                () -> {
                    System.out.println("Không có session. Mở màn hình đăng nhập.");
                    Platform.runLater(navService::showLoginView);
                }
        );
    }

    public static void main(String[] args) {
        launch(args);
    }
}