package cm.amk.crdgenerator.payload;

import lombok.*;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ResponseMessage {
    private Boolean success;
    private String message;
}
