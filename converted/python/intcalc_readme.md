# INTCALC.jcl → intcalc.py Conversion Guide

## Overview
This document explains how `INTCALC.jcl` was converted to `intcalc.py` and how to set up, run, and extend it.

---

## Original JCL Purpose
`INTCALC.jcl` is a mainframe batch job that:
- Reads the transaction category balance file
- Computes interest and fees for each account
- Writes system transaction output to a new GDG generation file

---

## JCL to Python Mapping

| JCL Element | JCL Value | Python Equivalent |
|---|---|---|
| `JOB` | `INTCALC` | `main()` function |
| `STEP15 EXEC PGM=CBACT04C` | Run interest calculator | `run_step15()` → `subprocess.run('cbact04c.py')` |
| `PARM='2022071800'` | Processing date parameter | Passed as CLI arg + `PARM` env var; defaults to today |
| `DD TCATBALF` | `TCATBALF.VSAM.KSDS` | `data/TCATBALF.dat` (env var `TCATBALF`) |
| `DD XREFFILE` | `CARDXREF.VSAM.KSDS` | `data/CARDXREF.dat` (env var `XREFFILE`) |
| `DD XREFFIL1` | `CARDXREF.VSAM.AIX.PATH` | `data/CARDXREF.AIX.dat` (env var `XREFFIL1`) |
| `DD ACCTFILE` | `ACCTDATA.VSAM.KSDS` | `data/ACCTDATA.dat` (env var `ACCTFILE`) |
| `DD DISCGRP` | `DISCGRP.VSAM.KSDS` | `data/DISCGRP.dat` (env var `DISCGRP`) |
| `DD TRANSACT` | `SYSTRAN(+1)` GDG | `data/SYSTRAN_YYYYMMDDHHMMSS.dat` (timestamped) |
| `RECFM=F,LRECL=350` | Fixed 350-byte records | Constant `TRANSACT_LRECL = 350` |
| `SYSPRINT/SYSOUT=*` | Console output | Python `logging` to stdout |

### Key Design Decision — PARM Handling
In JCL, `PARM='2022071800'` passes a processing date to the COBOL program. In Python this is:
1. Passed as a command-line argument: `python cbact04c.py 2022071800`
2. Also set as env var `PARM` for the subprocess
3. Defaults to today's date + `00` if `PROCESSING_DATE` env var is not set

---

## Folder Structure

```
carddemo-batch/
  src/
    jcl/
      INTCALC.jcl               <- original JCL (source of truth)
  converted/
    python/
      intcalc.py                <- this converted script
      cbact04c.py               <- COBOL program to be converted (dependency)
  data/
    TCATBALF.dat                <- transaction category balance (input)
    CARDXREF.dat                <- card cross-reference primary (input)
    CARDXREF.AIX.dat            <- card cross-reference alternate index (input)
    ACCTDATA.dat                <- account data (input)
    DISCGRP.dat                 <- discount group (input)
    SYSTRAN_YYYYMMDDHHMMSS.dat  <- output GDG file (created each run)
```

---

## Prerequisites

- Python 3.8 or higher
- No external libraries required

---

## Setup Instructions

### Step 1 — Create folders
```cmd
cd C:\Study\carddemo-batch
mkdir converted\python
mkdir data
```

### Step 2 — Copy converted file
```cmd
copy intcalc.py converted\python\intcalc.py
```

### Step 3 — Create placeholder data files for testing
```cmd
type nul > data\TCATBALF.dat
type nul > data\CARDXREF.dat
type nul > data\CARDXREF.AIX.dat
type nul > data\ACCTDATA.dat
type nul > data\DISCGRP.dat
```

### Step 4 — Commit to GitHub
```cmd
git add .
git commit -m "Add intcalc.py - JCL to Python conversion of INTCALC.jcl"
git push
```

---

## How to Run

### Run with default settings (uses today's date as processing date)
```cmd
cd C:\Study\carddemo-batch
python converted\python\intcalc.py
```

### Run with specific processing date
```cmd
set PROCESSING_DATE=2022071800
python converted\python\intcalc.py
```

### Run with custom file paths
```cmd
set TCATBALF=C:\mydata\TCATBALF.dat
set ACCTFILE=C:\mydata\ACCTDATA.dat
set PROCESSING_DATE=2026032100
python converted\python\intcalc.py
```

### Expected console output
```
2026-03-21 10:00:00 [INFO] ************************************************************
2026-03-21 10:00:00 [INFO] JOB INTCALC - START
2026-03-21 10:00:00 [INFO] Process transaction balance and compute interest and fees
2026-03-21 10:00:00 [INFO] Processing Date: 2026032100
2026-03-21 10:00:00 [INFO] ************************************************************
2026-03-21 10:00:00 [INFO] STEP15 - START - PGM=CBACT04C
2026-03-21 10:00:00 [INFO]          PARM='2026032100'
2026-03-21 10:00:00 [INFO] DD Allocations:
2026-03-21 10:00:00 [INFO]   TCATBALF     -> data/TCATBALF.dat
2026-03-21 10:00:00 [INFO]   XREFFILE     -> data/CARDXREF.dat
2026-03-21 10:00:00 [INFO]   ACCTFILE     -> data/ACCTDATA.dat
2026-03-21 10:00:00 [INFO]   TRANSACT     -> data/SYSTRAN_20260321100000.dat
2026-03-21 10:00:00 [INFO] STEP15 - COMPLETED SUCCESSFULLY - RC=0000
2026-03-21 10:00:00 [INFO] JOB INTCALC - COMPLETED SUCCESSFULLY
```

---

## Important Notes

### PARM Date Format
The original JCL hardcodes `PARM='2022071800'`. The format is `YYYYMMDDXX` where `XX` is a run sequence number. In Python this defaults to today's date + `00`. Override with `PROCESSING_DATE` env var.

### GDG Simulation
`SYSTRAN(+1)` in JCL creates a new GDG generation each run. In Python this is simulated by appending a full timestamp to the filename — `SYSTRAN_20260321100000.dat`. Each run produces a new unique file, preserving the same versioning behaviour.

### XREFFIL1 — Alternate Index
`XREFFIL1` points to `CARDXREF.VSAM.AIX.PATH` — an alternate index path on the mainframe. In Python this is a separate file. The COBOL program `CBACT04C` uses this for lookups by a secondary key.

### cbact04c.py dependency
`intcalc.py` calls `cbact04c.py` which comes from converting `CBACT04C.cbl`. Until that conversion is done, the script logs a warning and skips gracefully.

---

## Next Steps

| Step | Task | File |
|---|---|---|
| 1 ✅ | Convert POSTTRAN.jcl to Python | `converted/python/posttran.py` |
| 2 ✅ | Convert ACCTFILE.jcl to Python | `converted/python/acctfile.py` |
| 3 ✅ | Convert INTCALC.jcl to Python | `converted/python/intcalc.py` |
| 4 ✅ | Convert TCATBALF.jcl to Python | `converted/python/tcatbalf.py` |
| 5 | Convert CBTRN01C.cbl to Java | `converted/java/CbTrn01Service.java` |
| 6 | Convert CBTRN02C.cbl to Java | `converted/java/CbTrn02Service.java` |
| 7 | Convert CBTRN03C.cbl to Java | `converted/java/CbTrn03Service.java` |
| 8 | Convert CBACT01C.cbl to Java | `converted/java/CbAct01Service.java` |

---

## References
- Original JCL: `src/jcl/INTCALC.jcl`
- Apache 2.0 License — original source: https://github.com/aws-samples/aws-mainframe-modernization-carddemo
