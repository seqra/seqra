{% include "shared/inputs-preamble.md" %}

{% include "shared/inputs/project-root.md" %}
- `rule-ids` (optional) — full rule IDs to restrict the scan to
- `max-memory` (optional) — a `--max-memory` value to run scan with. Default: unset (engine default `8G`)
- `language` (optional) — project language. Read it from `.opentaint/tracking/state.yaml` when the caller does not supply it
