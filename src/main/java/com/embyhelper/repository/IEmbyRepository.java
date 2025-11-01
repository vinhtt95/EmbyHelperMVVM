package com.embyhelper.repository;

import embyclient.model.BaseItemDto;
import embyclient.model.UserLibraryTagItem;
import java.util.List;

/**
 * Interface trừu tượng hóa tất cả các lệnh gọi API Emby.
 * (Nguyên tắc D và I của SOLID)
 * Đây là lớp Facade cho SDK.
 */
public interface IEmbyRepository {
    void initialize(String userId);

    // --- Logic từ ItemService (chỉ API calls) ---
    List<BaseItemDto> getItemsByParentId(String parentId, Integer startIndex, Integer limit, boolean recursive, String includeItemTypes);
    BaseItemDto getItemInfo(String itemId);
    boolean updateItemInfo(String itemId, BaseItemDto item);
    List<BaseItemDto> searchItems(String searchTerm, String parentId, String fields, String includeItemTypes, boolean recursive);

    // --- Logic từ StudioService ---
    List<BaseItemDto> getStudios();
    List<BaseItemDto> getItemsByStudioId(String studioId);

    // --- Logic từ GenresService ---
    List<BaseItemDto> getGenres();
    List<BaseItemDto> getItemsByGenreName(String genreName);

    // --- Logic từ PeopleService ---
    List<BaseItemDto> getPeople();
    List<BaseItemDto> getItemsByPersonId(String personId);

    // --- Logic từ TagService ---
    List<UserLibraryTagItem> getTags();
    List<BaseItemDto> getItemsByTagName(String tagName);
}