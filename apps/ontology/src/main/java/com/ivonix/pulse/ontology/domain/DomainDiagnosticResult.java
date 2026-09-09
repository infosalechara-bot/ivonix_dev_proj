package com.ivonix.pulse.ontology.domain;

import java.util.List;

public record DomainDiagnosticResult(String domain, String diagnosis, double confidence, List<String> recommendedActions) {}