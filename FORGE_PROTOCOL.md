# Forge Protocol v0.1

## Regole comuni

- Non inventare fatti, metriche, test o fonti.
- Usa UTF-8 e ID generati da Forge.
- Mantieni `project knowledge` separata dalla conoscenza trasferibile.
- Non sovrascrivere una soluzione precedente: aggiungi una versione.
- Registra i fallimenti pertinenti, anche quando esiste un workaround.
- Dopo ogni scrittura esegui `forge validate`.

## Creare una Experience Capsule

1. Cerca prima esperienze analoghe e usa `forge novelty`.
2. Prepara almeno `title`; aggiungi problema, contesto, capacità, vincoli e fonti realmente noti.
3. Esegui `forge create-experience --file input.json` oppure `forge_create_experience` via MCP.
4. Controlla `capsule.json` e la sintesi `README.md` generata.

Stato iniziale consigliato: `confidence: 0.5`, `validation_status: observation` se non esistono test.

## Aggiornare un’esperienza

Usa `forge update-experience EXP-XXXXXX --file changes.json`. `id` e `created_at` sono immutabili. Non usare un aggiornamento per cancellare la storia delle soluzioni.

## Aggiungere una Solution Version

Usa `forge add-solution EXP-XXXXXX --file solution.json`. Campi principali: `description`, `changes`, `advantages`, `disadvantages`, `test_results`, `metrics`, `status`. Per una nuova versione dello stesso approccio passa lo stesso `id`; Forge incrementa `version` e collega `supersedes`. Una nuova famiglia può omettere `id` e riceverne uno nuovo.

Imposta `preferred` solo dopo aver considerato affidabilità, compatibilità, costo, complessità e risultati dei test.

## Registrare un Failure

Usa `forge register-failure EXP-XXXXXX --file failure.json`. Registra almeno `attempt` e `reason_failed`; quando disponibili aggiungi `environment`, `error_signature`, `conditions`, `workaround`, `resolved_by` e fonti. Forge crea sia la copia globale ricercabile sia il collegamento nella capsule.

## Creare un Pattern

Un pattern è un principio generale supportato da evidenza, non una descrizione rinominata della soluzione. Usa `forge create-pattern --file pattern.json` con condizioni, trade-off, esperienze correlate e confidence. Una sola osservazione dovrebbe restare `observation` o `experimental`.

## Aggiornare Project Knowledge

Usa `forge update-project <nome> --file project.json` o `forge_update_project`. Forge conserva il JSON sotto `memory/projects/<slug>/` e crea le sottodirectory `decisions`, `architecture`, `constraints`, `references`. Promuovi un fatto a Experience/Pattern solo quando è tecnicamente trasferibile.

## Proporre un’evoluzione di Forge

Usa `forge propose-update --file proposal.json` o `forge_propose_update`. Specifica problema, componente, modifica, beneficio, rischi e test. La proposta non modifica codice, Git, remote, commit o merge.

## Evidenza e validazione

Una fonte usa `kind`, `reference`, `description`, `verified`. I riferimenti possono indicare file, commit, issue, URL, test o conversazioni. Le fasi ammesse sono `observation`, `experimental`, `validated`, `stable`, `deprecated`.
