package cm.amk.crdgenerator.payload;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ResponseMessage {
    private Boolean success;
    private String message;
}
