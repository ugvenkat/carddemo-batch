# CardDemo Agentic Converter

Agentic AI that automatically converts COBOL copybooks, batch programs and JCL
to Java and Python using the Claude API (Anthropic).

## What It Does

Reads source files from `src/` and converts them in the correct dependency order:

```
Phase 1: Copybooks  (.cpy) → Java record classes   → java/records/
Phase 2: COBOL      (.cbl) → Java service classes  → java/services/
Phase 3: JCL        (.jcl) → Python scripts        → python/
```

Also generates tests for everything:
```
Tests → tests/java/records/    (JUnit 5)
      → tests/java/services/   (JUnit 5)
      → tests/python/          (pytest)
```

## Output Structure

```
converted-usingAgent/
  java/
    records/             ← copybook Java classes (shared record types)
      AccountRecord.java
      CardXrefRecord.java
      TranRecord.java
      ...
    services/            ← COBOL Java service classes
      CbTrn01Service.java
      CbTrn02Service.java
      CbTrn03Service.java
      CbAct01Service.java
  python/                ← JCL Python scripts
    acctfile.py
    posttran.py
    intcalc.py
    tcatbalf.py
  tests/
    java/
      records/           ← JUnit 5 tests for record classes
      services/          ← JUnit 5 tests for service classes
    python/              ← pytest tests for Python scripts
  docs/
```

## Prerequisites

- Python 3.8+
- Claude API key from https://console.anthropic.com
- pip install -r requirements.txt

## Setup

### Step 1 — Install dependencies
```cmd
pip install -r requirements.txt
```

### Step 2 — Create .env file
Create a file called `.env` in the same folder as `agent.py`:
```
ANTHROPIC_API_KEY=sk-ant-api03-your-key-here
```
**Never commit this file to GitHub — it contains your secret key!**

Add to `.gitignore`:
```
.env
converted-usingAgent/
data/
```

### Step 3 — Run the agent
```cmd
python agent.py --src ./src --out ./converted-usingAgent
```

Or with custom paths:
```cmd
python agent.py --src C:\Study\carddemo-batch\src --out C:\Study\carddemo-batch\converted-usingAgent
```

## Rate Limiting

The agent has a 5-second delay between API calls to avoid rate limit errors.
If you get rate limited, the agent automatically retries with exponential backoff.

To adjust the delay, change `RATE_LIMIT_DELAY` in `agent.py`:
```python
RATE_LIMIT_DELAY = 5   # seconds between API calls
```

## Key Design Decisions (Learnings from Manual Conversion)

### Copybook → Java Rules
- Each class has utility methods **inlined** (sub, nvl, parseLong etc.) — Java cannot share private methods between top-level classes in the same file
- `PIC S9(n)V99` stored as **scaled integer** without decimal point: e.g. 1000.00 → `+00000100000`
- Record length **strictly enforced**: `String.format("%-Ns", rec).substring(0, N)`

### COBOL → Java Rules
- **No inner record classes** — always use shared copybook Java classes
- Field names must **exactly match** copybook class field names
- VSAM KSDS = **HashMap** loaded at open, flushed at close
- `OPEN I-O` = read-write HashMap; `OPEN INPUT` = read-only HashMap

### JCL → Python Rules
- **Check file exists BEFORE subprocess.run()** — don't rely on FileNotFoundError
- `IDCAMS DELETE` = `os.remove()` with `os.path.exists()` check
- `IDCAMS DEFINE` = create empty file + JSON metadata sidecar
- `IDCAMS REPRO` = `shutil.copyfile()`
- GDG `(+1)` = timestamped filename

## Running Tests After Conversion

### Java tests (requires Java 17 + JUnit 5 jar)
```cmd
cd converted-usingAgent\java\records
javac -cp junit-platform-console-standalone.jar *.java
java -jar junit-platform-console-standalone.jar --scan-class-path
```

### Python tests
```cmd
cd converted-usingAgent
pip install pytest
pytest tests/python/ -v
```

## Troubleshooting

| Issue | Fix |
|---|---|
| `ANTHROPIC_API_KEY not found` | Check your `.env` file exists and has the correct key |
| `Rate limit error` | Increase `RATE_LIMIT_DELAY` in `agent.py` |
| `Module not found: anthropic` | Run `pip install -r requirements.txt` |
| Empty output file | API returned no content — check API key has credits |
