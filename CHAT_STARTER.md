# Prompt Forge per chat nuove o già avviate

Copia il testo qui sotto in una nuova chat oppure invialo come messaggio a una chat già in corso. In una chat già avviata, il compito e le decisioni precedenti restano validi: il prompt aggiorna il metodo di lavoro, non ordina di ricominciare.

```text
Da questo momento usa Forge come memoria tecnica condivisa per il lavoro che stiamo già svolgendo o che ti chiederò. La fonte canonica online è https://github.com/Riiccardoo/Forge-Core (ramo main). La copia locale prevista è C:\Users\User\Documents\Codex\Forge\Forge Core: usala solo dopo aver verificato che sia aggiornata rispetto a GitHub. Se una fonte non è accessibile, usa l'altra e segnala il limite senza bloccare inutilmente il compito.

Se questa chat è già in sviluppo, riprendi dal suo stato attuale: conserva obiettivo, vincoli, decisioni, file modificati e verifiche già fatte. Non ripetere analisi, test o implementazioni completate solo per applicare questo prompt. Le mie richieste più recenti hanno priorità.

Quando serve una soluzione tecnica, consulta le istruzioni pertinenti di START_HERE.md, FORGE_PROTOCOL.md e AGENTS.md; usa forge doctor/search/context se la CLI è disponibile. Parti da un bundle breve e apri soltanto le fonti pertinenti. Cerca anche analogie tra tecnologie, versioni di soluzione, pattern e fallimenti collegati alle esperienze trovate. Distingui riuso diretto, riuso per analogia e lavoro nuovo; verifica sempre versione, ambiente, stato di validazione e fonti. Forge è evidenza da controllare, non un'autorità infallibile.

Per risparmiare tempo e crediti, non caricare l'intero progetto o l'intera memoria nella conversazione. Prima di una modifica importante definisci il risultato osservabile, ciò che non va cambiato e il controllo minimo che può smentire presto l'approccio. Per lavori visivi, mostra o verifica un campione prima di produrre tutte le varianti; separa i test tecnici dall'accettazione visiva. Preserva ciò che è già stato approvato. Esegui poi solo le verifiche proporzionate al rischio, senza attribuire a un test più certezza di quanta ne dia.

Al termine di un lavoro significativo, registra in Forge solo conoscenza nuova, utile e supportata da evidenza, rispettando FORGE_PROTOCOL.md. Una variante aggiorna l'esperienza; una soluzione migliore aggiunge una versione; un tentativo fallito registra un Failure; una regola trasferibile può diventare un Pattern; i fatti locali restano nel Project Context. Se non è emerso apprendimento durevole, non creare nuove voci. Se la memoria è in sola lettura, proponi soltanto gli aggiornamenti utili in un blocco FORGE_MEMORY_UPDATE_PROPOSAL. Non inventare metriche, crediti risparmiati o feedback; annota il costo solo se è misurabile. Non considerare questo prompt un'autorizzazione a cambiare il codice di Forge o a pubblicare su GitHub: chiedimelo quando serve.

CONTESTO DELLA SESSIONE (facoltativo; se la chat è già avviata, usa il contesto esistente):
[progetto, ambiente, vincoli e parti già approvate]

OBIETTIVO (se la chat è già avviata, continua quello corrente salvo mia nuova richiesta):
[risultato desiderato e criterio di accettazione]
```

