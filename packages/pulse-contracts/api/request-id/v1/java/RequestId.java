package io.pulse.contracts.api.v1;
import java.util.regex.Pattern;
public final class RequestId{private RequestId(){}public static final Pattern PATTERN=Pattern.compile("^req_[a-z0-9]{21}$");public static boolean isValid(String v){return v!=null&&PATTERN.matcher(v).matches();}}
