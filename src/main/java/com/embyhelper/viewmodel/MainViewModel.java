package com.embyhelper.viewmodel;

import com.embyhelper.service.IAuthService;
import com.embyhelper.service.ILocalizationService;
import com.embyhelper.service.INavigationService;
import java.util.ResourceBundle;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.StringProperty;

/**
 * ViewModel chính, chứa các VM con và quản lý trạng thái chung.
 */
public class MainViewModel {

    private final INavigationService navService;
    private final ILocalizationService locService;
    private final IAuthService authService;

    // Các ViewModel con
    private final MetadataViewModel metadataViewModel;
    private final BatchViewModel batchViewModel;

    public MainViewModel(INavigationService navService,
                         ILocalizationService locService,
                         IAuthService authService,
                         MetadataViewModel metadataViewModel,
                         BatchViewModel batchViewModel) {
        this.navService = navService;
        this.locService = locService;
        this.authService = authService;
        this.metadataViewModel = metadataViewModel;
        this.batchViewModel = batchViewModel;
    }

    // --- Getters cho ViewModels con (để View truy cập) ---
    public MetadataViewModel getMetadataViewModel() { return metadataViewModel; }
    public BatchViewModel getBatchViewModel() { return batchViewModel; }
    public ResourceBundle getBundle() { return locService.getBundle(); }

    // --- Chuyển tiếp (Forward) các thuộc tính trạng thái chung ---

    /**
     * SỬA LỖI: Trả về một StringProperty cụ thể.
     * Tệp MainView.java (code-behind) chứa logic binding phức tạp
     * (kết hợp cả hai VM) nên phương thức này chỉ cần trả về
     * một giá trị mặc định hợp lệ để biên dịch.
     */
    public StringProperty statusTextProperty() {
        // Trả về property của metadataVM làm mặc định
        return metadataViewModel.statusTextProperty();
    }

    /**
     * SỬA LỖI: Trả về một BooleanProperty cụ thể.
     * Tệp MainView.java (code-behind) đã bind vào .or()
     * nên phương thức này chỉ cần trả về một giá trị mặc định hợp lệ.
     */
    public BooleanProperty isLoadingProperty() {
        // Trả về property của metadataVM làm mặc định
        return metadataViewModel.isLoadingProperty();
    }

    // --- Commands (từ Menu trong MainView) ---

    public void logout() {
        // 1. Xóa session đã lưu
        authService.logout();

        // 2. Khởi động lại ứng dụng
        navService.restartApplication();
    }

    public void switchLanguage(String langCode) {
        locService.switchLanguage(langCode);
        navService.restartApplication(); // Tải lại UI với ngôn ngữ mới
    }
}