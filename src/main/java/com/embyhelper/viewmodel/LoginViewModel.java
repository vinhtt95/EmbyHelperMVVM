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

        Task<String> loginTask = new Task<>() {
            @Override
            protected String call() throws Exception {
                String userId = authService.login(server, key, user, pass);
                if (userId != null) {
                    return userId;
                } else {
                    // Ném lỗi để onFailed bắt
                    throw new Exception(locService.getString("login.status.failure"));
                }
            }
        };

        // Xử lý khi Task thành công (trên luồng UI)
        loginTask.setOnSucceeded(e -> {
            String userId = loginTask.getValue();
            statusText.set(locService.getString("login.status.success"));
            isLoading.set(false);
            // Chuyển sang màn hình chính
            navService.showMainView(userId);
        });

        // Chạy Task (ViewModelBase sẽ xử lý setOnFailed)
        runTask(locService.getString("login.status.connecting"), loginTask);
    }
}