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
        // (SỬA ĐỔI) Hàm helper nội tuyến để kiểm tra null
        Consumer<String> safeCallback = (msg) -> {
            if (progressCallback != null) {
                progressCallback.accept(msg);
            }
        };

        safeCallback.accept("Đang lấy full metadata từ item nguồn (ID: " + sourceItemId + ")");
        BaseItemDto sourceItem = embyRepo.getItemInfo(sourceItemId);
        if (sourceItem == null) {
            safeCallback.accept("LỖI: Không thể lấy thông tin item nguồn.");
            return;
        }

        safeCallback.accept("Đang sao chép metadata từ '" + sourceItem.getName() + "' sang item đích (ID: " + destinationItemId + ")...");

        sourceItem.setId(destinationItemId);

        if (embyRepo.updateItemInfo(destinationItemId, sourceItem)) {
            safeCallback.accept("Cập nhật metadata thành công.");
        } else {
            safeCallback.accept("Cập nhật metadata thất bại (API).");
        }

        try {
            safeCallback.accept("Đang lấy danh sách ảnh nguồn...");
            List<ImageInfo> sourceImages = embyRepo.getItemImages(sourceItemId);
            List<ImageInfo> destImages = embyRepo.getItemImages(destinationItemId);

            safeCallback.accept("Đang xóa " + destImages.size() + " ảnh cũ ở đích...");
            for (ImageInfo img : destImages) {
                embyRepo.deleteImage(destinationItemId, img.getImageType(), img.getImageIndex());
            }

            safeCallback.accept("Đang sao chép " + sourceImages.size() + " ảnh mới...");
            String serverUrl = embyRepo.getBasePath();

            for (int i = 0; i < sourceImages.size(); i++) {
                ImageInfo img = sourceImages.get(i);
                ImageType type = img.getImageType();
                if (type == null) continue;

                safeCallback.accept(String.format("Đang copy ảnh (%d/%d): %s", (i+1), sourceImages.size(), type.getValue()));

                String imageUrl = null;
                if (type == ImageType.PRIMARY) {
                    Map<String, String> imageTags = sourceItem.getImageTags();
                    if (imageTags != null && imageTags.containsKey("Primary")) {
                        String tag = imageTags.get("Primary");
                        imageUrl = String.format("%s/Items/%s/Images/Primary?tag=%s", serverUrl, sourceItemId, tag);
                    }
                } else {
                    Integer index = img.getImageIndex();
                    if (index != null) {
                        imageUrl = String.format("%s/Items/%s/Images/%s/%d", serverUrl, sourceItemId, type.getValue(), index);
                    }
                }

                if (imageUrl == null) {
                    safeCallback.accept("Bỏ qua ảnh: không thể tạo URL.");
                    continue;
                }

                DownloadedImage downloadedImg = embyRepo.downloadImage(imageUrl);

                embyRepo.uploadImage(destinationItemId, type, downloadedImg.bytes, downloadedImg.mediaType);
            }
            safeCallback.accept("Sao chép ảnh hoàn tất!");

        } catch (Exception e) {
            safeCallback.accept("LỖI khi sao chép ảnh: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Logic sao chép, được lấy từ ImportJsonUseCase.
     *//*
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
        itemOnServer.setCriticRating(sourceItem.getCriticRating());

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
    }*/
}