package com.ivonix.pulse.ontology.ledger;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
public class LedgerService {
    private static final String GENESIS = "GENESIS";
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public LedgerService(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @Transactional
    public UUID submitTransaction(UUID organizationId, String txType, Map<String, Object> payload, UUID userId) {
        requireMember(organizationId, userId);
        if (txType == null || !txType.matches("[a-z][a-z0-9_.-]{0,63}")) throw new IllegalArgumentException("Invalid transaction type");
        if (payload == null) throw new IllegalArgumentException("Payload is required");

        String canonicalPayload = canonical(payload);
        UUID txId = UUID.randomUUID();
        Map<String, Object> previous = latestBlock(organizationId);
        long number = previous == null ? 0L : ((Number) previous.get("block_number")).longValue() + 1L;
        String previousHash = previous == null ? GENESIS : Objects.toString(previous.get("block_hash"));
        Instant now = Instant.now();
        String txHash = HashUtil.sha256(organizationId + "|" + txId + "|" + txType + "|" + canonicalPayload + "|" + now.toString());
        String blockHash = HashUtil.sha256(organizationId + "|" + number + "|" + previousHash + "|" + txHash);
        UUID blockId = UUID.randomUUID();

        jdbc.update("INSERT INTO ledger_blocks(id,organization_id,block_number,previous_block_hash,block_hash,block_timestamp,transaction_count,created_by) VALUES(?,?,?,?,?,?,?,?)",
                blockId, organizationId, number, previousHash, blockHash, now, 1, userId);
        jdbc.update("INSERT INTO ledger_transactions(id,organization_id,block_id,tx_type,payload,tx_hash,created_at,created_by) VALUES(?,?,?,?,?::jsonb,?,?,?)",
                txId, organizationId, blockId, txType, canonicalPayload, txHash, now, userId);
        return txId;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> verifyChain(UUID organizationId, UUID userId) {
        requireMember(organizationId, userId);
        List<Map<String, Object>> blocks = jdbc.queryForList("SELECT * FROM ledger_blocks WHERE organization_id=? ORDER BY block_number ASC", organizationId);
        String previous = GENESIS;
        long expected = 0;
        for (Map<String, Object> b : blocks) {
            long number = ((Number)b.get("block_number")).longValue();
            if (number != expected || !previous.equals(b.get("previous_block_hash"))) return result(false, number, "chain linkage mismatch");
            List<Map<String,Object>> txs = jdbc.queryForList("SELECT * FROM ledger_transactions WHERE block_id=? ORDER BY id ASC", b.get("id"));
            if (txs.size() != ((Number)b.get("transaction_count")).intValue()) return result(false, number, "transaction count mismatch");
            if (txs.size() != 1) return result(false, number, "unsupported block transaction count");
            Map<String,Object> tx = txs.get(0);
            String payload = canonicalJson((String)tx.get("payload"));
            String txHash = HashUtil.sha256(organizationId + "|" + tx.get("id") + "|" + tx.get("tx_type") + "|" + payload + "|" + tx.get("created_at"));
            if (!txHash.equals(tx.get("tx_hash"))) return result(false, number, "transaction hash mismatch");
            String blockHash = HashUtil.sha256(organizationId + "|" + number + "|" + previous + "|" + txHash);
            if (!blockHash.equals(b.get("block_hash"))) return result(false, number, "block hash mismatch");
            previous = blockHash;
            expected++;
        }
        return result(true, expected - 1, "chain verified");
    }

    public List<Map<String,Object>> blocks(UUID organizationId, UUID userId, int limit) {
        requireMember(organizationId, userId);
        return jdbc.queryForList("SELECT id,block_number,previous_block_hash,block_hash,block_timestamp,transaction_count,signer_node_id,signature FROM ledger_blocks WHERE organization_id=? ORDER BY block_number DESC LIMIT ?", organizationId, Math.min(Math.max(limit,1),100));
    }

    public List<Map<String,Object>> transactions(UUID organizationId, UUID userId, int limit) {
        requireMember(organizationId, userId);
        return jdbc.queryForList("SELECT id,block_id,tx_type,payload,tx_hash,created_at FROM ledger_transactions WHERE organization_id=? ORDER BY created_at DESC LIMIT ?", organizationId, Math.min(Math.max(limit,1),200));
    }

    private Map<String,Object> latestBlock(UUID org) {
        List<Map<String,Object>> rows = jdbc.queryForList("SELECT * FROM ledger_blocks WHERE organization_id=? ORDER BY block_number DESC LIMIT 1 FOR UPDATE", org);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private void requireMember(UUID org, UUID user) {
        if (org == null || user == null || jdbc.queryForObject("SELECT public.is_org_member(?,?)", Boolean.class, org, user) == null || !jdbc.queryForObject("SELECT public.is_org_member(?,?)", Boolean.class, org, user))
            throw new SecurityException("Organization membership required");
    }

    private String canonical(Object value) {
        try { return mapper.writeValueAsString(mapper.readTree(mapper.writeValueAsString(value))); }
        catch (JsonProcessingException e) { throw new IllegalArgumentException("Payload must be JSON serializable", e); }
    }
    private String canonicalJson(String json) {
        try { return mapper.writeValueAsString(mapper.readTree(json)); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Stored payload is invalid JSON", e); }
    }
    private Map<String,Object> result(boolean valid, long block, String message) { return Map.of("valid",valid,"checkedThroughBlock",block,"message",message); }
}