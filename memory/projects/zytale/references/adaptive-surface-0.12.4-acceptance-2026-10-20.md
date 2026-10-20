# Adaptive Surface 0.12.4 — user acceptance

- Date: 2026-10-20.
- Scope: installed Adaptive Surface 0.12.4 on Hytale 0.7.2, Windows, Java 25.
- Release SHA-256: `3744cb59d80ee42759954a4bac3d66e497ddee44f44a9d19e7c1505248f81d47` (installed JAR and distribution match).
- User feedback: the latest work succeeded, everything works; “Ottimo lavoro”. This is direct user acceptance of the installed result, not a separately instrumented client trace.
- Technical evidence: `ZytaleAdaptiveSurface/docs/VALIDATION-0.12.4.md` documents the client pose check, negative control, packaged geometry audit and isolated native boots.
- Forge lineage: `EXP-000001`, `SOL-000001:v013`. The 0.12.4 correction compensates the client yaw convention after the 0.12.3 isolation of cube transition rules.
- Limits: no instrumented GPU capture or long-duration large-world performance measurement; other Hytale client versions require revalidation.
