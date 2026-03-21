# TCATBALF.jcl → tcatbalf.py Conversion Guide

## Overview
This document explains how `TCATBALF.jcl` was converted to `tcatbalf.py` and how to set up, run, and extend it.

---

## Original JCL Purpose
`TCATBALF.jcl` is a mainframe batch job that:
1. **Deletes** the Transaction Category Balance VSAM KSDS file if it exists
2. **Defines** a new Transaction Category Balance VSAM KSDS file
3. **Copies** data from a flat sequential file into the new VSAM file

This job is typically run before the main batch cycle to reset and reload the transaction category balance file from a known good source.

---

## JCL to Python Mapping

| JCL Element | JCL Value | Python Equivalent |
|---|---|---|
| `JOB` | `TCATBALF` | `main()` function |
| `STEP05 EXEC PGM=IDCAMS` | DELETE CLUSTER | `step05_delete_vsam()` using `os.remove()` |
| `STEP10 EXEC PGM=IDCAMS` | DEFINE CLUSTER | `step10_define_vsam()` creates file + JSON metadata |
| `STEP15 EXEC PGM=IDCAMS` | REPRO INFILE→OUTFILE | `step15_repro_data()` using `shutil.copyfile()` |
| `DD TCATBAL` | `TCATBALF.PS` flat file | `data/TCATBALF.PS` (env var `TCATBAL`) |
| `DD TCATBALV` | `TCATBALF.VSAM.KSDS` | `data/TCATBALF.VSAM.KSDS` (env var `TCATBALV`) |
| `SET MAXCC = 0` | Unconditional success | Delete always returns RC=0 regardless |
| `KEYS(17 0)` | Key length=17, offset=0 | Stored in JSON metadata sidecar |
| `RECORDSIZE(50 50)` | Fixed 50-byte records | Stored in JSON metadata sidecar |
| `DISP=OLD` on TCATBALV | Exclusive write access | File opened exclusively (write mode) |
| `SYSPRINT DD SYSOUT=*` | Console output | Python `logging` to stdout |

### Difference from ACCTFILE.jcl
| Feature | ACCTFILE.jcl | TCATBALF.jcl |
|---|---|---|
| Delete error handling | `IF MAXCC LE 08 THEN SET MAXCC=0` | `SET MAXCC=0` (unconditional) |
| Record size | 300 bytes | 50 bytes |
| Key length | 11 | 17 |
| TCATBALV DISP | SHR (shared) | OLD (exclusive) |

---

## Folder Structure

```
carddemo-batch/
  src/
    jcl/
      TCATBALF.jcl              <- original JCL (source of truth)
  converted/
    python/
      tcatbalf.py               <- this converted script
  data/
    TCATBALF.PS                 <- flat file input (must exist before running)
    TCATBALF.VSAM.KSDS          <- created by this script
    TCATBALF.VSAM.KSDS.meta.json <- created by this script (cluster metadata)
```

---

## Prerequisites

- Python 3.8 or higher
- No external libraries required — uses only Python standard library (`os`, `shutil`, `json`)

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
copy tcatbalf.py converted\python\tcatbalf.py
```

### Step 3 — Create a test flat file (TCATBALF.PS)
```cmd
echo TEST TRANSACTION CATEGORY BALANCE DATA > data\TCATBALF.PS
```
In production this file contains fixed-length 50-byte transaction category balance records.

### Step 4 — Commit to GitHub
```cmd
git add .
git commit -m "Add tcatbalf.py - JCL to Python conversion of TCATBALF.jcl"
git push
```

---

## How to Run

### Run with default file paths
```cmd
cd C:\Study\carddemo-batch
python converted\python\tcatbalf.py
```

### Run with custom file paths
```cmd
set TCATBAL=C:\mydata\TCATBALF.PS
set TCATBALV=C:\mydata\TCATBALF.VSAM.KSDS
python converted\python\tcatbalf.py
```

### Expected console output
```
2026-03-21 10:00:00 [INFO] ************************************************************
2026-03-21 10:00:00 [INFO] JOB TCATBALF - START
2026-03-21 10:00:00 [INFO] Delete, Define and Load Transaction Category Balance VSAM File
2026-03-21 10:00:00 [INFO] ************************************************************
2026-03-21 10:00:00 [INFO] STEP05 - START - DELETE TRANSACTION CATEGORY BALANCE VSAM FILE
2026-03-21 10:00:00 [INFO]   NOT FOUND (OK): data/TCATBALF.VSAM.KSDS
2026-03-21 10:00:00 [INFO]   SET MAXCC = 0 (unconditional)
2026-03-21 10:00:00 [INFO] STEP05 - COMPLETED SUCCESSFULLY - RC=0000
2026-03-21 10:00:00 [INFO] STEP10 - START - DEFINE TRANSACTION CATEGORY BALANCE VSAM FILE
2026-03-21 10:00:00 [INFO]   CREATED VSAM FILE: data/TCATBALF.VSAM.KSDS
2026-03-21 10:00:00 [INFO]   CREATED METADATA:  data/TCATBALF.VSAM.KSDS.meta.json
2026-03-21 10:00:00 [INFO]   CLUSTER PROPERTIES: KEYS(17 0) RECORDSIZE(50 50)
2026-03-21 10:00:00 [INFO] STEP10 - COMPLETED SUCCESSFULLY - RC=0000
2026-03-21 10:00:00 [INFO] STEP15 - START - COPY FLAT FILE TO VSAM (REPRO)
2026-03-21 10:00:00 [INFO]   REPRO: data/TCATBALF.PS -> data/TCATBALF.VSAM.KSDS (42 bytes)
2026-03-21 10:00:00 [INFO] STEP15 - COMPLETED SUCCESSFULLY - RC=0000
2026-03-21 10:00:00 [INFO] JOB TCATBALF - COMPLETED SUCCESSFULLY
```

---

## Important Notes

### Unconditional MAXCC Reset
Unlike `ACCTFILE.jcl` which conditionally resets MAXCC only if RC ≤ 8, `TCATBALF.jcl` uses `SET MAXCC = 0` unconditionally — the delete step always succeeds. The Python script mirrors this exactly.

### DISP=OLD
`DISP=OLD` on `TCATBALV` means the file must exist AND grants exclusive access. In Python, after STEP10 creates the file, STEP15 writes to it — the same sequence is preserved.

### Relationship to INTCALC
`TCATBALF.jcl` should be run **before** `INTCALC.jcl` in the batch cycle:
1. `TCATBALF.jcl` → loads the transaction category balance file fresh
2. `INTCALC.jcl` → reads that file and computes interest

---

## Recommended Batch Run Order

For a complete batch cycle, run the jobs in this sequence:

```cmd
python converted\python\acctfile.py    :: 1. Set up account VSAM
python converted\python\tcatbalf.py   :: 2. Set up transaction category balance VSAM
python converted\python\posttran.py   :: 3. Post daily transactions
python converted\python\intcalc.py    :: 4. Calculate interest
```

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
- Original JCL: `src/jcl/TCATBALF.jcl`
- Apache 2.0 License — original source: https://github.com/aws-samples/aws-mainframe-modernization-carddemo
