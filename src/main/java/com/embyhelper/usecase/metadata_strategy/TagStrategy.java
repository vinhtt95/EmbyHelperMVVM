package com.embyhelper.usecase.metadata_strategy;

import com.embyhelper.model.MetadataType;
import com.embyhelper.repository.IEmbyRepository;
import embyclient.model.BaseItemDto;
import embyclient.model.NameLongIdPair;
import embyclient.model.UserLibraryTagItem;
import java.util.List;
import java.util.ResourceBundle;

public class TagStrategy implements IMetadataStrategy {

    @Override
    public MetadataType getType() { return MetadataType.TAG; }

    @Override
    public String getPluralName(ResourceBundle bundle) { return bundle.getString(getType().getPluralKey()); }

    @Override
    public String getSingularName(ResourceBundle bundle) { return bundle.getString(getType().getSingularKey()); }

    @Override
    public List<?> loadItems(IEmbyRepository repo) {
        return repo.getTags();
    }

    @Override
    public String getItemName(Object item) {
        if (item instanceof UserLibraryTagItem) return ((UserLibraryTagItem) item).getName();
        return null;
    }

    @Override
    public String getItemIdentifier(Object item) {
        // Tag được xác định bằng Tên (Name)
        if (item instanceof UserLibraryTagItem) return ((UserLibraryTagItem) item).getName();
        return null;
    }

    // --- Logic nghiệp vụ (từ TagService cũ) ---

    @Override
    public void copy(IEmbyRepository repo, String fromId, String toId) {
        BaseItemDto itemCopy = repo.getItemInfo(fromId);
        if (itemCopy == null) return;
        List<NameLongIdPair> tagsToCopy = itemCopy.getTagItems();
        if (tagsToCopy == null) return;

        List<BaseItemDto> listPaste = repo.getItemsByParentId(toId, null, null, true, "Movie,Series,Video,Game");
        if (listPaste == null) return;

        for (BaseItemDto stub : listPaste) {
            BaseItemDto itemPaste = repo.getItemInfo(stub.getId());
            if (itemPaste == null) continue;

            itemPaste.getTagItems().clear(); // Sửa lỗi logic từ TagService gốc (clear đúng list)
            itemPaste.getTagItems().addAll(tagsToCopy);
            repo.updateItemInfo(itemPaste.getId(), itemPaste);
        }
    }

    @Override
    public void clearByParent(IEmbyRepository repo, String parentId) {
        List<BaseItemDto> listItems = repo.getItemsByParentId(parentId, null, null, true, "Movie,Series,Video,Game");
        if (listItems == null) return;

        for (BaseItemDto stub : listItems) {
            BaseItemDto item = repo.getItemInfo(stub.getId());
            if (item == null) continue;

            item.getTagItems().clear();
            repo.updateItemInfo(item.getId(), item);
        }
    }

    @Override
    public void clearSpecific(IEmbyRepository repo, String tagName) {
        List<BaseItemDto> listItems = repo.getItemsByTagName(tagName);
        if (listItems == null) return;

        for (BaseItemDto stub : listItems) {
            BaseItemDto item = repo.getItemInfo(stub.getId());
            if (item == null) continue;

            boolean removed = item.getTagItems().removeIf(t -> t.getName() != null && t.getName().equals(tagName));
            if (removed) {
                repo.updateItemInfo(item.getId(), item);
            }
        }
    }

    @Override
    public void updateSpecific(IEmbyRepository repo, String oldName, String newSerializedName) {
        List<BaseItemDto> listItems = repo.getItemsByTagName(oldName);
        if (listItems == null) return;

        for (BaseItemDto stub : listItems) {
            BaseItemDto item = repo.getItemInfo(stub.getId());
            if (item == null) continue;

            boolean removed = item.getTagItems().removeIf(t -> t.getName() != null && t.getName().equals(oldName));
            if (removed) {
                NameLongIdPair newTag = new NameLongIdPair();
                newTag.setName(newSerializedName); // newName là tên đã serialize (có thể là JSON)
                item.getTagItems().add(newTag);
                repo.updateItemInfo(item.getId(), item);
            }
        }
    }
}