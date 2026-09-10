package com.ivonix.pulse.ontology.key;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.*;

@Service
public class KeyService {
    private static final Set<String> SUPPORTED_KEY_TYPES = Set.of("aes-256");
    private static final Set<String> SUPPORTED_PURPOSES = Set.of("encryption");
    private final JdbcTemplate jdbc;
    private final RestTemplate rest;
    private final String cryptoUrl;
    private final String cryptoSecret;
    private final byte[] masterKey;
    private final SecureRandom random = new SecureRandom();

    public KeyService(JdbcTemplate jdbc, RestTemplate rest,
                      @Value("${PULSE_CRYPTO_URL:http://127.0.0.1:8092}") String cryptoUrl,
                      @Value("${PULSE_CRYPTO_SERVICE_SECRET:}") String cryptoSecret) {
        this.jdbc = jdbc; this.rest = rest; this.cryptoUrl = cryptoUrl.replaceAll("/$", ""); this.cryptoSecret = cryptoSecret;
        String raw = System.getenv("PULSE_MASTER_KEY");
        if (raw == null || raw.isBlank()) throw new IllegalStateException("PULSE_MASTER_KEY is required");
        try { masterKey = Base64.getDecoder().decode(raw); } catch (Exception e) { throw new IllegalStateException("PULSE_MASTER_KEY must be base64", e); }
        if (masterKey.length != 32) throw new IllegalStateException("PULSE_MASTER_KEY must decode to 32 bytes");
    }

    public KeyView create(UUID userId, UUID orgId, KeyRequest r) {
        requireMember(orgId,userId); String type=req(r.keyType(),"keyType"), purpose=req(r.purpose(),"purpose"), alias=req(r.keyAlias(),"keyAlias");
        if(!SUPPORTED_KEY_TYPES.contains(type)) throw new IllegalArgumentException("Unsupported key type; currently supported: aes-256");
        if(!SUPPORTED_PURPOSES.contains(purpose)) throw new IllegalArgumentException("Unsupported key purpose; currently supported: encryption");
        KeyMaterial m=generate(type); UUID id=UUID.randomUUID();
        jdbc.update("insert into public.crypto_keys(id,organization_id,key_alias,key_type,purpose,hsm_backed,public_key,encrypted_private_key,created_by) values (?,?,?,?,?,?,?,?,?)",id,orgId,alias,type,purpose,false,m.publicKey(),seal(m.privateKey()),userId);
        audit(id,orgId,"create",userId,"success",null,null,null); return get(id,userId,orgId);
    }
    public String encrypt(UUID userId,UUID orgId,UUID keyId,String plaintext){return encrypt(userId,orgId,keyId,plaintext,null);}
    public String encrypt(UUID userId,UUID orgId,UUID keyId,String plaintext,UUID operationId){authorizeKey(keyId,userId,orgId,"encryption");return executeIdempotent(operationId,keyId,orgId,userId,"encrypt",plaintext,true);}
    public String decrypt(UUID userId,UUID orgId,UUID keyId,String ciphertext){return decrypt(userId,orgId,keyId,ciphertext,null);}
    public String decrypt(UUID userId,UUID orgId,UUID keyId,String ciphertext,UUID operationId){authorizeKey(keyId,userId,orgId,"encryption");return executeIdempotent(operationId,keyId,orgId,userId,"decrypt",ciphertext,false);}
    public String encryptSystem(UUID orgId,UUID keyId,String plaintext){authorizeSystemKey(keyId,orgId,"encryption");return encryptInternal(keyId,plaintext,null);}
    public String decryptSystem(UUID orgId,UUID keyId,String ciphertext){authorizeSystemKey(keyId,orgId,"encryption");return decryptInternal(keyId,ciphertext,null);}

    @Transactional
    private String executeIdempotent(UUID operationId,UUID keyId,UUID orgId,UUID actor,String op,String data,boolean encryption){
        if(data==null)throw new IllegalArgumentException(encryption?"plaintext required":"ciphertext required");
        if(operationId==null) return encryption ? encryptInternal(keyId,data,actor) : decryptInternal(keyId,data,actor);
        Map<String,Object> existing = findOperation(operationId,orgId,keyId,actor,op);
        if(existing!=null){String status=(String)existing.get("status");String result=(String)existing.get(encryption?"result_ciphertext":"result_plaintext");if("success".equals(status)&&result!=null)return result;throw new IllegalStateException("Crypto operation is already in progress or failed; use a new operationId");}
        try { jdbc.update("insert into public.crypto_operations(operation_id,key_id,organization_id,operation_type,requested_by,status,caller) values (?,?,?,?,?,?,?)",operationId,keyId,orgId,op,actor,"started","ontology"); }
        catch(org.springframework.dao.DuplicateKeyException e){Map<String,Object> raced=findOperation(operationId,orgId,keyId,actor,op);if(raced!=null&&"success".equals(raced.get("status"))){String result=(String)raced.get(encryption?"result_ciphertext":"result_plaintext");if(result!=null)return result;}throw new IllegalStateException("Crypto operation already exists",e);}
        try {
            String result=encryption?crypto("/v1/encrypt",new CryptoRequest(keyId,data)).ciphertext():crypto("/v1/decrypt",new CryptoRequest(keyId,data)).plaintext();
            if(result==null)throw new IllegalStateException("Crypto service returned no result");
            int updated=jdbc.update("update public.crypto_operations set status='success', result_ciphertext=?, result_plaintext=? where operation_id=? and organization_id=? and status='started'",encryption?result:null,encryption?null:result,operationId,orgId);
            if(updated!=1)throw new IllegalStateException("Crypto operation completion lost");
            return result;
        } catch(RuntimeException e){jdbc.update("update public.crypto_operations set status='failed' where operation_id=? and organization_id=? and status='started'",operationId,orgId);throw e;}
    }

    private Map<String,Object> findOperation(UUID operationId,UUID orgId,UUID keyId,UUID actor,String op){
        try{return jdbc.queryForMap("select status,result_ciphertext,result_plaintext from public.crypto_operations where operation_id=? and organization_id=? and key_id=? and requested_by is not distinct from ? and operation_type=?",operationId,orgId,keyId,actor,op);}catch(Exception e){return null;}
    }
    private String encryptInternal(UUID keyId,String plaintext,UUID actor){
        if(plaintext==null)throw new IllegalArgumentException("plaintext required");
        CryptoResponse r=crypto("/v1/encrypt",new CryptoRequest(keyId,plaintext));
        if(r==null||r.ciphertext()==null)throw new IllegalStateException("Crypto service returned no ciphertext"); audit(keyId,null,"encrypt",actor,"success",null,r.ciphertext(),null); return r.ciphertext();
    }
    private String decryptInternal(UUID keyId,String ciphertext,UUID actor){
        if(ciphertext==null)throw new IllegalArgumentException("ciphertext required");
        CryptoResponse r=crypto("/v1/decrypt",new CryptoRequest(keyId,ciphertext));
        if(r==null||r.plaintext()==null)throw new IllegalStateException("Crypto service returned no plaintext"); audit(keyId,null,"decrypt",actor,"success",null,null,r.plaintext()); return r.plaintext();
    }
    private CryptoResponse crypto(String path,CryptoRequest body){
        if(cryptoSecret==null||cryptoSecret.isBlank())throw new IllegalStateException("PULSE_CRYPTO_SERVICE_SECRET is required");
        HttpHeaders headers=new HttpHeaders(); headers.setContentType(MediaType.APPLICATION_JSON); headers.setBearerAuth(cryptoSecret);
        ResponseEntity<CryptoResponse> response=rest.exchange(cryptoUrl+path,HttpMethod.POST,new HttpEntity<>(body,headers),CryptoResponse.class);
        return response.getBody();
    }

    public KeyView get(UUID id,UUID userId,UUID orgId){authorizeKey(id,userId,orgId,null);return getUnchecked(id,orgId);}
    public List<KeyView> list(UUID userId,UUID orgId){requireMember(orgId,userId);return jdbc.query("select id,key_alias,key_type,purpose,hsm_backed,public_key,created_at,last_rotated_at,rotation_interval_days,status from public.crypto_keys where organization_id=? order by created_at desc",(rs,n)->view(rs),orgId);}
    private void authorizeSystemKey(UUID id,UUID orgId,String purpose){KeyView k=getUnchecked(id,orgId);if(k==null||!"active".equals(k.status()))throw new SecurityException("Key unavailable");if(!SUPPORTED_KEY_TYPES.contains(k.keyType())||(purpose!=null&&!purpose.equals(k.purpose())))throw new SecurityException("Key capability mismatch");}
    private KeyView authorizeKey(UUID id,UUID userId,UUID orgId,String purpose){requireMember(orgId,userId);KeyView k=getUnchecked(id,orgId);if(k==null||!"active".equals(k.status()))throw new SecurityException("Key unavailable");if(!SUPPORTED_KEY_TYPES.contains(k.keyType())||(purpose!=null&&!purpose.equals(k.purpose())))throw new SecurityException("Key capability mismatch");return k;}
    private KeyView getUnchecked(UUID id,UUID orgId){try{return jdbc.queryForObject("select id,key_alias,key_type,purpose,hsm_backed,public_key,created_at,last_rotated_at,rotation_interval_days,status from public.crypto_keys where id=? and organization_id=?",(rs,n)->view(rs),id,orgId);}catch(Exception e){return null;}}
    private KeyView view(java.sql.ResultSet rs)throws java.sql.SQLException{return new KeyView((UUID)rs.getObject("id"),rs.getString("key_alias"),rs.getString("key_type"),rs.getString("purpose"),rs.getBoolean("hsm_backed"),rs.getString("public_key"),rs.getObject("created_at",java.time.Instant.class),rs.getObject("last_rotated_at",java.time.Instant.class),rs.getInt("rotation_interval_days"),rs.getString("status"));}
    private void requireMember(UUID org,UUID user){Integer n=jdbc.queryForObject("select count(*) from public.organization_members where organization_id=? and user_id=?",Integer.class,org,user);if(n==null||n<1)throw new SecurityException("Organization membership required");}
    private void audit(UUID id,UUID org,String op,UUID user,String status,UUID operationId,String ciphertext,String plaintext){jdbc.update("insert into public.crypto_operations(key_id,organization_id,operation_type,requested_by,status,operation_id,result_ciphertext,result_plaintext,caller) values (?,?,?,?,?,?,?,?,?)",id,org,op,user,status,operationId,ciphertext,plaintext,"ontology");}
    private String req(String s,String n){if(s==null||s.isBlank())throw new IllegalArgumentException(n+" required");return s.trim().toLowerCase(Locale.ROOT);}
    private KeyMaterial generate(String type){if(!"aes-256".equals(type))throw new IllegalArgumentException("Unsupported key type");byte[] b=new byte[32];random.nextBytes(b);return new KeyMaterial(null,b);}
    private String seal(byte[] plain){try{byte[] iv=new byte[12];random.nextBytes(iv);Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(masterKey,"AES"),new GCMParameterSpec(128,iv));c.updateAAD("pulse-key-v1".getBytes(java.nio.charset.StandardCharsets.UTF_8));byte[] out=c.doFinal(plain);byte[] all=new byte[iv.length+out.length];System.arraycopy(iv,0,all,0,iv.length);System.arraycopy(out,0,all,iv.length,out.length);return Base64.getEncoder().encodeToString(all);}catch(Exception e){throw new IllegalStateException("Private material encryption failed",e);}}
    public record KeyRequest(String organizationId,String keyAlias,String keyType,String purpose){}
    public record KeyView(UUID id,String keyAlias,String keyType,String purpose,boolean hsmBacked,String publicKey,java.time.Instant createdAt,java.time.Instant lastRotatedAt,int rotationIntervalDays,String status){}
    public record CryptoRequest(UUID keyId,String data){}
    public record CryptoResponse(String ciphertext,String plaintext){}
    private record KeyMaterial(String publicKey,byte[] privateKey){}
}
