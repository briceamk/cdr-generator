package cm.amk.crdgenerator.model;

import lombok.*;

import java.util.List;

/**
 * @author bamk
 * @version 1.0
 * @since 25/12/2023
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CsvFileContent {

    public static String[] REQUIRED_HEADERS = {
            "originator_name",
            "originator_address",
            "connect_time1",
            "accounting_time",
            "bnumber",
            "anumber",
            "minute_length",
            "price",
            "cost"
    };
    private List<Crd> requiredCrdValues;
    private List<String> nonRequiredValues;

    private List<String> nonRequiredHeader;
}
