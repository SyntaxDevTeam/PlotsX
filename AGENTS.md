# Integration boundaries

- Keep domain plugins independent of Moonvale, miniPORTAL and any specific deployment or customer.
- Domain plugins expose general-purpose, documented local public API contracts. Preserve compatibility; do not rename or remove public methods just to remove historical profile terminology.
- Website payload mapping, authentication tokens, HTTP destinations and endpoint configuration belong to separately installed adapters, never the domain plugin.
- Adapters must require an administrator-configured destination and explicit selection of additional domain data sources. Do not add production server addresses as defaults.
- A configurable endpoint still uses a documented protocol. Do not claim arbitrary backend compatibility without implementing that protocol.
- Do not access another plugin's private implementation or database to satisfy a portal requirement.
- Document configuration migrations and verify destination validation, source selection and existing contracts when changing the adapter.
