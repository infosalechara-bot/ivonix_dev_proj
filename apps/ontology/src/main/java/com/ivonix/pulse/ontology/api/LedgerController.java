package com.ivonix.pulse.ontology.api;

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
    public LedgerController(LedgerService ledger){this.ledger=ledger;}
    private UUID user(){return UUID.fromString(SecurityContextHolder.getContext().getAuthentication().getName());}
    private UUID org(){Object d=SecurityContextHolder.getContext().getAuthentication().getDetails();if(!(d instanceof UUID))throw new SecurityException("Organization context missing");return (UUID)d;}
    @PostMapping("/transactions") public Map<String,Object> submit(@Valid @RequestBody TransactionRequest r){return Map.of("id",ledger.submitTransaction(org(),r.txType(),r.payload(),user()));}
    @GetMapping("/verify") public Map<String,Object> verify(){return ledger.verifyChain(org(),user());}
    @GetMapping("/blocks") public Object blocks(@RequestParam(defaultValue="20") int limit){return ledger.blocks(org(),user(),limit);}
    @GetMapping("/transactions") public Object transactions(@RequestParam(defaultValue="50") int limit){return ledger.transactions(org(),user(),limit);}
    public record TransactionRequest(@NotBlank String txType,@NotNull Map<String,Object> payload){}
}
