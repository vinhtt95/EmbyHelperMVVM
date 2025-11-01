package com.embyhelper.service.impl;

import com.embyhelper.repository.IConfigRepository;
import com.embyhelper.service.ILocalizationService;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.PropertyResourceBundle;
import java.util.ResourceBundle;

public class LocalizationService implements ILocalizationService {

    private final IConfigRepository configRepo;
    private ResourceBundle bundle;

    public LocalizationService(IConfigRepository configRepo) {
        this.configRepo = configRepo;
        loadBundle();
    }

    private void loadBundle() {
        String lang = configRepo.getLanguage();
        Locale currentLocale = Locale.of(lang);
        Locale.setDefault(currentLocale);

        // Tải gói ngôn ngữ (ví dụ: messages_vi.properties)
        this.bundle = loadBundleInternal(currentLocale.getLanguage());

        // Fallback sang tiếng Anh nếu không tải được
        if (this.bundle == null && !currentLocale.getLanguage().equals("en")) {
            System.out.println("Không tìm thấy file locale " + lang + ". Đang thử tải tiếng Anh.");
            this.bundle = loadBundleInternal("en");
        }

        // Fallback cuối cùng nếu cả tiếng Anh cũng lỗi
        if (this.bundle == null) {
            System.err.println("KHÔNG THỂ TẢI BẤT KỲ FILE PROPERTIES NÀO. SỬ DỤNG FALLBACK.");
            // Sử dụng ResourceBundle.getBundle để Java tự tìm (có thể không đúng encoding)
            this.bundle = ResourceBundle.getBundle("com.embyhelper.i18n.messages", currentLocale);
        }
    }

    // Logic tải file .properties với encoding UTF-8 (từ HelloApplication cũ)
    private ResourceBundle loadBundleInternal(String langCode) {
        String bundlePath = "com/embyhelper/i18n/messages_" + langCode + ".properties";
        try (InputStream is = LocalizationService.class.getClassLoader().getResourceAsStream(bundlePath)) {
            if (is != null) {
                try (InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
                    return new PropertyResourceBundle(reader);
                }
            }
        } catch (Exception e) {
            System.err.println("Lỗi đọc file properties cho locale " + langCode + ": " + e.getMessage());
        }
        return null;
    }

    @Override
    public ResourceBundle getBundle() {
        return bundle;
    }

    @Override
    public String getString(String key) {
        try {
            return bundle.getString(key);
        } catch (Exception e) {
            System.err.println("Không tìm thấy key i18n: " + key);
            return "!" + key + "!"; // Trả về key nếu không tìm thấy
        }
    }

    @Override
    public void switchLanguage(String langCode) {
        configRepo.saveLanguage(langCode);
        // NavigationService sẽ gọi restartApplication()
    }
}