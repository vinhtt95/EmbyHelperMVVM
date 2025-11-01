package com.embyhelper.model;

/**
 * Enum đại diện cho các loại metadata, chứa các key i18n.
 */
public enum MetadataType {
    STUDIO("metadata.studios", "metadata.studio.singular"),
    GENRE("metadata.genres", "metadata.genre.singular"),
    PEOPLE("metadata.people", "metadata.people.singular"),
    TAG("metadata.tags", "metadata.tag.singular");

    private final String pluralKey;
    private final String singularKey;

    MetadataType(String pluralKey, String singularKey) {
        this.pluralKey = pluralKey;
        this.singularKey = singularKey;
    }

    public String getPluralKey() { return pluralKey; }
    public String getSingularKey() { return singularKey; }
}