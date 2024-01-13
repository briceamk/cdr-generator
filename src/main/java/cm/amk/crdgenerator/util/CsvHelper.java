package cm.amk.crdgenerator.util;

import cm.amk.crdgenerator.model.Crd;
import cm.amk.crdgenerator.model.CsvFileContent;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Stream;

@Slf4j
public class CsvHelper {

    private static final char DELIMITER = ';';
    private static final String TYPE1 = "text/csv";
    private static final String TYPE2= "application/vnd.ms-excel";

    public static boolean isCSVFormat(MultipartFile file) {
        return TYPE1.equals(file.getContentType()) || TYPE2.equals(file.getContentType());
    }

    public static CsvFileContent fromCsvToCrd(InputStream is) {
        try (BufferedReader fileReader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
             CSVParser csvParser = new CSVParser(fileReader,
                     CSVFormat.EXCEL.withDelimiter(DELIMITER).withFirstRecordAsHeader().withIgnoreHeaderCase().withTrim())) {

            List<Crd> crdList = new ArrayList<>();
            List<String> otherLine = new ArrayList<>();


            Iterable<CSVRecord> csvRecords = csvParser.getRecords();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

            List<String> allHeaderInCsvFile = csvParser.getHeaderNames();

            List<String> nonRequiredHeaderName = allHeaderInCsvFile.stream()
                    .filter(header -> !Arrays.asList(CsvFileContent.REQUIRED_HEADERS).contains(header))
                    .toList();

            for (CSVRecord csvRecord : csvRecords) {
                Crd crd = Crd.builder()
                        .connectTime1(LocalDateTime.parse(csvRecord.get(CsvFileContent.REQUIRED_HEADERS[0]), formatter))
                        .accountingTime(LocalDateTime.parse(csvRecord.get(CsvFileContent.REQUIRED_HEADERS[1]), formatter))
                        .minuteLength(new BigDecimal(csvRecord.get(CsvFileContent.REQUIRED_HEADERS[2])))
                        .price(new BigDecimal(csvRecord.get(CsvFileContent.REQUIRED_HEADERS[3])).round(MathContext.UNLIMITED))
                        .cost(new BigDecimal(csvRecord.get(CsvFileContent.REQUIRED_HEADERS[4])).setScale(6, RoundingMode.HALF_UP))
                        .length(Integer.valueOf(csvRecord.get(CsvFileContent.REQUIRED_HEADERS[5])))
                        .build();
                nonRequiredHeaderName.forEach(header ->
                        otherLine.add(
                                csvRecord.get(header) == null || csvRecord.get(header).isEmpty()? " ": csvRecord.get(header)));


                crdList.add(crd);
            }
            return CsvFileContent.builder()
                    .requiredCrdValues(crdList)
                    .nonRequiredValues(otherLine)
                    .nonRequiredHeader(nonRequiredHeaderName)
                    .build();
        } catch (IOException e) {
            log.error("" , e);
            throw new RuntimeException("Technical error on file, please contact your administrator");
        } catch (Exception e) {
            log.error("", e);
            throw new RuntimeException("fail to parse CSV file: " + e.getMessage());
        }
    }

    public static ByteArrayInputStream fromCrdToCsv(CsvFileContent csvFileContent, BigDecimal quotient) {
        final CSVFormat format = CSVFormat.DEFAULT.withQuoteMode(QuoteMode.MINIMAL);

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm:ss");
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            CSVPrinter csvPrinter = new CSVPrinter(new PrintWriter(out), format);
            List<String> header = Stream.concat(
                    Arrays.stream(CsvFileContent.REQUIRED_HEADERS),
                    csvFileContent.getNonRequiredHeader().stream())
                    .toList();
            csvPrinter.printRecord(header);
            int initialIndexRequireValue = 0;
            int lastIndexNonRequiredValue = csvFileContent.getNonRequiredHeader().size();
            for (Crd crd : csvFileContent.getRequiredCrdValues()) {

                BigDecimal newMinuteLength = crd.getMinuteLength().multiply(quotient).setScale(10, RoundingMode.UP);
                BigDecimal newLength = newMinuteLength
                        .multiply(BigDecimal.valueOf(60)).setScale(0, RoundingMode.UP);

                LocalDateTime newAccountingTime = getNewAccountingTime(crd.getConnectTime1(), newMinuteLength);
                BigDecimal newCost = newLength
                        .multiply(newMinuteLength);

                List<String> line = new ArrayList<>(List.of(
                        crd.getConnectTime1().format(formatter),
                        newAccountingTime.format(formatter),
                        String.valueOf(newMinuteLength),
                        String.valueOf(crd.getPrice()),
                        String.valueOf(newCost),
                        String.valueOf(newLength)
                ));

                for(int i = initialIndexRequireValue; i < lastIndexNonRequiredValue; i++) {
                    line.add(line.size() , csvFileContent.getNonRequiredValues().get(i));
                }
                initialIndexRequireValue = lastIndexNonRequiredValue;
                lastIndexNonRequiredValue += csvFileContent.getNonRequiredHeader().size();

                csvPrinter.printRecord(line);
            }
            csvPrinter.flush();
            return new ByteArrayInputStream(out.toByteArray());
        } catch (IOException e) {
            log.error("", e);
            throw new RuntimeException("Technical error when processing csv file! contact your administrator");
        }catch (Exception e) {
            log.error("", e);
            throw new RuntimeException("fail to import data to CSV file: " + e.getMessage());
        }
    }

    private static LocalDateTime getNewAccountingTime(LocalDateTime connectTime1, BigDecimal newMinuteLength) {

        return connectTime1
                .plusMinutes(newMinuteLength.toBigInteger().longValue())
                .plusSeconds(newMinuteLength
                        .subtract(new BigDecimal(newMinuteLength.toBigInteger()))
                        .multiply(BigDecimal.valueOf(60))
                        .setScale(0, RoundingMode.UP).longValue());
    }
}

