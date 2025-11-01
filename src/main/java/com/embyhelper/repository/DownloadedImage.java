package com.embyhelper.repository;

/**
 * Lớp lồng (inner class) để trả về kết quả download ảnh
 * (SỬA LỖI: Chuyển ra file riêng)
 */
public class DownloadedImage {
    public byte[] bytes;
    public String mediaType;
    public DownloadedImage(byte[] bytes, String mediaType) {
        this.bytes = bytes;
        this.mediaType = mediaType;
    }
}