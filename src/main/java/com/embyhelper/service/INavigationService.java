package com.embyhelper.service;

import javafx.stage.Stage;

public interface INavigationService {
    void setPrimaryStage(Stage stage);
    void showLoginView();
    void showMainView(String userId);
    void restartApplication();
}