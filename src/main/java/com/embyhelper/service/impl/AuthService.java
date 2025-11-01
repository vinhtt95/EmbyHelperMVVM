package com.embyhelper.service.impl;

import com.embyhelper.repository.IConfigRepository;
import com.embyhelper.repository.IEmbyRepository;
import com.embyhelper.service.IAuthService;
import embyclient.ApiClient;
import embyclient.Configuration;
import embyclient.api.UserServiceApi;
import embyclient.auth.ApiKeyAuth;
import java.util.function.Consumer; // <-- LỖI ĐÃ ĐƯỢC SỬA (THÊM DÒNG NÀY)

public class AuthService implements IAuthService {

    private final IConfigRepository configRepo;
    private final IEmbyRepository embyRepo; // Để cấu hình ApiClient

    public AuthService(IConfigRepository configRepo, IEmbyRepository embyRepo) {
        this.configRepo = configRepo;
        this.embyRepo = embyRepo;
    }

    @Override
    public String login(String serverUrl, String apiKey, String username, String password) {
        try {
            // 1. Tạo ApiClient tạm thời
            ApiClient tempApiClient = new ApiClient();
            tempApiClient.setBasePath(serverUrl);
            ApiKeyAuth apikeyauth = (ApiKeyAuth) tempApiClient.getAuthentication("apikeyauth");
            apikeyauth.setApiKey(apiKey);

            // 2. Gọi API đăng nhập (logic từ AuthenUserService cũ)
            UserServiceApi userServiceApi = new UserServiceApi(tempApiClient);
            embyclient.model.AuthenticateUserByName body = new embyclient.model.AuthenticateUserByName();
            body.setUsername(username);
            body.setPw(password);

            embyclient.model.AuthenticationAuthenticationResult authResult =
                    userServiceApi.postUsersAuthenticatebyname(body, "Emby Helper");

            if (authResult != null && authResult.getAccessToken() != null) {
                String userId = authResult.getSessionInfo().getUserId();
                String accessToken = authResult.getAccessToken();

                // 3. Lưu session
                configRepo.saveSession(accessToken, userId);

                // 4. Cấu hình ApiClient MẶC ĐỊNH cho toàn ứng dụng
                Configuration.setDefaultApiClient(tempApiClient);
                Configuration.getDefaultApiClient().setAccessToken(accessToken);

                return userId;
            }
        } catch (Exception e) {
            System.err.println("Lỗi đăng nhập: " + e.getMessage());
            configRepo.clearSession();
        }
        return null;
    }

    @Override
    public String validateSavedSession() {
        String token = configRepo.getAccessToken();
        String userId = configRepo.getUserId();
        String server = configRepo.getServerAddress();
        String apiKey = configRepo.getApiKey();

        if (token == null || userId == null || server == null || apiKey == null) {
            return null;
        }

        try {
            // 1. Cấu hình client tạm thời
            ApiClient tempApiClient = new ApiClient();
            tempApiClient.setBasePath(server);
            ApiKeyAuth apikeyauth = (ApiKeyAuth) tempApiClient.getAuthentication("apikeyauth");
            apikeyauth.setApiKey(apiKey);
            tempApiClient.setAccessToken(token);

            // 2. Gọi API UserService để xác thực (logic từ HelloApplication cũ)
            UserServiceApi userServiceApi = new UserServiceApi(tempApiClient);
            embyclient.model.UserDto userDto = userServiceApi.getUsersById(userId);

            if (userDto != null) {
                // 3. Thành công! Cấu hình ApiClient MẶC ĐỊNH
                Configuration.setDefaultApiClient(tempApiClient);
                return userId;
            }
        } catch (Exception e) {
            System.err.println("Xác thực session thất bại: " + e.getMessage());
        }

        // Nếu thất bại, xóa session
        configRepo.clearSession();
        return null;
    }

    @Override
    public void validateSavedSessionAsync(Consumer<String> onSuccess, Runnable onFailure) {
        new Thread(() -> {
            String userId = validateSavedSession();
            if (userId != null) {
                onSuccess.accept(userId);
            } else {
                onFailure.run();
            }
        }).start();
    }

    @Override
    public void logout() {
        configRepo.clearSession();
        // Có thể gọi API /Sessions/Logout nếu muốn
    }
}