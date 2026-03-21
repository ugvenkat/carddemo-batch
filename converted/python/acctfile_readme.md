# ACCTFILE.jcl → acctfile.py Conversion Guide

## Overview
This document explains how `ACCTFILE.jcl` was converted to `acctfile.py` and how to set up, run, and extend it.

---

## Original JCL Purpose
`ACCTFILE.jcl` is a mainframe batch job that:
1. **Deletes** the Account VSAM KSDS file if it already exists
2. **Defines** a new Account VSAM KSDS file with specific cluster properties
3. **Copies** data from a flat sequential file into the new VSAM file

---

## JCL to Python Mapping

| JCL Element | JCL Value | Python Equivalent |
|---|---|---|
| `JOB` | `ACCTFILE` | `main()` function |
| `STEP05 EXEC PGM=IDCAMS` | DELETE CLUSTER | `step05_delete_vsam()` using `os.remove()` |
| `STEP10 EXEC PGM=IDCAMS` | DEFINE CLUSTER | `step10_define_vsam()` creates file + JSON metadata |
| `STEP15 EXEC PGM=IDCAMS` | REPRO INFILE→OUTFILE | `step15_repro_data()` using `shutil.copyfile()` |
| `DD ACCTDATA` | `ACCTDATA.PS` flat file | `data/ACCTDATA.PS` (env var `ACCTDATA`) |
| `DD ACCTVSAM` | `ACCTDATA.VSAM.KSDS` | `data/ACCTDATA.VSAM.KSDS` (env var `ACCTVSAM`) |
| `IF MAXCC LE 08 THEN SET MAXCC=0` | Ignore delete error | File-not-found is logged but not an error |
| `KEYS(11 0)` | Key length=11, offset=0 | Stored in JSON metadata sidecar |
| `RECORDSIZE(300 300)` | Fixed 300-byte records | Stored in JSON metadata sidecar |
| `SYSPRINT DD SYSOUT=*` | Console output | Python `logging` to stdout |

### Key Design Decision — VSAM Metadata Sidecar
On the mainframe, IDCAMS `DEFINE CLUSTER` writes to the VSAM catalog. In Python, a `.meta.json` file is created alongside the data file to store the same cluster properties (key length, record size, etc.). This allows downstream programs to read the file structure without hardcoding it.

---

## Folder Structure

```
carddemo-batch/
  src/
    jcl/
      ACCTFILE.jcl              <- original JCL (source of truth)
  converted/
    python/
      acctfile.py               <- this converted script
  data/
    ACCTDATA.PS                 <- flat file input (must exist before running)
    ACCTDATA.VSAM.KSDS          <- created by this script
    ACCTDATA.VSAM.KSDS.meta.json <- created by this script (cluster metadata)
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
copy acctfile.py converted\python\acctfile.py
```

### Step 3 — Create a test flat file (ACCTDATA.PS)
For testing, create a dummy input file:
```cmd
echo TEST ACCOUNT DATA > data\ACCTDATA.PS
```
In production this file would contain real fixed-length 300-byte account records.

### Step 4 — Commit to GitHub
```cmd
git add .
git commit -m "Add acctfile.py - JCL to Python conversion of ACCTFILE.jcl"
git push
```

---

## How to Run

### Run with default file paths
```cmd
cd C:\Study\carddemo-batch
python converted\python\acctfile.py
```

### Run with custom file paths
```cmd
set ACCTDATA=C:\mydata\ACCTDATA.PS
set ACCTVSAM=C:\mydata\ACCTDATA.VSAM.KSDS
python converted\python\acctfile.py
```

### Expected console output
```
2026-03-21 10:00:00 [INFO] ************************************************************
2026-03-21 10:00:00 [INFO] JOB ACCTFILE - START
2026-03-21 10:00:00 [INFO] Delete, Define and Load Account VSAM File
2026-03-21 10:00:00 [INFO] ************************************************************
2026-03-21 10:00:00 [INFO] STEP05 - START - DELETE ACCOUNT VSAM FILE IF EXISTS
2026-03-21 10:00:00 [INFO]   NOT FOUND (OK): data/ACCTDATA.VSAM.KSDS - continuing
2026-03-21 10:00:00 [INFO] STEP05 - COMPLETED SUCCESSFULLY - RC=0000
2026-03-21 10:00:00 [INFO] STEP10 - START - DEFINE ACCOUNT VSAM FILE
2026-03-21 10:00:00 [INFO]   CREATED VSAM FILE: data/ACCTDATA.VSAM.KSDS
2026-03-21 10:00:00 [INFO]   CREATED METADATA:  data/ACCTDATA.VSAM.KSDS.meta.json
2026-03-21 10:00:00 [INFO] STEP10 - COMPLETED SUCCESSFULLY - RC=0000
2026-03-21 10:00:00 [INFO] STEP15 - START - COPY FLAT FILE TO VSAM (REPRO)
2026-03-21 10:00:00 [INFO]   REPRO: data/ACCTDATA.PS -> data/ACCTDATA.VSAM.KSDS (128 bytes)
2026-03-21 10:00:00 [INFO] STEP15 - COMPLETED SUCCESSFULLY - RC=0000
2026-03-21 10:00:00 [INFO] JOB ACCTFILE - COMPLETED SUCCESSFULLY
```

---

## Important Notes

### IDCAMS DELETE vs ACCTFILE
`ACCTFILE.jcl` uses `IF MAXCC LE 08 THEN SET MAXCC = 0` — meaning a delete failure (RC=8, file not found) is acceptable. The Python script mirrors this by logging "NOT FOUND (OK)" and continuing.

### IDCAMS REPRO
`REPRO` on the mainframe does a record-by-record copy from sequential to VSAM. In Python, `shutil.copyfile()` does a binary copy. When `CBACT01C.cbl` is converted to Java, the actual record parsing will handle the 300-byte fixed-length structure.

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
- Original JCL: `src/jcl/ACCTFILE.jcl`
- Apache 2.0 License — original source: https://github.com/aws-samples/aws-mainframe-modernization-carddemo
