package com.embyhelper.view;

import com.embyhelper.di.ServiceLocator;
import com.embyhelper.viewmodel.LoginViewModel;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

public class LoginView {

    // --- FXML Controls ---
    @FXML private TextField txtServerAddress;
    @FXML private TextField txtApiKey;
    @FXML private TextField txtUsername;
    @FXML private PasswordField txtPassword;
    @FXML private Button btnConnect;
    @FXML private Label lblStatus;

    private LoginViewModel viewModel;

    @FXML
    public void initialize() {
        // 1. Lấy ViewModel từ ServiceLocator (DI)
        this.viewModel = ServiceLocator.getInstance().getLoginViewModel();

        // 2. Thiết lập Data Binding (Hai chiều)
        txtServerAddress.textProperty().bindBidirectional(viewModel.serverAddressProperty());
        txtApiKey.textProperty().bindBidirectional(viewModel.apiKeyProperty());
        txtUsername.textProperty().bindBidirectional(viewModel.usernameProperty());
        txtPassword.textProperty().bindBidirectional(viewModel.passwordProperty());

        // 3. Thiết lập Data Binding (Một chiều - từ VM -> View)
        lblStatus.textProperty().bind(viewModel.statusTextProperty());

        // Vô hiệu hóa button khi đang tải
        btnConnect.disableProperty().bind(viewModel.isLoadingProperty());
    }

    /**
     * FXML onAction="#onConnectButtonClick"
     * View chỉ gọi "Command" trên ViewModel.
     */
    @FXML
    protected void onConnectButtonClick() {
        viewModel.login();
    }
}