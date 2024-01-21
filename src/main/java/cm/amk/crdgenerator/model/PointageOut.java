package cm.amk.crdgenerator.model;

import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

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
public class PointageOut {
    private Sens sens;
    private LocalDateTime checkTime;
    private LocalDate day;
    private LocalTime checkInTime;
    private LocalTime checkOutTime;
    private LocalTime arrivedAfterTime;
    private LocalTime departureBeforeTime;
}
