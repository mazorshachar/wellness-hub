# Supplement interactions: what the evidence actually supports

Research pass behind the interaction warnings in the Supplements tab. The short
version: **most of what circulates about supplement interactions doesn't hold
up**, and the handful that do are mostly fixed by moving one dose two hours.

Sources are NIH Office of Dietary Supplements, the Linus Pauling Institute
Micronutrient Information Center, IOM/NASEM Dietary Reference Intakes, EFSA and
Cochrane. No vendor material was used, which matters more here than usual —
several of the most-repeated claims trace back to companies selling the pill.

---

## The structural insight

**Reduced absorption is not the same as harm.** The gut adapts. Calcium versus
iron is the cleanest example: a genuine 74% hit to absorption in a single meal
that produces **no measurable change in ferritin or haemoglobin** over 12 weeks
of supplementation.

Warn on acute absorption data alone and you generate a wall of noise that
teaches the user to ignore the app. So warnings are tiered by whether they
describe a real-world consequence, and each one carries its evidence grade.

---

## Tier 1 — cumulative, no timing fix

| Interaction | Threshold | Why it matters | Evidence |
|---|---|---|---|
| **Zinc → copper** | ≥40 mg/day zinc, sustained | Zinc induces a gut protein that binds copper and carries it out. Severe cases cause myeloneuropathy that **does not fully reverse**. The most common way a stack causes real harm. | Strong |
| **Vitamin E → vitamin K** | >400 IU/day | Interferes with vitamin K–dependent clotting. Large trials found more haemorrhagic stroke. Compounds badly with aspirin or anticoagulants. | Moderate–strong |
| **Folic acid masking B12** | >1000 µg/day without B12 | Not an absorption effect. Folate fixes the anaemia of B12 deficiency while neurological damage continues undetected. | Moderate |

Spacing doses does nothing for these — they're systemic or cumulative.

Also worth knowing, though not implemented as pairwise rules: **beta-carotene
≥20 mg/day is a contraindication in smokers**, not a caution (+16% to +28% lung
cancer across two large trials).

---

## Tier 2 — real, and fixed by separating doses

| Interaction | Threshold | Effect | Fix | Evidence |
|---|---|---|---|---|
| **Calcium ↔ iron** | ≥300 mg calcium in the same dose | 37–74% less iron absorbed, acutely | Two hours apart | Strong acute, weak for iron status |
| **Iron ↔ zinc** | ≥25 mg iron, **fasting only** | 56% less iron at a 5:1 zinc:iron ratio in water | Two hours apart | Moderate |
| **Calcium dose size** | ≥600 mg in one go | Absorption falls from ~36% at 300 mg to ~28% at 1000 mg | Split into ≤500 mg doses | Strong |
| **Iron ↔ manganese** | Any | Direct competition on the shared transporter | Separate | Moderate, low practical impact |

The iron ↔ zinc rule is conditional on an empty stomach because the effect
**vanishes entirely with a meal** — the same doses that cut absorption 56% in
water did nothing when given with a hamburger. That's why the app tracks whether
a dose slot is with food.

Not implemented as pill rules but worth knowing: tea, coffee and cocoa cut
non-heme iron absorption 50–90%, and phytate from bran or soy cuts it ~50% at
trivial doses. A one-hour gap substantially fixes both.

---

## The green ones — genuinely support each other

Only four survived. Every one is a **co-ingestion** effect: it requires the two
things in the same dose, not merely both in your cupboard.

| Pair | Effect | Condition | Evidence |
|---|---|---|---|
| **Vitamin C + iron** | +5.87 absolute percentage points absorbed | Same dose. Mainly for plant iron and phytate-heavy meals — trials adding vitamin C to a ferrous tablet found little benefit | Strong |
| **A/D/E/K + dietary fat** | +32% peak plasma vitamin D3 | ~3–5 g of fat in the meal is enough; more doesn't scale | Strong |
| **Vitamin D + calcium** | Required for active transport | A requirement, not a dial — above sufficiency, more D adds nothing | Strong |
| **Folate + B12** | −25% homocysteine from folate, −7% more with B12; plus the masking safety issue | Together | Strong for the biomarker; Cochrane finds no cardiovascular benefit |

The fat one is the highest-value of these — it applies to a large share of
stacks and the required action is trivially small.

---

## What was deliberately left out

This list is in the app itself, because otherwise the absence looks like an
oversight.

**Calcium ↔ magnesium.** Possibly the most heavily marketed interaction there
is, including the "2:1 ratio" idea. High calcium has not consistently affected
magnesium balance in human studies.

**Vitamin C destroys B12.** A 1970s finding, refuted in 1980 when it turned out
the B12 was fully recoverable once the extraction method was corrected — the
loss was an artefact of the assay. The ODS B12 fact sheet doesn't list vitamin C
as an interaction at all.

**Calcium ↔ zinc.** Human results contradict each other depending on the calcium
salt used. No reliable effect.

**Zinc ↔ folate.** 800 µg folic acid for 25 days changed neither zinc absorption
nor zinc status, even on a deliberately low-zinc diet.

**Magnesium ↔ iron.** No human absorption studies exist. The claim traces to
magnesium hydroxide antacids in overdose treatment — a pH effect in a poisoning
context, not a supplement interaction. Searching for it returns almost
exclusively vendor blogs.

**"Vitamin D is dangerous without K2."** The flagship RCT testing whether K2
slows arterial calcification found that it didn't, and subsequent meta-analyses
agree. The "K2 directs calcium to bone instead of arteries" story is an
extrapolation from two separate proteins, not a measured redistribution.

**Turmeric + black pepper.** The famous "2000% bioavailability" figure is a
single 1998 study, never independently replicated at that magnitude, measured
against a baseline where curcumin alone was undetectable — so it's ×20 of
almost nothing. It's co-authored by someone associated with the patent holder
for the pepper extract, and it's the lead citation on that product's marketing
site. A 2025 pharmacokinetic reappraisal found piperine gave no meaningful
benefit. Modern phytosome and micellar formulations achieve more without it.

There's also a safety angle nobody mentions: **piperine inhibits P-glycoprotein
and CYP3A4** — the same mechanism as grapefruit juice. For anyone on prescription
medication, a green "these support each other" badge would be actively
misleading.

This one is worth calling out because it's the example you raised, and it's the
clearest case of a claim that is everywhere and thin underneath.

---

## Implementation notes

- Interactions are evaluated **per dose slot**, not across the whole day, because
  almost every real one is a co-ingestion effect. Firing because two pills exist
  in the same list would be wrong most of the time.
- Slots carry a `withFood` flag, since several rules are conditional on it.
- Cumulative rules (zinc, vitamin E, folic acid) evaluate on daily totals.
- Every warning shown in the app carries its evidence grade and source, so the
  user can judge it rather than take the app's word.
- Thresholds are encoded, not implied. Calcium under 300 mg alongside iron
  produces no warning, which is correct and keeps the signal meaningful.

## Limits

This covers **nutrient–nutrient** interactions only. Supplement–drug
interactions are a larger and more consequential category — vitamin K with
warfarin, calcium with levothyroxine and certain antibiotics, magnesium with
some antibiotics, St John's wort with a long list. None of that is implemented,
and the app should not imply it has been checked.

Nothing here is medical advice, and none of these pairs bear on visceral fat,
blood pressure or body fat percentage — they're absorption mechanics.
