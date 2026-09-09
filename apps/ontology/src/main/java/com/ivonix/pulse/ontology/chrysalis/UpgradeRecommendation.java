package com.ivonix.pulse.ontology.chrysalis;

public record UpgradeRecommendation(String capabilityNeeded,String recommendedUpgrade,String complexity,Double estimatedCost,String instructions) {}
