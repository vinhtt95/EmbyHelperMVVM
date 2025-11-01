package com.embyhelper.usecase;

import com.embyhelper.repository.IEmbyRepository;
import embyclient.model.BaseItemDto;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONObject;

/**
 * UseCase cho logic "Batch Process Title & Release Date".
 * (Logic từ ItemService.checkAndProcessItemTitleAndDate và MainController.batchProcessTask)
 */
public class BatchProcessUseCase {

    private final IEmbyRepository embyRepo;

    public BatchProcessUseCase(IEmbyRepository embyRepo) {
        this.embyRepo = embyRepo;
    }

    public static class BatchProcessResult {
        public int successCount = 0;
        public int skippedCount = 0;
        public int errorCount = 0;
    }

    public BatchProcessResult execute(String parentId, Consumer<String> progressCallback) {
        BatchProcessResult result = new BatchProcessResult();
        progressCallback.accept("Đang lấy danh sách item từ Parent ID: " + parentId);

        // Lấy các item thuộc loại Movie
        List<BaseItemDto> itemsToProcess = embyRepo.getItemsByParentId(parentId, null, null, true, "Movie");
        if (itemsToProcess == null || itemsToProcess.isEmpty()) {
            progressCallback.accept("Không tìm thấy item con nào (Movie) trong Parent ID: " + parentId);
            return result;
        }

        int total = itemsToProcess.size();
        progressCallback.accept("Tìm thấy " + total + " movie. Bắt đầu xử lý...");

        for (int i = 0; i < total; i++) {
            BaseItemDto listItem = itemsToProcess.get(i);
            String itemId = listItem.getId();
            String itemName = listItem.getName();

            if (itemId == null) {
                System.err.println("Bỏ qua item vì không có ID: " + itemName);
                result.errorCount++;
                continue;
            }

            progressCallback.accept(String.format("Đang xử lý: (%d/%d) %s", (i + 1), total, itemName));

            try {
                BaseItemDto fullItem = embyRepo.getItemInfo(itemId);
                if (fullItem == null) {
                    System.err.println("Lỗi: Không thể lấy full info cho item ID: " + itemId);
                    result.errorCount++;
                    continue;
                }

                // Áp dụng logic nghiệp vụ
                boolean hasChanged = checkAndProcessItemTitleAndDate(fullItem);

                if (hasChanged) {
                    if (embyRepo.updateItemInfo(fullItem.getId(), fullItem)) {
                        System.out.println("Update thành công: " + itemName);
                        result.successCount++;
                    } else {
                        System.err.println("Update thất bại (API): " + itemName);
                        result.errorCount++;
                    }
                } else {
                    System.out.println("Bỏ qua (không thay đổi): " + itemName);
                    result.skippedCount++;
                }
            } catch (Exception e) {
                System.err.println("Lỗi nghiêm trọng khi xử lý item ID: " + itemId + " - " + e.getMessage());
                e.printStackTrace();
                result.errorCount++;
            }
        }

        progressCallback.accept(String.format("Xử lý hàng loạt hoàn tất! Cập nhật: %d, Bỏ qua: %d, Lỗi: %d",
                result.successCount, result.skippedCount, result.errorCount));
        return result;
    }

    // --- Logic nghiệp vụ (từ ItemService cũ) ---

    private boolean checkAndProcessItemTitleAndDate(BaseItemDto itemInfo) {
        boolean isUpdate = false;
        String originalTitle = itemInfo.getOriginalTitle();

        // 1. Xử lý Original Title (Logic cũ của bạn là 'if (true || ...)' nên nó luôn chạy)
        String fileName = itemInfo.getFileName();
        if(fileName != null && !fileName.isEmpty()) {
            String name = fileName.substring(0, fileName.lastIndexOf('.'));
            String newName = normalizeFileName(name);

            if (newName.isEmpty()) {
                newName = nameType2(itemInfo.getName()); // Thử fallback về item name
            }

            // Chỉ set nếu khác
            if (!newName.equals(originalTitle)) {
                itemInfo.setOriginalTitle(newName);
                originalTitle = newName; // Cập nhật biến local để dùng cho bước 2
                isUpdate = true;
                System.out.println("Đã set OriginalTitle: " + newName + " cho item: " + itemInfo.getName());
            }
        } else if (originalTitle == null || originalTitle.isEmpty()) {
            // Fallback nếu không có filename
            String newName = nameType2(itemInfo.getName());
            if (!newName.equals(originalTitle)) {
                itemInfo.setOriginalTitle(newName);
                originalTitle = newName;
                isUpdate = true;
            }
        }

        // 2. Xử lý Premiere Date
        if (itemInfo.getPremiereDate() == null) {
            OffsetDateTime releaseDate = null;
            if (originalTitle != null && !originalTitle.isEmpty()) {
                releaseDate = setDateRelease(originalTitle); // Thử lấy theo OriginalTitle
            }

            if (releaseDate == null) { // Nếu thất bại, thử lấy theo Item Name
                releaseDate = setDateRelease(itemInfo.getName());
            }

            if (releaseDate != null) {
                itemInfo.setPremiereDate(releaseDate);
                isUpdate = true;
                System.out.println("Đã set PremiereDate: " + releaseDate + " cho item: " + itemInfo.getName());
            }
        }

        // 3. Xử lý Production Year
        Integer currentYear = itemInfo.getProductionYear();
        Integer yearFromPremiere = null;

        if (itemInfo.getPremiereDate() != null) {
            yearFromPremiere = itemInfo.getPremiereDate().getYear();
        }

        if (yearFromPremiere != null) {
            if (currentYear == null || !currentYear.equals(yearFromPremiere)) {
                itemInfo.setProductionYear(yearFromPremiere);
                isUpdate = true;
                System.out.println("Đã set/update ProductionYear: " + yearFromPremiere + " cho item: " + itemInfo.getName());
            }
        }
        return isUpdate;
    }

    private String nameType2(String input) {
        if (input == null || input.isEmpty()) return "";
        return input.replaceAll("([a-zA-Z]+)(\\d+)", "$1-$2");
    }

    private static final Pattern NORMALIZE_PATTERN =
            Pattern.compile("^[^a-zA-Z]*([a-zA-Z]+)[^a-zA-Z0-9]*(\\d+)");

    private String normalizeFileName(String input) {
        if (input == null || input.isEmpty()) return "";
        Matcher matcher = NORMALIZE_PATTERN.matcher(input);
        if (matcher.find()) {
            String letters = matcher.group(1);
            String numbers = matcher.group(2);
            return letters.toUpperCase() + "-" + numbers;
        }
        return ""; // Trả về rỗng nếu không khớp
    }

    private OffsetDateTime setDateRelease(String code) {
        if (code == null || code.isEmpty()) return null;
        // API này là của bạn, tôi giữ nguyên
        String apiUrl = "http://localhost:8081/movies/movie/date/?movieCode=" + code;
        HttpURLConnection connection = null;
        try {
            URL url = new URL(apiUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "application/json");
            connection.setConnectTimeout(2000); // Giảm timeout
            connection.setReadTimeout(2000);

            if (connection.getResponseCode() == 200) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) response.append(line);

                    JSONObject jsonResponse = new JSONObject(response.toString());
                    String dataValue = jsonResponse.optString("data", null);
                    if (dataValue != null && !dataValue.equals("null")) {
                        return OffsetDateTime.parse(dataValue);
                    }
                }
            }
        } catch (Exception e) {
            // Bỏ qua lỗi (ví dụ: API không chạy, timeout, 404)
            // System.err.println("API setDateRelease lỗi cho code " + code + ": " + e.getMessage());
        } finally {
            if (connection != null) connection.disconnect();
        }
        return null;
    }
}