package cm.amk.crdgenerator.service;

import cm.amk.crdgenerator.config.FileStorageConfig;
import cm.amk.crdgenerator.exception.FileStorageException;
import cm.amk.crdgenerator.exception.PointageException;
import cm.amk.crdgenerator.exception.ResourceNotFoundException;
import cm.amk.crdgenerator.model.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static java.lang.Math.abs;
import static java.time.temporal.ChronoUnit.SECONDS;

/**
 * @author bamk
 * @version 1.0
 * @since 20/01/2024
 */
@Slf4j
@Service
public class PointageService {

    private static final String ZERO_TIME = "00:00:00";
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter FILE_NAME_TIME_FORMAT = DateTimeFormatter.ofPattern("HHmmss");
    private static final String DEFAULT_CHECKOUT_TIME = "18:00:00";
    private static final String DEFAULT_CHECKIN_TIME = "08:00:00";
    private static final String MIDDLE_DAY_TIME = "12:00:00";
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FILE_DATE_FORMAT = DateTimeFormatter.ofPattern("ddMMyyyy");
    private static final String PAS_POINTER = "PAS POINTER";
    private static final String JOUR_NON_OUVRABLE = "JOUR NON OUVRABLE";
    private static final List<DeductibleRule> RULES = List.of(
            DeductibleRule.builder()
                    .from(900)
                    .to(3600)
                    .deductibleHours(3600)
                    .build(),
            DeductibleRule.builder()
                    .from(3601)
                    .to(7200)
                    .deductibleHours(7200)
                    .build(),
            DeductibleRule.builder()
                    .from(7201)
                    .to(10800)
                    .deductibleHours(10800)
                    .build(),
            DeductibleRule.builder()
                    .from(10801)
                    .to(14400)
                    .deductibleHours(14400)
                    .build()
    );
    public static final String TIME_STRING_FORMAT = "%s:00:00";
    private final Path storagePath;

    public PointageService(FileStorageConfig fileStorageConfig) {
        this.storagePath = Paths.get(fileStorageConfig.getStorageLocation())
                .toAbsolutePath()
                .normalize();
        try{
            Files.createDirectories(this.storagePath);
        }catch (Exception ex){
            throw new FileStorageException("We can't create directory where file will be saved");
        }
    }
    public String processExcelFile(MultipartFile file, LocalDate dateDebut, LocalDate dateFin) throws IOException {
        validateDate(dateDebut, dateFin);
        Path targetLocation = saveExcelFile(file);
        MonthlyScoreIn monthlyScoreIn = parseFile(targetLocation.toString());
        validateClosingDays(monthlyScoreIn.getClosingDays(), dateDebut, dateFin);
        List<String> employeeNames = monthlyScoreIn.getPointageIns().stream().map(PointageIn::getName).distinct().toList();
        List<ClosingDay> closingDays = monthlyScoreIn.getClosingDays();
        ArrayList<LocalDate> openingDays = computeOpenDays(dateDebut, dateFin);
        List<MonthlyScoreOut> monthlyScoreOuts = computeMonthlyScoreOut(employeeNames, openingDays, monthlyScoreIn);


        return generateMonthlyScoreExcelFile(monthlyScoreOuts, dateDebut, dateFin, closingDays);
    }

    private String generateMonthlyScoreExcelFile(
            List<MonthlyScoreOut> monthlyScoreOuts, LocalDate dateDebut, LocalDate dateFin, List<ClosingDay> closingDays) throws IOException {
        String filename = String.format("Fichier_Pointage_%s_%s_%s.xlsx",
                dateDebut.format(FILE_DATE_FORMAT), dateFin.format(FILE_DATE_FORMAT), LocalTime.now().format(FILE_NAME_TIME_FORMAT));
        String fullPathFilename = String.valueOf(this.storagePath.resolve(filename));
        FileOutputStream outputStream = new FileOutputStream(fullPathFilename);
        XSSFWorkbook workbook = new XSSFWorkbook();
        String[] HEADER_NAME = {"NAME", "SENS", "POINTAGE", "DATE", "", "ARRIVE", "DEPART", "RETARD", "AVANCE"};
        String[] HEADER_SUMMARY_NAME = {
                "NAME",
                "TOTAL ARRIVE RETARD",
                "TOTAL DEDUCTIBLE ARRIVE RETARD",
                "TOTAL DEPART AVANCE ",
                "TOTAL DEDUCTIBLE DEPART AVANCE",
                "TOTAL NON POINTE ARRIVE",
                "TOTAL DEDUCTIBE NON POINTE ARRIVE",
                "TOTAL NON POINTE DEPART",
                "TOTAL DEDUCTIBLE NON POINTE DEPART",
                "TOTAL DEDUCTIBLE GENERAL"
        };
        Integer[] HEADER_INDEX = {0, 1, 2, 3, 4, 5, 6, 7, 8};
        Integer[] HEADER_SUMMARY_INDEX = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
        List<PointageSummary> pointageSummaries = new ArrayList<>();
        for(MonthlyScoreOut monthlyScoreOut: monthlyScoreOuts) {
            Sheet sheet = workbook.createSheet(monthlyScoreOut.getEmployeeName());
            sheet.setColumnWidth(0, 26 * 256);
            sheet.setColumnWidth(1, 5 * 256);
            sheet.setColumnWidth(2, 20 * 256);
            sheet.setColumnWidth(3, 13 * 256);
            sheet.setColumnWidth(4, 2 * 256);
            sheet.setColumnWidth(5, 13 * 256);
            sheet.setColumnWidth(6, 13 * 256);
            sheet.setColumnWidth(7, 18 * 256);
            sheet.setColumnWidth(8, 18 * 256);
            int rowNumber = 0;
            Row row = sheet.createRow(rowNumber);
            row.createCell(HEADER_INDEX[0]).setCellValue(HEADER_NAME[0]);
            row.createCell(HEADER_INDEX[1]).setCellValue(HEADER_NAME[1]);
            row.createCell(HEADER_INDEX[2]).setCellValue(HEADER_NAME[2]);
            row.createCell(HEADER_INDEX[3]).setCellValue(HEADER_NAME[3]);
            row.createCell(HEADER_INDEX[4]).setCellValue(HEADER_NAME[4]);
            row.createCell(HEADER_INDEX[5]).setCellValue(HEADER_NAME[5]);
            row.createCell(HEADER_INDEX[6]).setCellValue(HEADER_NAME[6]);
            row.createCell(HEADER_INDEX[7]).setCellValue(HEADER_NAME[7]);
            row.createCell(HEADER_INDEX[8]).setCellValue(HEADER_NAME[8]);
            formatHeaderCell(row, HEADER_INDEX, workbook);
            rowNumber += 1;
            long totalArrivedAfterTime = 0L;
            long totalDepartureBeforeTime = 0L;
            int totalNotPointedMorning = 0;
            int totalNotPointedEvening = 0;
            int totalDeductibleArrivedAfterTime = 0;
            int totalDeductibleDepartureBeforeTime =  0;
            int totalDeductibleNotPointedMorning =  0;
            int totalDeductibleNotPointedEvening =  0;
            int totalDeductible =  0;
            for(PointageOut pointageOut: monthlyScoreOut.getPointageOuts()) {
                row = sheet.createRow(rowNumber);
                row.createCell(HEADER_INDEX[0]).setCellValue(monthlyScoreOut.getEmployeeName());
                row.createCell(HEADER_INDEX[1]).setCellValue(pointageOut.getSens().name());
                row.createCell(HEADER_INDEX[2]).setCellValue(pointageOut.getCheckTime() != null ? pointageOut.getCheckTime().format(DATE_TIME_FORMAT): "");
                row.createCell(HEADER_INDEX[3]).setCellValue(pointageOut.getDay().format(DATE_FORMAT));
                row.createCell(HEADER_INDEX[4]).setCellValue("");

                Optional<ClosingDay> isClosingDay = closingDays.stream().filter(closingDay -> closingDay.getDate().isEqual(pointageOut.getDay())).findFirst();
                if (isClosingDay.isEmpty()) {
                    row.createCell(HEADER_INDEX[5]).setCellValue(pointageOut.getCheckInTime() != null ? pointageOut.getCheckInTime().format(TIME_FORMAT): "");
                    row.createCell(HEADER_INDEX[6]).setCellValue(pointageOut.getCheckOutTime() != null ? pointageOut.getCheckOutTime().format(TIME_FORMAT): "");
                    row.createCell(HEADER_INDEX[7]).setCellValue(pointageOut.getArrivedAfterTime() != null ? pointageOut.getArrivedAfterTime().format(TIME_FORMAT): pointageOut.getSens().equals(Sens.IN) ? PAS_POINTER : "" );
                    row.createCell(HEADER_INDEX[8]).setCellValue(pointageOut.getDepartureBeforeTime() != null ? pointageOut.getDepartureBeforeTime().format(TIME_FORMAT): pointageOut.getSens().equals(Sens.OUT) ? PAS_POINTER : "");
                    //We consider only time upper to 15minutes or 900seconds
                    if(pointageOut.getSens().equals(Sens.IN)) {
                        if(pointageOut.getArrivedAfterTime() != null) {
                            long seconds = TimeUnit.SECONDS.toSeconds(pointageOut.getArrivedAfterTime().toSecondOfDay());
                            totalArrivedAfterTime += seconds >=  900 ? seconds: 0;
                            int deductibleArrivedAfterTime = seconds >= 900 ? computeDeductibleHours(seconds, RULES) : 0;
                            totalDeductibleArrivedAfterTime += deductibleArrivedAfterTime;
                            totalDeductible += deductibleArrivedAfterTime;;
                        } else {
                            totalNotPointedMorning += 1;
                            totalDeductibleNotPointedMorning += 10800;
                            totalDeductible += 10800;
                        }
                    }
                    if(pointageOut.getSens().equals(Sens.OUT)) {
                        if(pointageOut.getDepartureBeforeTime() != null) {
                            long seconds = TimeUnit.SECONDS.toSeconds(pointageOut.getDepartureBeforeTime().toSecondOfDay());
                            totalDepartureBeforeTime += seconds >=  900 ? seconds: 0;
                            int deductibleBeforeDepartureTime = seconds >= 900 ? computeDeductibleHours(seconds, RULES) : 0;
                            totalDeductibleDepartureBeforeTime += deductibleBeforeDepartureTime;
                            totalDeductible += deductibleBeforeDepartureTime;
                        } else {
                            totalNotPointedEvening += 1;
                            totalDeductibleNotPointedEvening += 10800;
                            totalDeductible += 10800;
                        }
                    }
                } else {
                    row.createCell(HEADER_INDEX[5]).setCellValue(pointageOut.getCheckInTime() != null ? pointageOut.getCheckInTime().format(TIME_FORMAT): "");
                    row.createCell(HEADER_INDEX[6]).setCellValue(pointageOut.getCheckOutTime() != null ? pointageOut.getCheckOutTime().format(TIME_FORMAT): "");
                    row.createCell(HEADER_INDEX[7]).setCellValue(pointageOut.getArrivedAfterTime() != null ? JOUR_NON_OUVRABLE : pointageOut.getSens().equals(Sens.IN) ? JOUR_NON_OUVRABLE : "" );
                    row.createCell(HEADER_INDEX[8]).setCellValue(pointageOut.getDepartureBeforeTime() != null ? JOUR_NON_OUVRABLE : pointageOut.getSens().equals(Sens.OUT) ? JOUR_NON_OUVRABLE : "");
                }
                formatSensCell(row, HEADER_INDEX, workbook, pointageOut.getSens());
                rowNumber += 1;
            }
            pointageSummaries.add(PointageSummary.builder()
                    .employeeName(monthlyScoreOut.getEmployeeName())
                    .totalArrivedAfterTime(LocalTime.ofSecondOfDay(totalArrivedAfterTime))
                    .totalDeductibleArrivedAfterTime(String.format(TIME_STRING_FORMAT, Duration.ofSeconds(totalDeductibleArrivedAfterTime).toHours()))
                    .totalDepartureBeforeTime(LocalTime.ofSecondOfDay(totalDepartureBeforeTime))
                    .totalDeductibleDepartureBeforeTime(String.format(TIME_STRING_FORMAT, Duration.ofSeconds(totalDeductibleDepartureBeforeTime).toHours()))
                    .totalNotPointedMorning(totalNotPointedMorning)
                    .totalDeductibleNotPointedMorning(String.format(TIME_STRING_FORMAT, Duration.ofSeconds(totalDeductibleNotPointedMorning).toHours()))
                    .totalNotPointedEvening(totalNotPointedEvening)
                    .totalDeductibleNotPointedEvening(String.format(TIME_STRING_FORMAT, Duration.ofSeconds(totalDeductibleNotPointedEvening).toHours()))
                    .totalDeductible(String.format(TIME_STRING_FORMAT, Duration.ofSeconds(totalDeductible).toHours()))
                    .build());

        }

        Sheet sheet = workbook.createSheet("Synthese");
        sheet.setColumnWidth(0, 26 * 256);
        sheet.setColumnWidth(1, 20 * 256);
        sheet.setColumnWidth(2, 20 * 256);
        sheet.setColumnWidth(3, 20 * 256);
        sheet.setColumnWidth(4, 20 * 256);
        sheet.setColumnWidth(5, 20 * 256);
        sheet.setColumnWidth(6, 20 * 256);
        sheet.setColumnWidth(7, 20 * 256);
        sheet.setColumnWidth(8, 20 * 256);
        sheet.setColumnWidth(9, 20 * 256);
        int rowNumber = 0;
        Row row = sheet.createRow(rowNumber);
        row.createCell(HEADER_SUMMARY_INDEX[0]).setCellValue(HEADER_SUMMARY_NAME[0]);
        row.createCell(HEADER_SUMMARY_INDEX[1]).setCellValue(HEADER_SUMMARY_NAME[1]);
        row.createCell(HEADER_SUMMARY_INDEX[2]).setCellValue(HEADER_SUMMARY_NAME[2]);
        row.createCell(HEADER_SUMMARY_INDEX[3]).setCellValue(HEADER_SUMMARY_NAME[3]);
        row.createCell(HEADER_SUMMARY_INDEX[4]).setCellValue(HEADER_SUMMARY_NAME[4]);
        row.createCell(HEADER_SUMMARY_INDEX[5]).setCellValue(HEADER_SUMMARY_NAME[5]);
        row.createCell(HEADER_SUMMARY_INDEX[6]).setCellValue(HEADER_SUMMARY_NAME[6]);
        row.createCell(HEADER_SUMMARY_INDEX[7]).setCellValue(HEADER_SUMMARY_NAME[7]);
        row.createCell(HEADER_SUMMARY_INDEX[8]).setCellValue(HEADER_SUMMARY_NAME[8]);
        row.createCell(HEADER_SUMMARY_INDEX[9]).setCellValue(HEADER_SUMMARY_NAME[9]);
        formatSummaryHeaderCell(row, HEADER_SUMMARY_INDEX, workbook);
        rowNumber += 1;
        for(PointageSummary pointageSummary: pointageSummaries) {
            row = sheet.createRow(rowNumber);
            row.createCell(HEADER_SUMMARY_INDEX[0]).setCellValue(pointageSummary.getEmployeeName());
            row.createCell(HEADER_SUMMARY_INDEX[1]).setCellValue(pointageSummary.getTotalArrivedAfterTime() != null ? pointageSummary.getTotalArrivedAfterTime().format(TIME_FORMAT): LocalTime.parse("OO:00:00", TIME_FORMAT).format(TIME_FORMAT));
            row.createCell(HEADER_SUMMARY_INDEX[2]).setCellValue(pointageSummary.getTotalDeductibleArrivedAfterTime() != null ? pointageSummary.getTotalDeductibleArrivedAfterTime(): LocalTime.parse("OO:00:00", TIME_FORMAT).format(TIME_FORMAT));
            row.createCell(HEADER_SUMMARY_INDEX[3]).setCellValue(pointageSummary.getTotalDepartureBeforeTime() != null ? pointageSummary.getTotalDepartureBeforeTime().format(TIME_FORMAT): LocalTime.parse("OO:00:00", TIME_FORMAT).format(TIME_FORMAT));
            row.createCell(HEADER_SUMMARY_INDEX[4]).setCellValue(pointageSummary.getTotalDeductibleDepartureBeforeTime() != null ? pointageSummary.getTotalDeductibleDepartureBeforeTime(): LocalTime.parse("OO:00:00", TIME_FORMAT).format(TIME_FORMAT));
            row.createCell(HEADER_SUMMARY_INDEX[5]).setCellValue(pointageSummary.getTotalNotPointedMorning());
            row.createCell(HEADER_SUMMARY_INDEX[6]).setCellValue(pointageSummary.getTotalDeductibleNotPointedMorning() != null ? pointageSummary.getTotalDeductibleNotPointedMorning(): LocalTime.parse("00:00:00", TIME_FORMAT).format(TIME_FORMAT));
            row.createCell(HEADER_SUMMARY_INDEX[7]).setCellValue(pointageSummary.getTotalNotPointedEvening());
            row.createCell(HEADER_SUMMARY_INDEX[8]).setCellValue(pointageSummary.getTotalDeductibleNotPointedEvening() != null ? pointageSummary.getTotalDeductibleNotPointedEvening(): LocalTime.parse("00:00:00", TIME_FORMAT).format(TIME_FORMAT));
            row.createCell(HEADER_SUMMARY_INDEX[9]).setCellValue(pointageSummary.getTotalDeductible() != null ? pointageSummary.getTotalDeductible(): LocalTime.parse("00:00:00", TIME_FORMAT).format(TIME_FORMAT));
            formatSummaryCell(row, HEADER_SUMMARY_INDEX, workbook);
            rowNumber += 1;
        }
        workbook.write(outputStream);
        workbook.close();
        outputStream.close();
        return filename;
    }

    private void formatSummaryCell(Row row, Integer[] HEADER_SUMMARY_INDEX, XSSFWorkbook workbook) {
        for (Integer headerIndex : HEADER_SUMMARY_INDEX) {
            Cell cell = row.getCell(headerIndex);
            CellStyle cellStyle = workbook.createCellStyle();
            cellStyle.setBorderTop(BorderStyle.MEDIUM);
            cellStyle.setBorderBottom(BorderStyle.MEDIUM);
            cellStyle.setBorderRight(BorderStyle.THIN);
            cellStyle.setBorderLeft(BorderStyle.THIN);
            cell.setCellStyle(cellStyle);
        }

    }

    private int computeDeductibleHours(long seconds, List<DeductibleRule> rules) {
        Optional<DeductibleRule> optionalDeductibleRule = rules.stream()
                .filter(deductibleRule -> seconds >= deductibleRule.getFrom() && seconds <= deductibleRule.getTo())
                .findFirst();

        return optionalDeductibleRule.map(DeductibleRule::getDeductibleHours).orElse(0);
    }

    private void formatSensCell(Row row, Integer[] HEADER_INDEX, XSSFWorkbook workbook, Sens sens) {
        if(sens.equals(Sens.IN))
            formatCellSensIn(row, HEADER_INDEX, workbook);
        else
            formatCellSensOut(row, HEADER_INDEX, workbook);
    }

    private void formatCellSensOut(Row row, Integer[] HEADER_INDEX, XSSFWorkbook workbook) {
        for (Integer headerIndex : HEADER_INDEX) {
            Cell cell = row.getCell(headerIndex);
            CellStyle cellStyle = workbook.createCellStyle();

            if(headerIndex == 0 || headerIndex ==5) {
                cellStyle.setBorderLeft(BorderStyle.MEDIUM);
            }

            if(headerIndex ==  1) {
                cellStyle.setAlignment(HorizontalAlignment.CENTER);
            }

            if(headerIndex != 4) {
                cellStyle.setBorderBottom(BorderStyle.MEDIUM);
                cellStyle.setBorderRight(BorderStyle.THIN);
            }

            if(headerIndex == 3 || headerIndex == 8) {
                cellStyle.setBorderRight(BorderStyle.MEDIUM);
            }

            if(cell.getStringCellValue().equalsIgnoreCase(PAS_POINTER)) {
                Font font = cell.getSheet().getWorkbook().createFont();
                font.setBold(true);
                font.setColor(IndexedColors.RED.index);
                cellStyle.setFont(font);
            }

            if(cell.getStringCellValue().equalsIgnoreCase(JOUR_NON_OUVRABLE)) {
                Font font = cell.getSheet().getWorkbook().createFont();
                font.setBold(true);
                font.setColor(IndexedColors.ROYAL_BLUE.index);
                cellStyle.setFont(font);
            }

            if(headerIndex == 7 || headerIndex == 8) {
                if(!cell.getStringCellValue().isEmpty() && !cell.getStringCellValue().equals(PAS_POINTER) && !cell.getStringCellValue().equals(JOUR_NON_OUVRABLE)) {
                    long durationInMinutes = TimeUnit.SECONDS.toMinutes(
                            LocalTime.parse(cell.getStringCellValue(), TIME_FORMAT).toSecondOfDay());
                    if(durationInMinutes >= 15) {
                        Font font = cell.getSheet().getWorkbook().createFont();
                        font.setBold(true);
                        font.setColor(IndexedColors.RED.index);
                        cellStyle.setFont(font);
                    }
                }

            }

            cell.setCellStyle(cellStyle);
        }
    }

    private void formatCellSensIn(Row row, Integer[] HEADER_INDEX, XSSFWorkbook workbook) {
        for (Integer headerIndex : HEADER_INDEX) {
            Cell cell = row.getCell(headerIndex);
            CellStyle cellStyle = workbook.createCellStyle();

            if(headerIndex == 0 || headerIndex == 5) {
                cellStyle.setBorderLeft(BorderStyle.MEDIUM);
            }

            if(headerIndex ==  1) {
                cellStyle.setAlignment(HorizontalAlignment.CENTER);
            }

            if(headerIndex != 4) {
                cellStyle.setBorderTop(BorderStyle.MEDIUM);
                cellStyle.setBorderBottom(BorderStyle.THIN);
                cellStyle.setBorderRight(BorderStyle.THIN);
            }

            if(headerIndex == 3 || headerIndex == 8) {
                cellStyle.setBorderRight(BorderStyle.MEDIUM);

            }

            if(cell.getStringCellValue().equalsIgnoreCase(PAS_POINTER)) {
                Font font = cell.getSheet().getWorkbook().createFont();
                font.setBold(true);
                font.setColor(IndexedColors.RED.index);
                cellStyle.setFont(font);
            }

            if(cell.getStringCellValue().equalsIgnoreCase(JOUR_NON_OUVRABLE)) {
                Font font = cell.getSheet().getWorkbook().createFont();
                font.setBold(true);
                font.setColor(IndexedColors.ROYAL_BLUE.index);
                cellStyle.setFont(font);
            }

            if(headerIndex == 7 || headerIndex == 8) {
                if(!cell.getStringCellValue().isEmpty() && !cell.getStringCellValue().equals(PAS_POINTER) && !cell.getStringCellValue().equals(JOUR_NON_OUVRABLE)) {
                    long durationInMinutes = TimeUnit.SECONDS.toMinutes(
                            LocalTime.parse(cell.getStringCellValue(), TIME_FORMAT).toSecondOfDay());
                    if(durationInMinutes >= 15) {
                        Font font = cell.getSheet().getWorkbook().createFont();
                        font.setBold(true);
                        font.setColor(IndexedColors.RED.index);
                        cellStyle.setFont(font);
                    }
                }

            }

            cell.setCellStyle(cellStyle);
        }
    }

    private void formatSummaryHeaderCell(Row row, Integer[] HEADER_INDEX, XSSFWorkbook workbook) {
        row.setHeightInPoints(4 * row.getHeightInPoints());
        for (Integer headerIndex : HEADER_INDEX) {
            Cell cell = row.getCell(headerIndex);
            CellStyle cellStyle = workbook.createCellStyle();
            cellStyle.setAlignment(HorizontalAlignment.CENTER);
            cellStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            cellStyle.setFillForegroundColor(IndexedColors.SEA_GREEN.index);
            cellStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            Font font = cell.getSheet().getWorkbook().createFont();
            font.setBold(true);
            font.setColor(IndexedColors.WHITE.index);
            cellStyle.setFont(font);
            cellStyle.setBorderRight(BorderStyle.THIN);
            cellStyle.setWrapText(true);
            cell.setCellStyle(cellStyle);
        }
    }

    private void formatHeaderCell(Row row, Integer[] HEADER_INDEX, XSSFWorkbook workbook) {
        row.setHeightInPoints(4 * row.getHeightInPoints());
        for (Integer headerIndex : HEADER_INDEX) {
            Cell cell = row.getCell(headerIndex);
            CellStyle cellStyle = workbook.createCellStyle();
            cellStyle.setAlignment(HorizontalAlignment.CENTER);
            cellStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            if( headerIndex != 4) {
                cellStyle.setFillForegroundColor(IndexedColors.SEA_GREEN.index);
                cellStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
                Font font = cell.getSheet().getWorkbook().createFont();
                font.setBold(true);
                font.setColor(IndexedColors.WHITE.index);
                cellStyle.setFont(font);
            }
            cellStyle.setBorderRight(BorderStyle.THIN);
            cell.setCellStyle(cellStyle);
        }
    }

    private  List<MonthlyScoreOut> computeMonthlyScoreOut(
            List<String> employeeNames,
            ArrayList<LocalDate> openingDays,
            MonthlyScoreIn monthlyScoreIn
    ) {
        List<MonthlyScoreOut> monthlyScoreOuts = new ArrayList<>();
        for(String employeeName: employeeNames) {
            List<PointageOut> pointageOuts = new ArrayList<>();
            for(LocalDate openingDay: openingDays) {
                Optional<PointageIn> morningPointage = computeFirstDayCheckIn(employeeName, openingDay, monthlyScoreIn);
                Optional<PointageIn> eveningPointage = computeLastDayCheckOut(employeeName, openingDay, monthlyScoreIn);
                addMorningPointage(openingDay, morningPointage, pointageOuts);
                addEveningPointage(openingDay, eveningPointage, pointageOuts);

            }
            monthlyScoreOuts.add(
                    MonthlyScoreOut.builder()
                            .employeeName(employeeName)
                            .pointageOuts(pointageOuts)
                            .build()
            );

        }
        return monthlyScoreOuts;
    }

    private static void addEveningPointage(LocalDate openingDay, Optional<PointageIn> eveningPointage, List<PointageOut> pointageOuts) {
        if(eveningPointage.isEmpty()) {
            pointageOuts.add(
                    PointageOut.builder()
                            .sens(Sens.OUT)
                            .day(openingDay)
                            .build());
        }
        eveningPointage.ifPresent(pointageIn -> pointageOuts.add(PointageOut.builder()
                .sens(Sens.OUT)
                .checkTime(pointageIn.getCheckTime())
                .day(openingDay)
                .checkOutTime(pointageIn.getCheckTime().toLocalTime())
                .departureBeforeTime(
                        computeDepartureBeforeTime(pointageIn)
                )
                .build()));
    }

    private static void addMorningPointage(
            LocalDate openingDay,
            Optional<PointageIn> morningPointage,
            List<PointageOut> pointageOuts
    ) {
        if(morningPointage.isEmpty()) {
            pointageOuts.add(
                    PointageOut.builder()
                            .sens(Sens.IN)
                            .day(openingDay)
                            .build());
        }
        morningPointage.ifPresent(pointageIn -> pointageOuts.add(
                PointageOut.builder()
                        .sens(Sens.IN)
                        .checkTime(pointageIn.getCheckTime())
                        .day(openingDay)
                        .checkInTime(pointageIn.getCheckTime().toLocalTime())
                        .arrivedAfterTime(
                                computeArrivedAfterTime(pointageIn)
                        )
                        .build()));
    }

    private static LocalTime computeDepartureBeforeTime(PointageIn pointageIn) {
        long duration = LocalTime.parse(DEFAULT_CHECKOUT_TIME, TIME_FORMAT)
                .until(pointageIn.getCheckTime().toLocalTime(), SECONDS);
        return duration < 0 ? LocalTime.ofSecondOfDay(abs(duration)): LocalTime.parse(ZERO_TIME, TIME_FORMAT);
    }

    private static LocalTime computeArrivedAfterTime(PointageIn pointageIn) {
        long duration = LocalTime.parse(DEFAULT_CHECKIN_TIME, TIME_FORMAT)
                .until(pointageIn.getCheckTime().toLocalTime(), SECONDS);
        return duration > 0 ? LocalTime.ofSecondOfDay(duration): LocalTime.parse(ZERO_TIME, TIME_FORMAT);
    }

    private static Optional<PointageIn> computeLastDayCheckOut(String employeeName, LocalDate openingDay, MonthlyScoreIn monthlyScoreIn) {
        return monthlyScoreIn.getPointageIns().stream()
                .filter(pointageIn ->
                        pointageIn.getName().equalsIgnoreCase(employeeName) &&
                                pointageIn.getCheckTime().toLocalDate().isEqual(openingDay)
                )
                .filter(pointageIn ->
                        pointageIn.getCheckTime()
                                .isAfter(openingDay.atTime(LocalTime.parse(MIDDLE_DAY_TIME, TIME_FORMAT)))
                )
                .max(Comparator.comparing(PointageIn::getCheckTime));
    }

    private static Optional<PointageIn> computeFirstDayCheckIn(String employeeName, LocalDate openingDay, MonthlyScoreIn monthlyScoreIn) {
        return monthlyScoreIn.getPointageIns().stream()
                .filter(pointageIn ->
                        pointageIn.getName().equalsIgnoreCase(employeeName) &&
                                pointageIn.getCheckTime().toLocalDate().isEqual(openingDay)
                )
                .filter(pointageIn ->
                        pointageIn.getCheckTime().
                                isBefore(openingDay.atTime(LocalTime.parse(MIDDLE_DAY_TIME, TIME_FORMAT))
                                ) ||
                                pointageIn.getCheckTime().
                                        isEqual(
                                                openingDay.atTime(LocalTime.parse(MIDDLE_DAY_TIME, TIME_FORMAT))
                                        )
                )
                .min(Comparator.comparing(PointageIn::getCheckTime));
    }

    private void validateClosingDays(List<ClosingDay> closingDays, LocalDate dateDebut, LocalDate dateFin) {
        Optional<ClosingDay> badClosingDay = closingDays.stream().filter(
                        closingDay -> !(
                                (closingDay.getDate().isAfter(dateDebut) || closingDay.getDate().isEqual(dateDebut)) &&
                                (closingDay.getDate().isBefore(dateFin)  || closingDay.getDate().isEqual(dateFin))
                        )
                )
                .findFirst();
        if(badClosingDay.isPresent())
            throw new PointageException(
                    String.format("La date %s n'est pas comprise entre le %s et le %s.\nVeuillez corriger",
                            badClosingDay.get().getDate(),
                            dateDebut, dateFin)
            );
    }

    private ArrayList<LocalDate> computeOpenDays(LocalDate dateDebut, LocalDate dateFin) {
        ArrayList<LocalDate> periodes = new ArrayList<>();
        while(!dateDebut.isAfter(dateFin)) {
                periodes.add(dateDebut);
            dateDebut = dateDebut.plusDays(1);
        }
        return periodes;
    }

    private Path saveExcelFile(MultipartFile file) {
        //copy file to the target location (Replace existing image with the same image name
        Path targetLocation;
        try{
            String filename = Objects.requireNonNull(file.getOriginalFilename());
            targetLocation = this.storagePath.resolve(filename);

            Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);
            return targetLocation;
        }catch (IOException ex){
            throw new FileStorageException("Could not store file " + file.getOriginalFilename() + ". Please try again!");
        }
    }

    private MonthlyScoreIn parseFile(String filename) throws IOException {

        FileInputStream inputStream = new FileInputStream(filename);
        Workbook workbook = WorkbookFactory.create(inputStream);

        Sheet sheet1 = workbook.getSheetAt(0);
        Iterator<Row> rowIt1 = sheet1.iterator();
        ArrayList<PointageIn> pointageIns = new ArrayList<>();
        //We skeep the first line as it's header
        rowIt1.next();
        while(rowIt1.hasNext()) {
            Row row = rowIt1.next();
            if(row.getCell(3).getStringCellValue() != null) {
                pointageIns.add(
                        PointageIn.builder()
                                .department(row.getCell(0).getStringCellValue() != null ? row.getCell(0).getStringCellValue(): "")
                                .name(row.getCell(1).getStringCellValue() != null ? row.getCell(1).getStringCellValue(): "")
                                .checkTime(LocalDateTime.parse(row.getCell(3).getStringCellValue(), DATE_TIME_FORMAT))
                                .verifyCode(row.getCell(6).getStringCellValue() != null ? row.getCell(6).getStringCellValue(): "")
                                .build()
                );
            }

        }

        Sheet sheet2 = workbook.getSheetAt(1);
        Iterator<Row> rowIt2 = sheet2.iterator();
        ArrayList<ClosingDay> closingDays = new ArrayList<>();
        while(rowIt2.hasNext()) {
            Row row = rowIt2.next();
            if (row.getCell(0).getLocalDateTimeCellValue() != null) {
                closingDays.add(
                        ClosingDay.builder()
                                .date(row.getCell(0).getLocalDateTimeCellValue().toLocalDate())
                                .build()
                );
            }

        }
        workbook.close();

        return MonthlyScoreIn.builder()
                .pointageIns(pointageIns)
                .closingDays(closingDays)
                .build();
    }

    private void validateDate(LocalDate dateDebut, LocalDate dateFin) {
        if (dateDebut.isAfter(dateFin)) {
            throw new PointageException("La date de fin ne peut être antérieur a la date de debut!");
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
