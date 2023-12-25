package cm.amk.crdgenerator.service;

import cm.amk.crdgenerator.config.FileStorageConfig;
import cm.amk.crdgenerator.exception.FileStorageException;
import cm.amk.crdgenerator.exception.ResourceNotFoundException;
import cm.amk.crdgenerator.model.Crd;
import cm.amk.crdgenerator.util.CsvHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Service
public class CrdService {
    private final Path storagePath;

    public CrdService(FileStorageConfig fileStorageConfig) {
        this.storagePath = Paths.get(fileStorageConfig.getStorageLocation())
                .toAbsolutePath()
                .normalize();
        try{
            Files.createDirectories(this.storagePath);
        }catch (Exception ex){
            throw new FileStorageException("We can't create directory where file will be saved");
        }
    }

    public List<Crd> read(MultipartFile file) {

        try {
            List<Crd> datas = CsvHelper.csvToData(file.getInputStream());
            return datas;
        } catch (IOException e) {
            throw new RuntimeException("fail to read csv data: " + e.getMessage());
        }
    }

    public ByteArrayInputStream write(MultipartFile file, BigDecimal quotient) {
        List<Crd> datas = read(file);
        ByteArrayInputStream csvData = CsvHelper.dataToCSV(datas, quotient);
        return csvData;
    }

    public String saveFile(MultipartFile file, InputStreamResource resultFile)  {
        //copy file to the target location (Replace existing image with the same image name
        Path targetLocation;
        try{
            String fileName = file.getOriginalFilename().replace(" ", "_")
                    .replace(".csv", "_") + LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMddyyyy_HHmmss")) + ".csv";
            targetLocation = this.storagePath.resolve(fileName);

            Files.copy(resultFile.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);
            return fileName;
        }catch (IOException ex){
            throw new FileStorageException("Could not store file " + file.getOriginalFilename() + ". Please try again!");
        }
    }


    public Resource loadAsResource(String filename) {
        try {
            Path file = this.storagePath.resolve(filename);
            Resource resource = new UrlResource(file.toUri());
            if (resource.exists() || resource.isReadable()) {
                return resource;
            } else {
                throw new ResourceNotFoundException("Impossible de lire le fichier: " + filename);
            }
        } catch (MalformedURLException e) {
            throw new ResourceNotFoundException("Fichier introuvable: " + filename, e);
        }
    }
}
