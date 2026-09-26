# Data model notes

Gli schemi eseguibili sono in `forge/core/models.py`; questo file descrive le scelte principali.

- I campi essenziali restano pochi (`id`, `title` per Experience) per tollerare conoscenza parziale.
- Le liste hanno default vuoti; confidence è limitata a 0–1.
- `extra="allow"` consente estensioni future senza rendere illeggibili file vecchi.
- Date e ore sono ISO 8601 con timezone nel JSON.
- Solution Version vive sia dentro la capsule, per lettura completa, sia in `solutions/`, per ispezione puntuale.
- Failure vive nell’indice globale e nella directory dell’esperienza; la copia globale è la fonte letta dal repository.
- Il Markdown è una vista sintetica rigenerata, mentre JSON è la rappresentazione strutturata canonica.

