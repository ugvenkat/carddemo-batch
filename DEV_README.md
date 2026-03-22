# CardDemo Batch Modernization — Developer README

**Project:** AWS CardDemo Mainframe Modernization  
**Repo:** https://github.com/ugvenkat/carddemo-batch  
**Source:** https://github.com/aws-samples/aws-mainframe-modernization-carddemo (Apache 2.0)  
**Author:** ugvenkat  
**Last Updated:** March 2026

---

## Table of Contents
1. [Project Overview](#1-project-overview)
2. [Architecture](#2-architecture)
3. [What Each Service Does](#3-what-each-service-does)
4. [Repository Structure](#4-repository-structure)
5. [Prerequisites](#5-prerequisites)
6. [Quick Start](#6-quick-start)
7. [Creating Sample Input Files](#7-creating-sample-input-files)
8. [Running the Manual Conversion](#8-running-the-manual-conversion)
9. [Running the Agentic AI Converter](#9-running-the-agentic-ai-converter)
10. [Testing All Services](#10-testing-all-services)
11. [Testing Python JCL Scripts](#11-testing-python-jcl-scripts)
12. [Troubleshooting](#12-troubleshooting)

---

## 1. Project Overview

This project demonstrates mainframe modernization by converting a subset of the AWS CardDemo application from:

| From (Mainframe) | To (Modern) |
|---|---|
| COBOL copybooks (`.cpy`) | Java record classes |
| COBOL batch programs (`.cbl`) | Java service classes |
| JCL batch jobs (`.jcl`) | Python orchestration scripts |

Two approaches are provided:
- **Manual conversion** — in `converted/` — hand-crafted with full test coverage
- **Agentic AI conversion** — in `converted-usingAgent/` — auto-generated using Claude API

---

## 2. Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    MAINFRAME (Original)                      │
│                                                              │
│  JCL Jobs          COBOL Programs      VSAM Files           │
│  ─────────         ──────────────      ──────────           │
│  TCATBALF.jcl  ->  (IDCAMS utility)    TCATBALF.VSAM        │
│  ACCTFILE.jcl  ->  (IDCAMS utility)    ACCTDATA.VSAM        │
│  POSTTRAN.jcl  ->  CBTRN02C.cbl    ->  TRANSACT.VSAM        │
│  INTCALC.jcl   ->  CBACT04C.cbl    ->  SYSTRAN.VSAM         │
└─────────────────────────────────────────────────────────────┘
                           │
                           │ Modernization
                           ▼
┌─────────────────────────────────────────────────────────────┐
│                    MODERN (Converted)                        │
│                                                              │
│  Python Scripts    Java Services       Flat Files           │
│  ──────────────    ─────────────       ──────────           │
│  tcatbalf.py   ->  (file ops)          TCATBALF.dat         │
│  acctfile.py   ->  (file ops)          ACCTDATA.dat         │
│  posttran.py   ->  CbTrn02Service  ->  TRANSACT.dat         │
│  intcalc.py    ->  CbAct04Service  ->  SYSTRAN.dat          │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│                 BATCH PROCESSING FLOW                        │
│                                                              │
│  1. DALYTRAN.dat (daily transactions input)                  │
│         │                                                    │
│         ▼                                                    │
│  CbTrn01Service  ──► Reads & validates transactions          │
│         │            Looks up CARDXREF + ACCTDATA            │
│         ▼                                                    │
│  CbTrn02Service  ──► Posts valid transactions                │
│         │            Updates ACCTDATA balances               │
│         │            Writes TRANSACT.dat                     │
│         │            Writes DALYREJS.dat (rejects)           │
│         ▼                                                    │
│  CbTrn03Service  ──► Reads TRANSACT.dat                      │
│         │            Generates TRANREPT.txt report           │
│         │            with page/account/grand totals          │
│         ▼                                                    │
│  CbAct01Service  ──► Reads ACCTDATA.dat                      │
│                      Writes 3 output formats:               │
│                        ACCTOUT.dat  (formatted)             │
│                        ACCTARRY.dat (array format)          │
│                        ACCTVBRC.dat (variable length)       │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│                 AGENTIC AI CONVERTER                         │
│                                                              │
│  src/ (COBOL source)                                         │
│         │                                                    │
│         ▼                                                    │
│  agent.py (orchestrator)                                     │
│         │                                                    │
│         ├── Phase 1: Copybooks → Java record classes         │
│         ├── Phase 2: COBOL    → Java service classes         │
│         └── Phase 3: JCL      → Python scripts              │
│                                                              │
│         ▼                                                    │
│  converted-usingAgent/                                       │
│    java/records/   (11 Java record classes)                  │
│    java/services/  (4 Java service classes)                  │
│    python/         (4 Python scripts)                        │
│    tests/          (JUnit + pytest tests)                    │
└─────────────────────────────────────────────────────────────┘
```

---

## 3. What Each Service Does

### CbTrn01Service (from CBTRN01C.cbl)
**Purpose:** Read and validate daily transaction file  
**Inputs:** DALYTRAN.dat, CARDXREF.dat, ACCTDATA.dat  
**Processing:**
- Reads each transaction from the daily transaction file sequentially
- For each transaction, looks up the card number in the cross-reference file
- If found, reads the associated account record
- Logs any unverified card numbers  
**Key COBOL Paragraphs:** MAIN-PARA, 1000-DALYTRAN-GET-NEXT, 2000-LOOKUP-XREF, 3000-READ-ACCOUNT

---

### CbTrn02Service (from CBTRN02C.cbl)
**Purpose:** Validate and post daily transactions — the core batch posting engine  
**Inputs:** DALYTRAN.dat, CARDXREF.dat, ACCTDATA.dat, TCATBALF.dat  
**Outputs:** TRANSACT.dat, DALYREJS.dat (rejects)  
**Processing:**
- Reads each daily transaction
- Validates: card number exists in XREF, account found, credit limit not exceeded, account not expired
- Posts valid transactions: updates account balances, updates category balances, writes to transaction master
- Rejects invalid transactions with reason codes:
  - `0100` — Invalid card number
  - `0101` — Account not found
  - `0102` — Over credit limit
  - `0103` — Account expired  
**Key COBOL Paragraphs:** 1500-VALIDATE-TRAN, 2000-POST-TRANSACTION, 2700-UPDATE-TCATBAL, 2800-UPDATE-ACCOUNT-REC

---

### CbTrn03Service (from CBTRN03C.cbl)
**Purpose:** Generate transaction detail report filtered by date range  
**Inputs:** TRANSACT.dat, CARDXREF.dat, TRANTYPE.dat, TRANCATG.dat, DATEPARM.dat  
**Outputs:** TRANREPT.txt (133-char wide report)  
**Processing:**
- Reads date range from DATEPARM file
- Reads all transactions within date range
- Looks up transaction type and category descriptions
- Writes formatted 133-character wide report with:
  - Page headers and column headers
  - Detail line per transaction (ID, account, type, category, source, amount)
  - Account subtotals when card number changes
  - Page totals every 20 lines
  - Grand total at end  
**Key COBOL Paragraphs:** 0550-DATEPARM-READ, 1100-WRITE-TRANSACTION-REPORT, 1110-WRITE-PAGE-TOTALS

---

### CbAct01Service (from CBACT01C.cbl)
**Purpose:** Read account file and write to multiple output formats  
**Inputs:** ACCTDATA.dat  
**Outputs:** ACCTOUT.dat, ACCTARRY.dat, ACCTVBRC.dat  
**Processing:**
- Reads account VSAM file sequentially
- For each account writes three output formats:
  - `ACCTOUT.dat` — flat formatted record with all account fields, reissue date converted
  - `ACCTARRY.dat` — array-style with 5 balance slots per account
  - `ACCTVBRC.dat` — variable-length records (VB1=12 bytes, VB2=39 bytes)
- Applies business rule: if CURR-CYC-DEBIT = 0, default to 2525.00  
**Key COBOL Paragraphs:** 1300-POPUL-ACCT-RECORD, 1400-POPUL-ARRAY-RECORD, 1500-POPUL-VBRC-RECORD

---

### Python JCL Scripts

| Script | Original JCL | Purpose |
|---|---|---|
| `acctfile.py` | ACCTFILE.jcl | Delete/Define/Load Account VSAM (3 steps) |
| `tcatbalf.py` | TCATBALF.jcl | Delete/Define/Load Transaction Category Balance VSAM |
| `posttran.py` | POSTTRAN.jcl | Orchestrates CBTRN02C — post daily transactions |
| `intcalc.py` | INTCALC.jcl | Orchestrates CBACT04C — compute interest and fees |

---

## 4. Repository Structure

```
carddemo-batch/
├── src/                          ← ORIGINAL MAINFRAME SOURCE (do not modify)
│   ├── cobol/
│   │   ├── CBACT01C.cbl          ← Account file utility
│   │   ├── CBTRN01C.cbl          ← Daily transaction reader
│   │   ├── CBTRN02C.cbl          ← Transaction posting engine
│   │   └── CBTRN03C.cbl          ← Transaction report generator
│   ├── copybooks/
│   │   ├── CVACT01Y.cpy          ← Account record (300 bytes)
│   │   ├── CVACT02Y.cpy          ← Card record (150 bytes)
│   │   ├── CVACT03Y.cpy          ← Card xref record (50 bytes)
│   │   ├── CVCUS01Y.cpy          ← Customer record (500 bytes)
│   │   ├── CVTRA01Y.cpy          ← Transaction category balance (50 bytes)
│   │   ├── CVTRA03Y.cpy          ← Transaction type (60 bytes)
│   │   ├── CVTRA04Y.cpy          ← Transaction category (60 bytes)
│   │   ├── CVTRA05Y.cpy          ← Transaction record (350 bytes)
│   │   ├── CVTRA06Y.cpy          ← Daily transaction record (350 bytes)
│   │   ├── CVTRA07Y.cpy          ← Report structures
│   │   └── CODATECN.cpy          ← Date conversion utility
│   └── jcl/
│       ├── ACCTFILE.jcl
│       ├── INTCALC.jcl
│       ├── POSTTRAN.jcl
│       └── TCATBALF.jcl
│
├── converted/                    ← MANUAL CONVERSION (reference implementation)
│   ├── java/
│   │   ├── AccountRecord.java
│   │   ├── CardRecord.java
│   │   ├── CardXrefRecord.java
│   │   ├── CustomerRecord.java
│   │   ├── TransactionRecords.java
│   │   ├── CbTrn01Service.java
│   │   ├── CbTrn02Service.java
│   │   ├── CbTrn03Service.java
│   │   └── CbAct01Service.java
│   └── python/
│       ├── acctfile.py
│       ├── intcalc.py
│       ├── posttran.py
│       └── tcatbalf.py
│
├── converted-usingAgent/         ← AGENT-GENERATED CONVERSION
│   ├── java/
│   │   ├── records/              ← Copybook Java classes
│   │   │   ├── AccountRecord.java
│   │   │   ├── CardRecord.java
│   │   │   ├── CardXrefRecord.java
│   │   │   ├── CustomerRecord.java
│   │   │   ├── DailyTranRecord.java
│   │   │   ├── DateConversionRecord.java
│   │   │   ├── ReportStructures.java
│   │   │   ├── TranCatBalRecord.java
│   │   │   ├── TranCatRecord.java
│   │   │   ├── TranRecord.java
│   │   │   └── TranTypeRecord.java
│   │   └── services/             ← COBOL Java service classes
│   │       ├── CbAct01Service.java
│   │       ├── CbTrn01Service.java
│   │       ├── CbTrn02Service.java
│   │       └── CbTrn03Service.java
│   ├── python/                   ← JCL Python scripts
│   │   ├── acctfile.py
│   │   ├── intcalc.py
│   │   ├── posttran.py
│   │   └── tcatbalf.py
│   └── tests/
│       ├── java/
│       │   ├── records/          ← JUnit 5 tests for record classes
│       │   └── services/         ← JUnit 5 tests for service classes
│       └── python/               ← pytest tests for Python scripts
│
├── agent/                        ← AGENTIC AI CONVERTER
│   ├── agent.py                  ← Main orchestrator
│   ├── converter.py              ← Claude API calls + prompts
│   ├── file_reader.py            ← Reads src/ files
│   ├── file_writer.py            ← Writes converted-usingAgent/ files
│   ├── requirements.txt          ← Python dependencies
│   └── .env                      ← API key (NOT in git — create manually)
│
└── data/                         ← TEST DATA (NOT in git)
    ├── DALYTRAN.dat
    ├── CARDXREF.dat
    ├── ACCTDATA.dat
    └── ...
```

---

## 5. Prerequisites

### Required Software

| Software | Version | Download |
|---|---|---|
| Java JDK | 17 (LTS) | https://www.oracle.com/java/technologies/downloads/#java17-windows |
| Python | 3.8+ | https://www.python.org/downloads/ |
| Git | Latest | https://git-scm.com/downloads |

### Verify Installation
```cmd
java -version
javac -version
python --version
git --version
```

Expected output:
```
java version "17.0.x"
javac 17.0.x
Python 3.x.x
git version 2.x.x
```

### Python Dependencies (for agent only)
```cmd
cd C:\Study\carddemo-batch\agent
pip install -r requirements.txt
```

---

## 6. Quick Start

### Clone the Repository
```cmd
cd C:\Study
git clone https://github.com/ugvenkat/carddemo-batch.git
cd carddemo-batch
```

### Create Data Directory
```cmd
mkdir data
```

### Create All Sample Input Files
Run these Python commands from `C:\Study\carddemo-batch`:

```cmd
python -c "
# ACCTDATA.dat - Account record (300 bytes)
# Layout: CVACT01Y.cpy
# Fields: acctId(11) + activeStatus(1) + currBal(13) + creditLimit(13) +
#         cashCreditLimit(13) + openDate(10) + expirationDate(10) +
#         reissueDate(10) + currCycCredit(13) + currCycDebit(13) +
#         addrZip(10) + groupId(10) + filler(173)
line = ('00000000001' + 'Y' + '+000000500000' + '+000001000000' + '+000000200000' +
        '2020-01-01' + '2030-12-31' + '2026-01-01' +
        '+000000100000' + '+000000050000' + '75001     ' + 'GROUP001  ' + ' '*173)
with open('data/ACCTDATA.dat', 'w', encoding='utf-8', newline='\n') as f:
    f.write(line + '\n')
print('ACCTDATA.dat created, length=' + str(len(line)))
"
```

```cmd
python -c "
# CARDXREF.dat - Card cross-reference record (50 bytes)
# Layout: CVACT03Y.cpy
# Fields: xrefCardNum(16) + xrefCustId(9) + xrefAcctId(11) + filler(14)
line = '4111111111111111' + '000000001' + '00000000001' + ' '*14
with open('data/CARDXREF.dat', 'w', encoding='utf-8', newline='\n') as f:
    f.write(line + '\n')
print('CARDXREF.dat created, length=' + str(len(line)))
"
```

```cmd
python -c "
# DALYTRAN.dat - Daily transaction record (350 bytes)
# Layout: CVTRA06Y.cpy
# Fields: dalytranId(16)+typeCd(2)+catCd(4)+source(10)+desc(100)+
#         amt(12)+merchantId(9)+merchantName(50)+merchantCity(50)+
#         merchantZip(10)+cardNum(16)+origTs(26)+procTs(26)+filler(19)
line = ('TRN0000000000001' + 'PU' + '0001' + 'ONLINE    ' +
        'TEST PURCHASE'.ljust(100) + '+00000100000' + '000000001' +
        'TEST MERCHANT'.ljust(50) + 'DALLAS'.ljust(50) + '75001     ' +
        '4111111111111111' + '2026-03-21-10.00.00.000000' +
        '2026-03-21-10.00.00.000000' + ' '*19)
with open('data/DALYTRAN.dat', 'w', encoding='utf-8', newline='\n') as f:
    f.write(line + '\n')
print('DALYTRAN.dat created, length=' + str(len(line)))
"
```

```cmd
python -c "
# TCATBALF.dat - Transaction category balance record (50 bytes)
# Layout: CVTRA01Y.cpy
# Fields: trancatAcctId(11)+trancatTypeCd(2)+trancatCd(4)+tranCatBal(12)+filler(22) -- wait, that's only 49, padding to 50
line = ('00000000001' + 'PU' + '0001' + '+00000000000' + ' '*22)
line = line[:50].ljust(50)
with open('data/TCATBALF.dat', 'w', encoding='utf-8', newline='\n') as f:
    f.write(line + '\n')
print('TCATBALF.dat created, length=' + str(len(line)))
"
```

```cmd
python -c "
# TRANTYPE.dat - Transaction type descriptions (60 bytes)
# Layout: CVTRA03Y.cpy - tranType(2)+tranTypeDesc(50)+filler(8)
line = 'PU' + 'Purchase'.ljust(50) + ' '*8
with open('data/TRANTYPE.dat', 'w', encoding='utf-8', newline='\n') as f:
    f.write(line + '\n')
print('TRANTYPE.dat created, length=' + str(len(line)))
"
```

```cmd
python -c "
# TRANCATG.dat - Transaction category descriptions (60 bytes)
# Layout: CVTRA04Y.cpy - tranTypeCd(2)+tranCatCd(4)+tranCatTypeDesc(50)+filler(4)
line = 'PU' + '0001' + 'Groceries'.ljust(50) + '    '
with open('data/TRANCATG.dat', 'w', encoding='utf-8', newline='\n') as f:
    f.write(line + '\n')
print('TRANCATG.dat created, length=' + str(len(line)))
"
```

```cmd
python -c "
# DATEPARM.dat - Date range for report (format: YYYY-MM-DD YYYY-MM-DD)
with open('data/DATEPARM.dat', 'w', encoding='utf-8', newline='\n') as f:
    f.write('2026-01-01 2026-12-31\n')
print('DATEPARM.dat created')
"
```

```cmd
python -c "
# ACCTDATA.PS - Flat file input for acctfile.py (same layout as ACCTDATA.dat)
import shutil
shutil.copy('data/ACCTDATA.dat', 'data/ACCTDATA.PS')
print('ACCTDATA.PS created')
"
```

```cmd
python -c "
# TCATBALF.PS - Flat file input for tcatbalf.py
import shutil
shutil.copy('data/TCATBALF.dat', 'data/TCATBALF.PS')
print('TCATBALF.PS created')
"
```

---

## 7. Creating Sample Input Files

See [Section 6 Quick Start](#6-quick-start) for all sample file creation commands.

### Data File Layout Reference

| File | Layout Copybook | Record Length | Key Field |
|---|---|---|---|
| ACCTDATA.dat | CVACT01Y.cpy | 300 bytes | acctId (11 digits) |
| CARDXREF.dat | CVACT03Y.cpy | 50 bytes | xrefCardNum (16 chars) |
| DALYTRAN.dat | CVTRA06Y.cpy | 350 bytes | dalytranId (16 chars) |
| TCATBALF.dat | CVTRA01Y.cpy | 50 bytes | acctId+typeCd+catCd |
| TRANSACT.dat | CVTRA05Y.cpy | 350 bytes | tranId (16 chars) |
| TRANTYPE.dat | CVTRA03Y.cpy | 60 bytes | tranType (2 chars) |
| TRANCATG.dat | CVTRA04Y.cpy | 60 bytes | typeCd+catCd |

### Important: PIC S9(n)V99 Field Format
Monetary fields are stored as **scaled integers without decimal point**:
- `1000.00` → stored as `+00000100000` (12 chars for S9(9)V99)
- `5000.00` → stored as `+000000500000` (13 chars for S9(10)V99)

---

## 8. Running the Manual Conversion

### Step 1 — Compile Record Classes
```cmd
cd C:\Study\carddemo-batch\converted\java
javac AccountRecord.java CardRecord.java CardXrefRecord.java CustomerRecord.java TransactionRecords.java
```

### Step 2 — Compile Service Classes
```cmd
javac CbTrn01Service.java CbTrn02Service.java CbTrn03Service.java CbAct01Service.java
```

### Step 3 — Set Environment Variables (PowerShell)
```powershell
$env:DALYTRAN = "C:\Study\carddemo-batch\data\DALYTRAN.dat"
$env:XREFFILE = "C:\Study\carddemo-batch\data\CARDXREF.dat"
$env:ACCTFILE = "C:\Study\carddemo-batch\data\ACCTDATA.dat"
$env:CUSTFILE = "C:\Study\carddemo-batch\data\CUSTDATA.dat"
$env:CARDFILE = "C:\Study\carddemo-batch\data\CARDDATA.dat"
$env:TRANFILE = "C:\Study\carddemo-batch\data\TRANSACT.dat"
$env:TCATBALF = "C:\Study\carddemo-batch\data\TCATBALF.dat"
$env:DALYREJS = "C:\Study\carddemo-batch\data\DALYREJS.dat"
$env:CARDXREF = "C:\Study\carddemo-batch\data\CARDXREF.dat"
$env:TRANTYPE = "C:\Study\carddemo-batch\data\TRANTYPE.dat"
$env:TRANCATG = "C:\Study\carddemo-batch\data\TRANCATG.dat"
$env:TRANREPT = "C:\Study\carddemo-batch\data\TRANREPT.txt"
$env:DATEPARM = "C:\Study\carddemo-batch\data\DATEPARM.dat"
$env:OUTFILE  = "C:\Study\carddemo-batch\data\ACCTOUT.dat"
$env:ARRYFILE = "C:\Study\carddemo-batch\data\ACCTARRY.dat"
$env:VBRCFILE = "C:\Study\carddemo-batch\data\ACCTVBRC.dat"
```

### Step 4 — Run Services in Order
```powershell
cd C:\Study\carddemo-batch\converted\java

# Step 1: Read and validate transactions
java CbTrn01Service

# Step 2: Post transactions (generates TRANSACT.dat)
java CbTrn02Service

# Step 3: Generate report (reads TRANSACT.dat)
java CbTrn03Service

# Step 4: Process account file
java CbAct01Service
```

### Step 5 — Verify Output Files
```powershell
type C:\Study\carddemo-batch\data\TRANREPT.txt
type C:\Study\carddemo-batch\data\ACCTOUT.dat
type C:\Study\carddemo-batch\data\ACCTVBRC.dat
```

---

## 9. Running the Agentic AI Converter

### Step 1 — Get a Claude API Key
1. Go to https://console.anthropic.com
2. Sign up / Log in
3. Click **API Keys** → **Create Key**
4. Copy the key (starts with `sk-ant-api03-...`)

### Step 2 — Create .env File
```cmd
cd C:\Study\carddemo-batch\agent
python -c "
key = input('Paste your Claude API key: ')
with open('.env', 'w', encoding='utf-8') as f:
    f.write(f'ANTHROPIC_API_KEY={key}\n')
print('Done!')
"
```

> ⚠️ **NEVER commit `.env` to git — it contains your secret API key!**

### Step 3 — Install Dependencies
```cmd
pip install -r requirements.txt
```

### Step 4 — Run the Agent (Full Clean Run)
```cmd
cd C:\Study\carddemo-batch\agent
python agent.py --src ..\src --out ..\converted-usingAgent --clean
```

### Step 5 — Run the Agent (Resume — Skip Already Converted)
```cmd
python agent.py --src ..\src --out ..\converted-usingAgent
```

### Agent Output Structure
```
converted-usingAgent/
  java/records/     ← 11 Java record classes (from copybooks)
  java/services/    ← 4 Java service classes (from COBOL)
  python/           ← 4 Python scripts (from JCL)
  tests/java/       ← JUnit 5 tests
  tests/python/     ← pytest tests
```

### Agent Processing Order (Critical)
The agent always processes in this order:
1. **Copybooks** → Java record classes (must be first — services depend on these)
2. **COBOL** → Java service classes (uses record classes)
3. **JCL** → Python scripts

---

## 10. Testing All Services (Agent-Generated)

### Step 1 — Compile Record Classes
```cmd
cd C:\Study\carddemo-batch\converted-usingAgent\java\records
javac *.java
```

### Step 2 — Compile Service Classes
```cmd
cd C:\Study\carddemo-batch\converted-usingAgent\java\services
javac -cp "C:\Study\carddemo-batch\converted-usingAgent\java\services;C:\Study\carddemo-batch\converted-usingAgent\java\records" *.java
```

### Step 3 — Set Environment Variables (PowerShell)
```powershell
$CP = "C:\Study\carddemo-batch\converted-usingAgent\java\services;C:\Study\carddemo-batch\converted-usingAgent\java\records"

$env:DALYTRAN = "C:\Study\carddemo-batch\data\DALYTRAN.dat"
$env:XREFFILE = "C:\Study\carddemo-batch\data\CARDXREF.dat"
$env:ACCTFILE = "C:\Study\carddemo-batch\data\ACCTDATA.dat"
$env:CUSTFILE = "C:\Study\carddemo-batch\data\CUSTDATA.dat"
$env:CARDFILE = "C:\Study\carddemo-batch\data\CARDDATA.dat"
$env:TRANFILE = "C:\Study\carddemo-batch\data\TRANSACT.dat"
$env:TCATBALF = "C:\Study\carddemo-batch\data\TCATBALF.dat"
$env:DALYREJS = "C:\Study\carddemo-batch\data\DALYREJS.dat"
$env:CARDXREF = "C:\Study\carddemo-batch\data\CARDXREF.dat"
$env:TRANTYPE = "C:\Study\carddemo-batch\data\TRANTYPE.dat"
$env:TRANCATG = "C:\Study\carddemo-batch\data\TRANCATG.dat"
$env:TRANREPT = "C:\Study\carddemo-batch\data\TRANREPT_AGENT.txt"
$env:DATEPARM = "C:\Study\carddemo-batch\data\DATEPARM.dat"
$env:OUTFILE  = "C:\Study\carddemo-batch\data\ACCTOUT_AGENT.dat"
$env:ARRYFILE = "C:\Study\carddemo-batch\data\ACCTARRY_AGENT.dat"
$env:VBRCFILE = "C:\Study\carddemo-batch\data\ACCTVBRC_AGENT.dat"
```

### Step 4 — Run Services in Order
```powershell
cd C:\Study\carddemo-batch\converted-usingAgent\java\services

# Test 1: Read and validate transactions
java -cp $CP CbTrn01Service

# Test 2: Post transactions
java -cp $CP CbTrn02Service

# Test 3: Generate transaction report
java -cp $CP CbTrn03Service

# Test 4: Process account file
java -cp $CP CbAct01Service
```

### Step 5 — Verify Output
```powershell
type C:\Study\carddemo-batch\data\TRANREPT_AGENT.txt
type C:\Study\carddemo-batch\data\ACCTOUT_AGENT.dat
type C:\Study\carddemo-batch\data\ACCTVBRC_AGENT.dat
```

### Expected Results

**CbTrn01Service:**
```
INFO: START OF EXECUTION OF PROGRAM CBTRN01C
INFO: DailyTranRecord{dalytranId='TRN0000000000001'...}
INFO: SUCCESSFUL READ OF XREF
INFO: ACCOUNT ID : 1
INFO: END OF EXECUTION OF PROGRAM CBTRN01C
```

**CbTrn02Service:**
```
INFO: START OF EXECUTION OF PROGRAM CBTRN02C
INFO: TRANSACTIONS PROCESSED :1
INFO: TRANSACTIONS REJECTED  :0
INFO: END OF EXECUTION OF PROGRAM CBTRN02C
```

**CbTrn03Service report:**
```
CBTRN03C    TRANSACTION DETAIL REPORT    DATE RANGE: 2026-01-01 to 2026-12-31
TRANSACTION ID   ACCOUNT ID  TYPE ...    AMOUNT
-------------------------------------------------------------------
TRN0000000000001 1           PU-Purchase ...
                                          PAGE TOTAL:    1,000.00
                                         GRAND TOTAL:    1,000.00
```

**CbAct01Service:**
```
INFO: START OF EXECUTION OF PROGRAM CBACT01C
INFO: ACCT-ID=00000000001 STATUS=Y
INFO: END OF EXECUTION OF PROGRAM CBACT01C
```

---

## 11. Testing Python JCL Scripts

### Set Environment Variables
```powershell
$env:ACCTDATA = "C:\Study\carddemo-batch\data\ACCTDATA.PS"
$env:ACCTVSAM = "C:\Study\carddemo-batch\data\ACCTDATA.VSAM.KSDS"
$env:ACCTMETA = "C:\Study\carddemo-batch\data\ACCTDATA.VSAM.KSDS.meta.json"
$env:TCATBAL  = "C:\Study\carddemo-batch\data\TCATBALF.PS"
$env:TCATBALV = "C:\Study\carddemo-batch\data\TCATBALF.VSAM.KSDS"
$env:TCATMETA = "C:\Study\carddemo-batch\data\TCATBALF.VSAM.KSDS.meta.json"
$env:TRANFILE = "C:\Study\carddemo-batch\data\TRANSACT.dat"
$env:DALYTRAN = "C:\Study\carddemo-batch\data\DALYTRAN.dat"
$env:XREFFILE = "C:\Study\carddemo-batch\data\CARDXREF.dat"
$env:ACCTFILE = "C:\Study\carddemo-batch\data\ACCTDATA.dat"
$env:TCATBALF = "C:\Study\carddemo-batch\data\TCATBALF.dat"
$env:DALYREJS = "C:\Study\carddemo-batch\data\DALYREJS.dat"
```

### Run Each Script
```powershell
cd C:\Study\carddemo-batch\converted-usingAgent\python

python acctfile.py    # Delete/Define/Load account VSAM
python tcatbalf.py    # Delete/Define/Load tran category balance VSAM
python posttran.py    # Post daily transactions (calls cbtrn02c.py — gracefully skipped)
python intcalc.py     # Calculate interest (calls cbact04c.py — gracefully skipped)
```

### Expected Output (acctfile.py)
```
[INFO] JOB ACCTFILE - START
[INFO] STEP05 - DELETE ACCOUNT VSAM FILE IF EXISTS
[INFO] STEP10 - DEFINE ACCOUNT VSAM FILE
[INFO]   CLUSTER PROPERTIES: KEYS(11 0) RECORDSIZE(300 300)
[INFO] STEP15 - COPY FLAT FILE TO VSAM
[INFO] JOB ACCTFILE - COMPLETED SUCCESSFULLY
```

---

## 12. Troubleshooting

### Problem: `ANTHROPIC_API_KEY not found`
**Fix:** Create `.env` file in `agent/` folder with your key:
```
ANTHROPIC_API_KEY=sk-ant-api03-your-key-here
```

### Problem: `UnicodeDecodeError` when loading .env
**Fix:** Recreate the `.env` file using Python:
```cmd
python -c "
key = input('Paste key: ')
with open('agent/.env', 'w', encoding='utf-8') as f:
    f.write(f'ANTHROPIC_API_KEY={key}\n')
"
```

### Problem: `Credit balance too low`
**Fix:** Add credits at https://console.anthropic.com/settings/billing  
The agent uses ~$0.50-$1.00 per full run of all 19 files.

### Problem: `javac: file not found` when compiling services
**Fix:** Always compile records first, then services:
```cmd
cd records && javac *.java
cd ..\services && javac -cp "..\records" *.java
```

### Problem: Java service — `card number could not be verified`
**Fix:** Check CARDXREF.dat has the same card number as DALYTRAN.dat:
```cmd
python -c "
xref = open('data/CARDXREF.dat').readline()
tran = open('data/DALYTRAN.dat').readline()
print('XREF card:', repr(xref[0:16]))
print('TRAN card:', repr(tran[263:279]))
print('Match:', xref[0:16] == tran[263:279])
"
```

### Problem: Transaction rejected with code 0103 (account expired)
**Fix:** Update expiration date in ACCTDATA.dat to a future date:
```cmd
python -c "
line = open('data/ACCTDATA.dat').readline().rstrip('\n')
print('Current expiry:', repr(line[61:71]))
# Should be 2030-12-31 or later
"
```

### Problem: Agent output files appear twice in summary
This is a display-only issue — files are only written once. The summary shows the count of unique files.

### Problem: `git push` blocked — secret detected
**Fix:** Remove `.env` from git history:
```cmd
git filter-branch --force --index-filter "git rm --cached --ignore-unmatch agent/.env" --prune-empty --tag-name-filter cat -- --all
git push origin main --force
```
Then rotate your API key immediately at https://console.anthropic.com

---

## Key Technical Notes

### COBOL to Java Field Mapping
| COBOL PIC | Java Type | Storage Format |
|---|---|---|
| `PIC X(n)` | `String` | Padded to n chars |
| `PIC 9(n)` n≤4 | `int` | Numeric string |
| `PIC 9(n)` n>4 | `long` | Numeric string |
| `PIC S9(n)V99` | `BigDecimal` | Scaled integer (×100, no decimal point) |
| `PIC S9(n)V99 COMP-3` | `BigDecimal` | Same as above (COMP-3 is storage only) |
| `FILLER` | (skipped) | Not mapped |

### JCL to Python Mapping
| JCL Concept | Python Equivalent |
|---|---|
| `JOB` statement | `main()` function |
| `EXEC PGM=name` | `subprocess.run(['python', 'name.py'])` |
| `DD DSN=file` | `os.getenv('DD_NAME', 'default')` |
| `DISP=SHR` | File must exist before running |
| `DISP=NEW,CATLG` | Create new file |
| `IDCAMS DELETE` | `os.remove()` with exists check |
| `IDCAMS DEFINE` | Create empty file + JSON metadata |
| `IDCAMS REPRO` | `shutil.copyfile()` |
| `GDG(+1)` | Timestamped filename |
| `SYSOUT=*` | Python `logging` to stdout |

---

*This project is based on the AWS CardDemo open source application, licensed under Apache 2.0.*  
*Original source: https://github.com/aws-samples/aws-mainframe-modernization-carddemo*
