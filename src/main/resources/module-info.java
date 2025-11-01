module com.embyhelper {
    // Yêu cầu các mô-đun JavaFX
    requires javafx.controls;
    requires javafx.fxml;

    // Yêu cầu các phụ thuộc
    requires eemby.sdk.java;
    requires java.sql;
    requires java.prefs;
    requires com.google.gson;
    requires org.json;

    // Mở (opens) các gói cho JavaFX FXML và reflection (Gson)
    opens com.embyhelper to javafx.fxml;
    opens com.embyhelper.view to javafx.fxml;
    opens com.embyhelper.viewmodel to javafx.fxml, javafx.base;
    opens com.embyhelper.model to com.google.gson;
    opens com.embyhelper.view.controls to javafx.fxml;
    opens com.embyhelper.usecase.metadata_strategy to javafx.base;

    // Xuất (exports) các gói chính
    exports com.embyhelper;
    exports com.embyhelper.view;
    exports com.embyhelper.viewmodel;
    exports com.embyhelper.model;
}