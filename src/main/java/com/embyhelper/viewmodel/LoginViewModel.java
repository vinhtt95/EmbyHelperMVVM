package com.embyhelper.viewmodel;

import com.embyhelper.repository.IConfigRepository;
import com.embyhelper.service.IAuthService;
import com.embyhelper.service.ILocalizationService;
import com.embyhelper.service.INavigationService;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.concurrent.Task;

/**
 * ViewModel cho LoginView.
 */
public class LoginViewModel extends ViewModelBase {

    private final IAuthService authService;
    private final INavigationService navService;
    private final IConfigRepository configRepo;
    private final ILocalizationService locService;

    // --- Properties cho Data Binding ---
    private final StringProperty serverAddress = new SimpleStringProperty();
    private final StringProperty apiKey = new SimpleStringProperty();
    private final StringProperty username = new SimpleStringProperty();
    private final StringProperty password = new SimpleStringProperty();

    // --- Getters cho Properties ---
    public StringProperty serverAddressProperty() { return serverAddress; }
    public StringProperty apiKeyProperty() { return apiKey; }
    public StringProperty usernameProperty() { return username; }
    public StringProperty passwordProperty() { return password; }

    public LoginViewModel(IAuthService authService, INavigationService navService, IConfigRepository configRepo, ILocalizationService locService) {
        this.authService = authService;
        this.navService = navService;
        this.configRepo = configRepo;
        this.locService = locService;

        loadSavedConfig();
        statusText.set(locService.getString("login.status.notConnected"));
    }

    private void loadSavedConfig() {
        serverAddress.set(configRepo.getServerAddress());
        apiKey.set(configRepo.getApiKey());
        username.set(configRepo.getUsername());
    }

    /**
     * Được gọi bởi View khi nút "Connect" được nhấn.
     */
    public void login() {
        configRepo.saveLoginInfo(serverAddress.get(), apiKey.get(), username.get());

        final String server = serverAddress.get();
        final String key = apiKey.get();
        final String user = username.get();
        final String pass = password.get();

        // SỬA LỖI: Không dùng super.runTask() vì nó ghi đè OnSucceeded.
        // Tự quản lý Task giống như BatchViewModel.

        statusText.set(locService.getString("login.status.connecting"));
        isLoading.set(true);

        Task<String> loginTask = new Task<>() {
            @Override
            protected String call() throws Exception {
                String userId = authService.login(server, key, user, pass);
                if (userId != null) {
                    // Đặt message để OnSucceeded có thể lấy
                    updateMessage(locService.getString("login.status.success"));
                    return userId; // Trả về UserID
                } else {
                    // Ném lỗi để onFailed bắt
                    throw new Exception(locService.getString("login.status.failure"));
                }
            }
        };

        // Xử lý khi Task thành công (trên luồng UI)
        loginTask.setOnSucceeded(e -> {
            String userId = loginTask.getValue();      // Lấy UserID
            statusText.set(loginTask.getMessage());  // Lấy "login.status.success"
            isLoading.set(false);

            // HÀNH ĐỘNG QUAN TRỌNG: Chuyển sang màn hình chính
            navService.showMainView(userId);
        });

        // Xử lý khi Task thất bại (trên luồng UI)
        loginTask.setOnFailed(e -> {
            // Lấy lỗi từ Exception
            String errorMsg = e.getSource().getException().getMessage();
            if (errorMsg == null || errorMsg.isEmpty()) {
                errorMsg = "Lỗi không xác định.";
            }

            System.err.println("Lỗi khi chạy task login:");
            e.getSource().getException().printStackTrace();

            statusText.set(errorMsg); // Hiển thị lỗi
            isLoading.set(false);
        });

        // Chạy Task trên luồng nền
        new Thread(loginTask).start();
    }
}