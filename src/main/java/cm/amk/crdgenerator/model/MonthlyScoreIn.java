package cm.amk.crdgenerator.model;

import lombok.*;

import java.util.List;

/**
 * @author bamk
 * @version 1.0
 * @since 20/01/2024
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MonthlyScoreIn {
    private List<PointageIn> pointageIns;
    private List<ClosingDay> closingDays;
}
