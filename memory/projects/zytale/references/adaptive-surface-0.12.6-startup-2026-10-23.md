# Adaptive Surface 0.12.6 — avvio e riparazione locale

23 ottobre 2026. Correzione installata localmente, nessuna pubblicazione.

## Problema osservato

Il client mostrava `MISSING MOD DETECTED: ZytaleGenerated:AdaptiveSurfaceWorld`. Nel mondo **The Village - Zytale 306 Working Copy**, `config.json` conteneva quel riferimento con `Enabled=true`, ma `mods/ZAS_ActivePack.zip` era assente. Il log client delle 11:58:36 conferma la scansione delle mod del mondo; lo screenshot documenta il blocco.

Il controllo precede l'avvio del server Java. La rigenerazione nel costruttore della mod, usata fino alla 0.12.5, non poteva quindi risolvere questo caso. Le precedenti prove su server dedicato non coprivano il controllo preliminare del client.

## Correzione

- Cache interna in `mods/Zytale_AdaptiveSurface/generated/ZAS_ActivePack.zip`, fuori dalla scansione delle mod installabili.
- Caricamento tramite `AssetModule.registerPack` durante `setup`, prima del caricamento iniziale delle risorse. Nessun ricaricamento degli asset durante la partita.
- Il vecchio identificativo resta disabilitato nella configurazione. Se era abilitato, la correzione viene salvata prima di spostare il vecchio ZIP, proteggendo anche la migrazione interrotta da un crash.
- Migrazione del vecchio pacchetto mantenendo i byte; inventario dei materiali conservato anche nelle preparazioni per il riavvio. Con indice/cache incompleti restano attivi il pacchetto precedente e il rapporto dei materiali mancanti.
- `install.ps1` aggiornato con preparazione preventiva, backup e ripristino. Serve una migrazione iniziale per i vecchi mondi già bloccati, perché il codice della mod non può eseguire prima del controllo del client. Le nuove installazioni richiedono il solo JAR.

Riuso diretto: preparazione prudente e recupero della 0.12.5. Analogia: distinguere il controllo preliminare dell'applicazione dal successivo avvio del servizio. Lavoro nuovo: proprietà interna della cache e migrazione della dipendenza persistita.

## Ambiente verificato

Il server installato ora dichiara **Hytale 0.7.4**, SHA-256 `23fad4c8d42b73b4139540bbae26ca0b8a43388f1e07e7b423d616e81834fa79`. Compilazione Java 25, esecuzione con il runtime Hytale Temurin 26.0.2. Plugin e nuovi pacchetti Surface dichiarano `=0.7.4`.

La pagina delle [note ufficiali degli hotfix](https://hytale.com/news/2026/10/hotfixes-update-7), consultata durante il lavoro, riportava ancora 0.7.3: per la prova effettiva fanno fede binario, versione e log locali. La [documentazione ufficiale degli asset pack](https://docs.hytale.com/creating-content/asset-packs/) conferma le cartelle delle mod globali e dei singoli mondi. Il ciclo di caricamento è stato verificato sul codice dell'API del server installato.

## Prove

**279.130 controlli del nucleo superati. Nove avvii finali superati**, con gli hash degli artefatti finali:

| Scenario | Esito |
| --- | --- |
| Nuova installazione dal solo JAR ordinario, mod non dichiarate disabilitate per impostazione predefinita | Avvio e validazione nativa degli asset riusciti |
| Eliminazione della cache interna e riavvio | Rigenerazione, materiali iniziali e assenza di dipendenze mancanti verificati |
| Riapertura dal JAR ordinario | Avvio e validazione nativa riusciti |
| Vecchia cache con indice mancante, migrazione e riapertura | Due prove riuscite; ZIP conservato byte per byte, 89 materiali e 2.937 stati disponibili |
| Adaptive Window 0.4.5 con cache materiali preparata | 21 posizionamenti e riapertura riusciti |
| Copia del mondo segnalato | 9.173 chunk letti; 67 materiali dopo l'aggiunta dei materiali iniziali, 2.211 stati e serializzazione delle risorse di accesso verificati |
| Copia del mondo, JAR ordinario e Window installata 0.4.4 | Avvio e validazione nativa riusciti; avvisi di versione delle altre mod conservati nei log |

Sono tre avvii del JAR ordinario con validazione nativa e sei avvii con verifiche separate di integrazione. Il confronto dei prerequisiti individua esattamente l'identificativo dello screenshot nel mondo originale e nessuna mod obbligatoria mancante dopo la riparazione. È una verifica del contratto basata su manifest/configurazione, **non un ingresso pilotato nell'interfaccia del client**.

Un tentativo preliminare con la cache Window non preparata ha rifiutato il primo posizionamento. La cache è passata da 899 a 2.584 alias e la prova successiva, con riapertura, è riuscita. Il tentativo fallito rimane nel riepilogo: il ciclo separato di apprendimento di Window può richiedere un riavvio per materiali nuovi. Questa correzione non modifica il pacchetto Window.

## Installazione e conservazione

Installato `UserData/Mods/ZytaleAdaptiveSurface-0.12.6.jar`, rimuovendo la precedente 0.12.4 dalla cartella attiva e conservandola nel backup. Ricostruiti **65 materiali richiesti** dal mondo originale; al suo avvio possono aggiungersi i materiali iniziali della mod.

Backup: `backups/mod-20261023-122746/`. Il confronto dopo l'installazione conferma:

- tutti i **58 file** sotto `universe` invariati;
- gli altri **quattro JAR** invariati;
- unica variazione della configurazione del mondo: `ZytaleGenerated:AdaptiveSurfaceWorld.Enabled=false`;
- nessuna mod abilitata priva del relativo manifest;
- JAR installato identico al pacchetto verificato.

Le protezioni, le geometrie e gli asset della 0.12.5 restano invariati: **1.753 file di risorse identici byte per byte**. Nessuna classe di test/integrazione né materiale sintetico di test nel JAR ordinario.

## Artefatti e limiti

- `dist/ZytaleAdaptiveSurface-0.12.6.jar`
- `dist/ZytaleAdaptiveSurface-0.12.6.sha256`
- `outputs/startup-0.12.6/validation-summary.json`: avvii finali, tentativi preliminari e hash.
- `outputs/startup-0.12.6/installation-audit.json`: confronto del mondo originale.
- `outputs/startup-0.12.6/`: copie isolate, rapporti, log e input della memoria tecnica.

SHA-256 del JAR ordinario: `e8f78188448d33791441d8ee2dad85d8e514b2ecfe2238d763a2422e890acc33`.

Non è stato automatizzato il download da un catalogo pubblico né l'ingresso grafico del giocatore: il pacchetto non è stato pubblicato. La prova riproduce l'installazione dal solo file scaricato e il riavvio con cache assente. I mondi già bloccati da una vecchia versione richiedono la migrazione iniziale; quella del mondo segnalato è stata eseguita. Restano aperti i limiti di provenienza/licenza degli asset documentati nella 0.12.5: nessuna nuova autorizzazione alla distribuzione è implicita.
