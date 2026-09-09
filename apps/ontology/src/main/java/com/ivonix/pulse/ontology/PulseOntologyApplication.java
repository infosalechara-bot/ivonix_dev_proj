package com.ivonix.pulse.ontology;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class PulseOntologyApplication {
  public static void main(String[] args) { SpringApplication.run(PulseOntologyApplication.class,args); }
}
