package com.embyhelper.di;

import com.embyhelper.repository.IConfigRepository;
import com.embyhelper.repository.IEmbyRepository;
import com.embyhelper.repository.impl.EmbyRepositoryImpl;
import com.embyhelper.repository.impl.PrefsConfigRepository;
import com.embyhelper.service.IAuthService;
import com.embyhelper.service.ILocalizationService;
import com.embyhelper.service.INavigationService;
import com.embyhelper.service.impl.AuthService;
import com.embyhelper.service.impl.LocalizationService;
import com.embyhelper.service.impl.NavigationService;
import com.embyhelper.usecase.BatchProcessUseCase;
import com.embyhelper.usecase.ExportJsonUseCase;
import com.embyhelper.usecase.ImportJsonUseCase;
import com.embyhelper.viewmodel.BatchViewModel;
import com.embyhelper.viewmodel.LoginViewModel;
import com.embyhelper.viewmodel.MainViewModel;
import com.embyhelper.viewmodel.MetadataViewModel;

import java.util.ResourceBundle;

public class ServiceLocator {
    private static final ServiceLocator instance = new ServiceLocator();
    public static ServiceLocator getInstance() { return instance; }

    // (Các dịch vụ và use cases không đổi)
    private final IConfigRepository configRepository;
    private final ILocalizationService localizationService;
    private final INavigationService navigationService;
    private final IAuthService authService;
    private final IEmbyRepository embyRepository;
    private final BatchProcessUseCase batchProcessUseCase;
    private final ExportJsonUseCase exportJsonUseCase;
    private final ImportJsonUseCase importJsonUseCase;


    private ServiceLocator() {
        this.configRepository = new PrefsConfigRepository();
        this.localizationService = new LocalizationService(configRepository);
        this.navigationService = new NavigationService(localizationService);
        this.embyRepository = new EmbyRepositoryImpl();
        this.authService = new AuthService(configRepository, embyRepository);
        this.batchProcessUseCase = new BatchProcessUseCase(embyRepository);
        this.exportJsonUseCase = new ExportJsonUseCase(embyRepository);
        this.importJsonUseCase = new ImportJsonUseCase(embyRepository);
    }

    // (Getters cho dịch vụ không đổi)
    public IConfigRepository getConfigRepository() { return configRepository; }
    public ILocalizationService getLocalizationService() { return localizationService; }
    public INavigationService getNavigationService() { return navigationService; }
    public IAuthService getAuthService() { return authService; }
    public IEmbyRepository getEmbyRepository() { return embyRepository; }


    public LoginViewModel getLoginViewModel() {
        return new LoginViewModel(
                authService,
                navigationService,
                configRepository,
                localizationService
        );
    }

    public MainViewModel getMainViewModel(String userId) {
        if (userId != null) {
            embyRepository.initialize(userId);
        }
        ResourceBundle bundle = localizationService.getBundle();

        MetadataViewModel metadataVM = new MetadataViewModel(embyRepository, bundle);

        BatchViewModel batchVM = new BatchViewModel(
                batchProcessUseCase,
                exportJsonUseCase,
                importJsonUseCase,
                bundle
        );

        // ----- SỬA LỖI TẠI ĐÂY -----
        // Thêm `authService` vào hàm tạo của MainViewModel
        return new MainViewModel(
                navigationService,
                localizationService,
                authService, // <-- ĐÃ THÊM THAM SỐ CÒN THIẾU
                metadataVM,
                batchVM
        );
        // ----- KẾT THÚC SỬA LỖI -----
    }
}