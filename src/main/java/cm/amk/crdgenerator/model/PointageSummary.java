package cm.amk.crdgenerator.model;

import lombok.*;

import java.time.LocalTime;

/**
 * @author bamk
 * @version 1.0
 * @since 22/01/2024
 */
@Getter
@Setter
@Builder
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class PointageSummary {
    private String employeeName;
    private LocalTime totalArrivedAfterTime;
    private String totalDeductibleArrivedAfterTime;
    private LocalTime totalDepartureBeforeTime;
    private String totalDeductibleDepartureBeforeTime;
    private Integer totalNotPointedMorning;
    private Integer totalNotPointedEvening;
    private String totalDeductibleNotPointedMorning;
    private String totalDeductibleNotPointedEvening;
    private String totalDeductible;
}
