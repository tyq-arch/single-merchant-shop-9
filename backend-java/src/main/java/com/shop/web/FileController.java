package com.shop.web;

import com.shop.config.AppConfig;
import com.shop.errors.ApiException;
import com.shop.util.Utils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** 文件接口：商品图（JPG/PNG）与富媒体描述素材（图片/视频）本地上传。 */
@RestController
@RequestMapping("/api/files")
public class FileController {

    private final AppConfig config;

    public FileController(AppConfig config) {
        this.config = config;
    }

    /** 商品图片：最多 9 张、单张不超过 5MB、仅 JPG / PNG（FR-008）。 */
    @PostMapping("/images")
    public Map<String, Object> uploadImage(@RequestParam("file") MultipartFile file) throws IOException {
        String ext = extensionOf(file.getOriginalFilename());
        if (!config.getAllowedImageExt().contains(ext)) {
            throw new ApiException(400, "INVALID_IMAGE_FORMAT", "商品图片仅支持 jpg / jpeg / png 格式");
        }
        if (file.getSize() > config.getMaxImageBytes()) {
            throw new ApiException(400, "IMAGE_TOO_LARGE",
                    "图片大小不能超过 " + (config.getMaxImageBytes() / (1024 * 1024)) + "MB");
        }
        return store(file, ext, "image");
    }

    /** 富媒体描述素材：图片支持 JPG/PNG/GIF/WEBP，视频支持 MP4/WEBM。 */
    @PostMapping("/media")
    public Map<String, Object> uploadMedia(@RequestParam("file") MultipartFile file) throws IOException {
        String ext = extensionOf(file.getOriginalFilename());
        if (!config.getAllowedMediaExt().contains(ext)) {
            throw new ApiException(400, "INVALID_MEDIA_FORMAT",
                    "富媒体描述仅支持 jpg / jpeg / png / gif / webp / mp4 / webm");
        }
        boolean video = ".mp4".equals(ext) || ".webm".equals(ext);
        long limit = video ? config.getMaxMediaBytes() : config.getMaxImageBytes();
        if (file.getSize() > limit) {
            throw new ApiException(400, "MEDIA_TOO_LARGE",
                    "文件大小不能超过 " + (limit / (1024 * 1024)) + "MB");
        }
        return store(file, ext, video ? "video" : "image");
    }

    private Map<String, Object> store(MultipartFile file, String ext, String mediaType) throws IOException {
        if (file.isEmpty()) {
            throw new ApiException(400, "EMPTY_FILE", "上传文件为空");
        }
        String filename = Utils.newId() + ext;
        Path target = config.getUploadDir().resolve(filename);
        Files.createDirectories(config.getUploadDir());
        file.transferTo(target.toFile());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("url", "/uploads/" + filename);
        result.put("original_name", file.getOriginalFilename());
        result.put("size", file.getSize());
        result.put("media_type", mediaType);
        return result;
    }

    private static String extensionOf(String filename) {
        if (filename == null) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot).toLowerCase();
    }
}
