# PULSE universal core modules

Implemented in the Spring Boot application:

- PULSE FIND: consent-aware registrations, searches and vector matching; AERIE, FERRET and BEACON service seeds.
- PULSE Ontology: bounded recursive graph traversal and PageRank SQL integration.
- PULSE Engineering: machine-domain diagnostics and evidence integration.
- PULSE Shield: security events, threat intelligence and malware controls.
- PULSE Edge: SQLite durable offline queue with allow-listed cloud synchronization.
- PULSE CHRYSALIS: legacy compatibility assessment and controlled upgrade installation.
- Domain adapters: Automotive, Industrial, Robotics, Energy, Electronics, Agriculture, Aviation, Naval and Space.

All domain ingestion APIs derive organization scope from the authenticated PULSE JWT and verify device ownership before writing telemetry. Dynamic SQL is restricted to server-owned table allow-lists.