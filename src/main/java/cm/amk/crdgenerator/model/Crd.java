package cm.amk.crdgenerator.model;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Crd {
    private LocalDateTime connectTime1;
    private LocalDateTime accountingTime;
    private BigDecimal minuteLength;
    private BigDecimal price;
    private BigDecimal cost;
    private Integer length;

}
