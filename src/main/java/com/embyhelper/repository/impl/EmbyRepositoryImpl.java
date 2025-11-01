package com.embyhelper.repository.impl;

import com.embyhelper.repository.DownloadedImage; // THÊM IMPORT
import com.embyhelper.repository.IConfigRepository;
import com.embyhelper.repository.IEmbyRepository;
import com.squareup.okhttp.Interceptor; // THÊM IMPORT
import com.squareup.okhttp.MediaType; // THÊM IMPORT
import com.squareup.okhttp.OkHttpClient; // THÊM IMPORT
import com.squareup.okhttp.Request; // THÊM IMPORT
import com.squareup.okhttp.RequestBody; // THÊM IMPORT
import com.squareup.okhttp.Response; // THÊM IMPORT
import embyclient.Configuration;
import embyclient.api.*;
import embyclient.model.BaseItemDto;
import embyclient.model.ImageInfo; // THÊM IMPORT
import embyclient.model.ImageType; // THÊM IMPORT
import embyclient.model.QueryResultBaseItemDto;
import embyclient.model.QueryResultUserLibraryTagItem;
import embyclient.model.UserLibraryTagItem;
import java.io.IOException; // THÊM IMPORT
import java.util.ArrayList;
import java.util.Base64; // THÊM IMPORT
import java.util.List;

/**
 * Triển khai IEmbyRepository.
 * Đã sửa lỗi: Di dời việc khởi tạo các ServiceApi từ constructor vào
 * phương thức initialize() để đảm bảo chúng được tạo SAU KHI
 * Configuration.getDefaultApiClient() đã được xác thực (đăng nhập).
 */
public class EmbyRepositoryImpl implements IEmbyRepository {

    private String userId;
    private final IConfigRepository configRepo;

    // SỬA LỖI: Xóa 'final' và không khởi tạo ở đây
    private ItemsServiceApi itemsServiceApi;
    private UserLibraryServiceApi userLibraryServiceApi;
    private ItemUpdateServiceApi itemUpdateServiceApi;
    private StudiosServiceApi studiosServiceApi;
    private GenresServiceApi genresServiceApi;
    private PersonsServiceApi personsServiceApi;
    private TagServiceApi tagServiceApi;
    private ImageServiceApi imageServiceApi;
    private OkHttpClient uploadHttpClient;

    public EmbyRepositoryImpl(IConfigRepository configRepo) {
        // SỬA LỖI: Constructor bây giờ rỗng.
        // Việc khởi tạo sẽ được thực hiện trong initialize().
        this.configRepo = configRepo;
    }

    @Override
    public void initialize(String userId) {
        this.userId = userId;

        // SỬA LỖI: Khởi tạo tất cả các service API TẠI ĐÂY.
        // Giờ đây, Configuration.getDefaultApiClient() đã được
        // AuthService cấu hình chính xác (đã đăng nhập).
        this.itemsServiceApi = new ItemsServiceApi(Configuration.getDefaultApiClient());
        this.userLibraryServiceApi = new UserLibraryServiceApi(Configuration.getDefaultApiClient());
        this.itemUpdateServiceApi = new ItemUpdateServiceApi(Configuration.getDefaultApiClient());
        this.studiosServiceApi = new StudiosServiceApi(Configuration.getDefaultApiClient());
        this.genresServiceApi = new GenresServiceApi(Configuration.getDefaultApiClient());
        this.personsServiceApi = new PersonsServiceApi(Configuration.getDefaultApiClient());
        this.tagServiceApi = new TagServiceApi(Configuration.getDefaultApiClient());
        this.imageServiceApi = new ImageServiceApi(Configuration.getDefaultApiClient());
    }

    private OkHttpClient getUploadHttpClient() {
        if (uploadHttpClient == null) {
            this.uploadHttpClient = new OkHttpClient();
            Interceptor authInterceptor = chain -> {
                Request originalRequest = chain.request();
                Request.Builder builder = originalRequest.newBuilder();

                // (SỬA LỖI TẠI ĐÂY)
                String localAccessToken = configRepo.getAccessToken(); // Lấy token từ configRepo

                if (localAccessToken != null && !localAccessToken.isEmpty()) {
                    builder.removeHeader("X-Emby-Token");
                    builder.header("X-Emby-Token", localAccessToken);
                }
                Request newRequest = builder.build();
                return chain.proceed(newRequest);
            };
            this.uploadHttpClient.interceptors().add(authInterceptor);
        }
        return this.uploadHttpClient;
    }

    @Override
    public String getBasePath() {
        return Configuration.getDefaultApiClient().getBasePath();
    }

    @Override
    public List<ImageInfo> getItemImages(String itemId) {
        try {
            return imageServiceApi.getItemsByIdImages(itemId);
        } catch (Exception e) {
            System.err.println("Lỗi getItemImages (ID: " + itemId + "): " + e.getMessage());
            return new ArrayList<>();
        }
    }

    @Override
    public DownloadedImage downloadImage(String imageUrl) throws IOException {
        OkHttpClient client = new OkHttpClient(); // Client mới, không cần auth
        Request request = new Request.Builder().url(imageUrl).build();
        Response response = client.newCall(request).execute();
        if (!response.isSuccessful()) {
            response.body().close();
            throw new IOException("Download thất bại (Code " + response.code() + ") từ URL: " + imageUrl);
        }
        String mediaType = response.body().contentType().toString();
        byte[] bytes = response.body().bytes();
        response.body().close();
        return new DownloadedImage(bytes, mediaType);
    }

    @Override
    public void deleteImage(String itemId, ImageType type, Integer index) {
        try {
            Integer finalIndex = (index == null) ? 0 : index;
            imageServiceApi.deleteItemsByIdImagesByTypeByIndex(itemId, finalIndex, type);
        } catch (Exception e) {
            System.err.println("Lỗi deleteImage (ID: " + itemId + ", Type: " + type + ", Index: " + index + "): " + e.getMessage());
        }
    }

    @Override
    public void uploadImage(String itemId, ImageType type, byte[] imageBytes, String mediaType) throws IOException {
        // (Học từ Project 2)
        OkHttpClient client = getUploadHttpClient();
        String serverUrl = Configuration.getDefaultApiClient().getBasePath();
        String url = String.format("%s/Items/%s/Images/%s", serverUrl, itemId, type.getValue());

        // Emby yêu cầu gửi Base64
        String base64String = Base64.getEncoder().encodeToString(imageBytes);
        MediaType originalMediaType = MediaType.parse(mediaType);

        RequestBody body = RequestBody.create(originalMediaType, base64String);

        Request request = new Request.Builder().url(url).post(body).build();

        Response response = client.newCall(request).execute();
        if (!response.isSuccessful()) {
            String responseBody = response.body() != null ? response.body().string() : "No response body";
            response.body().close();
            throw new IOException("Upload thất bại (Code " + response.code() + "): " + responseBody);
        }
        response.body().close();
    }

    // --- Triển khai ItemService ---

    // (Đã sửa lỗi 107 tham số ở phiên trước)
    @Override
    public List<BaseItemDto> getItemsByParentId(String parentID, Integer startIndex, Integer limit, boolean recursive, String includeItemTypes, String fields) {
        // (SỬA ĐỔI: Thêm fields theo yêu cầu)
        String requestedFields = "OriginalTitle,Path,ImageTags,BackdropImageTags";
        if (fields != null && !fields.isEmpty()) {
            requestedFields += "," + fields;
        }

        try {
            QueryResultBaseItemDto result = itemsServiceApi.getItems(
                    null,	//artistType
                    null,	//maxOfficialRating
                    null,	//hasThemeSong
                    null,	//hasThemeVideo
                    null,	//hasSubtitles
                    null,	//hasSpecialFeature
                    null,	//hasTrailer
                    null,	//isSpecialSeason
                    null,	//adjacentTo
                    null,	//startItemId (10)
                    null,	//minIndexNumber
                    null,	//minStartDate
                    null,	//maxStartDate
                    null,	//minEndDate
                    null,	//maxEndDate
                    null,	//minPlayers
                    null,	//maxPlayers
                    null,	//parentIndexNumber
                    null,	//hasParentalRating
                    null,	//isHD (20)
                    null,	//isUnaired
                    null,	//minCommunityRating
                    null,	//minCriticRating
                    null,	//airedDuringSeason
                    null,	//minPremiereDate
                    null,	//minDateLastSaved
                    null,	//minDateLastSavedForUser
                    null,	//maxPremiereDate
                    null,	//hasOverview
                    null,	//hasImdbId (30)
                    null,	//hasTmdbId
                    null,	//hasTvdbId
                    null,	//excludeItemIds
                    startIndex,	//startIndex
                    limit,	//limit
                    recursive,	//recursive
                    null,	//searchTerm
                    null,	//sortOrder
                    parentID,	//parentId
                    requestedFields,	//fields
                    null,	//excludeItemTypes
                    includeItemTypes,	//includeItemTypes
                    null,	//anyProviderIdEquals
                    null,	//filters
                    null,	//isFavorite
                    null,	//isMovie
                    null,	//isSeries
                    null,	//isFolder
                    null,	//isNews
                    null,	//isKids (50)
                    null,	//isSports
                    null,	//isNew
                    null,	//isPremiere
                    null,	//isNewOrPremiere
                    null,	//isRepeat
                    null,	//projectToMedia
                    null,	//mediaTypes
                    null,	//imageTypes
                    null,	//sortBy
                    null,	//isPlayed (60)
                    null,	//genres
                    null,	//officialRatings
                    null,	//tags
                    null,	//excludeTags
                    null,	//years
                    null,	//enableImages
                    null,	//enableUserData
                    null,	//imageTypeLimit
                    null,	//enableImageTypes
                    null,	//person (70)
                    null,	//personIds
                    null,	//personTypes
                    null,	//studios
                    null,	//studioIds
                    null,	//artists
                    null,	//artistIds
                    null,	//albums
                    null,	//ids
                    null,	//videoTypes
                    null,	//containers (80)
                    null,	//audioCodecs
                    null,	//audioLayouts
                    null,	//videoCodecs
                    null,	//extendedVideoTypes
                    null,	//subtitleCodecs
                    null,	//path
                    null,	//userId (Sẽ dùng userId từ getInforItem)
                    null,	//minOfficialRating
                    null,	//isLocked
                    null,	//isPlaceHolder (90)
                    null,	//hasOfficialRating
                    null,	//groupItemsIntoCollections
                    null,	//is3D
                    null,	//seriesStatus
                    null,	//nameStartsWithOrGreater
                    null,	//artistStartsWithOrGreater
                    null,	//albumArtistStartsWithOrGreater
                    null,	//nameStartsWith
                    null	//nameLessThan
            );
            if (result != null && result.getItems() != null) {
                return result.getItems();
            }
        } catch (Exception e) {
            System.err.println("Lỗi getItemsByParentId: " + e.getMessage());
        }
        return new ArrayList<>();
    }

    @Override
    public BaseItemDto getItemInfo(String itemId) {
        if (this.userId == null) {
            System.err.println("Lỗi getItemInfo: userId chưa được khởi tạo.");
            return null;
        }
        try {
            return userLibraryServiceApi.getUsersByUseridItemsById(this.userId, itemId);
        } catch (Exception e) {
            System.err.println("Lỗi getItemInfo (ID: " + itemId + "): " + e.getMessage());
            return null;
        }
    }

    @Override
    public boolean updateItemInfo(String itemId, BaseItemDto item) {
        try {
            itemUpdateServiceApi.postItemsByItemid(item, itemId);
            return true;
        } catch (Exception e) {
            System.err.println("Lỗi updateItemInfo (ID: " + itemId + "): " + e.getMessage());
            return false;
        }
    }

    // (Đã sửa lỗi 107 tham số ở phiên trước)
    @Override
    public List<BaseItemDto> searchItems(String searchTerm, String parentId, String fields, String includeItemTypes, boolean recursive) {
        String requestedFields = "OriginalTitle,Path,ImageTags,BackdropImageTags";
        if (fields != null && !fields.isEmpty()) {
            requestedFields += "," + fields;
        }
        try {
            QueryResultBaseItemDto searchResult = itemsServiceApi.getItems(
                    null,	//artistType
                    null,	//maxOfficialRating
                    null,	//hasThemeSong
                    null,	//hasThemeVideo
                    null,	//hasSubtitles
                    null,	//hasSpecialFeature
                    null,	//hasTrailer
                    null,	//isSpecialSeason
                    null,	//adjacentTo
                    null,	//startItemId (10)
                    null,	//minIndexNumber
                    null,	//minStartDate
                    null,	//maxStartDate
                    null,	//minEndDate
                    null,	//maxEndDate
                    null,	//minPlayers
                    null,	//maxPlayers
                    null,	//parentIndexNumber
                    null,	//hasParentalRating
                    null,	//isHD (20)
                    null,	//isUnaired
                    null,	//minCommunityRating
                    null,	//minCriticRating
                    null,	//airedDuringSeason
                    null,	//minPremiereDate
                    null,	//minDateLastSaved
                    null,	//minDateLastSavedForUser
                    null,	//maxPremiereDate
                    null,	//hasOverview
                    null,	//hasImdbId (30)
                    null,	//hasTmdbId
                    null,	//hasTvdbId
                    null,	//excludeItemIds
                    null,	//startIndex
                    null,	//limit
                    recursive,	//recursive
                    searchTerm,	//searchTerm
                    null,	//sortOrder
                    parentId,	//parentId
                    requestedFields,	//fields (40)
                    null,	//excludeItemTypes
                    includeItemTypes,	//includeItemTypes
                    null,	//anyProviderIdEquals
                    null,	//filters
                    null,	//isFavorite
                    null,	//isMovie
                    null,	//isSeries
                    null,	//isFolder
                    null,	//isNews
                    null,	//isKids (50)
                    null,	//isSports
                    null,	//isNew
                    null,	//isPremiere
                    null,	//isNewOrPremiere
                    null,	//isRepeat
                    null,	//projectToMedia
                    null,	//mediaTypes
                    null,	//imageTypes
                    null,	//sortBy
                    null,	//isPlayed (60)
                    null,	//genres
                    null,	//officialRatings
                    null,	//tags
                    null,	//excludeTags
                    null,	//years
                    null,	//enableImages
                    null,	//enableUserData
                    null,	//imageTypeLimit
                    null,	//enableImageTypes
                    null,	//person (70)
                    null,	//personIds
                    null,	//personTypes
                    null,	//studios
                    null,	//studioIds
                    null,	//artists
                    null,	//artistIds
                    null,	//albums
                    null,	//ids
                    null,	//videoTypes
                    null,	//containers (80)
                    null,	//audioCodecs
                    null,	//audioLayouts
                    null,	//videoCodecs
                    null,	//extendedVideoTypes
                    null,	//subtitleCodecs
                    null,	//path
                    null,	//userId
                    null,	//minOfficialRating
                    null,	//isLocked
                    null,	//isPlaceHolder (90)
                    null,	//hasOfficialRating
                    null,	//groupItemsIntoCollections
                    null,	//is3D
                    null,	//seriesStatus
                    null,	//nameStartsWithOrGreater
                    null,	//artistStartsWithOrGreater
                    null,	//albumArtistStartsWithOrGreater
                    null,	//nameStartsWith
                    null	//nameLessThan
            );

            if (searchResult != null && searchResult.getItems() != null) {
                return searchResult.getItems();
            }
        } catch (Exception e) {
            System.err.println("Lỗi searchItems (Term: " + searchTerm + "): " + e.getMessage());
        }
        return new ArrayList<>();
    }

    // --- Triển khai StudioService ---
    // (Đã sửa lỗi 107 tham số ở phiên trước)
    @Override
    public List<BaseItemDto> getStudios() {
        try {
            QueryResultBaseItemDto listItems = studiosServiceApi.getStudios(
                    null,	//artistType
                    null,	//maxOfficialRating
                    null,	//hasThemeSong
                    null,	//hasThemeVideo
                    null,	//hasSubtitles
                    null,	//hasSpecialFeature
                    null,	//hasTrailer
                    null,	//isSpecialSeason
                    null,	//adjacentTo
                    null,	//startItemId (10)
                    null,	//minIndexNumber
                    null,	//minStartDate
                    null,	//maxStartDate
                    null,	//minEndDate
                    null,	//maxEndDate
                    null,	//minPlayers
                    null,	//maxPlayers
                    null,	//parentIndexNumber
                    null,	//hasParentalRating
                    null,	//isHD (20)
                    null,	//isUnaired
                    null,	//minCommunityRating
                    null,	//minCriticRating
                    null,	//airedDuringSeason
                    null,	//minPremiereDate
                    null,	//minDateLastSaved
                    null,	//minDateLastSavedForUser
                    null,	//maxPremiereDate
                    null,	//hasOverview
                    null,	//hasImdbId (30)
                    null,	//hasTmdbId
                    null,	//hasTvdbId
                    null,	//excludeItemIds
                    null,	//startIndex
                    null,	//limit
                    true,	//recursive (Từ StudioService gốc)
                    null,	//searchTerm
                    null,	//sortOrder
                    null,	//parentId
                    null,	//fields (40)
                    null,	//excludeItemTypes
                    null,	//includeItemTypes
                    null,	//anyProviderIdEquals
                    null,	//filters
                    null,	//isFavorite
                    null,	//isMovie
                    null,	//isSeries
                    null,	//isFolder
                    null,	//isNews
                    null,	//isKids (50)
                    null,	//isSports
                    null,	//isNew
                    null,	//isPremiere
                    null,	//isNewOrPremiere
                    null,	//isRepeat
                    null,	//projectToMedia
                    null,	//mediaTypes
                    null,	//imageTypes
                    "SortName",	//sortBy (Từ StudioService gốc)
                    null,	//isPlayed (60)
                    null,	//genres
                    null,	//officialRatings
                    null,	//tags
                    null,	//excludeTags
                    null,	//years
                    null,	//enableImages
                    null,	//enableUserData
                    null,	//imageTypeLimit
                    null,	//enableImageTypes
                    null,	//person (70)
                    null,	//personIds
                    null,	//personTypes
                    null,	//studios
                    null,	//studioIds
                    null,	//artists
                    null,	//artistIds
                    null,	//albums
                    null,	//ids
                    null,	//videoTypes
                    null,	//containers (80)
                    null,	//audioCodecs
                    null,	//audioLayouts
                    null,	//videoCodecs
                    null,	//extendedVideoTypes
                    null,	//subtitleCodecs
                    null,	//path
                    null,	//userId
                    null,	//minOfficialRating
                    null,	//isLocked
                    null,	//isPlaceHolder (90)
                    null,	//hasOfficialRating
                    null,	//groupItemsIntoCollections
                    null,	//is3D
                    null,	//seriesStatus
                    null,	//nameStartsWithOrGreater
                    null,	//artistStartsWithOrGreater
                    null,	//albumArtistStartsWithOrGreater
                    null,	//nameStartsWith
                    null	//nameLessThan
            );
            if (listItems != null && listItems.getItems() != null) {
                return listItems.getItems();
            }
        } catch (Exception e) {
            System.err.println("Lỗi getStudios: " + e.getMessage());
        }
        return new ArrayList<>();
    }

    // (Đã sửa lỗi 107 tham số ở phiên trước)
    @Override
    public List<BaseItemDto> getItemsByStudioId(String studioId) {
        try {
            QueryResultBaseItemDto listItems = itemsServiceApi.getItems(
                    null,	//artistType
                    null,	//maxOfficialRating
                    null,	//hasThemeSong
                    null,	//hasThemeVideo
                    null,	//hasSubtitles
                    null,	//hasSpecialFeature
                    null,	//hasTrailer
                    null,	//isSpecialSeason
                    null,	//adjacentTo
                    null,	//startItemId (10)
                    null,	//minIndexNumber
                    null,	//minStartDate
                    null,	//maxStartDate
                    null,	//minEndDate
                    null,	//maxEndDate
                    null,	//minPlayers
                    null,	//maxPlayers
                    null,	//parentIndexNumber
                    null,	//hasParentalRating
                    null,	//isHD (20)
                    null,	//isUnaired
                    null,	//minCommunityRating
                    null,	//minCriticRating
                    null,	//airedDuringSeason
                    null,	//minPremiereDate
                    null,	//minDateLastSaved
                    null,	//minDateLastSavedForUser
                    null,	//maxPremiereDate
                    null,	//hasOverview
                    null,	//hasImdbId (30)
                    null,	//hasTmdbId
                    null,	//hasTvdbId
                    null,	//excludeItemIds
                    null,	//startIndex
                    null,	//limit
                    true,	//recursive (Từ StudioService gốc)
                    null,	//searchTerm
                    "Ascending",	//sortOrder (Từ StudioService gốc)
                    null,	//parentId
                    null,	//fields (40)
                    null,	//excludeItemTypes
                    "Movie,Series,Video,Game",	//includeItemTypes (Từ StudioService gốc)
                    null,	//anyProviderIdEquals
                    null,	//filters
                    null,	//isFavorite
                    null,	//isMovie
                    null,	//isSeries
                    null,	//isFolder
                    null,	//isNews
                    null,	//isKids (50)
                    null,	//isSports
                    null,	//isNew
                    null,	//isPremiere
                    null,	//isNewOrPremiere
                    null,	//isRepeat
                    null,	//projectToMedia
                    null,	//mediaTypes
                    null,	//imageTypes
                    null,	//sortBy
                    null,	//isPlayed (60)
                    null,	//genres
                    null,	//officialRatings
                    null,	//tags
                    null,	//excludeTags
                    null,	//years
                    null,	//enableImages
                    null,	//enableUserData
                    null,	//imageTypeLimit
                    null,	//enableImageTypes
                    null,	//person (70)
                    null,	//personIds
                    null,	//personTypes
                    null,	//studios
                    studioId,	//studioIds
                    null,	//artists
                    null,	//artistIds
                    null,	//albums
                    null,	//ids
                    null,	//videoTypes
                    null,	//containers (80)
                    null,	//audioCodecs
                    null,	//audioLayouts
                    null,	//videoCodecs
                    null,	//extendedVideoTypes
                    null,	//subtitleCodecs
                    null,	//path
                    null,	//userId
                    null,	//minOfficialRating
                    null,	//isLocked
                    null,	//isPlaceHolder (90)
                    null,	//hasOfficialRating
                    null,	//groupItemsIntoCollections
                    null,	//is3D
                    null,	//seriesStatus
                    null,	//nameStartsWithOrGreater
                    null,	//artistStartsWithOrGreater
                    null,	//albumArtistStartsWithOrGreater
                    null,	//nameStartsWith
                    null	//nameLessThan
            );
            if (listItems != null && listItems.getItems() != null) {
                return listItems.getItems();
            }
        } catch (Exception e) {
            System.err.println("Lỗi getItemsByStudioId: " + e.getMessage());
        }
        return new ArrayList<>();
    }

    // --- Triển khai GenresService ---
    // (Đã sửa lỗi 107 tham số ở phiên trước)
    @Override
    public List<BaseItemDto> getGenres() {
        try {
            QueryResultBaseItemDto listItems = genresServiceApi.getGenres(
                    null,	//artistType
                    null,	//maxOfficialRating
                    null,	//hasThemeSong
                    null,	//hasThemeVideo
                    null,	//hasSubtitles
                    null,	//hasSpecialFeature
                    null,	//hasTrailer
                    null,	//isSpecialSeason
                    null,	//adjacentTo
                    null,	//startItemId (10)
                    null,	//minIndexNumber
                    null,	//minStartDate
                    null,	//maxStartDate
                    null,	//minEndDate
                    null,	//maxEndDate
                    null,	//minPlayers
                    null,	//maxPlayers
                    null,	//parentIndexNumber
                    null,	//hasParentalRating
                    null,	//isHD (20)
                    null,	//isUnaired
                    null,	//minCommunityRating
                    null,	//minCriticRating
                    null,	//airedDuringSeason
                    null,	//minPremiereDate
                    null,	//minDateLastSaved
                    null,	//minDateLastSavedForUser
                    null,	//maxPremiereDate
                    null,	//hasOverview
                    null,	//hasImdbId (30)
                    null,	//hasTmdbId
                    null,	//hasTvdbId
                    null,	//excludeItemIds
                    null,	//startIndex
                    null,	//limit
                    null,	//recursive
                    null,	//searchTerm
                    null,	//sortOrder
                    null,	//parentId
                    null,	//fields (40)
                    null,	//excludeItemTypes
                    null,	//includeItemTypes
                    null,	//anyProviderIdEquals
                    null,	//filters
                    null,	//isFavorite
                    null,	//isMovie
                    null,	//isSeries
                    null,	//isFolder
                    null,	//isNews
                    null,	//isKids (50)
                    null,	//isSports
                    null,	//isNew
                    null,	//isPremiere
                    null,	//isNewOrPremiere
                    null,	//isRepeat
                    null,	//projectToMedia
                    null,	//mediaTypes
                    null,	//imageTypes
                    null,	//sortBy
                    null,	//isPlayed (60)
                    null,	//genres
                    null,	//officialRatings
                    null,	//tags
                    null,	//excludeTags
                    null,	//years
                    null,	//enableImages
                    null,	//enableUserData
                    null,	//imageTypeLimit
                    null,	//enableImageTypes
                    null,	//person (70)
                    null,	//personIds
                    null,	//personTypes
                    null,	//studios
                    null,	//studioIds
                    null,	//artists
                    null,	//artistIds
                    null,	//albums
                    null,	//ids
                    null,	//videoTypes
                    null,	//containers (80)
                    null,	//audioCodecs
                    null,	//audioLayouts
                    null,	//videoCodecs
                    null,	//extendedVideoTypes
                    null,	//subtitleCodecs
                    null,	//path
                    null,	//userId
                    null,	//minOfficialRating
                    null,	//isLocked
                    null,	//isPlaceHolder (90)
                    null,	//hasOfficialRating
                    null,	//groupItemsIntoCollections
                    null,	//is3D
                    null,	//seriesStatus
                    null,	//nameStartsWithOrGreater
                    null,	//artistStartsWithOrGreater
                    null,	//albumArtistStartsWithOrGreater
                    null,	//nameStartsWith
                    null	//nameLessThan
            );
            if (listItems != null && listItems.getItems() != null) {
                return listItems.getItems();
            }
        } catch (Exception e) {
            System.err.println("Lỗi getGenres: " + e.getMessage());
        }
        return new ArrayList<>();
    }

    // (Đã sửa lỗi 107 tham số ở phiên trước)
    @Override
    public List<BaseItemDto> getItemsByGenreName(String genreName) {
        try {
            QueryResultBaseItemDto listItems = itemsServiceApi.getItems(
                    null,	//artistType
                    null,	//maxOfficialRating
                    null,	//hasThemeSong
                    null,	//hasThemeVideo
                    null,	//hasSubtitles
                    null,	//hasSpecialFeature
                    null,	//hasTrailer
                    null,	//isSpecialSeason
                    null,	//adjacentTo
                    null,	//startItemId (10)
                    null,	//minIndexNumber
                    null,	//minStartDate
                    null,	//maxStartDate
                    null,	//minEndDate
                    null,	//maxEndDate
                    null,	//minPlayers
                    null,	//maxPlayers
                    null,	//parentIndexNumber
                    null,	//hasParentalRating
                    null,	//isHD (20)
                    null,	//isUnaired
                    null,	//minCommunityRating
                    null,	//minCriticRating
                    null,	//airedDuringSeason
                    null,	//minPremiereDate
                    null,	//minDateLastSaved
                    null,	//minDateLastSavedForUser
                    null,	//maxPremiereDate
                    null,	//hasOverview
                    null,	//hasImdbId (30)
                    null,	//hasTmdbId
                    null,	//hasTvdbId
                    null,	//excludeItemIds
                    null,	//startIndex
                    null,	//limit
                    true,	//recursive (Từ GenresService gốc)
                    null,	//searchTerm
                    null,	//sortOrder
                    null,	//parentId
                    null,	//fields (40)
                    null,	//excludeItemTypes
                    "Movie,Series,Video,Game,MusicAlbum",	//includeItemTypes (Từ GenresService gốc)
                    null,	//anyProviderIdEquals
                    null,	//filters
                    null,	//isFavorite
                    null,	//isMovie
                    null,	//isSeries
                    null,	//isFolder
                    null,	//isNews
                    null,	//isKids (50)
                    null,	//isSports
                    null,	//isNew
                    null,	//isPremiere
                    null,	//isNewOrPremiere
                    null,	//isRepeat
                    null,	//projectToMedia
                    null,	//mediaTypes
                    null,	//imageTypes
                    null,	//sortBy
                    null,	//isPlayed (60)
                    genreName,	//genres
                    null,	//officialRatings
                    null,	//tags
                    null,	//excludeTags
                    null,	//years
                    null,	//enableImages
                    null,	//enableUserData
                    null,	//imageTypeLimit
                    null,	//enableImageTypes
                    null,	//person (70)
                    null,	//personIds
                    null,	//personTypes
                    null,	//studios
                    null,	//studioIds
                    null,	//artists
                    null,	//artistIds
                    null,	//albums
                    null,	//ids
                    null,	//videoTypes
                    null,	//containers (80)
                    null,	//audioCodecs
                    null,	//audioLayouts
                    null,	//videoCodecs
                    null,	//extendedVideoTypes
                    null,	//subtitleCodecs
                    null,	//path
                    null,	//userId
                    null,	//minOfficialRating
                    null,	//isLocked
                    null,	//isPlaceHolder (90)
                    null,	//hasOfficialRating
                    null,	//groupItemsIntoCollections
                    null,	//is3D
                    null,	//seriesStatus
                    null,	//nameStartsWithOrGreater
                    null,	//artistStartsWithOrGreater
                    null,	//albumArtistStartsWithOrGreater
                    null,	//nameStartsWith
                    null	//nameLessThan
            );
            if (listItems != null && listItems.getItems() != null) {
                return listItems.getItems();
            }
        } catch (Exception e) {
            System.err.println("Lỗi getItemsByGenreName: " + e.getMessage());
        }
        return new ArrayList<>();
    }

    // --- Triển khai PeopleService ---
    // (Đã sửa lỗi 107 tham số ở phiên trước)
    @Override
    public List<BaseItemDto> getPeople() {
        try {
            QueryResultBaseItemDto listPeople = personsServiceApi.getPersons(
                    null,	//artistType
                    null,	//maxOfficialRating
                    null,	//hasThemeSong
                    null,	//hasThemeVideo
                    null,	//hasSubtitles
                    null,	//hasSpecialFeature
                    null,	//hasTrailer
                    null,	//isSpecialSeason
                    null,	//adjacentTo
                    null,	//startItemId (10)
                    null,	//minIndexNumber
                    null,	//minStartDate
                    null,	//maxStartDate
                    null,	//minEndDate
                    null,	//maxEndDate
                    null,	//minPlayers
                    null,	//maxPlayers
                    null,	//parentIndexNumber
                    null,	//hasParentalRating
                    null,	//isHD (20)
                    null,	//isUnaired
                    null,	//minCommunityRating
                    null,	//minCriticRating
                    null,	//airedDuringSeason
                    null,	//minPremiereDate
                    null,	//minDateLastSaved
                    null,	//minDateLastSavedForUser
                    null,	//maxPremiereDate
                    null,	//hasOverview
                    null,	//hasImdbId (30)
                    null,	//hasTmdbId
                    null,	//hasTvdbId
                    null,	//excludeItemIds
                    null,	//startIndex
                    null,	//limit
                    null,	//recursive
                    null,	//searchTerm
                    null,	//sortOrder
                    null,	//parentId
                    null,	//fields (40)
                    null,	//excludeItemTypes
                    null,	//includeItemTypes
                    null,	//anyProviderIdEquals
                    null,	//filters
                    null,	//isFavorite
                    null,	//isMovie
                    null,	//isSeries
                    null,	//isFolder
                    null,	//isNews
                    null,	//isKids (50)
                    null,	//isSports
                    null,	//isNew
                    null,	//isPremiere
                    null,	//isNewOrPremiere
                    null,	//isRepeat
                    null,	//projectToMedia
                    null,	//mediaTypes
                    null,	//imageTypes
                    null,	//sortBy
                    null,	//isPlayed (60)
                    null,	//genres
                    null,	//officialRatings
                    null,	//tags
                    null,	//excludeTags
                    null,	//years
                    null,	//enableImages
                    null,	//enableUserData
                    null,	//imageTypeLimit
                    null,	//enableImageTypes
                    null,	//person (70)
                    null,	//personIds
                    null,	//personTypes
                    null,	//studios
                    null,	//studioIds
                    null,	//artists
                    null,	//artistIds
                    null,	//albums
                    null,	//ids
                    null,	//videoTypes
                    null,	//containers (80)
                    null,	//audioCodecs
                    null,	//audioLayouts
                    null,	//videoCodecs
                    null,	//extendedVideoTypes
                    null,	//subtitleCodecs
                    null,	//path
                    null,	//userId
                    null,	//minOfficialRating
                    null,	//isLocked
                    null,	//isPlaceHolder (90)
                    null,	//hasOfficialRating
                    null,	//groupItemsIntoCollections
                    null,	//is3D
                    null,	//seriesStatus
                    null,	//nameStartsWithOrGreater
                    null,	//artistStartsWithOrGreater
                    null,	//albumArtistStartsWithOrGreater
                    null,	//nameStartsWith
                    null	//nameLessThan
            );
            if (listPeople != null && listPeople.getItems() != null) {
                return listPeople.getItems();
            }
        } catch (Exception e) {
            System.err.println("Lỗi getPeople: " + e.getMessage());
        }
        return new ArrayList<>();
    }

    // (Đã sửa lỗi 107 tham số ở phiên trước)
    @Override
    public List<BaseItemDto> getItemsByPersonId(String personId) {
        try {
            QueryResultBaseItemDto listPeople = itemsServiceApi.getItems(
                    null,	//artistType
                    null,	//maxOfficialRating
                    null,	//hasThemeSong
                    null,	//hasThemeVideo
                    null,	//hasSubtitles
                    null,	//hasSpecialFeature
                    null,	//hasTrailer
                    null,	//isSpecialSeason
                    null,	//adjacentTo
                    null,	//startItemId (10)
                    null,	//minIndexNumber
                    null,	//minStartDate
                    null,	//maxStartDate
                    null,	//minEndDate
                    null,	//maxEndDate
                    null,	//minPlayers
                    null,	//maxPlayers
                    null,	//parentIndexNumber
                    null,	//hasParentalRating
                    null,	//isHD (20)
                    null,	//isUnaired
                    null,	//minCommunityRating
                    null,	//minCriticRating
                    null,	//airedDuringSeason
                    null,	//minPremiereDate
                    null,	//minDateLastSaved
                    null,	//minDateLastSavedForUser
                    null,	//maxPremiereDate
                    null,	//hasOverview
                    null,	//hasImdbId (30)
                    null,	//hasTmdbId
                    null,	//hasTvdbId
                    null,	//excludeItemIds
                    null,	//startIndex
                    null,	//limit
                    true,	//recursive (Từ PeopleService gốc)
                    null,	//searchTerm
                    "Ascending",	//sortOrder (Từ PeopleService gốc)
                    null,	//parentId
                    null,	//fields (40)
                    null,	//excludeItemTypes
                    "Movie,Series,Video,Game",	//includeItemTypes (Từ PeopleService gốc)
                    null,	//anyProviderIdEquals
                    null,	//filters
                    null,	//isFavorite
                    null,	//isMovie
                    null,	//isSeries
                    null,	//isFolder
                    null,	//isNews
                    null,	//isKids (50)
                    null,	//isSports
                    null,	//isNew
                    null,	//isPremiere
                    null,	//isNewOrPremiere
                    null,	//isRepeat
                    null,	//projectToMedia
                    null,	//mediaTypes
                    null,	//imageTypes
                    null,	//sortBy
                    null,	//isPlayed (60)
                    null,	//genres
                    null,	//officialRatings
                    null,	//tags
                    null,	//excludeTags
                    null,	//years
                    null,	//enableImages
                    null,	//enableUserData
                    null,	//imageTypeLimit
                    null,	//enableImageTypes
                    null,	//person (70)
                    personId,	//personIds
                    null,	//personTypes
                    null,	//studios
                    null,	//studioIds
                    null,	//artists
                    null,	//artistIds
                    null,	//albums
                    null,	//ids
                    null,	//videoTypes
                    null,	//containers (80)
                    null,	//audioCodecs
                    null,	//audioLayouts
                    null,	//videoCodecs
                    null,	//extendedVideoTypes
                    null,	//subtitleCodecs
                    null,	//path
                    null,	//userId
                    null,	//minOfficialRating
                    null,	//isLocked
                    null,	//isPlaceHolder (90)
                    null,	//hasOfficialRating
                    null,	//groupItemsIntoCollections
                    null,	//is3D
                    null,	//seriesStatus
                    null,	//nameStartsWithOrGreater
                    null,	//artistStartsWithOrGreater
                    null,	//albumArtistStartsWithOrGreater
                    null,	//nameStartsWith
                    null	//nameLessThan
            );
            if (listPeople != null && listPeople.getItems() != null) {
                return listPeople.getItems();
            }
        } catch (Exception e) {
            System.err.println("Lỗi getItemsByPersonId: " + e.getMessage());
        }
        return new ArrayList<>();
    }

    // --- Triển khai TagService ---
    // (Đã sửa lỗi 107 tham số ở phiên trước)
    @Override
    public List<UserLibraryTagItem> getTags() {
        try {
            QueryResultUserLibraryTagItem listTags = tagServiceApi.getTags(
                    null,	//artistType
                    null,	//maxOfficialRating
                    null,	//hasThemeSong
                    null,	//hasThemeVideo
                    null,	//hasSubtitles
                    null,	//hasSpecialFeature
                    null,	//hasTrailer
                    null,	//isSpecialSeason
                    null,	//adjacentTo
                    null,	//startItemId (10)
                    null,	//minIndexNumber
                    null,	//minStartDate
                    null,	//maxStartDate
                    null,	//minEndDate
                    null,	//maxEndDate
                    null,	//minPlayers
                    null,	//maxPlayers
                    null,	//parentIndexNumber
                    null,	//hasParentalRating
                    null,	//isHD (20)
                    null,	//isUnaired
                    null,	//minCommunityRating
                    null,	//minCriticRating
                    null,	//airedDuringSeason
                    null,	//minPremiereDate
                    null,	//minDateLastSaved
                    null,	//minDateLastSavedForUser
                    null,	//maxPremiereDate
                    null,	//hasOverview
                    null,	//hasImdbId (30)
                    null,	//hasTmdbId
                    null,	//hasTvdbId
                    null,	//excludeItemIds
                    null,	//startIndex
                    null,	//limit
                    null,	//recursive
                    null,	//searchTerm
                    null,	//sortOrder
                    null,	//parentId
                    null,	//fields (40)
                    null,	//excludeItemTypes
                    null,	//includeItemTypes
                    null,	//anyProviderIdEquals
                    null,	//filters
                    null,	//isFavorite
                    null,	//isMovie
                    null,	//isSeries
                    null,	//isFolder
                    null,	//isNews
                    null,	//isKids (50)
                    null,	//isSports
                    null,	//isNew
                    null,	//isPremiere
                    null,	//isNewOrPremiere
                    null,	//isRepeat
                    null,	//projectToMedia
                    null,	//mediaTypes
                    null,	//imageTypes
                    null,	//sortBy
                    null,	//isPlayed (60)
                    null,	//genres
                    null,	//officialRatings
                    null,	//tags
                    null,	//excludeTags
                    null,	//years
                    null,	//enableImages
                    null,	//enableUserData
                    null,	//imageTypeLimit
                    null,	//enableImageTypes
                    null,	//person (70)
                    null,	//personIds
                    null,	//personTypes
                    null,	//studios
                    null,	//studioIds
                    null,	//artists
                    null,	//artistIds
                    null,	//albums
                    null,	//ids
                    null,	//videoTypes
                    null,	//containers (80)
                    null,	//audioCodecs
                    null,	//audioLayouts
                    null,	//videoCodecs
                    null,	//extendedVideoTypes
                    null,	//subtitleCodecs
                    null,	//path
                    null,	//userId
                    null,	//minOfficialRating
                    null,	//isLocked
                    null,	//isPlaceHolder (90)
                    null,	//hasOfficialRating
                    null,	//groupItemsIntoCollections
                    null,	//is3D
                    null,	//seriesStatus
                    null,	//nameStartsWithOrGreater
                    null,	//artistStartsWithOrGreater
                    null,	//albumArtistStartsWithOrGreater
                    null,	//nameStartsWith
                    null	//nameLessThan
            );
            if (listTags != null && listTags.getItems() != null) {
                return listTags.getItems();
            }
        } catch (Exception e) {
            System.err.println("Lỗi getTags: " + e.getMessage());
        }
        return new ArrayList<>();
    }

    // (Đã sửa lỗi 107 tham số ở phiên trước)
    @Override
    public List<BaseItemDto> getItemsByTagName(String tagName) {
        try {
            QueryResultBaseItemDto listItems = itemsServiceApi.getItems(
                    null,	//artistType
                    null,	//maxOfficialRating
                    null,	//hasThemeSong
                    null,	//hasThemeVideo
                    null,	//hasSubtitles
                    null,	//hasSpecialFeature
                    null,	//hasTrailer
                    null,	//isSpecialSeason
                    null,	//adjacentTo
                    null,	//startItemId (10)
                    null,	//minIndexNumber
                    null,	//minStartDate
                    null,	//maxStartDate
                    null,	//minEndDate
                    null,	//maxEndDate
                    null,	//minPlayers
                    null,	//maxPlayers
                    null,	//parentIndexNumber
                    null,	//hasParentalRating
                    null,	//isHD (20)
                    null,	//isUnaired
                    null,	//minCommunityRating
                    null,	//minCriticRating
                    null,	//airedDuringSeason
                    null,	//minPremiereDate
                    null,	//minDateLastSaved
                    null,	//minDateLastSavedForUser
                    null,	//maxPremiereDate
                    null,	//hasOverview
                    null,	//hasImdbId (30)
                    null,	//hasTmdbId
                    null,	//hasTvdbId
                    null,	//excludeItemIds
                    null,	//startIndex
                    null,	//limit
                    true,	//recursive (Từ TagService gốc)
                    null,	//searchTerm
                    "Ascending",	//sortOrder (Từ TagService gốc)
                    null,	//parentId
                    null,	//fields (40)
                    null,	//excludeItemTypes
                    "Movie,Series,Video,Game",	//includeItemTypes (Từ TagService gốc)
                    null,	//anyProviderIdEquals
                    null,	//filters
                    null,	//isFavorite
                    null,	//isMovie
                    null,	//isSeries
                    null,	//isFolder
                    null,	//isNews
                    null,	//isKids (50)
                    null,	//isSports
                    null,	//isNew
                    null,	//isPremiere
                    null,	//isNewOrPremiere
                    null,	//isRepeat
                    null,	//projectToMedia
                    null,	//mediaTypes
                    null,	//imageTypes
                    null,	//sortBy
                    null,	//isPlayed (60)
                    null,	//genres
                    null,	//officialRatings
                    tagName,	//tags
                    null,	//excludeTags
                    null,	//years
                    null,	//enableImages
                    null,	//enableUserData
                    null,	//imageTypeLimit
                    null,	//enableImageTypes
                    null,	//person (70)
                    null,	//personIds
                    null,	//personTypes
                    null,	//studios
                    null,	//studioIds
                    null,	//artists
                    null,	//artistIds
                    null,	//albums
                    null,	//ids
                    null,	//videoTypes
                    null,	//containers (80)
                    null,	//audioCodecs
                    null,	//audioLayouts
                    null,	//videoCodecs
                    null,	//extendedVideoTypes
                    null,	//subtitleCodecs
                    null,	//path
                    null,	//userId
                    null,	//minOfficialRating
                    null,	//isLocked
                    null,	//isPlaceHolder (90)
                    null,	//hasOfficialRating
                    null,	//groupItemsIntoCollections
                    null,	//is3D
                    null,	//seriesStatus
                    null,	//nameStartsWithOrGreater
                    null,	//artistStartsWithOrGreater
                    null,	//albumArtistStartsWithOrGreater
                    null,	//nameStartsWith
                    null	//nameLessThan
            );
            if (listItems != null && listItems.getItems() != null) {
                return listItems.getItems();
            }
        } catch (Exception e) {
            System.err.println("Lỗi getItemsByTagName: " + e.getMessage());
        }
        return new ArrayList<>();
    }
}