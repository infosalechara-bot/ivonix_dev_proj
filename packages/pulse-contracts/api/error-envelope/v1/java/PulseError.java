package io.pulse.contracts.api.v1;
import java.util.Map;
public record PulseError(String code,String message,String messageKey,Map<String,Object> details,String requestId,String docsUrl){}
