package cm.amk.crdgenerator.model;

import lombok.*;

import java.util.List;

/**
 * @author bamk
 * @version 1.0
 * @since 21/01/2024
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MonthlyScoreOut {
    private String employeeName;
    private List<PointageOut> pointageOuts;
}
