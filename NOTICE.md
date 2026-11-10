# Fuentes de datos y licencias

El código de dosis y las tablas en `tools/` (`aliases_es.tsv`, `curated.tsv`, `curated_pairs.tsv`,
`tripsit_classes.tsv`) son propios de este proyecto.

El archivo generado `data/generated/interactions.tsv` **no se publica en este repositorio**; se
construye localmente con `tools/build_interactions.py` a partir de estas fuentes:

| Fuente | Uso | Licencia / condiciones |
|---|---|---|
| [DDInter 2.0](https://ddinter2.scbdd.com/) (Xiong et al., Nucleic Acids Research) | Pares de interacción medicamento–medicamento | CC BY-NC-SA 4.0: atribución, uso no comercial, compartir igual |
| [TripSit drugs](https://github.com/TripSit/drugs) (`combos.json`) | Combinaciones con drogas recreativas | El repositorio no declara licencia |
| [CIMA – AEMPS](https://cima.aemps.es/) | Nombres oficiales en español (ATC) y productos autorizados en España | Condiciones de reutilización de la AEMPS, con atribución |
| [INVIMA – datos.gov.co](https://www.datos.gov.co/) | Código Único de Medicamentos (Colombia) | Datos abiertos del Gobierno de Colombia |
| [RxNorm – NLM](https://www.nlm.nih.gov/research/umls/rxnorm/) | Contenido prescribible (sin licencia UMLS) | Dominio público / sin licencia requerida para este subconjunto |

**Aviso:** dosis es un diario personal y no reemplaza a un médico ni a un farmacéutico. Los avisos
de interacción son una referencia general y pueden estar incompletos.
