package cm.amk.crdgenerator.api;

import cm.amk.crdgenerator.payload.ResponseMessage;
import cm.amk.crdgenerator.service.PointageService;
import cm.amk.crdgenerator.util.ExcelHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
@Slf4j
@Controller
@RequiredArgsConstructor
public class PointageAPI {
    private final PointageService pointage;

    @GetMapping("/pointage")
    public String crd() {
        return "pointage";
    }

    @PostMapping(value = "/pointage-upload")
    public String generate(Model model,
                           @RequestParam(value = "file") MultipartFile file,
                           @RequestParam(value = "date_debut") LocalDate dateDebut,
                           @RequestParam(value = "date_fin") LocalDate dateFin) {
        try {
            if (!ExcelHelper.isExcelFormat(file)) {
                model.addAttribute("responseMessage", ResponseMessage.builder().success(false).message("Fichier excel invalide: " + file.getOriginalFilename() + "!").build());
                return "pointage_download";
            }
            String filename="";
            filename = pointage.processExcelFile(file,dateDebut, dateFin);
            model.addAttribute("responseMessage", ResponseMessage.builder().success(true).message( "Votre fichier a été correctement traité").build());
            model.addAttribute("filename", filename);
            return "pointage_download";
        } catch (Exception e) {
            log.error("{}", e.getMessage());
            model.addAttribute("responseMessage", ResponseMessage.builder().success(false).message(e.getMessage()).build());
            return "pointage_download";
        }
    }

    @GetMapping("/pointage-download/{filename:.+}")
    @ResponseBody
    public ResponseEntity<Resource> serveFile(@PathVariable String filename) {
        Resource file = pointage.loadAsResource(filename);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.getFilename() + "\"")
                .body(file);
    }
}
