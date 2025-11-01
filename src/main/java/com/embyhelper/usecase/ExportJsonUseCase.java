package com.embyhelper.usecase;

import com.embyhelper.repository.IEmbyRepository;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import embyclient.model.BaseItemDto;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.function.Consumer;
import embyclient.JSON.OffsetDateTimeTypeAdapter; // Lấy từ MainController

public class ExportJsonUseCase {

    private final IEmbyRepository embyRepo;
    private static final Gson gson = new GsonBuilder()
            .registerTypeAdapter(OffsetDateTime.class, new OffsetDateTimeTypeAdapter())
            .setPrettyPrinting()
            .create();

    public ExportJsonUseCase(IEmbyRepository embyRepo) {
        this.embyRepo = embyRepo;
    }

    public static class ExportResult {
        public int successCount = 0;
        public int errorCount = 0;
    }

    public ExportResult execute(String parentId, File directory, Consumer<String> progressCallback) {
        ExportResult result = new ExportResult();
        progressCallback.accept("Đang lấy danh sách item từ Parent ID: " + parentId);

        List<BaseItemDto> itemsToExport = embyRepo.getItemsByParentId(parentId, null, null, true, "Movie"); // Giả sử chỉ Movie
        if (itemsToExport == null || itemsToExport.isEmpty()) {
            progressCallback.accept("Không tìm thấy item con nào trong Parent ID: " + parentId);
            return result;
        }

        int total = itemsToExport.size();
        progressCallback.accept("Tìm thấy " + total + " items. Bắt đầu xuất...");
        String directoryPath = directory.getAbsolutePath();

        for (int i = 0; i < total; i++) {
            BaseItemDto listItem = itemsToExport.get(i);
            String itemId = listItem.getId();
            String itemName = listItem.getName();

            if (itemId == null) {
                System.err.println("Bỏ qua item vì không có ID: " + itemName);
                result.errorCount++;
                continue;
            }

            progressCallback.accept(String.format("Đang xuất: (%d/%d) %s", (i + 1), total, itemName));

            try {
                BaseItemDto fullItem = embyRepo.getItemInfo(itemId);
                if (fullItem == null) {
                    System.err.println("Lỗi: Không thể lấy full info cho item ID: " + itemId);
                    result.errorCount++;
                    continue;
                }

                String originalTitle = fullItem.getOriginalTitle();
                String baseFileName = (originalTitle != null && !originalTitle.isEmpty()) ? originalTitle : itemName;
                String safeFileName = baseFileName.replaceAll("[^a-zA-Z0-9.-]", "_") + ".json";
                File outputFile = new File(directoryPath, safeFileName);

                try (FileWriter writer = new FileWriter(outputFile)) {
                    gson.toJson(fullItem, writer);
                    result.successCount++;
                } catch (IOException ioEx) {
                    System.err.println("Lỗi khi ghi file JSON: " + safeFileName + " - " + ioEx.getMessage());
                    result.errorCount++;
                }
            } catch (Exception e) {
                System.err.println("Lỗi nghiêm trọng khi xử lý item ID: " + itemId + " - " + e.getMessage());
                e.printStackTrace();
                result.errorCount++;
            }
        }

        progressCallback.accept(String.format("Xuất hoàn tất! Thành công: %d, Lỗi: %d. Đã lưu tại: %s",
                result.successCount, result.errorCount, directoryPath));
        return result;
    }
}