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
    public static String TYPE3= "application/vnd.ms-excel";

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
                        .clientName(csvRecord.get(CsvFileContent.REQUIRED_HEADERS[0]))
                        .ingressRemoteSignalingIP(csvRecord.get(CsvFileContent.REQUIRED_HEADERS[1]))
                        .connectDateTime(LocalDateTime.parse(csvRecord.get(CsvFileContent.REQUIRED_HEADERS[2]), formatter))
                        .endDateTime(LocalDateTime.parse(csvRecord.get(CsvFileContent.REQUIRED_HEADERS[3]), formatter))
                        .ingressCalledNumber(csvRecord.get(CsvFileContent.REQUIRED_HEADERS[4]))
                        .ingressANI(csvRecord.get(CsvFileContent.REQUIRED_HEADERS[5]))
                        .clientDuration(new BigDecimal(csvRecord.get(CsvFileContent.REQUIRED_HEADERS[6])))
                        .rateForClient(new BigDecimal(csvRecord.get(CsvFileContent.REQUIRED_HEADERS[7])).round(MathContext.UNLIMITED))
                        .revenue(new BigDecimal(csvRecord.get(CsvFileContent.REQUIRED_HEADERS[8])).setScale(6, RoundingMode.HALF_UP))
                        .build();
                nonRequiredHeaderName.forEach(header -> otherLine.add(csvRecord.get(header)));


                crdList.add(crd);
            }
            return CsvFileContent.builder()
                    .requiredCrdValues(crdList)
                    .nonRequiredValues(otherLine)
                    .nonRequiredHeader(nonRequiredHeaderName)
                    .build();
        } catch (IOException e) {
            log.error("" , e);
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
                BigDecimal carrierDuration2 = crd.getClientDuration().multiply(quotient).setScale(3, RoundingMode.HALF_UP);
                LocalDateTime endDateTime2 = crd.getConnectDateTime()
                        .plusSeconds(carrierDuration2.longValue())
                        .plusNanos(
                                (carrierDuration2
                                        .subtract(new BigDecimal(carrierDuration2.toBigInteger())).setScale(3, RoundingMode.HALF_UP))
                                        .multiply(new BigDecimal(1000000000)).longValue());
                BigDecimal carrierDuration3 = BigDecimal.valueOf(ChronoUnit.MILLIS.between(crd.getConnectDateTime(), endDateTime2))
                        .divide(new BigDecimal(1000), RoundingMode.HALF_UP)
                        .setScale(0, RoundingMode.HALF_UP);
                BigDecimal revenue2 = carrierDuration2
                        .multiply(crd.getRateForClient())
                        .divide(new BigDecimal(60), RoundingMode.HALF_UP).setScale(5, RoundingMode.HALF_UP );
                List<String> line = new ArrayList<>(List.of(
                        crd.getClientName(),
                        crd.getIngressRemoteSignalingIP(),
                        crd.getConnectDateTime().format(formatter),
                        endDateTime2.format(formatter),
                        crd.getIngressCalledNumber(),
                        crd.getIngressANI(),
                        String.valueOf(carrierDuration3),
                        String.valueOf(crd.getRateForClient()),
                        String.valueOf(revenue2)
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
            throw new RuntimeException("fail to import data to CSV file: " + e.getMessage());
        }
    }
}
