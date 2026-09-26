# Start Here — usare Forge senza MCP

Forge è una memoria tecnica condivisa. La source of truth è sotto `memory/`; `.forge/index.sqlite3` è solo una cache ricostruibile.

## Prima di risolvere un problema

1. Leggi questo file e `FORGE_PROTOCOL.md`.
2. Esegui `forge doctor`.
3. Cerca con `forge search "descrizione concreta del task"`.
4. Costruisci un bundle con `forge context "task" --project <progetto>`.
5. Apri soltanto le capsule, i pattern e i failure segnalati come pertinenti.

Non caricare l’intera memoria in chat. Parti dal context bundle e approfondisci solo gli ID necessari.

## Come leggere i risultati

- **Experience (`EXP-*`)**: un problema tecnico affrontato con contesto, vincoli e lineage delle soluzioni.
- **Solution (`SOL-*`)**: una famiglia di soluzioni; `v001`, `v002`… preservano l’evoluzione. `preferred` significa migliore secondo il contesto noto, non universalmente corretta.
- **Failure (`FAIL-*`)**: un approccio fallito con condizioni, firma errore e workaround.
- **Pattern (`PAT-*`)**: una regola trasferibile, distinta dalla soluzione specifica che l’ha suggerita.
- **Project memory**: fatti locali a un progetto, non generalizzabili automaticamente.
- **Evolution proposal (`EVP-*`)**: proposta di cambiare Forge; non è un’autorizzazione ad auto-modificare il codice.

Controlla sempre `confidence`, `validation_status`, fonti e compatibilità. `analogy: true` significa che i principi/capacità sono simili ma il codice può non essere riutilizzabile.

## Se la CLI non è installata

Puoi usare `python -m forge.cli ...` dalla root. In ultima istanza cerca in `memory/experiences/*/README.md`, poi apri il relativo `capsule.json`. Cerca i failure globali sotto `memory/failures/` e i pattern sotto `memory/patterns/`.

## Dopo un lavoro significativo

Registra soltanto conoscenza osservata o verificata. Usa `forge novelty` per scegliere tra nuova esperienza, aggiornamento, nuova versione, failure, pattern o aggiornamento progetto. Non elevare una singola osservazione a `stable`.

