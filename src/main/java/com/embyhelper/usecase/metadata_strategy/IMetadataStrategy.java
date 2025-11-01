package com.embyhelper.usecase.metadata_strategy;

import com.embyhelper.model.MetadataType;
import com.embyhelper.repository.IEmbyRepository;
import java.util.List;
import java.util.ResourceBundle;

/**
 * Interface cho mẫu thiết kế Strategy, tuân thủ OCP.
 */
public interface IMetadataStrategy {
    MetadataType getType();
    String getPluralName(ResourceBundle bundle);
    String getSingularName(ResourceBundle bundle);

    // Trả về List<Object> để tương thích (BaseItemDto hoặc UserLibraryTagItem)
    List<?> loadItems(IEmbyRepository repo);

    String getItemName(Object item); // Lấy tên hiển thị
    String getItemIdentifier(Object item); // Lấy ID hoặc Tên (cho Genre/Tag)

    // Các hành động nghiệp vụ
    void copy(IEmbyRepository repo, String fromId, String toId);
    void clearByParent(IEmbyRepository repo, String parentId);
    void clearSpecific(IEmbyRepository repo, String idOrName);
    void updateSpecific(IEmbyRepository repo, String oldIdOrName, String newSerializedName);
}