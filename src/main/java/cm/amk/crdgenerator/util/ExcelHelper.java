package cm.amk.crdgenerator.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
public class ExcelHelper {

    public static final String TYPE1 = "application/vnd.ms-excel";
    public static final String TYPE2 = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    public static final String TYPE3 = "application/octet-stream";

    public static boolean hasExcelFormat(MultipartFile file) {
        log.info("***************************: {}", file.getContentType());
        if(!TYPE1.equals(file.getContentType()) && !TYPE2.equals(file.getContentType()) && !TYPE3.equals(file.getContentType())) {
            return false;
        }
        return true;
    }
}
