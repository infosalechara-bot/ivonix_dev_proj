# PULSE Schema Registry v1

`public.event_types` is the authoritative registry. The key is `(name,event_version)`. Active registry versions must have matching golden payload fixtures; fixtures must have matching registry entries. No parallel reliability schema table is introduced.
