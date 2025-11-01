package com.embyhelper.service;

import java.util.ResourceBundle;

public interface ILocalizationService {
    ResourceBundle getBundle();
    String getString(String key);
    void switchLanguage(String langCode);
}