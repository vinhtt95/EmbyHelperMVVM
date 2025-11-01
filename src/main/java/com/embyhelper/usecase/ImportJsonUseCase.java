package com.embyhelper.usecase;

import com.embyhelper.repository.IEmbyRepository;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import embyclient.model.BaseItemDto;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import embyclient.JSON.OffsetDateTimeTypeAdapter;

public class ImportJsonUseCase {

    private final IEmbyRepository embyRepo;
    private static final Gson gson = new GsonBuilder()
            .registerTypeAdapter(OffsetDateTime.class, new OffsetDateTimeTypeAdapter())
            .setPrettyPrinting()
            .create();

    public ImportJsonUseCase(IEmbyRepository embyRepo) {
        this.embyRepo = embyRepo;
    }

    public static class ImportResult {
        public int successCount = 0;
        public int notFoundCount = 0;
        public int ambiguousCount = 0; // Bị trùng
        public int errorCount = 0;
    }

    public ImportResult execute(String parentId, File directory, Consumer<String> progressCallback) {
        ImportResult result = new ImportResult();
        progressCallback.accept("Đang quét file JSON trong: " + directory.getName());

        // 1. Lấy danh sách file .json (đệ quy)
        List<File> jsonFiles = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(directory.toPath())) {
            jsonFiles = stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().toLowerCase().endsWith(".json"))
                    .map(Path::toFile)
                    .collect(Collectors.toList());
        } catch (IOException e) {
            progressCallback.accept("Lỗi quét thư mục: " + e.getMessage());
            return result;
        }

        if (jsonFiles.isEmpty()) {
            progressCallback.accept("Không tìm thấy file .json nào.");
            return result;
        }

        int total = jsonFiles.size();
        progressCallback.accept("Tìm thấy " + total + " file .json. Bắt đầu nhập...");

        for (int i = 0; i < total; i++) {
            File jsonFile = jsonFiles.get(i);
            String fileName = jsonFile.getName();
            progressCallback.accept(String.format("Đang nhập file: (%d/%d) %s", (i + 1), total, fileName));

            try {
                // 2. Đọc và Parse JSON
                BaseItemDto itemFromFile;
                try (FileReader reader = new FileReader(jsonFile)) {
                    itemFromFile = gson.fromJson(reader, BaseItemDto.class);
                }

                if (itemFromFile == null) {
                    System.err.println("Lỗi parse file: " + fileName);
                    result.errorCount++;
                    continue;
                }

                // 3. Lấy OriginalTitle
                String originalTitle = itemFromFile.getOriginalTitle();
                if (originalTitle == null || originalTitle.isEmpty()) {
                    System.err.println("Bỏ qua file (không có OriginalTitle): " + fileName);
                    result.errorCount++;
                    continue;
                }

                // 4. Tìm item trên server
                List<BaseItemDto> itemsOnServer = findItemsByOriginalTitle(parentId, originalTitle);

                // 5. Xử lý logic (từ MainController cũ)
                if (itemsOnServer == null || itemsOnServer.isEmpty()) {
                    System.err.println(String.format("Không tìm thấy item khớp: %s (File: %s)", originalTitle, fileName));
                    result.notFoundCount++;
                    continue;
                }
//                else if (itemsOnServer.size() > 1) {
//                    // Logic cũ của bạn đã comment phần này, tôi giữ nguyên
//                    System.err.println(String.format("Bỏ qua (nhiều kết quả): %d items cho %s (File: %s)", itemsOnServer.size(), originalTitle, fileName));
//                    result.ambiguousCount++;
//                    continue;
//                }
                else {
                    // Logic cũ: update TẤT CẢ các item tìm thấy
                    for (BaseItemDto itemOnServer : itemsOnServer) {
                        if(itemOnServer.getId().equals(itemFromFile.getId())) {
                            continue; // Bỏ qua nếu là chính nó
                        }

                        System.out.println("Tìm thấy item: " + itemOnServer.getName() + " (ID: " + itemOnServer.getId() + "). Đang cập nhật...");
                        if (updateItemFromJson(itemOnServer, itemFromFile)) {
                            System.out.println("Update thành công cho: " + originalTitle);
                            result.successCount++;
                        } else {
                            System.err.println("Update thất bại (API) cho: " + originalTitle);
                            result.errorCount++;
                        }
                    }
                }

            } catch (JsonSyntaxException jsonEx) {
                System.err.println("Lỗi cú pháp JSON: " + fileName + " - " + jsonEx.getMessage());
                result.errorCount++;
            } catch (Exception e) {
                System.err.println("Lỗi nghiêm trọng khi xử lý file: " + fileName + " - " + e.getMessage());
                e.printStackTrace();
                result.errorCount++;
            }
        }

        progressCallback.accept(String.format("Nhập JSON hoàn tất! Thành công: %d, Không tìm thấy: %d, Trùng lặp: %d, Lỗi: %d",
                result.successCount, result.notFoundCount, result.ambiguousCount, result.errorCount));
        return result;
    }

    // --- Logic nghiệp vụ (từ ItemService cũ) ---

    private List<BaseItemDto> findItemsByOriginalTitle(String parentId, String originalTitle) {
        if (originalTitle == null || originalTitle.isEmpty()) {
            return new ArrayList<>();
        }

        // 1. Dùng API search (đã tối ưu trong EmbyRepositoryImpl)
        List<BaseItemDto> searchResult = embyRepo.searchItems(originalTitle, parentId, "OriginalTitle", "Movie", true);

        if (searchResult == null || searchResult.isEmpty()) {
            return new ArrayList<>();
        }

        List<BaseItemDto> matchingFullItems = new ArrayList<>();

        // 2. Lọc lại kết quả KHỚP CHÍNH XÁC
        for (BaseItemDto stubItem : searchResult) {
            if (stubItem.getOriginalTitle() != null && originalTitle.equalsIgnoreCase(stubItem.getOriginalTitle())) {
                // Đã tìm thấy! Lấy full info
                BaseItemDto fullItem = embyRepo.getItemInfo(stubItem.getId());
                if (fullItem != null) {
                    matchingFullItems.add(fullItem);
                }
            }
        }
        return matchingFullItems;
    }

    private boolean updateItemFromJson(BaseItemDto itemOnServer, BaseItemDto itemFromFile) {
        if (itemOnServer == null || itemFromFile == null) {
            return false;
        }

        String serverId = itemOnServer.getId();

        // Sao chép metadata
        itemOnServer.setName(itemFromFile.getName());
        itemOnServer.setOriginalTitle(itemFromFile.getOriginalTitle());
        itemOnServer.setPremiereDate(itemFromFile.getPremiereDate());
        itemOnServer.setProductionYear(itemFromFile.getProductionYear());
        itemOnServer.setSortName(itemFromFile.getSortName());
        itemOnServer.setOverview(itemFromFile.getOverview());

        itemOnServer.getStudios().clear();
        if (itemFromFile.getStudios() != null) {
            itemOnServer.getStudios().addAll(itemFromFile.getStudios());
        }

        itemOnServer.getGenreItems().clear();
        if (itemFromFile.getGenreItems() != null) {
            itemOnServer.getGenreItems().addAll(itemFromFile.getGenreItems());
        }

        itemOnServer.getPeople().clear();
        if (itemFromFile.getPeople() != null) {
            itemOnServer.getPeople().addAll(itemFromFile.getPeople());
        }

        itemOnServer.getTagItems().clear();
        if (itemFromFile.getTagItems() != null) {
            itemOnServer.getTagItems().addAll(itemFromFile.getTagItems());
        }

        return embyRepo.updateItemInfo(serverId, itemOnServer);
    }
}