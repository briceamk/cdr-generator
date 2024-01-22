package cm.amk.crdgenerator.model;

import lombok.*;

import java.time.LocalDateTime;

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
public class PointageIn {
    private String department;
    private String name;
    private LocalDateTime checkTime;
    private String verifyCode;
}
