package cm.amk.crdgenerator.util;

import cm.amk.crdgenerator.model.Crd;
import cm.amk.crdgenerator.model.CsvFileContent;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.math.BigDecimal;
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

    public static String TYPE1 = "text/csv";
    public static String TYPE2= "application/vnd.ms-excel";

    public static boolean isCSVFormat(MultipartFile file) {
        return TYPE1.equals(file.getContentType()) || TYPE2.equals(file.getContentType());
    }

    public static CsvFileContent fromCsvToCrd(InputStream is) {
        try (BufferedReader fileReader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
             CSVParser csvParser = new CSVParser(fileReader,
                     CSVFormat.newFormat(';').withFirstRecordAsHeader().withIgnoreHeaderCase().withTrim());) {

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
                BigDecimal MinuteLengthComputed = crd.getMinuteLength().multiply(quotient).setScale(3, RoundingMode.HALF_UP);
                LocalDateTime connectTime1 = crd.getConnectTime1()
                        .plusSeconds(MinuteLengthComputed.longValue())
                        .plusNanos(
                                (MinuteLengthComputed
                                        .subtract(new BigDecimal(MinuteLengthComputed.toBigInteger())).setScale(3, RoundingMode.HALF_UP))
                                        .multiply(new BigDecimal(1000000000)).longValue());
                BigDecimal connectTime1Duration = BigDecimal.valueOf(ChronoUnit.MILLIS.between(crd.getConnectTime1(), connectTime1))
                        .divide(new BigDecimal(1000), RoundingMode.HALF_UP)
                        .setScale(0, RoundingMode.HALF_UP);
                BigDecimal revenue = MinuteLengthComputed
                        .multiply(crd.getPrice())
                        .divide(new BigDecimal(60), RoundingMode.HALF_UP).setScale(5, RoundingMode.HALF_UP );
                BigDecimal length = BigDecimal.valueOf(crd.getLength()).multiply(quotient);
                List<String> line = new ArrayList<String>(List.of(
                        crd.getConnectTime1().format(formatter),
                        connectTime1.format(formatter),
                        String.valueOf(connectTime1Duration),
                        String.valueOf(crd.getPrice()),
                        String.valueOf(revenue),
                        String.valueOf(length)
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
}
