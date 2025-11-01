package com.embyhelper.usecase.metadata_strategy;

import com.embyhelper.model.MetadataType;
import com.embyhelper.repository.IEmbyRepository;
import embyclient.model.BaseItemDto;
import embyclient.model.BaseItemPerson;
import java.util.List;
import java.util.ResourceBundle;

public class PeopleStrategy implements IMetadataStrategy {

    @Override
    public MetadataType getType() { return MetadataType.PEOPLE; }

    @Override
    public String getPluralName(ResourceBundle bundle) { return bundle.getString(getType().getPluralKey()); }

    @Override
    public String getSingularName(ResourceBundle bundle) { return bundle.getString(getType().getSingularKey()); }

    @Override
    public List<?> loadItems(IEmbyRepository repo) {
        return repo.getPeople();
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

    // --- Logic nghiệp vụ (từ PeopleService cũ) ---

    @Override
    public void copy(IEmbyRepository repo, String fromId, String toId) {
        BaseItemDto itemCopy = repo.getItemInfo(fromId);
        if (itemCopy == null) return;
        List<BaseItemPerson> peopleToCopy = itemCopy.getPeople();
        if (peopleToCopy == null) return;

        List<BaseItemDto> listPaste = repo.getItemsByParentId(toId, null, null, true, "Movie,Series,Video,Game");
        if (listPaste == null) return;

        for (BaseItemDto stub : listPaste) {
            BaseItemDto itemPaste = repo.getItemInfo(stub.getId());
            if (itemPaste == null) continue;

            itemPaste.getPeople().clear();
            itemPaste.getPeople().addAll(peopleToCopy);
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

            item.getPeople().clear();
            repo.updateItemInfo(item.getId(), item);
        }
    }

    @Override
    public void clearSpecific(IEmbyRepository repo, String personId) {
        List<BaseItemDto> listItems = repo.getItemsByPersonId(personId);
        if (listItems == null) return;

        for (BaseItemDto stub : listItems) {
            BaseItemDto item = repo.getItemInfo(stub.getId());
            if (item == null) continue;

            boolean removed = item.getPeople().removeIf(p -> p.getId() != null && p.getId().equals(personId));
            if (removed) {
                repo.updateItemInfo(item.getId(), item);
            }
        }
    }

    @Override
    public void updateSpecific(IEmbyRepository repo, String oldPersonId, String newName) {
        List<BaseItemDto> listItems = repo.getItemsByPersonId(oldPersonId);
        if (listItems == null) return;

        for (BaseItemDto stub : listItems) {
            BaseItemDto item = repo.getItemInfo(stub.getId());
            if (item == null) continue;

            boolean removed = item.getPeople().removeIf(p -> p.getId() != null && p.getId().equals(oldPersonId));
            if (removed) {
                BaseItemPerson newPerson = new BaseItemPerson();
                newPerson.setName(newName); // newName là tên đơn giản (từ TagModel)
                item.getPeople().add(newPerson);
                repo.updateItemInfo(item.getId(), item);
            }
        }
    }
}