package com.embyhelper.usecase;

import com.embyhelper.repository.DownloadedImage; // THÊM IMPORT
import com.embyhelper.repository.IEmbyRepository;
import embyclient.model.BaseItemDto;
import embyclient.model.ImageInfo; // THÊM IMPORT
import embyclient.model.ImageType; // THÊM IMPORT
import java.util.List; // THÊM IMPORT
import java.util.Map; // THÊM IMPORT
import java.util.function.Consumer;
/**
 * UseCase cho logic "Sao chép Metadata"
 */
public class CopyMetadataUseCase {

    private final IEmbyRepository embyRepo;

    public CopyMetadataUseCase(IEmbyRepository embyRepo) {
        this.embyRepo = embyRepo;
    }

    public void execute(String sourceItemId, String destinationItemId, Consumer<String> progressCallback) {
        progressCallback.accept("Đang lấy full metadata từ item nguồn (ID: " + sourceItemId + ")");
        BaseItemDto sourceItem = embyRepo.getItemInfo(sourceItemId);
        if (sourceItem == null) {
            progressCallback.accept("LỖI: Không thể lấy thông tin item nguồn.");
            return;
        }

        progressCallback.accept("Đang lấy full metadata từ item đích (ID: " + destinationItemId + ")");
        BaseItemDto itemOnServer = embyRepo.getItemInfo(destinationItemId);
        if (itemOnServer == null) {
            progressCallback.accept("LỖI: Không thể lấy thông tin item đích.");
            return;
        }

        progressCallback.accept("Đang sao chép metadata từ '" + sourceItem.getName() + "' sang '" + itemOnServer.getName() + "'...");

        // Sao chép metadata
        if (updateItemFromSource(itemOnServer, sourceItem)) {
            progressCallback.accept("Cập nhật thành công cho: " + itemOnServer.getOriginalTitle());
        } else {
            progressCallback.accept("Cập nhật thất bại (API) cho: " + itemOnServer.getOriginalTitle());
        }

        try {
            // 1. Lấy danh sách ảnh
            progressCallback.accept("Đang lấy danh sách ảnh nguồn...");
            List<ImageInfo> sourceImages = embyRepo.getItemImages(sourceItemId);
            List<ImageInfo> destImages = embyRepo.getItemImages(destinationItemId);

            // 2. Xóa ảnh cũ ở đích
            progressCallback.accept("Đang xóa " + destImages.size() + " ảnh cũ ở đích...");
            for (ImageInfo img : destImages) {
                embyRepo.deleteImage(destinationItemId, img.getImageType(), img.getImageIndex());
            }

            // 3. Sao chép ảnh mới
            progressCallback.accept("Đang sao chép " + sourceImages.size() + " ảnh mới...");
            String serverUrl = embyRepo.getBasePath();

            for (int i = 0; i < sourceImages.size(); i++) {
                ImageInfo img = sourceImages.get(i);
                ImageType type = img.getImageType();
                if (type == null) continue;

                progressCallback.accept(String.format("Đang copy ảnh (%d/%d): %s", (i+1), sourceImages.size(), type.getValue()));

                // 3a. Build URL (Học từ ItemDetailLoader)
                String imageUrl = null;
                if (type == ImageType.PRIMARY) {
                    Map<String, String> imageTags = sourceItem.getImageTags(); // Dùng sourceItem (metadata)
                    if (imageTags != null && imageTags.containsKey("Primary")) {
                        String tag = imageTags.get("Primary");
                        imageUrl = String.format("%s/Items/%s/Images/Primary?tag=%s", serverUrl, sourceItemId, tag);
                    }
                } else {
                    // Dùng ImageIndex (Học từ ImageUrlHelper)
                    Integer index = img.getImageIndex();
                    if (index != null) {
                        imageUrl = String.format("%s/Items/%s/Images/%s/%d", serverUrl, sourceItemId, type.getValue(), index);
                    }
                }

                if (imageUrl == null) {
                    progressCallback.accept("Bỏ qua ảnh: không thể tạo URL.");
                    continue;
                }

                // 3b. Download
                DownloadedImage downloadedImg = embyRepo.downloadImage(imageUrl);

                // 3c. Upload
                embyRepo.uploadImage(destinationItemId, type, downloadedImg.bytes, downloadedImg.mediaType);
            }
            progressCallback.accept("Sao chép ảnh hoàn tất!");

        } catch (Exception e) {
            progressCallback.accept("LỖI khi sao chép ảnh: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Logic sao chép, được lấy từ ImportJsonUseCase.
     */
    private boolean updateItemFromSource(BaseItemDto itemOnServer, BaseItemDto sourceItem) {
        if (itemOnServer == null || sourceItem == null) {
            return false;
        }

        String serverId = itemOnServer.getId();

        // Sao chép metadata
        itemOnServer.setName(sourceItem.getName());
        itemOnServer.setOriginalTitle(sourceItem.getOriginalTitle());
        itemOnServer.setPremiereDate(sourceItem.getPremiereDate());
        itemOnServer.setProductionYear(sourceItem.getProductionYear());
        itemOnServer.setSortName(sourceItem.getSortName());
        itemOnServer.setOverview(sourceItem.getOverview());

        itemOnServer.getStudios().clear();
        if (sourceItem.getStudios() != null) {
            itemOnServer.getStudios().addAll(sourceItem.getStudios());
        }

        itemOnServer.getGenreItems().clear();
        if (sourceItem.getGenreItems() != null) {
            itemOnServer.getGenreItems().addAll(sourceItem.getGenreItems());
        }

        itemOnServer.getPeople().clear();
        if (sourceItem.getPeople() != null) {
            itemOnServer.getPeople().addAll(sourceItem.getPeople());
        }

        itemOnServer.getTagItems().clear();
        if (sourceItem.getTagItems() != null) {
            itemOnServer.getTagItems().addAll(sourceItem.getTagItems());
        }

        return embyRepo.updateItemInfo(serverId, itemOnServer);
    }
}