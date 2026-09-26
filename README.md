# Forge v0.1

Forge è una memoria tecnica locale, evolutiva e leggibile da Git per ChatGPT, Codex e altri agenti. Conserva non solo “la risposta”, ma problema, contesto, tentativi, fallimenti, prove, versioni della soluzione, metriche e principi trasferibili. Il principio operativo è semplice: ogni problema risolto deve rendere il prossimo problema strutturalmente simile più economico e affidabile.

La memoria canonica è composta da JSON e Markdown sotto `memory/`. SQLite FTS5 è soltanto un indice rigenerabile: può essere cancellato e ricostruito senza perdere conoscenza.

## Caratteristiche già operative

- Experience Capsule validate con Pydantic e sintesi Markdown;
- lineage delle soluzioni con versioni immutabili e una versione preferita;
- memoria dei fallimenti ricercabile prima di ripetere un approccio;
- Pattern separati dalle soluzioni specifiche;
- retrieval ibrido FTS5 + matching strutturato;
- analogie cross-technology guidate soprattutto dalle capacità;
- Novelty Detector con decisioni verificabili;
- context bundle concisi in JSON o Markdown, con budget approssimato;
- memoria di progetto separata dalla conoscenza trasferibile;
- proposte di evoluzione del codice senza auto-modifica;
- CLI senza servizi cloud e server MCP ufficiale.

## Architettura

```text
forge/
  config.py                 configurazione TOML e path risolti
  cli.py                    CLI argparse
  mcp_server.py             15 tool MCP, trasporto stdio
  core/
    models.py               schema Pydantic
    repository.py           file canonici e scritture atomiche
    index.py                indice SQLite FTS5 rigenerabile
    similarity.py           scoring strutturato e inferenza capacità
    retrieval.py            ranking ibrido e analogie
    novelty.py              classificazione della novità
    context_builder.py      bundle compatti JSON/Markdown
    lineage.py              confronto bilanciato delle soluzioni
    validation.py           validazione globale e riferimenti
    service.py              façade condivisa da CLI e MCP
memory/                     source of truth versionabile
config/default.toml         pesi, soglie, budget e modalità read-only
tests/                      test funzionali e di integrazione
examples/                   dati dimostrativi, mai memoria attiva
```

## Installazione rapida

Richiede Python 3.12 o successivo.

### Windows PowerShell

```powershell
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install --upgrade pip
.\.venv\Scripts\python.exe -m pip install -e ".[mcp,dev]"
.\.venv\Scripts\forge.exe init
.\.venv\Scripts\forge.exe doctor
```

In alternativa:

```powershell
.\scripts\setup.ps1
```

### Linux/macOS

```bash
python3 -m venv .venv
.venv/bin/python -m pip install --upgrade pip
.venv/bin/python -m pip install -e '.[mcp,dev]'
.venv/bin/forge init
.venv/bin/forge doctor
```

Oppure `./scripts/setup.sh`.

## CLI

```text
forge init
forge doctor
forge status
forge search "procedural building placement"
forge context "procedural building placement" --project my-project
forge context "task" --json
forge novelty "problem description"
forge novelty "known problem" --solution "new approach"
forge novelty "known problem" --event-type failure --failed-attempt "approach A"
forge show EXP-000001
forge validate
forge rebuild-index
forge export-context "task" --format markdown --output context.md
```

Le operazioni di scrittura accettano un oggetto JSON con `--data` oppure un file con `--file`:

```powershell
forge create-experience --file .\capsule-input.json
forge update-experience EXP-000001 --data '{"confidence":0.8}'
forge add-solution EXP-000001 --file .\solution.json
forge register-failure EXP-000001 --file .\failure.json
forge create-pattern --file .\pattern.json
forge update-project my-project --file .\project.json
forge propose-update --file .\proposal.json
```

## MCP

Avvio locale su `stdio`:

```powershell
$env:FORGE_ROOT = (Get-Location).Path
.\.venv\Scripts\python.exe -m forge.mcp_server
```

```bash
FORGE_ROOT="$PWD" .venv/bin/python -m forge.mcp_server
```

Il processo non stampa messaggi su stdout: quel canale è riservato al protocollo MCP. Il server espone:

`forge_status`, `forge_search`, `forge_get_experience`, `forge_build_context`, `forge_detect_novelty`, `forge_create_experience`, `forge_update_experience`, `forge_register_failure`, `forge_add_solution_version`, `forge_create_pattern`, `forge_get_related`, `forge_validate_memory`, `forge_rebuild_index`, `forge_project_context`, `forge_update_project`, `forge_propose_update`.

Per verificare import e contratto senza avviare un server persistente:

```powershell
python -m forge.mcp_server --check
```

## Layout della memoria

```text
memory/
  experiences/EXP-XXXXXX-slug/
    capsule.json
    README.md
    solutions/
    failures/
    evidence/
  patterns/PAT-XXXXXX-slug/
    pattern.json
    README.md
  failures/FAIL-XXXXXX.json
  projects/<project>/
    project.json
    decisions/
    architecture/
    constraints/
    references/
  evolution/proposals/EVP-XXXXXX.json
  benchmarks/
```

Gli ID vengono calcolati scandendo tutti i file esistenti sotto un lock locale; un ID precedente non viene riutilizzato. Le scritture JSON passano da un file temporaneo e da una sostituzione atomica.

## Retrieval e analogie

La ricerca unisce ranking lessicale FTS5 e score strutturato. I pesi predefiniti privilegiano `capabilities_required`; tecnologia e tool hanno un peso minore. Di conseguenza, una esperienza Hytale su `world_modification`, `spatial_placement`, `object_creation` e `persistence` può emergere per “Procedural building placement in Unreal Engine”. Il risultato è marcato `analogy: true` e avverte che API e codice richiedono verifica di compatibilità.

L’interfaccia `SemanticBackend` è disponibile per futuri embeddings, ma Forge v0.1 non ne dipende.

## Novelty Detector

`detect_novelty` confronta il problema con le capsule esistenti e restituisce una delle decisioni:

- `NEW_EXPERIENCE`;
- `UPDATE_EXISTING`;
- `NEW_SOLUTION_VERSION`;
- `NEW_FAILURE`;
- `NEW_PATTERN`;
- `PROJECT_ONLY_UPDATE`.

La motivazione riporta ID, score e soglia; non contiene ragionamento interno del modello. Per distinguere failure, pattern o conoscenza solo progettuale, usare `event_type` o le opzioni CLI corrispondenti.

## Workflow con ChatGPT/Codex

1. Leggere [START_HERE.md](START_HERE.md).
2. Eseguire `forge doctor` e `forge search "<task>"`.
3. Costruire `forge context "<task>" --project <nome>`.
4. Verificare failure, stato di validazione, confidence e compatibilità.
5. Lavorare e testare nel progetto.
6. A fine attività eseguire `forge novelty` sui fatti realmente emersi.
7. Aggiornare memoria generale, memoria progetto o proposta evolutiva secondo [FORGE_PROTOCOL.md](FORGE_PROTOCOL.md).
8. Eseguire `forge validate`.

## Project memory ed evolution proposal

Un vincolo specifico di un progetto resta sotto `memory/projects/<project>` finché non esiste evidenza che sia trasferibile. Una proposta di cambiare Forge viene salvata come `EVP-*` sotto `memory/evolution/proposals`; non modifica codice, non crea commit e non fa merge.

## Test

```powershell
python -m pytest -q
forge doctor
forge validate
python -m forge.mcp_server --check
```

La suite copre creazione e aggiornamento capsule, schema, lineage, failure, pattern, progetti, indice, retrieval, analogia Hytale→Unreal, novelty, budget del contesto, CLI e handshake in-memory con un client MCP ufficiale.

## Limiti v0.1

- nessun embedding o semantic model: sinonimi non inclusi negli hint possono essere persi;
- aggiornamenti concorrenti sono protetti nella generazione ID, ma non esiste ancora locking distribuito;
- il confronto metriche è deliberatamente consultivo, non un selettore automatico;
- non esiste import automatico da fonti esterne;
- gli allegati in `evidence/` sono gestiti come file e riferimenti, non acquisiti dal web.
