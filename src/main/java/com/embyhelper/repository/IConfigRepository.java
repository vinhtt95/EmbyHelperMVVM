package com.embyhelper.repository;

/**
 * Interface cho việc lưu trữ và truy xuất cấu hình.
 * (Trừu tượng hóa ConfigService cũ)
 */
public interface IConfigRepository {
    void saveLoginInfo(String server, String apiKey, String username);
    String getServerAddress();
    String getApiKey();
    String getUsername();

    void saveSession(String accessToken, String userId);
    String getAccessToken();
    String getUserId();
    void clearSession();

    void saveLanguage(String langCode);
    String getLanguage();

    void saveLoginWindowSize(double width, double height);
    double getLoginWindowWidth();
    double getLoginWindowHeight();

    void saveMainWindowSize(double width, double height);
    double getMainWindowWidth();
    double getMainWindowHeight();
}