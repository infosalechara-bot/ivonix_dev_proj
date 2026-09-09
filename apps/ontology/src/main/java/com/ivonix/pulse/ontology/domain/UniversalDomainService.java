package com.ivonix.pulse.ontology.domain;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class UniversalDomainService {
    private record Spec(String table, Set<String> fields) {}
    private static final Map<String, Spec> SPECS = Map.of(
        "automotive", new Spec("automotive_data", Set.of("obd_codes","engine_rpm","vehicle_speed","coolant_temp")),
        "industrial", new Spec("industrial_data", Set.of("vibration_rms","temperature","current_draw","speed_rpm")),
        "robotics", new Spec("robotics_data", Set.of("joint_positions","joint_torques","end_effector_pose")),
        "energy", new Spec("energy_data", Set.of("power_output","battery_voltage","battery_current","state_of_charge")),
        "electronics", new Spec("electronics_data", Set.of("pcb_temperature","signal_strength","power_consumption","error_flags")),
        "agriculture", new Spec("agriculture_data", Set.of("soil_moisture","soil_ph","ambient_temp","humidity")),
        "aviation", new Spec("aviation_data", Set.of("altitude","airspeed","engine_vibration","flight_phase")),
        "naval", new Spec("naval_data", Set.of("engine_fuel_flow","exhaust_temp","hull_stress","position")),
        "space", new Spec("space_data", Set.of("orbital_position","power_generation","thermal_reading","radiation_level"))
    );
    private final JdbcTemplate jdbc;
    public UniversalDomainService(JdbcTemplate jdbc){this.jdbc=jdbc;}
    public List<Map<String,Object>> latest(String domain, UUID deviceId, UUID orgId, int limit){
        Spec s=spec(domain); requireDevice(deviceId,orgId); int n=Math.max(1,Math.min(limit,100));
        return jdbc.queryForList("SELECT * FROM public."+s.table+" WHERE device_id=? ORDER BY timestamp DESC LIMIT ?",deviceId,n);
    }
    public void ingest(String domain, UUID deviceId, UUID orgId, Map<String,Object> data){
        Spec s=spec(domain); requireDevice(deviceId,orgId); if(data==null||data.isEmpty()) throw new IllegalArgumentException("data is required");
        List<String> cols=new ArrayList<>(); List<Object> vals=new ArrayList<>();
        for(var e:data.entrySet()){if(!s.fields.contains(e.getKey())) throw new IllegalArgumentException("Unsupported field: "+e.getKey()); cols.add(e.getKey()); vals.add(e.getValue());}
        Object[] args=new Object[vals.size()+1]; args[0]=deviceId; System.arraycopy(vals.toArray(),0,args,1,vals.size());
        jdbc.update("INSERT INTO public."+s.table+" (device_id,"+String.join(",",cols)+") VALUES (? ,"+String.join(",",Collections.nCopies(cols.size(),"?"))+")",args);
    }
    public Map<String,Object> diagnose(String domain, UUID deviceId, UUID orgId){
        var rows=latest(domain,deviceId,orgId,10); if(rows.isEmpty()) return Map.of("probableFault","Insufficient data","confidence",0.0,"recommendedActions",List.of("Collect telemetry"));
        Map<String,Object> r=rows.get(0); return switch(domain.toLowerCase(Locale.ROOT)){
            case "automotive" -> numeric(r,"coolant_temp",100,"Engine overheating",0.90,"Check coolant level;Inspect radiator");
            case "industrial" -> numeric(r,"vibration_rms",1.5,"Excessive vibration / bearing risk",0.85,"Inspect bearings;Check alignment");
            case "robotics" -> mapRule(r,"joint_torques","Joint torque anomaly",0.82,"Inspect actuator/load calibration");
            case "energy" -> numericLow(r,"battery_voltage",20,"Low battery voltage",0.88,"Inspect battery and power bus");
            case "electronics" -> numeric(r,"pcb_temperature",85,"PCB overheating",0.90,"Inspect thermal path and load");
            case "agriculture" -> numericLow(r,"soil_moisture",20,"Low soil moisture",0.84,"Inspect irrigation");
            case "aviation" -> numeric(r,"engine_vibration",1.5,"Engine vibration anomaly",0.86,"Follow certified maintenance procedure");
            case "naval" -> numeric(r,"exhaust_temp",600,"Exhaust temperature anomaly",0.86,"Inspect propulsion system");
            case "space" -> numeric(r,"radiation_level",100,"Radiation threshold exceeded",0.92,"Enter mission-defined protective procedure");
            default -> throw new IllegalArgumentException("Unsupported domain");
        };
    }
    private Map<String,Object> numeric(Map<String,Object> r,String k,double t,String fault,double c,String actions){Object v=r.get(k); double x=v instanceof Number n?n.doubleValue():Double.NaN; return x>t?result(fault,c,actions):result("No rule-based fault detected",0.1,"");}
    private Map<String,Object> numericLow(Map<String,Object> r,String k,double t,String fault,double c,String actions){Object v=r.get(k); double x=v instanceof Number n?n.doubleValue():Double.NaN; return x<t?result(fault,c,actions):result("No rule-based fault detected",0.1,"");}
    private Map<String,Object> mapRule(Map<String,Object> r,String k,String fault,double c,String actions){return r.get(k)!=null?result(fault,c,actions):result("No rule-based fault detected",0.1,"");}
    private Map<String,Object> result(String f,double c,String a){return Map.of("probableFault",f,"confidence",c,"recommendedActions",a.isBlank()?List.of():List.of(a.split(";")));}
    private Spec spec(String d){Spec s=SPECS.get(d.toLowerCase(Locale.ROOT)); if(s==null)throw new IllegalArgumentException("Unsupported domain: "+d); return s;}
    private void requireDevice(UUID device,UUID org){if(!jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM public.devices WHERE id=? AND organization_id=?)",Boolean.class,device,org))throw new SecurityException("Device is not in organization");}
}
