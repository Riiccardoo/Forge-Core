# Active Forge memory

Questa directory è la source of truth versionabile della memoria Forge. I contenuti
derivano da osservazioni, verifiche e risultati registrati tramite CLI o MCP; consulta
`FORGE_PROTOCOL.md` prima di aggiungerli o aggiornarli.

## Catalogo attivo

| ID | Ambito |
| --- | --- |
| `EXP-000001` | Superfici adattive Hytale e persistenza della geometria |
| `EXP-000002` | Riparazione architettonica circoscritta e validazione multipart |
| `EXP-000003` | Vetrata pilota Blockbench, materiali nativi e accettazione visiva |
| `EXP-000004` | Catalogo AdaptiveWindow per Update 7, vetro nativo e muratura separata dalla cornice |

La memoria di progetto consolidata è in `projects/zytale/project.json`. Le cartelle
`experiences/` e `failures/` conservano rispettivamente soluzioni versionate e casi di
errore riutilizzabili. I file `README.md` delle singole esperienze sono viste generate;
i dati strutturati JSON restano il riferimento per gli strumenti.

## Controlli

Esegui `forge doctor` per verificare configurazione e percorsi, quindi `forge validate`
prima di pubblicare modifiche. Non inserire esempi fittizi nella memoria attiva.

