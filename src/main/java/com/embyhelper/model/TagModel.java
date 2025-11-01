package com.embyhelper.model;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import java.util.Map;
import java.util.Objects;

public class TagModel {

    private static final Gson gson = new Gson();

    private final boolean isJson;
    private final String simpleName;
    private final String key;
    private final String value;

    // UserData (để lưu ID hoặc tên gốc cho logic nghiệp vụ)
    private Object userData;

    public TagModel(String simpleName) {
        this.isJson = false;
        this.simpleName = simpleName;
        this.key = null;
        this.value = null;
    }

    public TagModel(String key, String value) {
        this.isJson = true;
        this.simpleName = null;
        this.key = key;
        this.value = value;
    }

    /**
     * Phân tích một chuỗi thô (tên) thành TagModel.
     * @param rawName Tên từ Emby (ví dụ: "MyTag" hoặc "{\"key\":\"value\"}")
     * @return một đối tượng TagModel.
     */
    public static TagModel parse(String rawName) {
        if (rawName == null || rawName.isEmpty()) {
            return new TagModel("");
        }

        if (rawName.startsWith("{") && rawName.endsWith("}")) {
            try {
                JsonObject jsonObject = gson.fromJson(rawName, JsonObject.class);
                Map.Entry<String, com.google.gson.JsonElement> firstEntry = jsonObject.entrySet().stream().findFirst().orElse(null);
                if (firstEntry != null && firstEntry.getValue().isJsonPrimitive()) { // Kiểm tra là primitive
                    return new TagModel(firstEntry.getKey(), firstEntry.getValue().getAsString());
                } else {
                    System.err.println("Cấu trúc JSON không mong đợi: " + rawName);
                }
            } catch (JsonSyntaxException | IllegalStateException e) {
                System.err.println("Lỗi parse JSON trong TagModel, coi là chuỗi thường: " + rawName + " - " + e.getMessage());
            }
        }
        // Nếu không phải JSON hoặc parse lỗi, trả về simple tag
        return new TagModel(rawName);
    }

    /**
     * Chuyển đổi TagModel trở lại định dạng chuỗi của Emby.
     * @return Chuỗi để lưu vào Emby (ví dụ: "MyTag" hoặc "{\"key\":\"value\"}")
     */
    public String serialize() {
        if (isJson) {
            JsonObject jsonObject = new JsonObject();
            jsonObject.addProperty(key, value);
            return gson.toJson(jsonObject);
        } else {
            return simpleName;
        }
    }

    /**
     * Lấy tên hiển thị cho UI.
     * @return Ví dụ: "MyTag" hoặc "key | value"
     */
    public String getDisplayName() {
        if (isJson) {
            return String.format("%s | %s", key, value);
        } else {
            return simpleName;
        }
    }

    // --- Getters & Setters ---

    public boolean isJson() { return isJson; }
    public String getKey() { return key; }
    public String getValue() { return value; }
    public String getSimpleName() { return simpleName; }

    public Object getUserData() { return userData; }
    public void setUserData(Object userData) { this.userData = userData; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TagModel tagModel = (TagModel) o;
        return isJson == tagModel.isJson &&
                Objects.equals(simpleName, tagModel.simpleName) &&
                Objects.equals(key, tagModel.key) &&
                Objects.equals(value, tagModel.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(isJson, simpleName, key, value);
    }

    @Override
    public String toString() {
        return "TagModel{" + getDisplayName() + "}";
    }
}