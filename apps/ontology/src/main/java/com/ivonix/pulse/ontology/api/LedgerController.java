package com.ivonix.pulse.ontology.api;

import com.ivonix.pulse.ontology.ledger.LedgerContractService;
import com.ivonix.pulse.ontology.ledger.LedgerService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/ledger")
public class LedgerController {
    private final LedgerService ledger;
    private final LedgerContractService contracts;

    public LedgerController(LedgerService ledger, LedgerContractService contracts) {
        this.ledger = ledger;
        this.contracts = contracts;
    }

    private UUID user() {
        return UUID.fromString(SecurityContextHolder.getContext().getAuthentication().getName());
    }

    private UUID org() {
        Object details = SecurityContextHolder.getContext().getAuthentication().getDetails();
        if (!(details instanceof UUID)) throw new SecurityException("Organization context missing");
        return (UUID) details;
    }

    @PostMapping("/transactions")
    public Map<String, Object> submit(@Valid @RequestBody TransactionRequest r) {
        return Map.of("id", ledger.submitTransaction(org(), r.txType(), r.payload(), user()));
    }

    @GetMapping("/verify")
    public Map<String, Object> verify() {
        return ledger.verifyChain(org(), user());
    }

    @GetMapping("/blocks")
    public Object blocks(@RequestParam(defaultValue = "20") int limit) {
        return ledger.blocks(org(), user(), limit);
    }

    @GetMapping("/transactions")
    public Object transactions(@RequestParam(defaultValue = "50") int limit) {
        return ledger.transactions(org(), user(), limit);
    }

    @PostMapping("/contracts")
    public Map<String, Object> createContract(@Valid @RequestBody ContractRequest r) {
        UUID id = contracts.create(org(), user(), r.name(), r.code());
        return Map.of("id", id, "active", true);
    }

    @PostMapping("/contracts/{contractId}/execute")
    public Map<String, Object> executeContract(
            @PathVariable UUID contractId,
            @Valid @RequestBody ContractExecutionRequest r) {
        return contracts.execute(contractId, org(), user(), r.input());
    }

    public record TransactionRequest(@NotBlank String txType, @NotNull Map<String, Object> payload) {}

    public record ContractRequest(@NotBlank String name, @NotNull Map<String, Object> code) {}

    public record ContractExecutionRequest(@NotNull Map<String, Object> input) {}
}
