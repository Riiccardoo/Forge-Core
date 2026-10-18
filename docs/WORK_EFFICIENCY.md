# Lavorare più rapidamente con la memoria Forge

Questo documento è una procedura leggera, non un obbligo di ripetere tutti i passaggi per ogni piccolo cambiamento. Serve per i lavori con rischio di revisioni costose: integrazioni native, installazioni, modifiche al mondo salvato, generazione di molte varianti e risultati visivi.

## Prima di produrre

1. Fissa il risultato osservabile, la versione/ambiente e le parti già approvate da preservare.
2. Cerca in Forge al massimo poche esperienze pertinenti, anche per analogia. Leggi i failure collegati e confronta soluzione preferita e ultima versione.
3. Formula il controllo minimo che può invalidare presto l'approccio: per esempio la firma dell'API installata, il contratto di un asset nel client, un singolo oggetto o una vista campione.
4. Se il risultato è visivo, separa la riuscita tecnica dall'accettazione estetica. Non produrre tutte le varianti prima del primo controllo visivo quando è praticabile.
5. Dopo un'approvazione, considera la parte approvata un riferimento da preservare; applica solo le modifiche richieste e verifica le regressioni mirate.

## Controlli iniziali ricavati dai failure

| Lavoro | Controllo iniziale utile | Evidenza |
| --- | --- | --- |
| Asset con più usi | Verificare separatamente icona, modello tenuto e modello piazzato, con dimensioni e percorsi accettati dal client. | `FAIL-000021`, `FAIL-000025`, `FAIL-000041` |
| Integrazione Hytale nativa | Ispezionare firme e comportamento della versione realmente installata prima di compilare o distribuire. | `FAIL-000026` |
| Mondo salvato | Preparare uno stato desiderato e un controllo su un piccolo campione prima della scrittura estesa; distinguere vecchi dettagli da nuovo terreno. | `FAIL-000028`, `FAIL-000032`, `FAIL-000033` |
| Immagini con aree protette | Applicare la generazione solo attraverso maschere verificabili; il prompt da solo non garantisce pixel invariati. | `FAIL-000029` |
| Effetti visivi | Validare nel client una prova rappresentativa; i test statici/server non attestano qualità visiva. | `FAIL-000016`, `FAIL-000024`, `FAIL-000038` |

Questi controlli sono esempi condizionati dal contesto, non regole universali. Per altri motori trasferire il principio e verificare le rispettive API.

## Come misurare il miglioramento

Per lavori significativi, registra solo dati osservabili e disponibili:

- identificativo del task, progetto, versione e criterio di accettazione;
- esperienze/failure/pattern consultati prima della soluzione;
- tempo trascorso, chiamate agli strumenti, revisioni e consumo di crediti **solo se misurati**;
- controlli iniziali eseguiti, fallimenti evitati o ripetuti;
- versione tecnicamente verificata e versione approvata dall'utente, tenendole distinte;
- fonte del feedback e limiti della verifica (per esempio client locale, ma non stabilità a lungo termine).

Le `metrics` di una Solution Version possono descrivere costo o prestazioni della soluzione tecnica: non assumerle come crediti dell'agente. Se mancano log affidabili, non stimare percentuali di risparmio. Dopo alcuni casi comparabili, confrontare numero di revisioni, tempo fino al primo risultato approvato e fallimenti ripetuti; attribuire causalità solo con cautela.
