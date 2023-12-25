package cm.amk.crdgenerator.api;

import cm.amk.crdgenerator.payload.ResponseMessage;
import cm.amk.crdgenerator.service.FactureService;
import cm.amk.crdgenerator.util.ExcelHelper;
import cm.amk.crdgenerator.util.ZipHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;

@Controller
@RequiredArgsConstructor
public class FactureAPI {

    private final FactureService factureService;
    
    @GetMapping("/facture")
    public String skv(Model model) {
        return "facture";
    }

    @PostMapping(value = "/facture-upload")
    public String generate(Model model,
                           @RequestParam(value = "file", required = true) MultipartFile file,
                           @RequestParam(value = "quotient", required = true, defaultValue = "1") BigDecimal quotient) {
        try {
            if (!ExcelHelper.isExcelFormat(file) && !ZipHelper.hasZipFormat(file)) {
                model.addAttribute("responseMessage", ResponseMessage.builder().success(false).message("Fichier zip ou excel invalide: " + file.getOriginalFilename() + "!").build());
                return "facture_download";
            }
            String filename="";
            if(ExcelHelper.isExcelFormat(file)){
                filename = factureService.processExcelFile(file,quotient);
            }
            else {
                filename = factureService.processZipFile(file, quotient);
            }
           model.addAttribute("responseMessage", ResponseMessage.builder().success(true).message( "Votre fichier a été correctement traité").build());
            model.addAttribute("filename", filename);
            return "facture_download";
        } catch (Exception e) {
           model.addAttribute("responseMessage", ResponseMessage.builder().success(false).message( e.getLocalizedMessage()).build());
            return "facture_download";
        }
    }

    @GetMapping("/facture-download/{filename:.+}")
    @ResponseBody
    public ResponseEntity<Resource> serveFile(@PathVariable String filename) {
        Resource file = factureService.loadAsResource(filename);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.getFilename() + "\"")
                .body(file);
    }
}
