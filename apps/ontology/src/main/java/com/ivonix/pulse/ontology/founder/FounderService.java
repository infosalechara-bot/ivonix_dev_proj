package com.ivonix.pulse.ontology.founder;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;

@Service
public class FounderService {
  private static final int CONFIRMATION_TTL_MINUTES = 10;
  private final JdbcTemplate jdbc;
  private final SecureRandom random = new SecureRandom();

  public FounderService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

  public FounderProfile profile(UUID userId, UUID orgId) {
    return jdbc.query("select id,codename,trust_level,created_at from public.executive_profiles where user_id=? and organization_id=?",
        ps -> { ps.setObject(1,userId); ps.setObject(2,orgId); },
        rs -> rs.next() ? new FounderProfile((UUID)rs.getObject("id"), rs.getString("codename"), rs.getString("trust_level"), rs.getObject("created_at", Instant.class)) : null);
  }

  public List<Message> messages(UUID userId, UUID orgId) {
    requireFounder(userId,orgId);
    return jdbc.query("select id,sender,message_text,message_type,urgency,created_at,read_at from public.founder_messages where organization_id=? order by created_at desc limit 100",
        ps -> ps.setObject(1,orgId),
        (rs,n) -> new Message((UUID)rs.getObject("id"),rs.getString("sender"),rs.getString("message_text"),rs.getString("message_type"),rs.getString("urgency"),rs.getObject("created_at",Instant.class),rs.getObject("read_at",Instant.class)));
  }

  @Transactional
  public Message sendMessage(UUID userId, UUID orgId, MessageRequest request) {
    requireFounder(userId,orgId);
    String text=Objects.requireNonNull(request.messageText(),"messageText").trim();
    if(text.isBlank() || text.length()>10000) throw new IllegalArgumentException("Message must be 1..10000 characters");
    UUID id=UUID.randomUUID();
    jdbc.update("insert into public.founder_messages(id,organization_id,sender,message_text,message_type,urgency) values (?,?,?,?,?,?)",
        id,orgId,"founder",text,validType(request.messageType()),validUrgency(request.urgency()));
    return jdbc.queryForObject("select id,sender,message_text,message_type,urgency,created_at,read_at from public.founder_messages where id=?",
        (rs,n)->new Message((UUID)rs.getObject("id"),rs.getString("sender"),rs.getString("message_text"),rs.getString("message_type"),rs.getString("urgency"),rs.getObject("created_at",Instant.class),rs.getObject("read_at",Instant.class)),id);
  }

  @Transactional
  public ConfirmationRequest requestConfirmation(UUID userId, UUID orgId, String action, String resourceType, String resourceId) {
    requireFounder(userId,orgId);
    String description=normalize(action,2000);
    String type=normalize(resourceType,100);
    String resource=normalize(resourceId,200);
    String contextHash=contextHash(orgId,description,type,resource);
    String token=randomToken();
    UUID id=UUID.randomUUID();
    jdbc.update("insert into public.executive_confirmations(id,organization_id,requested_action,requested_by,confirmation_token_hash,approved_action_hash,expires_at) values (?,?,?,?,?,?,now() + interval '10 minutes')",
        id,orgId,description,userId,hash(token),contextHash, null);
    jdbc.update("update public.executive_confirmations set approved_action_hash=? where id=?",contextHash,id);
    jdbc.update("insert into public.founder_messages(organization_id,sender,message_text,message_type,urgency) values (?,?,?,?,?)",
        orgId,"pulse_agent","Confirmation required: "+description+" ["+type+":"+resource+"]","signal","critical");
    return jdbc.queryForObject("select id,requested_action,status,requested_at,expires_at from public.executive_confirmations where id=?",
        (rs,n)->new ConfirmationRequest((UUID)rs.getObject("id"),rs.getString("requested_action"),rs.getString("status"),rs.getObject("requested_at",Instant.class),rs.getObject("expires_at",Instant.class),token),id);
  }

  @Transactional
  public ConfirmationStatus confirm(UUID userId, UUID orgId, UUID confirmationId, String token, boolean approve) {
    requireFounder(userId,orgId);
    String tokenHash=hash(Objects.requireNonNull(token,"token"));
    int changed;
    if(approve) {
      changed=jdbc.update("update public.executive_confirmations set status='approved',confirmed_at=now(),confirmed_by=? where id=? and organization_id=? and requested_by=? and status='pending' and expires_at>now() and confirmation_token_hash=?",
          userId,confirmationId,orgId,userId,tokenHash);
    } else {
      changed=jdbc.update("update public.executive_confirmations set status='denied',denied_at=now(),denied_by=? where id=? and organization_id=? and requested_by=? and status='pending' and expires_at>now() and confirmation_token_hash=?",
          userId,confirmationId,orgId,userId,tokenHash);
    }
    if(changed!=1) throw new SecurityException("Confirmation token is invalid, expired, or already consumed");
    return jdbc.queryForObject("select id,status,requested_action,requested_at,confirmed_at from public.executive_confirmations where id=?",
        (rs,n)->new ConfirmationStatus((UUID)rs.getObject("id"),rs.getString("status"),rs.getString("requested_action"),rs.getObject("requested_at",Instant.class),rs.getObject("confirmed_at",Instant.class)),confirmationId);
  }

  @Transactional
  public int expirePending(UUID userId, UUID orgId) {
    requireFounder(userId,orgId);
    return jdbc.update("update public.executive_confirmations set status='expired' where organization_id=? and requested_by=? and status='pending' and expires_at<=now()",orgId,userId);
  }

  private String contextHash(UUID org,String action,String type,String resource){return hash(org+"|"+action+"|"+type+"|"+resource);}
  private String normalize(String v,int max){String x=Objects.requireNonNull(v,"context").trim();if(x.isBlank()||x.length()>max)throw new IllegalArgumentException("Invalid founder approval context");return x;}
  private void requireFounder(UUID userId, UUID orgId) {
    Integer n=jdbc.queryForObject("select count(*) from public.executive_profiles where user_id=? and organization_id=?",Integer.class,userId,orgId);
    if(n==null || n!=1) throw new SecurityException("Founder authorization required");
  }
  private String validType(String v){String x=v==null?"text":v;if(!Set.of("text","voice","image","signal").contains(x))throw new IllegalArgumentException("Unsupported message type");return x;}
  private String validUrgency(String v){String x=v==null?"normal":v;if(!Set.of("normal","important","critical").contains(x))throw new IllegalArgumentException("Unsupported urgency");return x;}
  private String randomToken(){byte[] b=new byte[32];random.nextBytes(b);return Base64.getUrlEncoder().withoutPadding().encodeToString(b);}
  private String hash(String raw){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}

  public record FounderProfile(UUID id,String codename,String trustLevel,Instant createdAt) {}
  public record Message(UUID id,String sender,String messageText,String messageType,String urgency,Instant createdAt,Instant readAt) {}
  public record MessageRequest(String messageText,String messageType,String urgency) {}
  public record ConfirmationRequest(UUID id,String requestedAction,String status,Instant requestedAt,Instant expiresAt,String confirmationToken) {}
  public record ConfirmationStatus(UUID id,String status,String requestedAction,Instant requestedAt,Instant confirmedAt) {}
}
