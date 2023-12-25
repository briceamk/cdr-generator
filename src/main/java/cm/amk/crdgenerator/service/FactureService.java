package cm.amk.crdgenerator.service;

import cm.amk.crdgenerator.config.FileStorageConfig;
import cm.amk.crdgenerator.exception.FileStorageException;
import cm.amk.crdgenerator.exception.ResourceNotFoundException;
import org.apache.poi.EncryptedDocumentException;
import org.apache.poi.ss.usermodel.*;
import org.apache.tomcat.util.http.fileupload.FileUtils;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

@Service("skvService")
public class FactureService {

    private final Path storagePath;

    public FactureService(FileStorageConfig fileStorageConfig) {
        this.storagePath = Paths.get(fileStorageConfig.getStorageLocation())
                .toAbsolutePath()
                .normalize();
        try{
            Files.createDirectories(this.storagePath);
        }catch (Exception ex){
            throw new FileStorageException("We can't create directory where file will be saved");
        }
    }

    public String processExcelFile(MultipartFile file, BigDecimal quotient) {
        Path targetLocation = saveExcelFile(file);
        transformExcelFile(targetLocation.toString(), quotient);
        return file.getOriginalFilename();
    }

    private Path saveExcelFile(MultipartFile file) {
        //copy file to the target location (Replace existing image with the same image name
        Path targetLocation;
        try{
            String filename = file.getOriginalFilename();
            targetLocation = this.storagePath.resolve(filename);

            Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);
            return targetLocation;
        }catch (IOException ex){
            throw new FileStorageException("Could not store file " + file.getOriginalFilename() + ". Please try again!");
        }
    }

    public String processZipFile(MultipartFile file, BigDecimal quotient) throws IOException {
        String originalFilename = file.getOriginalFilename();
        Map<String, Object> map = unzipFile(file);
        List<String> fileList = (List) map.get("fileList");
        File destDir = (File) map.get("destDir");
        fileList.stream().forEach(filePath -> transformExcelFile(filePath, quotient));
        String zipFileName = zipFiles(fileList, destDir, originalFilename);
        return zipFileName;
    }

    private void transformExcelFile(String filename, BigDecimal quotient) {
        try {
            FileInputStream inputStream = new FileInputStream(new File(filename));
            Workbook workbook = WorkbookFactory.create(inputStream);
            //Update value in second sheet
            Sheet sheet1 = workbook.getSheetAt(1);
            Iterator<Row> rowIt = sheet1.iterator();
            BigDecimal totalMinutes = new BigDecimal("0");
            BigDecimal totalAmount = new BigDecimal("0");
            int lastRowIndex = sheet1.getLastRowNum();
            String currency = "";
            while(rowIt.hasNext()) {
                Row row = rowIt.next();
                // iterate on cells for the current row
                Iterator<Cell> cellIterator = row.cellIterator();
                while (cellIterator.hasNext()) {
                    Cell cell = cellIterator.next();
                    if(cell.getRowIndex() != 0)  {
                        if((cell.getColumnIndex() == 2 || cell.getColumnIndex() == 4)){
                            String cellValue = cell.toString().replaceAll("\\s","").replace(",","");
                            if(cell.getRowIndex() == lastRowIndex && cell.getColumnIndex() == 4) {
                                currency = cellValue.substring(0, 3);
                                cellValue = cellValue.replace(currency, "");
                            }
                            if(cellValue.startsWith(".")) {
                                cellValue = "0" +cellValue;
                            }
                            BigDecimal newValue = new BigDecimal(cellValue);
                            newValue = newValue.multiply(quotient);
                            if(cell.getRowIndex() == lastRowIndex) {
                                if(cell.getColumnIndex() == 2){
                                    cell.setCellValue(String.format(Locale.US, "%,.3f",totalMinutes.setScale(3, RoundingMode.HALF_UP).doubleValue()));
                                    //cell.setCellStyle(style);
                                }
                                else{
                                    cell.setCellValue(currency + " " +String.format(Locale.US, "%,.3f",totalAmount.setScale(3, RoundingMode.HALF_UP).doubleValue()));
                                    //cell.setCellStyle(currencyStyle);
                                }

                            } else {
                                cell.setCellValue(String.format(Locale.US, "%,.3f",newValue.setScale(3, RoundingMode.HALF_UP).doubleValue()));
                                //cell.setCellStyle(style);

                                if(cell.getColumnIndex() == 2)
                                    totalMinutes = totalMinutes.add(newValue);

                                else
                                    totalAmount = totalAmount.add(newValue);
                            }
                        }

                    }
                }
            }

            // Update value in first sheet
            Sheet sheet0 = workbook.getSheetAt(0);
            Cell totalCell = sheet0.getRow(19).getCell(1);
            totalCell.setCellValue(currency + " " +String.format(Locale.US, "%,.3f",totalAmount.setScale(3, RoundingMode.HALF_UP).doubleValue()));
            //totalCell.setCellStyle(currencyStyle);
            Cell totalDueCell = sheet0.getRow(21).getCell(1);
            totalDueCell.setCellValue(currency + " " +String.format(Locale.US, "%,.3f",totalAmount.setScale(3, RoundingMode.HALF_UP).doubleValue()));
            //totalDueCell.setCellStyle(currencyStyle);

            inputStream.close();
            FileOutputStream outputStream = new FileOutputStream(filename);
            workbook.write(outputStream);
            workbook.close();
            outputStream.close();
        }
        catch (IOException | EncryptedDocumentException  ex) {
            ex.printStackTrace();
        }
    }

    private String zipFiles(List<String> unzippedFileList, File destDir, String outputFilename) throws IOException {
        String filename = outputFilename.replace(".zip", "") + LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMddyyyy_HHmmss")) + ".zip";
        Path targetLocation = this.storagePath.resolve(filename);
        FileOutputStream fos = new FileOutputStream(targetLocation.toString());
        ZipOutputStream zipOut = new ZipOutputStream(fos);
        for (String srcFile : unzippedFileList) {
            File fileToZip = new File(srcFile);
            FileInputStream fis = new FileInputStream(fileToZip);
            ZipEntry zipEntry = new ZipEntry(fileToZip.getName());
            zipOut.putNextEntry(zipEntry);

            byte[] bytes = new byte[1024];
            int length;
            while((length = fis.read(bytes)) >= 0) {
                zipOut.write(bytes, 0, length);
            }
            fis.close();
        }
        zipOut.close();
        fos.close();
        FileUtils.deleteDirectory(destDir);
        return filename;
    }

    private Map<String, Object> unzipFile(MultipartFile file) throws IOException {
        Path targetLocation = saveZipFile(file);
        byte[] buffer = new byte[1024];
        ZipInputStream zis = new ZipInputStream(new FileInputStream(targetLocation.toString()));
        File destDir = new File(targetLocation.toString().replace(".zip", ""));
        ZipEntry zipEntry = zis.getNextEntry();
        while (zipEntry != null) {
            File newFile = newFile(destDir, zipEntry);
            if (zipEntry.isDirectory()) {
                if (!newFile.isDirectory() && !newFile.mkdirs()) {
                    throw new IOException("Failed to create directory " + newFile);
                }
            } else {
                // fix for Windows-created archives
                File parent = newFile.getParentFile();
                if (!parent.isDirectory() && !parent.mkdirs()) {
                    throw new IOException("Failed to create directory " + parent);
                }

                // write file content
                FileOutputStream fos = new FileOutputStream(newFile);
                int len;
                while ((len = zis.read(buffer)) > 0) {
                    fos.write(buffer, 0, len);
                }
                fos.close();
            }
            zipEntry = zis.getNextEntry();
        }
        zis.closeEntry();
        zis.close();
        Files.delete(targetLocation);
        Map<String, Object> map = new LinkedHashMap<>();
        List<String> fileList = getFileList(destDir);
        map.put("fileList", fileList);
        map.put("destDir", destDir);
        return map;
    }

    private List<String> getFileList(File destDir) {
        List<String> fileList = new ArrayList<>();
        File dir = destDir;
        if(dir.listFiles().length == 1 && !dir.listFiles()[0].isFile()) {
            dir = dir.listFiles()[0];
        }
        for(File file: dir.listFiles()) {
            if(file.isFile()) {
                fileList.add(dir.toPath().resolve(file.toString()).toString());
            }
        }
        return fileList;
    }

    private Path saveZipFile(MultipartFile file) {
        //copy file to the target location (Replace existing image with the same image name
        Path targetLocation;
        try{
            String fileName = file.getOriginalFilename().replace(" ", "_")
                    .replace(".zip", "_") + LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMddyyyy_HHmmss")) + ".zip";
            targetLocation = this.storagePath.resolve(fileName);

            Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);
            return targetLocation;
        }catch (IOException ex){
            throw new FileStorageException("Could not store file " + file.getOriginalFilename() + ". Please try again!");
        }
    }

    private static File newFile(File destinationDir, ZipEntry zipEntry) throws IOException {
        File destFile = new File(destinationDir, zipEntry.getName());

        String destDirPath = destinationDir.getCanonicalPath();
        String destFilePath = destFile.getCanonicalPath();

        if (!destFilePath.startsWith(destDirPath + File.separator)) {
            throw new IOException("Entry is outside of the target dir: " + zipEntry.getName());
        }

        return destFile;
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
