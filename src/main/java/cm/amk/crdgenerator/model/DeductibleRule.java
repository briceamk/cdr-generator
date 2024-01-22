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
@NoArgsConstructor
@AllArgsConstructor
public class DeductibleRule {
    private int from;
    private int to;
    private int deductibleHours;
}
