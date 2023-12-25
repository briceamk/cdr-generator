package cm.amk.crdgenerator.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
public class ZipHelper {
    public static final String TYPE_WINDOWS = "application/x-zip-compressed";
    public static final String TYPE_MACOS = "application/zip";
    public static boolean hasZipFormat(MultipartFile file) {
        log.info("***************************: {}", file.getContentType());
        return TYPE_WINDOWS.equals(file.getContentType()) || TYPE_MACOS.equals(file.getContentType());
    }
}
