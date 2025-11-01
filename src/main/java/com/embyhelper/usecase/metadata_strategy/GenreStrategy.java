package com.embyhelper.usecase.metadata_strategy;

import com.embyhelper.model.MetadataType;
import com.embyhelper.repository.IEmbyRepository;
import embyclient.model.BaseItemDto;
import embyclient.model.NameLongIdPair;
import java.util.List;
import java.util.ResourceBundle;

public class GenreStrategy implements IMetadataStrategy {

    @Override
    public MetadataType getType() { return MetadataType.GENRE; }

    @Override
    public String getPluralName(ResourceBundle bundle) { return bundle.getString(getType().getPluralKey()); }

    @Override
    public String getSingularName(ResourceBundle bundle) { return bundle.getString(getType().getSingularKey()); }

    @Override
    public List<?> loadItems(IEmbyRepository repo) {
        return repo.getGenres();
    }

    @Override
    public String getItemName(Object item) {
        if (item instanceof BaseItemDto) return ((BaseItemDto) item).getName();
        return null;
    }

    @Override
    public String getItemIdentifier(Object item) {
        // Genre được xác định bằng Tên (Name)
        if (item instanceof BaseItemDto) return ((BaseItemDto) item).getName();
        return null;
    }

    // --- Logic nghiệp vụ (từ GenresService cũ) ---

    @Override
    public void copy(IEmbyRepository repo, String fromId, String toId) {
        BaseItemDto itemCopy = repo.getItemInfo(fromId);
        if (itemCopy == null) return;
        List<NameLongIdPair> genresToCopy = itemCopy.getGenreItems();
        if (genresToCopy == null) return;

        List<BaseItemDto> listPaste = repo.getItemsByParentId(toId, null, null, true, "Movie,Series,Video,Game,MusicAlbum",null);
        if (listPaste == null) return;

        for (BaseItemDto stub : listPaste) {
            BaseItemDto itemPaste = repo.getItemInfo(stub.getId());
            if (itemPaste == null) continue;

            itemPaste.getGenreItems().clear();
            itemPaste.getGenreItems().addAll(genresToCopy);
            repo.updateItemInfo(itemPaste.getId(), itemPaste);
        }
    }

    @Override
    public void clearByParent(IEmbyRepository repo, String parentId) {
        List<BaseItemDto> listItems = repo.getItemsByParentId(parentId, null, null, true, "Movie,Series,Video,Game,MusicAlbum",null);
        if (listItems == null) return;

        for (BaseItemDto stub : listItems) {
            BaseItemDto item = repo.getItemInfo(stub.getId());
            if (item == null) continue;

            item.getGenreItems().clear();
            repo.updateItemInfo(item.getId(), item);
        }
    }

    @Override
    public void clearSpecific(IEmbyRepository repo, String genreName) {
        List<BaseItemDto> listItems = repo.getItemsByGenreName(genreName);
        if (listItems == null) return;

        for (BaseItemDto stub : listItems) {
            BaseItemDto item = repo.getItemInfo(stub.getId());
            if (item == null) continue;

            boolean removed = item.getGenreItems().removeIf(g -> g.getName() != null && g.getName().equals(genreName));
            if (removed) {
                repo.updateItemInfo(item.getId(), item);
            }
        }
    }

    @Override
    public void updateSpecific(IEmbyRepository repo, String oldName, String newSerializedName) {
        List<BaseItemDto> listItems = repo.getItemsByGenreName(oldName);
        if (listItems == null) return;

        for (BaseItemDto stub : listItems) {
            BaseItemDto item = repo.getItemInfo(stub.getId());
            if (item == null) continue;

            boolean removed = item.getGenreItems().removeIf(g -> g.getName() != null && g.getName().equals(oldName));
            if (removed) {
                NameLongIdPair newGenre = new NameLongIdPair();
                newGenre.setName(newSerializedName); // newName là tên đã serialize (có thể là JSON)
                item.getGenreItems().add(newGenre);
                repo.updateItemInfo(item.getId(), item);
            }
        }
    }
}