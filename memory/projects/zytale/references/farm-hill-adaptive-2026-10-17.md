# Blackridge: collina della fattoria e Adaptive Surface

17 ottobre 2026. Mappa installata: **The Village - Zytale 306 Working Copy**, Hytale 0.7.2. Questo documento registra la ricostruzione della collina cancellata accanto ai campi di grano e la successiva passata con Zytale Adaptive Surface. I dati di costruzione restano nella memoria del progetto `zytale`; le mappe complete e i dati del giocatore restano negli archivi locali.

## Richiesta e risultato

Il lato verso il campo doveva diventare una salita verde, abbastanza ripida da essere evidente alla recinzione; la parte incavata doveva guardare verso l'esterno. Il terreno doveva raccordarsi alla foresta, con alberi ed erba alta, e gli elementi rimasti in aria andavano eliminati. In seguito l'utente ha chiesto superfici inclinate come nell'immagine di esempio, usando **Adaptive Surface** sulla collina appena ricostruita.

La collina è stata ricostruita in 16.005 colonne nella fascia approssimativa X −212…−39, Z 48…254, con quote finali 78…117. Sei sezioni del lato campo misurano una salita di 23…25 blocchi lungo 30 blocchi orizzontali. Il lato esterno ha una rientranza aperta, raccordata al terreno preesistente. La forma è stata calcolata prima di spostare o ripiantare la vegetazione, così gli alberi non restano incassati nel nuovo pendio.

La prima passata ha ripiantato 186 alberi nativi e aggiunto 13.921 celle di erba alta, 1.019 felci e 257 cespugli. Ha rimosso 35 componenti naturali sospesi, pari a 814 blocchi, e un elemento di sottobosco senza appoggio. Non sono rimasti componenti sospesi nell'area verificata.

La seconda passata ha applicato **9.835 superfici Adaptive Surface** registrate alla stessa collina. Le forme sono state risolte con `Topology.resolve` della versione installata **ZytaleAdaptiveSurface 0.9.5**, usando il materiale del blocco sottostante. Sono state ricollocate 643 piante su terreno pieno; sulle pendenze 7.093 piante hanno lasciato posto ai raccordi verdi. Il sottobosco contato nella fascia della collina è passato da 13.024 a 5.718 celle, conservato a gruppi fra le superfici e sui tratti adatti. Questa riduzione è un dato della modifica, non una stima visiva.

## Verifiche e protezione

| Passata | Modifiche | Lettura nativa dopo riavvio | Controlli specifici |
| --- | ---: | ---: | --- |
| Collina e foresta | 359.218 blocchi in 40 chunk | 13.107.200 celle, zero differenze | 16.005 colonne con fondazione continua; 448 contatti delle radici sostenuti; zero elementi sospesi locali |
| Adaptive Surface | 10.478 blocchi in 34 chunk | 11.141.120 celle, zero differenze | 9.835 superfici sostenute; 643 piante ricollocate su terreno pieno; 55.706 celle degli alberi e 3.336.433 celle del terreno originario invariate |

I controlli hanno inoltre escluso modifiche nelle colonne protette, agli edifici e ai campi. I componenti nativi estranei al terreno e i dati del giocatore sono stati conservati; il ponte temporaneo di modifica è stato rimosso. La distanza di visualizzazione della mappa resta **608 blocchi**. Le verifiche riguardano il salvataggio nativo e il suo riavvio: non è stato eseguito un benchmark di prestazioni del client né sono state fatte nuove riprese con Computer Use.

## Copie locali

La prima passata ha un archivio completo della mappa aggiornata in `outputs/zytale-hill-rebuilt-2026-10-17/ZYTALE_BLACKRIDGE_COLLINA_CAMPO.zip` e una copia completa precedente alla ricostruzione. La seconda passata ha un checkpoint differenziale in `outputs/zytale-hill-adaptive-2026-10-17/COLLINA_ADAPTIVE_SURFACE_DELTA.zip`: per recuperare quello stato servono sia questo file sia l'archivio completo della prima passata. Il differenziale precedente alla passata Adaptive Surface è conservato nella stessa cartella. Hash, manifest e controlli dei file sono registrati nei rispettivi `VERIFICA_*.json` locali. Nessun archivio della mappa o dato del giocatore viene caricato nel repository.

## Feedback dell'utente

Dopo aver visto il risultato con Adaptive Surface, l'utente ha scritto: **«Il risultato ottenuto davvero buono»** e poi **«Ottimo lavoro»**. Il feedback conferma l'apprezzamento visivo espresso dall'utente; non sostituisce una misura delle prestazioni o un collaudo esaustivo di ogni punto della mappa.

## Lezione tecnica

Su questa versione di Hytale, una correzione di terreno esistente resta controllabile quando la geometria viene resa continua prima della vegetazione, gli alberi vengono reinseriti sulla quota finale e gli elementi scollegati vengono rilevati con una verifica di contatto. Per la passata cosmetica successiva, riusare le forme del `Topology` effettivamente installato ha dato raccordi coerenti senza cambiare i blocchi pieni del terreno né gli alberi. Il risultato è validato per questo salvataggio; per altri mondi, asset e versioni servono nuove verifiche native.
