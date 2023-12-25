package cm.amk.crdgenerator.util;

import cm.amk.crdgenerator.model.Crd;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Slf4j
public class CsvHelper {

    public static String TYPE1 = "text/csv";
    public static String TYPE2= "application/vnd.ms-excel";
    public static String TYPE3= "application/vnd.ms-excel";
    static String[] HEADERS = {
            "ClientName",
            "Ingress RemoteSignalingIP",
            "ConnectDateTime",
            "EndDateTime",
            "Ingress Called Number",
            "Ingress ANI",
            "ClientDuration",
            "Rate for Client",
            "Revenue"
    };

    public static boolean hasCSVFormat(MultipartFile file) {
        log.info("***************************: {}", file.getContentType());
        if (!TYPE1.equals(file.getContentType()) && !TYPE2.equals(file.getContentType())) {
            return false;
        }
        return true;
    }

    public static List<Crd> csvToData(InputStream is) {
        try (BufferedReader fileReader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
             CSVParser csvParser = new CSVParser(fileReader,
                     CSVFormat.DEFAULT.withFirstRecordAsHeader().withIgnoreHeaderCase().withTrim());) {

            List<Crd> datas = new ArrayList<>();

            Iterable<CSVRecord> csvRecords = csvParser.getRecords();

            DateTimeFormatter formatter1 = DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm:ss");
            DateTimeFormatter formatter2 = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.S");

            for (CSVRecord csvRecord : csvRecords) {
                Crd data = Crd.builder()
                        .clientName(csvRecord.get(HEADERS[0]))
                        .ingressRemoteSignalingIP(csvRecord.get(HEADERS[1]))
                        .connectDateTime(LocalDateTime.parse(csvRecord.get(HEADERS[2]), formatter1))
                        .endDateTime(LocalDateTime.parse(csvRecord.get(HEADERS[3]), formatter2))
                        .ingressCalledNumber(csvRecord.get(HEADERS[4]))
                        .ingressANI(csvRecord.get(HEADERS[5]))
                        .clientDuration(new BigDecimal(csvRecord.get(HEADERS[6])))
                        .rateForClient(new BigDecimal(csvRecord.get(HEADERS[7])).round(MathContext.UNLIMITED))
                        .revenue(new BigDecimal(csvRecord.get(HEADERS[8])).setScale(6))
                        .build();

                datas.add(data);
            }
            return datas;
        } catch (IOException e) {
            throw new RuntimeException("fail to parse CSV file: " + e.getMessage());
        }
    }


    public static ByteArrayInputStream dataToCSV(List<Crd> datas, BigDecimal quotient) {
        final CSVFormat format = CSVFormat.DEFAULT.withQuoteMode(QuoteMode.MINIMAL);

        DateTimeFormatter formatter1 = DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm:ss");
        DateTimeFormatter formatter2 = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.S");
        DateTimeFormatter formatter3 = DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm:ss.SSS");
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            CSVPrinter csvPrinter = new CSVPrinter(new PrintWriter(out), format);
            csvPrinter.printRecord(HEADERS);
            for (Crd data : datas) {
                BigDecimal carrierDuration2 = data.getClientDuration().multiply(quotient).setScale(3);
                LocalDateTime endDateTime2 = data.getConnectDateTime()
                        .plusSeconds(carrierDuration2.longValue())
                        .plusNanos(
                                (carrierDuration2
                                        .subtract(new BigDecimal(carrierDuration2.toBigInteger())).setScale(3))
                                        .multiply(new BigDecimal(1000000000)).longValue());
                BigDecimal carrierDuration3 = BigDecimal.valueOf(ChronoUnit.MILLIS.between(data.getConnectDateTime(), endDateTime2))
                        .divide(new BigDecimal(1000))
                        .setScale(0, RoundingMode.HALF_UP);
                BigDecimal revenue2 = carrierDuration2
                        .multiply(data.getRateForClient())
                        .divide(new BigDecimal(60), RoundingMode.HALF_UP).setScale(5, RoundingMode.HALF_UP );
                List<String> line = Arrays.asList(
                        data.getClientName(),
                        data.getIngressRemoteSignalingIP(),
                        data.getConnectDateTime().format(formatter1),
                        endDateTime2.format(formatter3),
                        data.getIngressCalledNumber(),
                        data.getIngressANI(),
                        String.valueOf(carrierDuration3),
                        String.valueOf(data.getRateForClient()),
                        String.valueOf(revenue2)
                );

                csvPrinter.printRecord(line);
            }
            csvPrinter.flush();
            return new ByteArrayInputStream(out.toByteArray());
        } catch (IOException e) {
            throw new RuntimeException("fail to import data to CSV file: " + e.getMessage());
        }
    }
}
