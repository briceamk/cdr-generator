package cm.amk.crdgenerator.model;

import lombok.*;

import java.time.LocalDate;

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
public class ClosingDay {
    private LocalDate date;
}
