package cm.amk.crdgenerator.api;

import cm.amk.crdgenerator.payload.ResponseMessage;
import cm.amk.crdgenerator.service.CrdService;
import cm.amk.crdgenerator.util.CsvHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
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
public class CdrAPI {
    private final CrdService crdService;

    @GetMapping("/cdr")
    public String crd(Model model) {
        return "cdr";
    }

    @PostMapping(value = "/cdr-upload")
    public String generate(Model model,
                           @RequestParam(value = "file", required = true) MultipartFile file,
                           @RequestParam(value = "quotient", required = true, defaultValue = "1") BigDecimal quotient) {
        try {
            if (!CsvHelper.hasCSVFormat(file)) {
                model.addAttribute("responseMessage", ResponseMessage.builder().success(false).message("Fichier CSV invalide: " + file.getOriginalFilename() + "!").build());
                return "cdr_download";
            }

            InputStreamResource resultFile = new InputStreamResource(crdService.write(file, quotient));
            String filename = crdService.saveFile(file, resultFile);
            model.addAttribute("responseMessage", ResponseMessage.builder().success(true).message( "Votre fichier a été correctement traité").build());
            model.addAttribute("filename", filename);
            return "cdr_download";
        } catch (Exception e) {
            model.addAttribute("responseMessage", ResponseMessage.builder().success(false).message( e.getLocalizedMessage()).build());
            return "cdr_download";
        }
    }

    @GetMapping("/cdr-download/{filename:.+}")
    @ResponseBody
    public ResponseEntity<Resource> serveFile(@PathVariable String filename) {
        Resource file = crdService.loadAsResource(filename);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.getFilename() + "\"")
                .body(file);
    }
}
