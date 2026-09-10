package io.pulse.contracts.api.v1;
import java.util.List;
public record PulsePage<T>(List<T> items,String cursor,boolean hasMore,Integer total){}
