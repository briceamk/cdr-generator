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
    private String clientName;
    private String  ingressRemoteSignalingIP;
    private LocalDateTime connectDateTime;
    private LocalDateTime endDateTime;
    private String   ingressCalledNumber ;
    private String   ingressANI;
    private BigDecimal clientDuration;
    private BigDecimal rateForClient;
    private BigDecimal revenue;

}
