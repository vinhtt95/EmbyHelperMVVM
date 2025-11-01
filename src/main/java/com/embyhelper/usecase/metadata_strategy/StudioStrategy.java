package com.embyhelper.usecase.metadata_strategy;

import com.embyhelper.model.MetadataType;
import com.embyhelper.repository.IEmbyRepository;
import embyclient.model.BaseItemDto;
import embyclient.model.NameLongIdPair;
import java.util.List;
import java.util.ResourceBundle;

public class StudioStrategy implements IMetadataStrategy {

    @Override
    public MetadataType getType() { return MetadataType.STUDIO; }

    @Override
    public String getPluralName(ResourceBundle bundle) { return bundle.getString(getType().getPluralKey()); }

    @Override
    public String getSingularName(ResourceBundle bundle) { return bundle.getString(getType().getSingularKey()); }

    @Override
    public List<?> loadItems(IEmbyRepository repo) {
        return repo.getStudios();
    }

    @Override
    public String getItemName(Object item) {
        if (item instanceof BaseItemDto) return ((BaseItemDto) item).getName();
        return null;
    }

    @Override
    public String getItemIdentifier(Object item) {
        if (item instanceof BaseItemDto) return ((BaseItemDto) item).getId();
        return null;
    }

    // --- Triển khai logic nghiệp vụ (từ StudioService cũ) ---

    @Override
    public void copy(IEmbyRepository repo, String fromId, String toId) {
        BaseItemDto itemCopy = repo.getItemInfo(fromId);
        if (itemCopy == null) {
            System.err.println("Không tìm thấy item mẫu: " + fromId);
            return;
        }
        List<NameLongIdPair> studiosToCopy = itemCopy.getStudios();
        if (studiosToCopy == null) return;

        List<BaseItemDto> listPaste = repo.getItemsByParentId(toId, null, null, true, "Movie,Series,Video,Game");
        if (listPaste == null) return;

        for (BaseItemDto stub : listPaste) {
            BaseItemDto itemPaste = repo.getItemInfo(stub.getId());
            if (itemPaste == null) continue;

            itemPaste.getStudios().clear();
            itemPaste.getStudios().addAll(studiosToCopy);

            if (repo.updateItemInfo(itemPaste.getId(), itemPaste)) {
                System.out.println("Copy studio thành công cho: " + itemPaste.getName());
            }
        }
    }

    @Override
    public void clearByParent(IEmbyRepository repo, String parentId) {
        List<BaseItemDto> listItems = repo.getItemsByParentId(parentId, null, null, true, "Movie,Series,Video,Game");
        if (listItems == null) return;

        for (BaseItemDto stub : listItems) {
            BaseItemDto item = repo.getItemInfo(stub.getId());
            if (item == null) continue;

            item.getStudios().clear();
            if (repo.updateItemInfo(item.getId(), item)) {
                System.out.println("Clear studio thành công cho: " + item.getName());
            }
        }
    }

    @Override
    public void clearSpecific(IEmbyRepository repo, String studioId) {
        List<BaseItemDto> listItems = repo.getItemsByStudioId(studioId);
        if (listItems == null) return;

        Long longStudioId;
        try { longStudioId = Long.parseLong(studioId); }
        catch (NumberFormatException e) {
            System.err.println("Invalid Studio ID: " + studioId);
            return;
        }

        for (BaseItemDto stub : listItems) {
            BaseItemDto item = repo.getItemInfo(stub.getId());
            if (item == null) continue;

            boolean removed = item.getStudios().removeIf(s -> s.getId() != null && s.getId().equals(longStudioId));
            if (removed && repo.updateItemInfo(item.getId(), item)) {
                System.out.println("Clear studio thành công cho: " + item.getName());
            }
        }
    }

    @Override
    public void updateSpecific(IEmbyRepository repo, String oldStudioId, String newName) {
        List<BaseItemDto> listItems = repo.getItemsByStudioId(oldStudioId);
        if (listItems == null) return;

        Long longStudioId;
        try { longStudioId = Long.parseLong(oldStudioId); }
        catch (NumberFormatException e) {
            System.err.println("Invalid Studio ID: " + oldStudioId);
            return;
        }

        for (BaseItemDto stub : listItems) {
            BaseItemDto item = repo.getItemInfo(stub.getId());
            if (item == null) continue;

            boolean removed = item.getStudios().removeIf(s -> s.getId() != null && s.getId().equals(longStudioId));
            if (removed) {
                NameLongIdPair newStudio = new NameLongIdPair();
                newStudio.setName(newName); // newName là tên đơn giản (từ TagModel)
                item.getStudios().add(newStudio);

                if (repo.updateItemInfo(item.getId(), item)) {
                    System.out.println("Update studio thành công cho: " + item.getName());
                }
            }
        }
    }
}