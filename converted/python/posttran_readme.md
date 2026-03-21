# POSTTRAN.jcl → posttran.py Conversion Guide

## Overview
This document explains how `POSTTRAN.jcl` was converted to `posttran.py` and how to set up, run, and extend the converted Python script.

---

## Original JCL Purpose
`POSTTRAN.jcl` is a mainframe batch job that:
- Processes and loads the daily transaction file
- Creates transaction category balances
- Updates the transaction master VSAM file

---

## JCL to Python Mapping

| JCL Element | JCL Value | Python Equivalent |
|---|---|---|
| `JOB` | `POSTTRAN` | `main()` function in `posttran.py` |
| `EXEC PGM` | `CBTRN02C` | `subprocess.run(['cbtrn02c.py'])` |
| `DD TRANFILE` | `TRANSACT.VSAM.KSDS` | `data/TRANSACT.dat` (env var `TRANFILE`) |
| `DD DALYTRAN` | `DALYTRAN.PS` | `data/DALYTRAN.dat` (env var `DALYTRAN`) |
| `DD XREFFILE` | `CARDXREF.VSAM.KSDS` | `data/CARDXREF.dat` (env var `XREFFILE`) |
| `DD DALYREJS` | `DALYREJS(+1)` GDG | `data/DALYREJS_YYYYMMDD.dat` (timestamped) |
| `DD ACCTFILE` | `ACCTDATA.VSAM.KSDS` | `data/ACCTDATA.dat` (env var `ACCTFILE`) |
| `DD TCATBALF` | `TCATBALF.VSAM.KSDS` | `data/TCATBALF.dat` (env var `TCATBALF`) |
| `SYSPRINT/SYSOUT=*` | Console output | Python `logging` to stdout |
| Non-zero RC | Job abend | `sys.exit(rc)` |

---

## Folder Structure

After setup your `carddemo-batch` repo should look like this:

```
carddemo-batch/
  src/
    cobol/
      CBTRN02C.cbl        <- original COBOL program called by this JCL
    jcl/
      POSTTRAN.jcl        <- original JCL (source of truth)
  converted/
    python/
      posttran.py         <- this converted script
      cbtrn02c.py         <- COBOL program converted to Python (next step)
    java/
      (future COBOL to Java conversions)
  data/
    TRANSACT.dat          <- transaction master file (input)
    DALYTRAN.dat          <- daily transaction input file
    CARDXREF.dat          <- card cross-reference file
    ACCTDATA.dat          <- account data file
    TCATBALF.dat          <- transaction category balance file
  .gitignore
```

---

## Prerequisites

- Python 3.8 or higher
- No external libraries required — uses only Python standard library

Verify your Python version:
```cmd
python --version
```

---

## Setup Instructions

### Step 1 — Create the folder structure
Run from `C:\Study\carddemo-batch>`:
```cmd
mkdir converted
mkdir converted\python
mkdir converted\java
mkdir data
```

### Step 2 — Copy the converted file
```cmd
copy posttran.py converted\python\posttran.py
```

### Step 3 — Create placeholder data files for testing
```cmd
type nul > data\TRANSACT.dat
type nul > data\DALYTRAN.dat
type nul > data\CARDXREF.dat
type nul > data\ACCTDATA.dat
type nul > data\TCATBALF.dat
```

### Step 4 — Commit to GitHub
```cmd
git add .
git commit -m "Add posttran.py - JCL to Python conversion of POSTTRAN.jcl"
git push
```

---

## How to Run

### Run with default file paths
```cmd
cd C:\Study\carddemo-batch
python converted\python\posttran.py
```

### Run with custom file paths (override defaults using environment variables)
```cmd
set TRANFILE=C:\mydata\TRANSACT.dat
set DALYTRAN=C:\mydata\DALYTRAN.dat
set ACCTFILE=C:\mydata\ACCTDATA.dat
python converted\python\posttran.py
```

### Expected console output
```
2026-03-21 10:00:00 [INFO] ************************************************************
2026-03-21 10:00:00 [INFO] JOB POSTTRAN - START
2026-03-21 10:00:00 [INFO] Process and load daily transaction file
2026-03-21 10:00:00 [INFO] ************************************************************
2026-03-21 10:00:00 [INFO] ============================================================
2026-03-21 10:00:00 [INFO] STEP15 - START - PGM=CBTRN02C
2026-03-21 10:00:00 [INFO] ============================================================
2026-03-21 10:00:00 [INFO] DD Allocations:
2026-03-21 10:00:00 [INFO]   TRANFILE     -> data/TRANSACT.dat
2026-03-21 10:00:00 [INFO]   DALYTRAN     -> data/DALYTRAN.dat
...
2026-03-21 10:00:00 [INFO] STEP15 - COMPLETED SUCCESSFULLY - RC=0000
2026-03-21 10:00:00 [INFO] JOB POSTTRAN - COMPLETED SUCCESSFULLY
```

---

## Important Notes

### GDG Simulation
In JCL, `DALYREJS(+1)` means a new Generation Data Group file is created each run.
In Python this is simulated by appending today's date to the filename:
```
DALYREJS_20260321.dat
```
Each run creates a new dated file — preserving the same GDG behaviour.

### DISP=SHR
In JCL `DISP=SHR` means the file already exists and is shared.
In Python this means the file is opened in read mode — it must exist before the job runs.

### cbtrn02c.py dependency
`posttran.py` calls `cbtrn02c.py` via `subprocess.run()`.
Until `CBTRN02C.cbl` is converted to `cbtrn02c.py`, the script will log a warning and skip that step gracefully:
```
[WARNING] Program cbtrn02c.py not found. Skipping — will be replaced with converted COBOL program.
```

---

## Next Steps

| Step | Task | File |
|---|---|---|
| 1 ✅ | Convert POSTTRAN.jcl to Python | `converted/python/posttran.py` |
| 2 | Convert INTCALC.jcl to Python | `converted/python/intcalc.py` |
| 3 | Convert TCATBALF.jcl to Python | `converted/python/tcatbalf.py` |
| 4 | Convert CBTRN01C.cbl to Java | `converted/java/CbTrn01Service.java` |
| 5 | Convert CBTRN02C.cbl to Java | `converted/java/CbTrn02Service.java` |
| 6 | Convert CBTRN03C.cbl to Java | `converted/java/CbTrn03Service.java` |
| 7 | Convert CBACT01C.cbl to Java | `converted/java/CbAct01Service.java` |

---

## References
- Original JCL: `src/jcl/POSTTRAN.jcl`
- Original COBOL program called: `src/cobol/CBTRN02C.cbl`
- Apache 2.0 License — original source: https://github.com/aws-samples/aws-mainframe-modernization-carddemo
