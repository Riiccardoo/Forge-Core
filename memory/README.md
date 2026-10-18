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
| `EXP-000005` | Disconnect da Common asset runtime e pacchetti di progresso dopo lo stato Playing |
| `EXP-000006` | Rifiuto nel client delle animazioni Atmosphere generate e limiti dei test sul server |
| `EXP-000007` | Nebbia volumetrica continua AtmosphereTool, trasparenza indipendente, installazione client e limiti del browser mod |
| `EXP-000008` | Terreno Hytale circoscritto, sezioni ECS e verifica dopo riavvio |
| `EXP-000009` | Panorami 360° con aree protette immutabili |
| `EXP-000010` | Controllo Creative Fullbright tramite componente nativo |
| `EXP-000011` | Freschezza degli atlanti Blockbench ed esportazione vegetazione |
| `EXP-000012` | Vegetazione approvata con chiome animate e asset distinti per inventario |
| `EXP-000013` | Consolidamento dei pacchetti asset senza cambiare gli ID dei blocchi esistenti |

Pattern attivi: `PAT-000001` (contratti distinti per gli usi di un asset), `PAT-000002` (validazione tecnica distinta dall'accettazione visiva) e `PAT-000003` (composizione deterministica delle aree protette, ancora sperimentale). Apri i rispettivi `pattern.json` e verifica condizioni, trade-off e stato prima di applicarli.

La memoria di progetto consolidata è in `projects/zytale/project.json`. Le cartelle
`experiences/` e `failures/` conservano rispettivamente soluzioni versionate e casi di
errore riutilizzabili. I file `README.md` delle singole esperienze sono viste generate;
i dati strutturati JSON restano il riferimento per gli strumenti.

## Controlli

Esegui `forge doctor` per verificare configurazione e percorsi, quindi `forge validate`
prima di pubblicare modifiche. Non inserire esempi fittizi nella memoria attiva.

