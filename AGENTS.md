# Istruzioni per agenti

Questa repository contiene Forge e la sua memoria tecnica.

Prima di iniziare una nuova soluzione:

1. esegui `forge doctor` (o `python -m forge.cli doctor`);
2. interroga Forge con una descrizione concreta del task;
3. cerca anche analogie strutturali e cross-technology;
4. leggi failure pertinenti prima di ripetere un approccio;
5. riusa conoscenza compatibile, verificando fonti, validation status e ambiente;
6. non assumere che una soluzione vecchia sia ancora valida o che codice per un’altra tecnologia sia direttamente portabile.

Mantieni il contesto proporzionato al task: usa il bundle breve, poi apri soltanto i dettagli necessari. Controlla sia la soluzione preferita sia l'ultima versione e i failure collegati alle esperienze principali. Per modifiche visive o d'integrazione costose, verifica prima un campione o il contratto minimo che potrebbe smentire l'approccio; conserva le parti già approvate.

Dopo un’attività significativa, valuta se è emersa nuova conoscenza tecnica. Se sì, usa il Novelty Detector e aggiorna Experience, Solution, Failure, Pattern o Project Memory secondo `FORGE_PROTOCOL.md`. Se non c'è apprendimento durevole, non aggiungere memoria. Non inventare evidenza, metriche di costo o feedback e non promuovere automaticamente un fatto di progetto a regola universale.

Le modifiche alla memoria sono normali quando autorizzate. Le modifiche al codice di Forge richiedono lavoro esplicito: una conversazione che suggerisce un miglioramento va registrata come Evolution Proposal, non applicata automaticamente. Non creare commit, remote, push o merge senza richiesta.

La source of truth è `memory/`; `.forge/index.sqlite3` è rigenerabile. Non modificare a mano copie duplicate di failure o solution: usa repository, CLI o MCP e poi esegui `forge validate`.

