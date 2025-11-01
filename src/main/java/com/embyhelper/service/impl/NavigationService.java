package com.embyhelper.service.impl;

import com.embyhelper.MainApplication;
import com.embyhelper.di.ServiceLocator;
import com.embyhelper.repository.IConfigRepository;
import com.embyhelper.service.ILocalizationService;
import com.embyhelper.service.INavigationService;
import com.embyhelper.view.MainView; // <-- THÊM IMPORT
import com.embyhelper.viewmodel.MainViewModel; // <-- THÊM IMPORT
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class NavigationService implements INavigationService {

    private final ILocalizationService localizationService;
    private Stage primaryStage;

    private javafx.beans.value.ChangeListener<Number> widthListener;
    private javafx.beans.value.ChangeListener<Number> heightListener;

    public NavigationService(ILocalizationService localizationService) {
        this.localizationService = localizationService;
    }

    @Override
    public void setPrimaryStage(Stage stage) {
        this.primaryStage = stage;
    }

    @Override
    public void showLoginView() {
        try {
            if (primaryStage == null) return;

            FXMLLoader fxmlLoader = createLoader("login-view.fxml");

            IConfigRepository configRepo = ServiceLocator.getInstance().getConfigRepository();
            double width = configRepo.getLoginWindowWidth();
            double height = configRepo.getLoginWindowHeight();
            Scene scene = new Scene(fxmlLoader.load(), width, height);

            clearWindowSizeListeners();
            widthListener = (obs, oldVal, newVal) -> configRepo.saveLoginWindowSize(primaryStage.getWidth(), primaryStage.getHeight());
            heightListener = (obs, oldVal, newVal) -> configRepo.saveLoginWindowSize(primaryStage.getWidth(), primaryStage.getHeight());
            primaryStage.widthProperty().addListener(widthListener);
            primaryStage.heightProperty().addListener(heightListener);

            applyStylesAndShow(scene, localizationService.getString("login.title"));

        } catch (Exception e) {
            System.err.println("Lỗi nghiêm trọng khi tải LoginView:");
            e.printStackTrace();
        }
    }

    @Override
    public void showMainView(String userId) {
        try {
            if (primaryStage == null) return;

            // SỬA LỖI: Logic DI (Dependency Injection)

            // 1. Tạo ViewModel TRƯỚC
            MainViewModel mainViewModel = ServiceLocator.getInstance().getMainViewModel(userId);

            // 2. Tạo FXML Loader
            FXMLLoader fxmlLoader = createLoader("main-view.fxml");

            IConfigRepository configRepo = ServiceLocator.getInstance().getConfigRepository();
            double width = configRepo.getMainWindowWidth();
            double height = configRepo.getMainWindowHeight();

            // 3. Tải FXML (Việc này sẽ tạo ra Controller - MainView)
            Scene scene = new Scene(fxmlLoader.load(), width, height);

            // 4. Lấy Controller (View) từ Loader
            MainView controller = fxmlLoader.getController();

            // 5. TIÊM (Inject) ViewModel vào Controller
            controller.setViewModel(mainViewModel);
            // (Bây giờ MainView.setViewModel() sẽ chạy và thực hiện binding)

            // 6. Cập nhật listeners cho cửa sổ
            clearWindowSizeListeners();
            widthListener = (obs, oldVal, newVal) -> configRepo.saveMainWindowSize(primaryStage.getWidth(), primaryStage.getHeight());
            heightListener = (obs, oldVal, newVal) -> configRepo.saveMainWindowSize(primaryStage.getWidth(), primaryStage.getHeight());
            primaryStage.widthProperty().addListener(widthListener);
            primaryStage.heightProperty().addListener(heightListener);

            // 7. Hiển thị
            applyStylesAndShow(scene, "Emby Helper Dashboard");

        } catch (Exception e) {
            System.err.println("Lỗi nghiêm trọng khi tải MainView:");
            e.printStackTrace();
        }
    }

    @Override
    public void restartApplication() {
        if (primaryStage != null) {
            primaryStage.close();
        }
        Platform.runLater(() -> {
            try {
                Stage newStage = new Stage();
                new MainApplication().start(newStage);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    // --- Helper Methods ---

    private FXMLLoader createLoader(String fxmlFile) {
        String fxmlPath = "view/" + fxmlFile;
        FXMLLoader fxmlLoader = new FXMLLoader(MainApplication.class.getResource(fxmlPath));
        fxmlLoader.setResources(localizationService.getBundle());
        return fxmlLoader;
    }

    private void applyStylesAndShow(Scene scene, String title) {
        String cssPath = "view/style.css";
        String css = MainApplication.class.getResource(cssPath).toExternalForm();
        scene.getStylesheets().add(css);
        primaryStage.setTitle(title);
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    private void clearWindowSizeListeners() {
        if (widthListener != null) {
            primaryStage.widthProperty().removeListener(widthListener);
        }
        if (heightListener != null) {
            primaryStage.heightProperty().removeListener(heightListener);
        }
    }
}