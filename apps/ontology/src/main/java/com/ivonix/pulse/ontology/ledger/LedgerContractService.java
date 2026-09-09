package com.ivonix.pulse.ontology.ledger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class LedgerContractService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final LedgerService ledger;
    public LedgerContractService(JdbcTemplate jdbc,ObjectMapper mapper,LedgerService ledger){this.jdbc=jdbc;this.mapper=mapper;this.ledger=ledger;}

    @Transactional public UUID create(UUID org,UUID user,String name,Map<String,Object> code){
        requireMember(org,user); if(name==null||name.isBlank()||code==null)throw new IllegalArgumentException("Contract name and declarative code are required");
        UUID id=UUID.randomUUID(); jdbc.update("INSERT INTO ledger_contracts(id,organization_id,name,code,active) VALUES(?,?,?,?,true)",id,org,name,write(code)); return id;
    }
    @Transactional public Map<String,Object> execute(UUID id,UUID org,UUID user,Map<String,Object> input){
        requireMember(org,user); Map<String,Object> c=jdbc.queryForMap("SELECT code FROM ledger_contracts WHERE id=? AND organization_id=? AND active=true",id,org);
        try { JsonNode code=mapper.readTree(Objects.toString(c.get("code"))); boolean pass=true; JsonNode rules=code.path("rules"); if(!rules.isArray()||rules.size()>100)throw new IllegalArgumentException("Contract rules must be an array of at most 100 rules"); for(JsonNode r:rules) if(!matches(r,input)) {pass=false;break;} if(!pass)return Map.of("executed",true,"passed",false,"transactionId",""); JsonNode action=code.path("onPass"); String type=action.path("txType").asText("contract.approved"); Map<String,Object> payload=new LinkedHashMap<>(); payload.put("contractId",id.toString()); payload.put("input",input); if(action.has("payload")&&action.get("payload").isObject())action.get("payload").fields().forEachRemaining(e->payload.put(e.getKey(),mapper.convertValue(e.getValue(),Object.class))); UUID tx=ledger.submitTransaction(org,type,payload,user); return Map.of("executed",true,"passed",true,"transactionId",tx.toString()); }
        catch(Exception e){throw new IllegalArgumentException("Invalid declarative contract",e);}
    }
    private boolean matches(JsonNode r,Map<String,Object> input){String field=r.path("field").asText();String op=r.path("op").asText();Object actual=input.get(field);Object expected=mapper.convertValue(r.get("value"),Object.class);if(actual==null)return "exists".equals(op)&&r.path("value").asBoolean(false)==false;return switch(op){case "exists"->true;case "eq"->Objects.equals(String.valueOf(actual),String.valueOf(expected));case "neq"->!Objects.equals(String.valueOf(actual),String.valueOf(expected));case "contains"->String.valueOf(actual).contains(String.valueOf(expected));case "gt","gte","lt","lte"->compare(actual,expected,op);default->throw new IllegalArgumentException("Unsupported rule operator");};}
    private boolean compare(Object a,Object b,String op){double x=Double.parseDouble(a.toString()),y=Double.parseDouble(b.toString());return switch(op){case "gt"->x>y;case "gte"->x>=y;case "lt"->x<y;default->x<=y;};}
    private String write(Object o){try{return mapper.writeValueAsString(o);}catch(Exception e){throw new IllegalArgumentException("Invalid contract JSON",e);}}
    private void requireMember(UUID org,UUID user){Integer n=jdbc.queryForObject("SELECT count(*) FROM organization_members WHERE organization_id=? AND user_id=?",Integer.class,org,user);if(n==null||n==0)throw new SecurityException("Organization membership required");}
}
