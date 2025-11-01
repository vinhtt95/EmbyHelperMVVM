package com.embyhelper.service;

import java.util.function.Consumer;

public interface IAuthService {
    /**
     * Cố gắng đăng nhập và trả về UserID nếu thành công.
     * @return UserID, hoặc null nếu thất bại.
     */
    String login(String serverUrl, String apiKey, String username, String password);

    /**
     * Xác thực session đã lưu một cách đồng bộ.
     * @return UserID nếu hợp lệ, ngược lại null.
     */
    String validateSavedSession();

    /**
     * Xác thực session đã lưu một cách bất đồng bộ.
     * @param onSuccess Callback với UserID nếu hợp lệ.
     * @param onFailure Callback nếu không hợp lệ.
     */
    void validateSavedSessionAsync(Consumer<String> onSuccess, Runnable onFailure);

    /**
     * Xóa session đã lưu.
     */
    void logout();
}