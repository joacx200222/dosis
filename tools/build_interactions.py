"""Builds data/generated/interactions.tsv, the interaction file bundled in every dosis app.

Sources:
  * DDInter 2.0 CSVs (ddinter_*.csv, CC BY-NC-SA 4.0): drug-drug pairs, plus "proxy" drugs
    (Ethanol, Caffeine, Nicotine, Amyl Nitrite, St. John's Wort...) used for substance rows.
  * TripSit combos.json (github.com/TripSit/drugs): recreational drug combinations.
  * tools/curated.tsv, tools/curated_pairs.tsv, tools/tripsit_classes.tsv, tools/aliases_es.tsv:
    dosis's own tables.

Usage:  python -I tools/build_interactions.py <raw_dir> <output.tsv>
Downloads (put them in raw_dir):
  https://ddinter2.scbdd.com/static/media/download/ddinter_downloads_code_{A,B,D,H,L,P,R,V}.csv
  https://raw.githubusercontent.com/TripSit/drugs/main/combos.json
  Optional, Spanish names: CIMA (AEMPS) ATC list, pages 1..37, saved as raw_dir/cima/atc_<page>.json:
  https://cima.aemps.es/cima/rest/maestras?maestra=7&codigo=A&pagina=<page>
  Optional, brands/products:
  - CIMA products, pages 1..128 -> raw_dir/cima/products/p_<page>.json
    https://cima.aemps.es/cima/rest/medicamentos?pagina=<page>
  - INVIMA (datos.gov.co) datasets i7cb-raxc, vgr4-gemg, vwwf-4ftk, spzp-dfuc -> raw_dir/invima/<id>_<offset>.json
    https://www.datos.gov.co/resource/<id>.json?$select=producto,principioactivo,atc
      &$group=producto,principioactivo,atc&$limit=50000&$offset=<offset>
  - RxNorm prescribable subset (no license) -> raw_dir/rxnorm/RXNCONSO.RRF and RXNREL.RRF
    https://download.nlm.nih.gov/rxnorm/RxNorm_full_prescribe_<MMDDYYYY>.zip

Output format (UTF-8, tab-separated, one record per line):
  #dosis-interactions  <version>  <date>
  S  <source code>  <label>                         sources
  D  <english>  <spanish or ''>  <aliases|..>  <generated|..>  <1 = common>  <brands/products|..>
     drugs; index = order of D lines. A product listed under several drugs is a combination.
  P  <a base36>  <items>   items = <delta base36><level>; level . unknown , minor ; moderate ! major
  Q  <drug a index>  <drug b index>  <level>  <source codes,>  <description>   curated pair (overrides P)
  X  <drug index>  <SUBSTANCE>  <level>  <source codes,>  <description>
"""
import csv
import datetime
import glob
import json
import os
import re
import sys
import unicodedata
from collections import defaultdict

HERE = os.path.dirname(os.path.abspath(__file__))
LEVELS = {"unknown": 0, "minor": 1, "moderate": 2, "major": 3}
LEVEL_CHAR = {0: ".", 1: ",", 2: ";", 3: "!"}
LEVEL_ES = {0: "sin gravedad indicada", 1: "leve", 2: "moderada", 3: "grave"}

SOURCES = [
    ("ddinter", "DDInter 2.0 (CC BY-NC-SA 4.0)"),
    ("tripsit", "TripSit"),
    ("dosis", "Tabla dosis"),
]

# Every Substance in the Kotlin enum, in order. Kept in sync with Interactions.kt.
SUBSTANCES = [
    "ALCOHOL", "TOBACCO",
    "CANNABIS", "COCAINE", "AMPHETAMINES", "MDMA", "MEPHEDRONE", "KETAMINE", "PCP", "MXE", "DXM",
    "GHB", "NITROUS", "POPPERS", "OPIOIDS", "BENZODIAZEPINES", "LSD", "MUSHROOMS", "DMT",
    "AYAHUASCA", "MESCALINE", "TWO_C", "TWO_C_T", "NBOME", "DOX", "FIVE_MEO", "AMT",
    "GRAPEFRUIT", "DAIRY", "CAFFEINE",
    "SUPPLEMENTS",
]

# DDInter drugs that stand in for a substance. (DDInter name, Spanish label)
PROXIES = {
    "ALCOHOL": [("Ethanol", "alcohol (etanol)")],
    "TOBACCO": [("Nicotine", "nicotina")],
    "CAFFEINE": [("Caffeine", "cafeína")],
    "DAIRY": [("Calcium carbonate", "calcio (como el de los lácteos)")],
    "COCAINE": [("Cocaine (nasal)", "cocaína"), ("Cocaine (topical)", "cocaína")],
    "CANNABIS": [("Dronabinol", "THC (dronabinol)"), ("Nabilone", "cannabinoide (nabilona)"),
                 ("Cannabidiol", "CBD")],
    "KETAMINE": [("Ketamine", "ketamina"), ("Esketamine", "esketamina")],
    "AMPHETAMINES": [("Amphetamine", "anfetamina"), ("Metamfetamine", "metanfetamina"),
                     ("Dextroamphetamine", "dextroanfetamina")],
    "DXM": [("Dextromethorphan", "dextrometorfano")],
    "GHB": [("Sodium oxybate", "oxibato sódico (GHB)")],
    "NITROUS": [("Nitrous oxide", "óxido nitroso")],
    "POPPERS": [("Amyl Nitrite", "nitrito de amilo")],
    "OPIOIDS": [("Diamorphine", "heroína"), ("Fentanyl", "fentanilo"), ("Morphine", "morfina")],
    "BENZODIAZEPINES": [("Alprazolam", "alprazolam"), ("Clonazepam", "clonazepam"), ("Diazepam", "diazepam")],
    "SUPPLEMENTS": [("St. John's Wort", "hierba de San Juan"), ("Ginkgo biloba", "ginkgo"),
                    ("Garlic", "ajo"), ("Ginseng", "ginseng"), ("Valerian", "valeriana"), ("Kava", "kava"),
                    ("Melatonin", "melatonina"), ("Echinacea", "equinácea"),
                    ("Omega-3 fatty acids", "omega 3"), ("Turmeric", "cúrcuma"), ("Ginger", "jengibre"),
                    ("Licorice", "regaliz"), ("Saw palmetto", "saw palmetto"), ("Glucosamine", "glucosamina"),
                    ("Yohimbine", "yohimbina"), ("Goldenseal", "sello de oro"), ("Milk thistle", "cardo mariano"),
                    ("Chamomile", "manzanilla"), ("Cranberry", "arándano rojo"), ("Aloe Vera Leaf", "aloe vera")],
}

# TripSit combos.json key -> Substance. AYAHUASCA = worst of DMT and MAOIs.
TRIPSIT = {
    "alcohol": "ALCOHOL", "cannabis": "CANNABIS", "cocaine": "COCAINE", "caffeine": "CAFFEINE",
    "amphetamines": "AMPHETAMINES", "mdma": "MDMA", "mephedrone": "MEPHEDRONE", "ketamine": "KETAMINE",
    "pcp": "PCP", "mxe": "MXE", "dextromethorphan": "DXM", "ghb/gbl": "GHB", "nitrous": "NITROUS",
    "opioids": "OPIOIDS", "benzodiazepines": "BENZODIAZEPINES", "lsd": "LSD", "mushrooms": "MUSHROOMS",
    "dmt": "DMT", "mescaline": "MESCALINE", "2c-x": "TWO_C", "2c-t-x": "TWO_C_T", "nbomes": "NBOME",
    "dox": "DOX", "5-meo-xxt": "FIVE_MEO", "amt": "AMT",
}
TRIPSIT_STATUS = {
    "Dangerous": (3, "la combinación es peligrosa"),
    "Unsafe": (3, "la combinación es insegura"),
    "Caution": (2, "úsala con precaución"),
    "Low Risk & Synergy": (2, "bajo riesgo, pero los efectos se potencian"),
    "Low Risk & No Synergy": (1, "bajo riesgo, sin potenciación"),
    "Low Risk & Decrease": (1, "bajo riesgo; los efectos pueden disminuir"),
}
TRIPSIT_GROUP_ES = {
    "ssris": "antidepresivos ISRS", "maois": "IMAO", "opioids": "opioides", "tramadol": "tramadol",
    "benzodiazepines": "benzodiacepinas", "lithium": "litio", "dextromethorphan": "dextrometorfano",
    "diphenhydramine": "difenhidramina", "pregabalin": "pregabalina", "amphetamines": "anfetaminas",
    "ketamine": "ketamina", "ghb/gbl": "GHB", "cannabis": "cannabis", "caffeine": "cafeína",
}


def norm(s):
    s = unicodedata.normalize("NFD", s.strip().lower())
    s = "".join(c for c in s if unicodedata.category(c) != "Mn")
    return re.sub(r"\s+", " ", s)


def b36(n):
    digits = "0123456789abcdefghijklmnopqrstuvwxyz"
    out = ""
    while True:
        n, r = divmod(n, 36)
        out = digits[r] + out
        if n == 0:
            return out


def spanish_word(w):
    """Rule-based guess of the Spanish INN for one English word (used only for matching)."""
    w = re.sub(r"ph", "f", w)
    w = re.sub(r"th", "t", w)
    w = re.sub(r"rh", "r", w)
    w = re.sub(r"ae|oe", "e", w)
    w = re.sub(r"y", "i", w)
    w = re.sub(r"k", "c", w)
    w = re.sub(r"chl", "cl", w)
    w = re.sub(r"(.)\1", r"\1", w)  # double letters
    if re.match(r"^s[bcdfgklmnpqrstvwxz]", w):
        w = "e" + w
    for end, rep in (("ine", "ina"), ("ole", "ol"), ("one", "ona"), ("ide", "ida"), ("ate", "ato"),
                     ("ane", "ano"), ("ene", "eno"), ("ase", "asa"), ("ose", "osa"), ("ium", "io"),
                     ("ac", "aco"), ("en", "eno"), ("in", "ina"), ("ic", "ico"), ("yl", "ilo")):
        if w.endswith(end):
            return w[: -len(end)] + rep
    return w


def spanish_guesses(english):
    base = re.sub(r"\s*\(.*?\)", "", norm(english)).strip()
    words = base.split()
    guesses = set()
    if len(words) == 2 and words[1] == "acid":
        guesses.add("acido " + spanish_word(words[0]))
    guesses.add(" ".join(spanish_word(w) for w in words))
    guesses.add(base)
    guesses.discard(norm(english))
    return sorted(g for g in guesses if g)


# English chemistry words -> Spanish, for salts like "Magnesium hydroxide" -> "magnesio hidroxido".
SALT_WORDS = {
    "magnesium": "magnesio", "calcium": "calcio", "sodium": "sodio", "potassium": "potasio",
    "aluminum": "aluminio", "aluminium": "aluminio", "iron": "hierro", "ferrous": "ferroso", "ferric": "ferrico",
    "zinc": "zinc", "lithium": "litio", "hydroxide": "hidroxido", "carbonate": "carbonato", "bicarbonate": "bicarbonato",
    "chloride": "cloruro", "sulfate": "sulfato", "citrate": "citrato", "oxide": "oxido", "acetate": "acetato",
    "gluconate": "gluconato", "lactate": "lactato", "phosphate": "fosfato", "hydrochloride": "hidrocloruro",
    "fumarate": "fumarato", "anhydrous": "", "succinate": "succinato", "tartrate": "tartrato", "maleate": "maleato",
    "mesylate": "mesilato", "bromide": "bromuro", "iodide": "yoduro", "nitrate": "nitrato", "acid": "acido",
}

JUNK = re.compile(r"combinac|\bvarios\b|\botros\b|asociac|\bcon\b|derivados|preparados|agentes|extracto", re.I)

# Salt / form words at the end of a CIMA name: "Metamizol sodico" is the same medicine as "Metamizol".
SALT_SUFFIX = re.compile(
    r"\s+(sodico|sodica|potasico|potasica|calcico|calcica|magnesico|clorhidrato|hidrocloruro|bromhidrato|"
    r"mesilato|maleato|fumarato|succinato|tartrato|besilato|medoxomilo|dipropionato|monohidrato|dihidrato|"
    r"trihidrato|anhidro)$"
)
# "Valsartan, amlodipino e hidroclorotiazida" -> three ingredients.
COMBO_SPLIT = re.compile(r",\s*|\s+e\s+|\s+y\s+")


def spanish_variants(english):
    """Candidate Spanish spellings of an English INN, used only for EXACT matching against CIMA."""
    import itertools
    base = re.sub(r"\s*\(.*?\)", "", norm(english)).strip()
    words = [w for w in base.replace(",", " ").split() if w]
    if not words or len(words) > 3:
        return set()
    opts = []
    for w in words:
        o = {w, spanish_word(w), w + "o", w + "a", spanish_word(w) + "o"}
        if w in SALT_WORDS:
            o = {SALT_WORDS[w]}
        opts.append([x for x in o if x is not None])
    out = set()
    for combo in itertools.product(*opts):
        ws = [c for c in combo if c]
        if not ws:
            continue
        out.add(" ".join(ws))
        if len(ws) == 2:
            out.add(f"{ws[1]} {ws[0]}")          # magnesio hidroxido / hidroxido magnesio
            out.add(f"{ws[1]} de {ws[0]}")       # hidroxido de magnesio
            if ws[1] == "acido":
                out.add(f"acido {ws[0]}")        # valproic acid -> acido valproico
    return out


def official_spanish(raw_dir, drugs, manual):
    """Spanish INN names from CIMA (AEMPS) ATC list -> display names, extra aliases and new drugs.

    Returns (display, extra, new): display[english] = Spanish name for drugs without a manual name,
    extra[english] = more Spanish aliases, new = CIMA ingredients DDInter does not have (no pairs).
    """
    import difflib
    files = glob.glob(os.path.join(raw_dir, "cima", "atc_*.json"))
    if not files:
        return {}, {}, []
    cima = {}
    for f in files:
        with open(f, encoding="utf-8") as fh:
            for r in json.load(fh)["resultados"]:
                name = r["nombre"].strip()
                if len(r["codigo"]) == 7 and not JUNK.search(name):
                    cima.setdefault(norm(name), name[0].upper() + name[1:])
    keys = list(cima)
    display, extra, used = {}, defaultdict(list), set()
    known = {}  # every name we already resolve -> english drug
    for d in drugs:
        names_d = {norm(d)} | set(spanish_guesses(d)) | spanish_variants(d)
        if d in manual:
            names_d |= {norm(manual[d][0])} | {norm(a) for a in manual[d][1]}
        for n in names_d:
            known.setdefault(n, d)
        hit = next((n for n in sorted(names_d) if n in cima), None)
        if hit is None and " " not in norm(d):
            g = spanish_word(norm(d))
            m = difflib.get_close_matches(g, keys, n=1, cutoff=0.9)
            # Only endings may differ (ibuprofen ~ ibuprofeno), never the middle (pentamidina != pentamicina).
            if m and " " not in m[0] and len(os.path.commonprefix([m[0], g])) >= max(len(m[0]), len(g)) - 3:
                hit = m[0]
        if hit:
            used.add(hit)
            if d in manual and manual[d][0]:
                if norm(manual[d][0]) != hit:
                    extra[d].append(cima[hit])
            else:
                display[d] = cima[hit]
    def resolve(n):
        if n in known:
            return known[n]
        stripped = SALT_SUFFIX.sub("", n).strip()
        return known.get(stripped) if stripped != n else None

    new = []
    for n, name in cima.items():
        if n in used:
            continue
        parts = [x.strip() for x in COMBO_SPLIT.split(n) if x.strip()]
        if len(parts) > 1:
            # A combination becomes ONE product (alias on each ingredient), like "Acriptega".
            # Inverted names we cannot resolve ("Acriflavinio, cloruro de") are dropped.
            ds = [resolve(x) for x in parts]
            if all(ds) and len(set(ds)) > 1:
                for d in set(ds):
                    extra[d].append(name)
            continue
        d = resolve(n)
        if d:
            extra[d].append(name)
        else:
            new.append(name)
    return display, extra, sorted(set(new))


# ---------------------------------------------------------------------------------------------
# Products / brands
# ---------------------------------------------------------------------------------------------
# Words that end a product's brand part ("FOSAMAX SEMANAL 70 mg COMPRIMIDOS" -> "Fosamax Semanal").
FORM_CUT = re.compile(
    r"\b(comprimidos?|capsulas?|tabletas?|grageas?|solucion|soliucion|suspension|jarabe|inyectables?|inyeccion|crema|gel|"
    r"pomada|unguento|polvo|granulado|sobres?|gotas|parches?|ampollas?|viales?|recubiert[oa]s?|efervescentes?|"
    r"dispersables?|liberacion|bucodispersables?|masticables?|pelicula|emulsion|locion|champu|spray|aerosol|"
    r"inhalador|colirio|ovulos?|supositorios?|jeringa|perfusion|equipo|kit|polvo|oral|topic[oa]|oftalmic[oa]|"
    r"nasal|vaginal|rectal|transdermic[oa]s?|esteril|para|con|en|efg|usp|bp|tableta|capsula|crema)\b.*$"
)
LEADING_FORM = re.compile(r"^(pomada|crema|gel|jarabe|solucion|tabletas?|capsulas?|gotas)\s+")
SALT_LEAD = re.compile(
    r"^(tartrato|clorhidrato|clohidrato|hidrocloruro|citrato|sulfato|maleato|fosfato|acetato|succinato|fumarato|"
    r"besilato|mesilato|bromuro|cloruro|nitrato|dinitrato|mononitrato|gluconato|lactato|carbonato|oxido|"
    r"bromhidrato|diclorhidrato|valerato|propionato|dipropionato|furoato|pivalato|sodio|calcio|potasio|magnesio)\s+de\s+"
)
SALT_TAIL = re.compile(
    r"\s+(sodic[oa]|potasic[oa]|calcic[oa]|magnesic[oa]|clorhidrato|clohidrato|diclorhidrato|hidrocloruro|"
    r"bromhidrato|mesilato|maleato|fumarato|succinato|tartrato|besilato|medoxomilo|dipropionato|propionato|"
    r"acetato|sulfato|citrato|fosfato|monohidrato|dihidrato|trihidrato|hemihidrato|anhidr[oa]|base|usp|bp|"
    r"micronizad[oa]|diluid[oa].*|sodium|potassium|calcium|hydrochloride|sulfate|citrate|maleate|"
    r"mesylate|tartrate|succinate|fumarate|acetate|monohydrate|anhydrous|ep|nf|ph eur|hcl)$"
)
GENERIC_WORDS = {"acido", "sodio", "calcio", "potasio", "magnesio", "cloruro", "vitamina", "extracto", "aceite",
                 "agua", "sal", "sales", "complejo", "acid", "sodium", "calcium", "potassium", "vitamin"}
# Excipients / vehicles that INVIMA sometimes lists as if they were active ingredients.
EXCIPIENT = re.compile(
    r"^(agua|solucion|base|vehiculo|excipiente|lauril|sacarina|metano|siliconas?|alcohol|glicerina|glicerol|"
    r"sorbitol|sacarosa|lactosa|almidon|talco|parafina|vaselina|lanolina|propilenglicol|polietilenglicol|"
    r"esencia|saborizante|colorante|conservante|aroma|perfume)\b"
)
# Form words glued to the brand ("Bisartan Tabletaslosartanpotasico").
GLUED_FORM = re.compile(r"(tabletas?|capsulas?|recubiertas?|solucion|jarabe|suspension|inyectable)")


def ingredient_candidates(text):
    """Normalized spellings to try for one messy ingredient text (INVIMA / CIMA / RxNorm)."""
    t = norm(text)
    t = re.sub(r"^capa\s*\d+\s*:\s*", "", t)
    # "CARBONATO DE CALCIO EQUIVALENTE A CALCIO": the real salt (before) first, the moiety (after) second.
    m = re.search(r"equi?valente?\s*a\s*(.*)$", t)
    parts = [t[: m.start()], m.group(1)] if m else [t]
    # "1.5 G DE CARBONATO DE CALCIO", "0.12G DE PREMEZCLA ... QUE CONTIENE OXIDO DE ZINC"
    parts = [re.sub(r"^.*\bque contiene\s+", "", re.sub(r"^[\d.,\s]*(mg|mcg|g|ui|ml)?\s*de\s+", "", p)) for p in parts]
    out = []
    for p in parts:
        p = re.sub(r"\(.*?\)", " ", p)
        p = re.split(r"[\d%>]", p)[0]                # cut at doses
        # "ESPORAS DE BACILLUS CLAUSII RESISTENTE A POLIANTIBIOTICOS" -> "bacillus clausii"
        p = re.sub(r"^(esporas|cepas?|liofilizado|polvo|extracto seco|cultivo)\s+(de\s+)?", "", p)
        p = re.split(r"\s+(resistente|liofilizad|vivos?|inactivad|atenuad)", p)[0]
        p = re.sub(r"[^a-z\s-]", " ", p)
        p = re.sub(r"\s+", " ", p).strip(" -")
        for _ in range(3):
            q = SALT_TAIL.sub("", p).strip()
            if q == p:
                break
            p = q
        if not p:
            continue
        out.append(p)
        if p.startswith("n-"):          # "n-acetilcisteina" -> "acetilcisteina"
            out.append(p[2:])
        lead = SALT_LEAD.sub("", p)
        if lead != p:
            out.append(lead)
    # A bare generic word ("calcio", "magnesio", "vitamina") never identifies one medicine.
    return [o for o in dict.fromkeys(out) if len(o) >= 3 and o not in GENERIC_WORDS]


def clean_product(name):
    """Brand part of a product name in Title Case, or None if nothing usable is left."""
    s = norm(name).replace("®", " ").replace("™", " ").replace("©", " ")
    s = re.split(r"\d", s)[0]
    s = LEADING_FORM.sub("", s)
    s = FORM_CUT.sub("", s)
    s = GLUED_FORM.split(s)[0]
    s = re.sub(r"[^a-z0-9\s.+/-]", " ", s)
    s = re.sub(r"\s*-\s*", "-", s)
    s = re.sub(r"\s+", " ", s).strip(" .-/+")
    if len(s) < 3 or not re.search(r"[a-z]{2}", s):
        return None
    return " ".join(w.upper() if ("." in w and len(w) <= 6) else w.capitalize() for w in s.split())


def import_products(raw_dir, drugs, es):
    """Adds brand names as aliases on their ingredients (combinations on every ingredient).

    Sources (all optional, in raw_dir): invima/*.json (Colombia, datos.gov.co), cima/products/*.json
    (Spain), rxnorm/RXNCONSO.RRF + RXNREL.RRF (United States, prescribable subset).
    Ingredients nobody knows yet become new medicines (without interaction pairs).
    Mutates es; returns (drugs, stats).
    """
    drugs = set(drugs)
    known = {}

    def learn(d):
        sp, al = es.get(d, ("", []))
        for n in [norm(d), norm(sp)] + [norm(a) for a in al] + spanish_guesses(d) + sorted(spanish_variants(d)):
            if n:
                known.setdefault(n, d)
                known.setdefault(SALT_TAIL.sub("", n).strip(), d)

    for d in sorted(drugs):
        learn(d)

    # ATC code -> official Spanish name (CIMA), used first for INVIMA rows.
    atc_name = {}
    for f in glob.glob(os.path.join(raw_dir, "cima", "atc_*.json")):
        with open(f, encoding="utf-8") as fh:
            for r in json.load(fh)["resultados"]:
                if len(r["codigo"]) == 7:
                    atc_name[r["codigo"]] = r["nombre"]

    new_ingredients = {}

    def resolve(text, create=True, english=False):
        cands = ingredient_candidates(text)
        for c in cands:
            if c in known:
                return known[c]
        if not cands or not create:
            return None
        c = cands[-1] if len(cands) > 1 and not english else cands[0]
        words = c.split()
        if not words or words[0] in GENERIC_WORDS and len(words) == 1 or len(c) > 45 or EXCIPIENT.match(c):
            return None
        name = " ".join(w.capitalize() for w in words)
        d = new_ingredients.setdefault(c, name)
        if d not in drugs:
            drugs.add(d)
            es.setdefault(d, (name if not english else "", []))
            learn(d)
        return d

    products = {}  # normalized brand -> (display, set(drugs), source rank)

    def add_product(raw_name, ingredient_drugs, rank):
        brand = clean_product(raw_name)
        # Every ingredient must be known: a half-resolved combination would hide interactions.
        if not brand or not ingredient_drugs or None in ingredient_drugs or len(ingredient_drugs) > 6:
            return
        ds = set(ingredient_drugs)
        nb = norm(brand)
        tokens = re.findall(r"[a-z]+", nb)
        if not tokens:
            return
        first = tokens[0]
        # Generic products ("Levetiracetam 500 mg", "Ibuprofeno Cinfa", "Acetaminofen-clorfeniramina")
        # are the ingredient itself, not a brand.
        if first in GENERIC_WORDS or first in known or nb in known:
            return
        if any(len(t) >= 5 and known.get(t) in ds for t in tokens):  # "Adorem Acetaminofen"
            return
        flat = nb.replace(" ", "").replace("-", "")
        for d in ds:
            for n in (norm(d), norm(es.get(d, ("", []))[0])):
                n0 = n.split(" ")[0] if n else ""
                if len(n0) >= 5 and (n0 in flat or first.startswith(n0[:6])):
                    return
        prev = products.get(nb)
        if prev is None or rank < prev[2]:
            products[nb] = (brand, ds, rank)

    # INVIMA (Colombia): one row per product x ingredient, with ATC.
    rows = []
    for f in sorted(glob.glob(os.path.join(raw_dir, "invima", "*.json"))):
        with open(f, encoding="utf-8") as fh:
            for r in json.load(fh):
                prod, pa, atc = r.get("producto"), r.get("principioactivo"), (r.get("atc") or "").strip().upper()
                if prod and pa:
                    rows.append((prod, pa, atc))
    # An unknown INVIMA ingredient only becomes a medicine if 3+ products use it (filters typos).
    seen_in = defaultdict(set)
    for prod, pa, _ in rows:
        c = ingredient_candidates(pa)
        if c:
            seen_in[c[-1]].add(prod)
    inv = defaultdict(set)
    for prod, pa, atc in rows:
        d = resolve(pa, create=False)
        if d is None and len(atc) == 7 and atc[5:] < "50" and atc in atc_name:
            # Single-ingredient ATC, but only if its name agrees with the text: INVIMA sometimes has the
            # wrong code (Enterogermina = Bacillus clausii is filed as A07FA03 "Escherichia coli").
            atc_words = {w for w in re.findall(r"[a-z]{4,}", norm(atc_name[atc]))}
            pa_words = {w for w in re.findall(r"[a-z]{4,}", norm(pa))}
            if atc_words & pa_words:
                d = resolve(atc_name[atc], create=False)
        if d is None:
            c = ingredient_candidates(pa)
            if c and len(seen_in[c[-1]]) >= 3:
                d = resolve(pa)
        inv[prod].add(d)
    for prod, ds in inv.items():
        add_product(prod, ds, 1)

    # CIMA (Spain): "vtm" holds the ingredient(s), "a + b".
    for f in sorted(glob.glob(os.path.join(raw_dir, "cima", "products", "*.json"))):
        with open(f, encoding="utf-8") as fh:
            for r in json.load(fh)["resultados"]:
                vtm = (r.get("vtm") or {}).get("nombre")
                if not vtm:
                    continue
                ds = {resolve(x) for x in re.split(r"\s*\+\s*", vtm)}
                add_product(r["nombre"], ds, 2)

    # RxNorm (US): every ingredient (IN) and brand (BN) with its ingredients.
    conso = os.path.join(raw_dir, "rxnorm", "RXNCONSO.RRF")
    rel = os.path.join(raw_dir, "rxnorm", "RXNREL.RRF")
    if os.path.exists(conso) and os.path.exists(rel):
        ing, brand = {}, {}
        with open(conso, encoding="utf-8") as fh:
            for line in fh:
                p = line.split("|")
                if p[11] != "RXNORM":
                    continue
                if p[12] == "IN":
                    ing.setdefault(p[0], p[14])
                elif p[12] == "BN":
                    brand.setdefault(p[0], p[14])
        # Allergen extracts (pollen, dander, foods for skin tests) are not medicines people log,
        # so they only enter if an existing name already covers them.
        allergen = re.compile(r"allergenic|pollen|extract|dander|venom|feather|hair\b|mold\b|smut|mite", re.I)
        in_drug = {cui: resolve(name, create=not allergen.search(name), english=True) for cui, name in ing.items()}
        bn_ings = defaultdict(set)
        with open(rel, encoding="utf-8") as fh:
            for line in fh:
                p = line.split("|", 9)
                if p[7] not in ("has_tradename", "tradename_of"):
                    continue
                a, b = p[0], p[4]
                if a in brand and b in in_drug:
                    bn_ings[a].add(in_drug[b])
                elif b in brand and a in in_drug:
                    bn_ings[b].add(in_drug[a])
        for cui, ds in bn_ings.items():
            add_product(brand[cui], ds, 3)

    # Attach: a product is listed on each of its ingredients (several = combination product).
    # Kept apart from the hand-written/official names so search can rank brands after them.
    product_of = defaultdict(list)
    for nb, (display, ds, _) in sorted(products.items()):
        for d in ds:
            sp, al = es.get(d, ("", []))
            if nb != norm(sp) and nb != norm(d) and nb not in {norm(x) for x in al[:60]}:
                product_of[d].append(display)
    stats = {"products": len(products), "new_ingredients": len(new_ingredients),
             "combos": sum(1 for v in products.values() if len(v[1]) > 1)}
    return sorted(drugs), product_of, stats


def read_tsv(name, cols):
    rows = []
    with open(os.path.join(HERE, name), encoding="utf-8") as f:
        for line in f:
            line = line.rstrip("\n")
            if not line.strip() or line.startswith("#"):
                continue
            parts = line.split("\t")
            rows.append(parts + [""] * (cols - len(parts)))
    return rows


def main(raw_dir, out_path):
    # --- DDInter ---
    pairs = {}
    names = set()
    for path in sorted(glob.glob(os.path.join(raw_dir, "ddinter_*.csv"))):
        with open(path, encoding="utf-8", newline="") as f:
            for r in csv.DictReader(f):
                a, b = r["Drug_A"].strip(), r["Drug_B"].strip()
                level = LEVELS.get(r["Level"].strip().lower())
                if level is None or a == b:
                    continue
                names.update((a, b))
                k = (a, b) if a < b else (b, a)
                pairs[k] = max(pairs.get(k, -1), level)
    if not pairs:
        sys.exit("No DDInter CSVs found in " + raw_dir)

    aliases = read_tsv("aliases_es.tsv", 3)
    missing = [r[0] for r in aliases if r[0] not in names]
    es = {r[0]: (r[1], [a for a in r[2].split("|") if a]) for r in aliases}
    popular = {r[0] for r in aliases if r[1]}  # hand-curated common medicines rank first in search
    # Official Spanish names (CIMA / AEMPS ATC list in raw_dir/cima/, optional).
    known_drugs = sorted(names | {r[0] for r in aliases})
    display, extra_es, new_es = official_spanish(raw_dir, known_drugs, es)
    for d, name in display.items():
        es[d] = (name, extra_es.get(d, []))
    for d, more in extra_es.items():
        if d not in display:
            sp, al = es.get(d, ("", []))
            es[d] = (sp, al + [m for m in more if norm(m) not in {norm(x) for x in al} and norm(m) != norm(sp)])
    for name in new_es:
        es.setdefault(name, (name, []))
    drugs = sorted(set(known_drugs) | set(new_es))
    # Brands and products (INVIMA, CIMA, RxNorm): aliases on their ingredients, new ingredients if unknown.
    drugs, product_of, product_stats = import_products(raw_dir, drugs, es)
    index = {d: i for i, d in enumerate(drugs)}

    # --- substance candidates: (drug, SUBSTANCE) -> list of (level, source, text) ---
    cand = defaultdict(list)

    def ddinter_level(x, y):
        return pairs.get((x, y) if x < y else (y, x))

    for sub, proxies in PROXIES.items():
        for drug in drugs:
            hits = []
            for proxy, label in proxies:
                if proxy == drug or proxy not in names:
                    continue
                lvl = ddinter_level(drug, proxy)
                if lvl is not None:
                    hits.append((lvl, label))
            if not hits:
                continue
            best = {}
            for lvl, label in hits:
                best[label] = max(best.get(label, -1), lvl)
            ordered = sorted(best.items(), key=lambda kv: -kv[1])
            worst = ordered[0][1]
            if len(ordered) == 1:
                text = f"DDInter: interacción {LEVEL_ES[worst]} con {ordered[0][0]}."
            else:
                text = "DDInter: " + ", ".join(f"{lab} ({LEVEL_ES[l]})" for lab, l in ordered[:6]) + "."
            cand[(drug, sub)].append((max(worst, 2) if worst == 0 else worst, "ddinter", text))

    # TripSit, via medicine groups
    with open(os.path.join(raw_dir, "combos.json"), encoding="utf-8") as f:
        combos = json.load(f)

    def tripsit(group, key):
        v = combos.get(group, {}).get(key) or combos.get(key, {}).get(group)
        if not v or v.get("status") not in TRIPSIT_STATUS:
            return None
        lvl, txt = TRIPSIT_STATUS[v["status"]]
        return lvl, txt, (v.get("note") or "").strip()

    classes = read_tsv("tripsit_classes.tsv", 3)
    unknown_class = [r[0] for r in classes if r[0] not in index]
    for drug, group, approx in classes:
        if drug not in index:
            continue
        ref = f" (referencia: datos de {TRIPSIT_GROUP_ES.get(group, group)})" if approx == "approx" else ""
        for key, sub in TRIPSIT.items():
            if key == group:
                continue
            hit = tripsit(group, key)
            if hit:
                lvl, txt, note = hit
                note = f' TripSit dice: "{note[:220]}"' if note else ""
                cand[(drug, sub)].append((lvl, "tripsit", f"TripSit: {txt}{ref}.{note}"))
        # Ayahuasca = DMT + MAOI
        aya = [h for h in (tripsit(group, "dmt"), tripsit(group, "maois")) if h]
        if aya and group not in ("maois",):
            lvl = max(h[0] for h in aya)
            cand[(drug, "AYAHUASCA")].append(
                (lvl, "tripsit", f"TripSit (DMT + IMAO): {TRIPSIT_STATUS_TEXT(lvl)}{ref}."))

    # Curated
    curated = read_tsv("curated.tsv", 4)
    unknown_cur = set()
    for ingredients, subs, level, text in curated:
        for sub in subs.split("|"):
            if sub not in SUBSTANCES:
                sys.exit(f"Unknown substance {sub} in curated.tsv")
            for drug in ingredients.split("|"):
                drug = drug.strip()
                if drug not in index:
                    unknown_cur.add(drug)
                    continue
                cand[(drug, sub)].append((LEVELS[level], "dosis", text))

    # Curated drug-drug rules
    groups, rules = {}, []
    for row in read_tsv("curated_pairs.tsv", 4):
        if row[0].startswith("@") and not row[2]:
            groups[row[0]] = row[1]
        else:
            rules.append(row)

    def expand(spec, seen=()):
        out = set()
        for part in spec.split("|"):
            part = part.strip()
            if part.startswith("@"):
                if part not in groups or part in seen:
                    sys.exit(f"Unknown or recursive group {part} in curated_pairs.tsv")
                out |= expand(groups[part], seen + (part,))
            elif part:
                out.add(part)
        return out

    curated_pairs = {}
    unknown_pairs = set()
    for a_spec, b_spec, level, text in rules:
        lvl = LEVELS[level]
        for a in expand(a_spec):
            for b in expand(b_spec):
                if a == b:
                    continue
                if a not in index or b not in index:
                    unknown_pairs.update(x for x in (a, b) if x not in index)
                    continue
                k = (a, b) if a < b else (b, a)
                prev = curated_pairs.get(k)
                if prev is None or lvl > prev[0]:
                    curated_pairs[k] = (lvl, text)

    # --- write ---
    os.makedirs(os.path.dirname(os.path.abspath(out_path)), exist_ok=True)
    by_a = defaultdict(list)
    for (a, b), lvl in pairs.items():
        ia, ib = index[a], index[b]
        if ia > ib:
            ia, ib = ib, ia
        by_a[ia].append((ib, lvl))

    lines = [f"#dosis-interactions\t1\t{datetime.date.today().isoformat()}"]
    lines += [f"S\t{c}\t{label}" for c, label in SOURCES]
    for d in drugs:
        spanish, extra = es.get(d, ("", []))
        known = {norm(d), norm(spanish)} | {norm(x) for x in extra}
        guesses = [g for g in spanish_guesses(d) if g not in known]
        prods = [p.replace("|", "/").replace("\t", " ") for p in product_of.get(d, [])]
        lines.append("\t".join(["D", d, spanish, "|".join(extra), "|".join(guesses), "1" if d in popular else "",
                                "|".join(prods)]))
    for ia in sorted(by_a):
        items, prev = [], ia
        for ib, lvl in sorted(by_a[ia]):
            items.append(b36(ib - prev) + LEVEL_CHAR[lvl])
            prev = ib
        lines.append(f"P\t{b36(ia)}\t{''.join(items)}")
    filled = 0
    for (a, b), (lvl, text) in sorted(curated_pairs.items()):
        dd = pairs.get((a, b))
        if dd is None:
            filled += 1
        final = max(lvl, dd or 0)
        srcs = "dosis" if dd is None else "ddinter,dosis"
        desc = text if dd is None else f"{text} · DDInter: {LEVEL_ES[dd]}."
        ia, ib = sorted((index[a], index[b]))
        lines.append("	".join(["Q", str(ia), str(ib), LEVEL_CHAR[final], srcs, desc]))
    sub_rows = 0
    for (drug, sub), items in sorted(cand.items(), key=lambda kv: (index[kv[0][0]], SUBSTANCES.index(kv[0][1]))):
        worst = max(l for l, _, _ in items)
        srcs = sorted({s for _, s, _ in items}, key=[c for c, _ in SOURCES].index)
        texts = []
        for l, _, t in sorted(items, key=lambda x: -x[0]):
            if t not in texts:
                texts.append(t)
        desc = " · ".join(texts[:3]).replace("\t", " ").replace("\n", " ")
        lines.append("\t".join(["X", str(index[drug]), sub, LEVEL_CHAR[worst], ",".join(srcs), desc]))
        sub_rows += 1

    with open(out_path, "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(lines) + "\n")

    print(f"products: {product_stats}")
    print(f"spanish names: official={len(display)} new_without_pairs={len(new_es)} "
          f"with_spanish={sum(1 for d in drugs if es.get(d, ('',))[0])}")
    print(f"drugs={len(drugs)} pairs={len(pairs)} curated_pairs={len(curated_pairs)} (new={filled}) "
          f"substance_rows={sub_rows} size={os.path.getsize(out_path)}")
    if unknown_pairs:
        print("curated_pairs.tsv names not found:", ", ".join(sorted(unknown_pairs)))
    if missing:
        print("aliases_es.tsv names not in DDInter (added without pairs):", ", ".join(missing))
    if unknown_class:
        print("tripsit_classes.tsv names not found:", ", ".join(unknown_class))
    if unknown_cur:
        print("curated.tsv names not found:", ", ".join(sorted(unknown_cur)))


def TRIPSIT_STATUS_TEXT(level):
    return {3: "combinación peligrosa", 2: "úsala con precaución", 1: "bajo riesgo"}.get(level, "sin datos")


if __name__ == "__main__":
    if len(sys.argv) != 3:
        sys.exit(__doc__)
    main(sys.argv[1], sys.argv[2])
