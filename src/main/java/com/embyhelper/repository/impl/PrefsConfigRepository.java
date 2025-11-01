package com.embyhelper.repository.impl;

import com.embyhelper.repository.IConfigRepository;
import java.util.prefs.Preferences;

/**
 * Triển khai IConfigRepository sử dụng Java Preferences.
 * (Đây là ConfigService cũ)
 */
public class PrefsConfigRepository implements IConfigRepository {

    private static final String PREF_NODE = "com/embyhelper";
    private static final String KEY_SERVER = "serverAddress";
    private static final String KEY_APIKEY = "apiKey";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_ACCESS_TOKEN = "accessToken";
    private static final String KEY_USER_ID = "userId";
    private static final String KEY_LANGUAGE = "language";
    private static final String KEY_LOGIN_WIDTH = "loginWidth";
    private static final String KEY_LOGIN_HEIGHT = "loginHeight";
    private static final String KEY_MAIN_WIDTH = "mainWidth";
    private static final String KEY_MAIN_HEIGHT = "mainHeight";

    private final Preferences prefs;

    public PrefsConfigRepository() {
        prefs = Preferences.userRoot().node(PREF_NODE);
    }

    private void flush() {
        try {
            prefs.flush();
        } catch (Exception e) {
            System.err.println("Lỗi flush preferences: " + e.getMessage());
        }
    }

    @Override
    public void saveLoginInfo(String server, String apiKey, String username) {
        prefs.put(KEY_SERVER, server != null ? server : "");
        prefs.put(KEY_APIKEY, apiKey != null ? apiKey : "");
        prefs.put(KEY_USERNAME, username != null ? username : "");
        flush();
    }

    @Override
    public String getServerAddress() { return prefs.get(KEY_SERVER, "http://localhost:8096/emby"); }

    @Override
    public String getApiKey() { return prefs.get(KEY_APIKEY, ""); }

    @Override
    public String getUsername() { return prefs.get(KEY_USERNAME, "admin"); }

    @Override
    public void saveSession(String accessToken, String userId) {
        prefs.put(KEY_ACCESS_TOKEN, accessToken != null ? accessToken : "");
        prefs.put(KEY_USER_ID, userId != null ? userId : "");
        flush();
    }

    @Override
    public String getAccessToken() {
        String token = prefs.get(KEY_ACCESS_TOKEN, null);
        return (token == null || token.isEmpty()) ? null : token;
    }

    @Override
    public String getUserId() {
        String userId = prefs.get(KEY_USER_ID, null);
        return (userId == null || userId.isEmpty()) ? null : userId;
    }

    @Override
    public void clearSession() {
        prefs.remove(KEY_ACCESS_TOKEN);
        prefs.remove(KEY_USER_ID);
        flush();
        System.out.println("Đã xóa session đã lưu.");
    }

    @Override
    public void saveLanguage(String langCode) {
        prefs.put(KEY_LANGUAGE, langCode);
        flush();
    }

    @Override
    public String getLanguage() { return prefs.get(KEY_LANGUAGE, "vi"); }

    @Override
    public void saveLoginWindowSize(double width, double height) {
        prefs.putDouble(KEY_LOGIN_WIDTH, width);
        prefs.putDouble(KEY_LOGIN_HEIGHT, height);
        flush();
    }

    @Override
    public double getLoginWindowWidth() { return prefs.getDouble(KEY_LOGIN_WIDTH, 450); }

    @Override
    public double getLoginWindowHeight() { return prefs.getDouble(KEY_LOGIN_HEIGHT, 500); }

    @Override
    public void saveMainWindowSize(double width, double height) {
        prefs.putDouble(KEY_MAIN_WIDTH, width);
        prefs.putDouble(KEY_MAIN_HEIGHT, height);
        flush();
    }

    @Override
    public double getMainWindowWidth() { return prefs.getDouble(KEY_MAIN_WIDTH, 900); }

    @Override
    public double getMainWindowHeight() { return prefs.getDouble(KEY_MAIN_HEIGHT, 850); }
}