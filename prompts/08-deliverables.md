## Prompt
Human: "Lets discuss on deliverables:", followed by the brief's 6 deliverables (pasted verbatim). The AI mapped each deliverable to its file and raised three points (A: proportionality and reading order; B: limitations not "concise / discovered within scope"; C: no verbatim response excerpts in the AI records). Human: "Yes proceed" (A and B, plus C for records 02, 03, 04, 04b, 06).

## Timestamp
2026-10-23

## Output artifact
- README.md: a "Start with docs/design.md" reading guide; `specs/` described as the process trail
- docs/limitations.md: split into "Discovered while building" (L7–L11; L11 `-00:00` is new) and "Known limits of the in-memory prototype" (L1–L6). Ids are kept stable because the acceptance report cites L8.
- prompts/02, 03, 04, 04b, 06: new "Response excerpts (verbatim)" sections quoting the AI output that the human edited or rejected
- prompts/README.md: the template gains an optional "Response excerpts" section

## Response summary
All 6 deliverables already existed: code and tests; `docs/design.md`; `prompts/`; README Setup; `docs/assumptions.md`; `docs/limitations.md`. The changes sharpen proportionality (reviewer entry point), make the limitations reflect what was actually discovered, and strengthen the AI-use record with verbatim excerpts rather than AI-written summaries only.

## Response excerpts (verbatim)
> My suggestion: split the file into "Discovered while building" and "Known limits of the prototype", one line each, and add the `-00:00` item.

## Human review note
**Accepted** by the human reviewer on 2026-10-23: all three changes (A, B, C) as written.

## Verification
By the AI:
- Every excerpt was copied from this session's transcript.
- L-ids are unchanged, so the `limitation L8` link in `docs/acceptance-report.md` still resolves.
- No code changed, so the test suite is unaffected.

Human verification (as stated by the user, 2026-10-23):
- read the README change;
- read the new `docs/limitations.md`;
- compared the verbatim excerpts with what was said in the session;
- checked the six deliverables against the brief's list.
