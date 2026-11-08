# P1 Gate 1 Accuracy Harness Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Measure how often Bolo's sound flags agree with a pronunciation teacher's, on any labelled set of recordings, and print a per-pattern pass/fail report for Gate 1. Today it runs on our own synthetic recordings only; GMU or local recordings plug in later.

**Architecture:**
- **One shared rule.** The flag rule is pulled out of `AttemptScorer` into a shipped domain class, `SoundFlags`, so the harness measures exactly the rule learners get ("Gate 1 is decided by code that we then ship", Architecture §0).
- **Harness location.** The harness itself (manifest, alignment, metrics, report, runner) is a developer tool under `src/test/java/app/bolo/goldenset`. It is not shipped, and ArchUnit doesn't police test sources.
- **How it runs.** It calls Azure's scripted assessment directly through `AzureScriptedAssessment`. It caches each vendor result as JSON, so rescoring and threshold sweeps cost nothing.
- **Command.** `./gradlew goldenSet -Pset=<dir>` (Gradle `JavaExec` on the test classpath).

**Tech Stack:** Java 21, Jackson 3 (`tools.jackson`), JUnit 5 + AssertJ, Gradle. macOS `say` + `afconvert` for the synthetic set.

**Spec:**
- `Bolo-PRD-MVP.md`: NFR-10 (agreement with a human ESL teacher ≥ 80% before launch), ASM-2 (the 6 patterns), US-3.2 (clarity, not accent).
- `Bolo-Architecture-MVP.md`: §10 Phase 0 "Scoring bake-off" (GoldenSetRunner, agreement table per vendor and pattern), §9 (the golden-set suite fails when agreement drops).

## Global Constraints
- **Patterns** are exactly `SoundPattern`: V_BH, W_V, TH_TD, Z_J, S_SH, S_CLUSTER_VOWEL.
- **Gate bar per pattern:** precision ≥ 0.80 and recall ≥ 0.60, judged only when the teacher marked ≥ 5 issues for that pattern ("not enough data" otherwise). This is the bar proposed to the founder in place of a raw 80%. Raw agreement (NFR-10) is reported alongside it.
- **Gate verdict:**
  - PASS when ≥ 4 patterns have enough data and all of them pass;
  - FAIL when any pattern with enough data fails;
  - NOT DECIDED otherwise.
- **Thresholds:** the defaults are the shipped ones: practice/Try it uses sound < 60 in a word < 75. The sweep also reports sound < 40/50/70 (the Coach's talk path uses 50).
- **Clips:** WAV, 16 kHz, mono, 16-bit, ≤ 60 s (the Azure REST short-audio limit). Anything else is a manifest error naming the file.
- **Nothing real is committed.** `golden-sets/*` is git-ignored except `golden-sets/synthetic/` and `golden-sets/README.md`. **GMU audio is not downloaded** until the founder confirms.
- **The Azure key** is read from `BOLO_AZURE_SPEECH_KEY` and never printed, logged, cached or written to a report.

## Review Focus
1. A teacher label on a word that occurs twice in the reference ("her … her") must hit the occurrence the teacher meant (`nth`). An ambiguous label without `nth` is a manifest error, not a guess. Test: Task 2.
2. Azure hears nothing (`heard=false`) or returns words that don't match the reference. That recording is reported as skipped, with the reason, and kept out of the metrics. It is not counted as "no issues". Test: Task 3.
3. A pattern with 1–4 teacher issues is NOT ENOUGH DATA even at 100%, and never PASS. Test: Task 3.
4. Offline mode with a missing or corrupt cache file: a clear error naming the recording, exit code 2, and no vendor call. Test: Task 4.
5. The key never appears in the report, the cache or stdout. Test: Task 4.

---

### Task 1: `SoundFlags`, the one flag rule (assessment domain, shipped)

**Files:**
- Create: `api/src/main/java/app/bolo/assessment/domain/SoundFlags.java`
- Modify: `api/src/main/java/app/bolo/assessment/domain/AttemptScorer.java` (use `SoundFlags` for SOUND and EXTRA_VOWEL; keep MISSING, upgrade and levels)
- Test: `api/src/test/java/app/bolo/assessment/domain/SoundFlagsTest.java`; the existing `AttemptScorerTest` must stay green unchanged.

**Interfaces:**
- Produces:
  - `record SoundFlags.Flag(int position, String word, SoundPattern pattern, double soundScore)`. `position` is the index in the input word list. EXTRA_VOWEL flags sit on the s-cluster word after the insertion, with soundScore = the insertion word's accuracy.
  - `static List<Flag> flags(List<SpokenWord> words, double soundBelow, double wordBelow)`. Order is input order, at most one flag per (position, pattern).
  - `static final double PRACTICE_SOUND_BELOW = 60, WORD_BELOW = 75`.
  - `static boolean startsWithSCluster(String word)`.
  - `static List<SoundPattern> patternsIn(SpokenWord w)`: the patterns whose sounds are in the word's phonemes, plus S_CLUSTER_VOWEL when `startsWithSCluster`. These are the opportunities.

- [ ] **Step 1: Write the failing tests**
  - `aWeakPatternSoundInAnUnclearWordIsFlagged`: "very" with an accuracy-55 word and v at 30 → `Flag(0,"very",V_BH,30)`.
  - `aWeakSoundInAClearWordIsAccent`: same v at 30, word 80 → no flags.
  - `theExtraVowelIsFlaggedOnTheClusterWord`: ["i"(Insertion), "school"] → `Flag(1,"school",S_CLUSTER_VOWEL, <i's accuracy>)`.
  - `aRepeatedWordBeforeAClusterIsNotTheExtraVowel`: ["the"(Insertion), "the", "station"] → no flags.
  - `theThresholdIsAParameter`: v at 55 in a word at 60 → flagged at soundBelow 60, not at 50.
  - `patternsInNamesTheOpportunities`: "think" with θ → [TH_TD]; "snow" → [S_CLUSTER_VOWEL]; "cat" → [].
- [ ] **Step 2:** Run `cd api && ./gradlew test --tests '*SoundFlagsTest*'`. Expected: compile failure, because `SoundFlags` doesn't exist.
- [ ] **Step 3:** Implement `SoundFlags`, moving the Insertion/sound logic out of `AttemptScorer` (including `isLoneShortVowel`). `AttemptScorer` maps flags to `Focus`, with SOUND for sound patterns and EXTRA_VOWEL for S_CLUSTER_VOWEL, using `PRACTICE_SOUND_BELOW`/`WORD_BELOW`.
- [ ] **Step 4:** Run `./gradlew test --tests '*SoundFlagsTest*' --tests '*AttemptScorerTest*' --tests '*AttemptAcceptanceTest*'`. Expected: PASS.
- [ ] **Step 5:** Commit `refactor(assessment): SoundFlags — one flag rule for practice and the accuracy harness`.

### Task 2: Golden-set manifest and reference alignment (test sources)

**Files:**
- Create: `api/src/test/java/app/bolo/goldenset/GoldenSet.java` (records and loader) and `Alignment.java`.
- Test: `api/src/test/java/app/bolo/goldenset/GoldenSetTest.java` and `AlignmentTest.java`.
- Fixtures: `api/src/test/resources/goldenset/demo/`, with a manifest of 2 recordings ("Very few vans visit the village.", "I study at a small school near the station."). Each WAV is 1 s of silence at 16 kHz, committed. Task 4 adds `cache/azure/*.json`.

**Interfaces:**
- Manifest (`manifest.json` in the set's directory):
  ```json
  {"name": "...", "source": "...", "licence": "...",
   "recordings": [{"id": "s01", "audio": "s01.wav", "reference": "Please call Stella.", "labelledBy": "AB",
                   "issues": [{"word": "Stella", "pattern": "S_CLUSTER_VOWEL"}, {"word": "her", "nth": 2, "pattern": "Z_J"}]}]}
  ```
- Produces:
  - `record GoldenSet(Path dir, String name, String source, String licence, List<Recording> recordings)`, with `static GoldenSet load(Path dir)`, which throws `GoldenSetException(String)` naming the recording.
  - `record Recording(String id, Path audio, String reference, String labelledBy, Set<Label> labels)`.
  - `record Label(int refIndex, SoundPattern pattern)`.
  - `static List<String> Alignment.tokens(String reference)`: lower-case words with punctuation stripped, keeping apostrophes.
  - `static Map<Integer,Integer> Alignment.align(List<SpokenWord> words, List<String> tokens)`: input position → reference index. Insertions get no index; Omissions and spoken words take the next matching token within 3 positions; an unmatched word gets no index.

- [ ] **Step 1: Write the failing tests**
  - **GoldenSetTest:**
    - `loadsTheDemoSet` (2 recordings, labels resolved to refIndex);
    - `nthPicksTheOccurrence` ("her … her", nth 2 → second index);
    - `anAmbiguousLabelIsAnError` (message contains the id and "nth");
    - `aLabelOnAWordNotInTheReferenceIsAnError`;
    - `anUnknownPatternIsAnError`;
    - `aDuplicateIdIsAnError`;
    - `audioMustBe16kMonoAndAtMost60s` (8 kHz → error; 61 s → error naming the file; generate WAVs in a temp dir with `Wav.wrap`).
  - **AlignmentTest:**
    - `punctuationAndCaseDoNotMatter` ("Stella." ↔ "stella");
    - `anInsertionTakesNoIndex`;
    - `anOmissionTakesItsIndex`;
    - `anUnmatchedWordIsSkippedNotShifted` (a following word still aligns).
- [ ] **Step 2:** Run `./gradlew test --tests '*goldenset*'`. Expected: compile failure.
- [ ] **Step 3:** Implement. WAV checks reuse `common.audio.Wav` (header: rate, channels, bits; duration from the data size).
- [ ] **Step 4:** Run `./gradlew test --tests '*goldenset*'`. Expected: PASS.
- [ ] **Step 5:** Commit `test(goldenset): manifest, labels and reference alignment for the accuracy harness`.

### Task 3: Agreement metrics and the report

**Files:**
- Create: `api/src/test/java/app/bolo/goldenset/Agreement.java` and `Report.java`.
- Test: `api/src/test/java/app/bolo/goldenset/AgreementTest.java` and `ReportTest.java`.

**Interfaces:**
- Consumes: `SoundFlags.flags/patternsIn`, `GoldenSet`, `Alignment`, `PronunciationResult`. Map `ScoredWord` → `SpokenWord` exactly as `AttemptService` does (word, start, end, accuracy, errorType, phonemes → `SpokenSound`).
- Produces:
  - `record Outcome(Recording rec, PronunciationResult result)`.
  - `static Agreement Agreement.of(List<Outcome> outcomes, double soundBelow, double wordBelow, int minPositives)`, which exposes:
    - `Map<SoundPattern, PatternStats> byPattern()`;
    - `List<Disagreement> disagreements()`;
    - `List<Skipped> skipped()`;
    - `GateVerdict gate()`;
    - `double rawAgreement()`.
  - `record PatternStats(int tp, int fp, int fn, int tn, Double precision, Double recall, double falseFlagRate, Verdict verdict)`.
  - `enum Verdict {PASS, FAIL, NOT_ENOUGH_DATA}` and `enum GateVerdict {PASS, FAIL, NOT_DECIDED}`.
  - `record Disagreement(String recordingId, String word, SoundPattern pattern, boolean teacher, boolean bolo, Double soundScore)`.
  - `static String Report.markdown(GoldenSet set, Agreement atDefault, Map<Double, Agreement> sweep)`.
- **Counting:**
  - Opportunities per recording are the (refIndex, pattern) pairs from `patternsIn` on aligned words, plus any teacher label outside them (counted, listed under "labels on words without that sound").
  - TP, FP, FN and TN are computed over the opportunities.
  - Precision is null with no flags, and recall is null with no teacher issues.
  - Verdict: NOT_ENOUGH_DATA when tp + fn < minPositives (default 5); PASS when precision ≥ 0.80 and recall ≥ 0.60.

- [ ] **Step 1: Write the failing tests.** They use synthetic `PronunciationResult`s built in the test.
  - `countsAgainstTheTeacher`: 1 TP (very/V_BH), 1 FP (village, teacher none), 1 FN (think, θ scored 80, teacher TH_TD), 1 TN → exact counts.
  - `fewTeacherIssuesIsNotEnoughData`: 3/3 correct → NOT_ENOUGH_DATA, gate NOT_DECIDED.
  - `aPatternBelowTheBarFails`: 5 teacher issues, Bolo finds 2 → recall 0.4 → FAIL, gate FAIL.
  - `fourPassingPatternsPassTheGate`.
  - `nothingHeardIsSkippedNotCountedAsClean`: heard=false → in `skipped()` with reason "nothing heard", absent from all counts.
  - `cleanSpeechReportsTheFalseFlagRate`: no teacher issues, 1 flag in 10 opportunities → falseFlagRate 0.10, verdict NOT_ENOUGH_DATA.
  - **ReportTest** `theReportNamesTheGateEachPatternAndEveryDisagreement`: the markdown contains "Gate 1:", one row per pattern, each disagreement's recording id and word, the sweep table with 40/50/60/70, and the line "Measured on scripted reading" (the limitation: the talk path scores unscripted speech).
- [ ] **Step 2:** Run `./gradlew test --tests '*goldenset*'`. Expected: FAIL, because the classes are missing.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Run `./gradlew test --tests '*goldenset*'`. Expected: PASS.
- [ ] **Step 5:** Commit `test(goldenset): per-pattern agreement, gate verdict and the markdown report`.

### Task 4: The runner, vendor cache and `goldenSet` task

**Files:**
- Create:
  - `api/src/test/java/app/bolo/goldenset/GoldenSetRunner.java` (`main`);
  - `scripts/golden-set.sh`.
- Modify:
  - `api/build.gradle` (task `goldenSet`);
  - `api/src/main/java/app/bolo/speech/adapter/out/azure/AzureScriptedAssessment.java` (constructor overload with a `Duration timeout`; the existing constructor keeps 8 s).
- Test:
  - `api/src/test/java/app/bolo/goldenset/GoldenSetRunnerTest.java`;
  - an `AzureScriptedAssessmentTest` case.

**Interfaces:**
- `GoldenSetRunner.run(Path setDir, Mode mode, Function<Recording, PronunciationResult> vendor, PrintStream out) → int exitCode`, with `enum Mode {CACHE_THEN_VENDOR, OFFLINE, REFRESH}`.
- The cache lives at `<set>/cache/azure/<id>.json`, holding the `PronunciationResult` as JSON.
- Output: it writes `<set>/report.md`, prints it, and returns:
  - 0 for gate PASS or NOT DECIDED;
  - 1 for FAIL;
  - 2 for a set or cache error.
- `main` args: `<setDir> [--offline|--refresh]`. The vendor is `AzureScriptedAssessment(key, "https://"+region+".stt.speech.microsoft.com", 30 s)` from `BOLO_AZURE_SPEECH_KEY` and `BOLO_AZURE_SPEECH_REGION` (default centralindia). A missing key outside offline mode → exit 2, "set BOLO_AZURE_SPEECH_KEY or use --offline".
- Gradle task:
  ```groovy
  tasks.register('goldenSet', JavaExec) {
      classpath = sourceSets.test.runtimeClasspath
      mainClass = 'app.bolo.goldenset.GoldenSetRunner'
      args project.property('set'), *(project.findProperty('mode') ? ["--${project.property('mode')}"] : [])
  }
  ```
- `scripts/golden-set.sh <setDir> [--offline|--refresh]`:
  - sources `scripts/box.sh`;
  - reads the key with `box 'grep -m1 ^BOLO_AZURE_SPEECH_KEY= ~/bolo/bolo.env | cut -d= -f2-'` straight into an exported variable, never echoed;
  - fails if it's empty;
  - runs the gradle task.

- [ ] **Step 1: Write the failing tests**
  - `offlineRunWritesTheReportFromTheCache`: the demo set with cache files → exit 0, report.md exists, the vendor function is never called.
  - `offlineWithAMissingCacheNamesTheRecording`: exit 2, the output names the id, and there's no vendor call.
  - `aCorruptCacheIsAnErrorNotAGuess`: exit 2.
  - `aVendorResultIsCachedAndReused`: the first run calls the vendor once per recording and the second calls it zero times.
  - `refreshCallsTheVendorAgain`.
  - `theKeyNeverReachesTheReportOrTheCache`: run with the vendor throwing `new SpeechUnavailableException("key=SECRETKEY")` → exit 2; the output, report and cache don't contain "SECRETKEY"; only the exception class is printed.
  - **AzureScriptedAssessmentTest** `aLongerTimeoutWaitsForASlowAnswer`: a local server answers after 1 s → the 500 ms timeout throws `SpeechUnavailableException` and the 3 s timeout returns the result.
- [ ] **Step 2:** Run `./gradlew test --tests '*GoldenSetRunnerTest*' --tests '*AzureScriptedAssessmentTest*'`. Expected: FAIL.
- [ ] **Step 3:** Implement the runner, the cache (Jackson), the overload, the gradle task and the script.
- [ ] **Step 4:** Run the same tests, then `./gradlew goldenSet -Pset=src/test/resources/goldenset/demo -Pmode=offline`. Expected: PASS, and the report prints "Gate 1: NOT DECIDED".
- [ ] **Step 5:** Commit `feat(goldenset): runner with a vendor cache, ./gradlew goldenSet and scripts/golden-set.sh`.

### Task 5: Our synthetic set, a live run, and docs

**Files:**
- Create:
  - `golden-sets/README.md` (how to add a set, the label format for the teacher, licences, "never commit real recordings");
  - `golden-sets/synthetic/manifest.json` plus WAVs;
  - `scripts/make-synthetic-set.sh` (`say -v <voice> -o x.aiff "<sentence>"` then `afconvert -f WAVE -d LEI16@16000 -c 1`).
- Modify:
  - `.gitignore` (`golden-sets/*`, `!golden-sets/README.md`, `!golden-sets/synthetic/`);
  - `docs/RUNBOOK.md` (an "Accuracy harness (Gate 1)" section).
- Set contents:
  - 3 sentences dense in the six patterns. Our own text, not GMU's, e.g. "Very few vans visit the village on Wednesday", "Three thick things with this theme", "She sells fresh fish at the zoo station".
  - Each sentence is read by 4 voices (Samantha, Daniel, Karen, Rishi). The labels: no issues. This measures false flags on clear speech.
  - Plus `practice-ischool-16k.wav` (labels: S_CLUSTER_VOWEL on school and station) and `practice-very-16k.wav` (no issues), copied from the test fixtures.

- [ ] **Step 1:** Generate the set with the script and write the manifest. Run `./gradlew test --tests '*GoldenSetTest*'`, which gains `theSyntheticSetLoads` (loads `../golden-sets/synthetic` with 14 recordings). Expected: PASS.
- [ ] **Step 2:** Live run: `scripts/golden-set.sh golden-sets/synthetic`. No talk may be live, because F0 has one slot. Expected:
  - exit 0 and "Gate 1: NOT DECIDED", since the synthetic set has too few teacher issues;
  - S_CLUSTER_VOWEL TP 2;
  - the false-flag rate per pattern is printed.
  - Commit the cache and report.md, so they can be reviewed offline.
- [ ] **Step 3:** Run `git check-ignore golden-sets/gmu/x.wav`. Expected: ignored. `golden-sets/synthetic/manifest.json` must not be ignored.
- [ ] **Step 4:** Write the RUNBOOK section: the commands; the label format; what each verdict means; the limitation (scripted reading vs the Coach's unscripted talk scoring); and the next steps (GMU after permission, 5–8 local speakers, the teacher's labels, a second pronunciation vendor as another `Function<Recording, PronunciationResult>`).
- [ ] **Step 5:** Commit `feat(goldenset): synthetic set, live baseline report and runbook`.
