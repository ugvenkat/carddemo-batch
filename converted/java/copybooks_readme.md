# Copybook → Java Record Classes Conversion Guide

## Overview
This document covers all 11 COBOL copybooks converted to Java for the `carddemo-batch` project.
In COBOL, copybooks define shared data structures included via `COPY` statements.
In Java, they become standalone record classes that COBOL programs import and use.

---

## Correct Conversion Order
```
1. Copybooks  → Java record classes   ← THIS FILE
2. COBOL      → Java service classes  (use copybook classes)
3. JCL        → Python scripts        (orchestrate Java services)
```

---

## Copybook to Java File Mapping

| Copybook | Java File | Java Class | Record Length |
|---|---|---|---|
| `CVACT01Y.cpy` | `AccountRecord.java` | `AccountRecord` | 300 bytes |
| `CVACT02Y.cpy` | `CardRecord.java` | `CardRecord` | 150 bytes |
| `CVACT03Y.cpy` | `CardXrefRecord.java` | `CardXrefRecord` | 50 bytes |
| `CVCUS01Y.cpy` | `CustomerRecord.java` | `CustomerRecord` | 500 bytes |
| `CVTRA01Y.cpy` | `TransactionRecords.java` | `TranCatBalRecord` | 50 bytes |
| `CVTRA03Y.cpy` | `TransactionRecords.java` | `TranTypeRecord` | 60 bytes |
| `CVTRA04Y.cpy` | `TransactionRecords.java` | `TranCatRecord` | 60 bytes |
| `CVTRA05Y.cpy` | `TransactionRecords.java` | `TranRecord` | 350 bytes |
| `CVTRA06Y.cpy` | `TransactionRecords.java` | `DailyTranRecord` | 350 bytes |
| `CVTRA07Y.cpy` | `TransactionRecords.java` | `ReportStructures` | 133 bytes |
| `CODATECN.cpy` | `TransactionRecords.java` | `DateConversionRecord` | utility |

---

## Which COBOL Program Uses Which Copybook

| Copybook | CBTRN01C | CBTRN02C | CBTRN03C | CBACT01C |
|---|---|---|---|---|
| `CVACT01Y` → `AccountRecord` | ✅ | ✅ | ❌ | ✅ |
| `CVACT02Y` → `CardRecord` | ✅ | ❌ | ❌ | ❌ |
| `CVACT03Y` → `CardXrefRecord` | ✅ | ✅ | ✅ | ❌ |
| `CVCUS01Y` → `CustomerRecord` | ✅ | ❌ | ❌ | ❌ |
| `CVTRA01Y` → `TranCatBalRecord` | ❌ | ✅ | ❌ | ❌ |
| `CVTRA03Y` → `TranTypeRecord` | ❌ | ❌ | ✅ | ❌ |
| `CVTRA04Y` → `TranCatRecord` | ❌ | ❌ | ✅ | ❌ |
| `CVTRA05Y` → `TranRecord` | ✅ | ✅ | ✅ | ❌ |
| `CVTRA06Y` → `DailyTranRecord` | ✅ | ✅ | ❌ | ❌ |
| `CVTRA07Y` → `ReportStructures` | ❌ | ❌ | ✅ | ❌ |
| `CODATECN` → `DateConversionRecord` | ❌ | ❌ | ❌ | ✅ |

---

## COBOL to Java Concept Mapping

| COBOL Concept | Java Equivalent |
|---|---|
| `COPY copybook` | `import` / use of Java class |
| `01 RECORD-NAME` | `public class RecordName` |
| `05 FIELD PIC X(n)` | `public String field` |
| `05 FIELD PIC 9(n)` | `public long field` or `public int field` |
| `05 FIELD PIC S9(n)V99` | `public BigDecimal field` |
| `05 FIELD PIC 9(n) COMP` | `public int field` (binary) |
| `05 FILLER PIC X(n)` | Skipped — not mapped |
| `10 SUB-FIELD` (nested) | Flattened into class fields |
| `88 CONDITION VALUE` | `public static final String` constant |
| `REDEFINES` | Alternative parsing method |
| Fixed record length | `public static final int RECORD_LENGTH` |
| `RECORD KEY IS FD-xxx` | `public String key()` method |
| `READ file INTO record` | `RecordClass.fromLine(String line)` |
| `WRITE record FROM data` | `record.toLine()` |

---

## Field Size Reference

| COBOL PIC | Java Type | Bytes | Notes |
|---|---|---|---|
| `PIC X(n)` | `String` | n | Padded to n chars |
| `PIC 9(9)` | `long` | 9 digits | Customer/SSN IDs |
| `PIC 9(11)` | `long` | 11 digits | Account IDs |
| `PIC 9(16)` | `String` | 16 chars | Card numbers (kept as String) |
| `PIC S9(10)V99` | `BigDecimal` | 13 chars | Signed decimal with 2 decimals |
| `PIC S9(9)V99` | `BigDecimal` | 12 chars | Signed decimal with 2 decimals |
| `PIC 9(3) COMP` | `int` | 3 digits | FICO score |
| `PIC 9(4) COMP` | `int` | 4 digits | Category codes |

---

## Folder Structure

```
carddemo-batch/
  src/
    copybooks/                     <- original COBOL copybooks (source of truth)
      CVACT01Y.cpy
      CVACT02Y.cpy
      CVACT03Y.cpy
      CVCUS01Y.cpy
      CVTRA01Y.cpy
      CVTRA03Y.cpy
      CVTRA04Y.cpy
      CVTRA05Y.cpy
      CVTRA06Y.cpy
      CVTRA07Y.cpy
      CODATECN.cpy
  converted/
    java/
      AccountRecord.java           <- CVACT01Y
      CardRecord.java              <- CVACT02Y
      CardXrefRecord.java          <- CVACT03Y
      CustomerRecord.java          <- CVCUS01Y
      TransactionRecords.java      <- CVTRA01Y, 03Y, 04Y, 05Y, 06Y, 07Y, CODATECN
      CbTrn01Service.java          <- uses AccountRecord, CardXrefRecord, CustomerRecord, TranRecord, DailyTranRecord
      CbTrn02Service.java          <- uses AccountRecord, CardXrefRecord, TranRecord, DailyTranRecord, TranCatBalRecord
      CbTrn03Service.java          <- uses CardXrefRecord, TranRecord, TranTypeRecord, TranCatRecord, ReportStructures
      CbAct01Service.java          <- uses AccountRecord, DateConversionRecord
```

---

## Setup Instructions

### Step 1 — Copy converted files
```cmd
copy AccountRecord.java        C:\Study\carddemo-batch\converted\java\
copy CardRecord.java           C:\Study\carddemo-batch\converted\java\
copy CardXrefRecord.java       C:\Study\carddemo-batch\converted\java\
copy CustomerRecord.java       C:\Study\carddemo-batch\converted\java\
copy TransactionRecords.java   C:\Study\carddemo-batch\converted\java\
```

### Step 2 — Compile record classes first
```cmd
cd C:\Study\carddemo-batch\converted\java
javac AccountRecord.java CardRecord.java CardXrefRecord.java CustomerRecord.java TransactionRecords.java
```

### Step 3 — Then compile COBOL service classes
```cmd
javac CbTrn01Service.java CbTrn02Service.java CbTrn03Service.java CbAct01Service.java
```

### Step 4 — Commit to GitHub
```cmd
cd C:\Study\carddemo-batch
git add .
git commit -m "Add copybook Java record classes - AccountRecord, CardRecord, CardXrefRecord, CustomerRecord, TransactionRecords"
git push
```

---

## Important Notes

### Why CVTRA05Y and CVTRA06Y are separate classes
`CVTRA05Y` (TRAN-RECORD) is the **transaction master** — records already posted.
`CVTRA06Y` (DALYTRAN-RECORD) is the **daily transaction input** — incoming records to be validated and posted. They have identical layouts but different field name prefixes (`TRAN-` vs `DALYTRAN-`) which is why COBOL had two separate copybooks and Java has two separate classes.

### CODATECN — Date Conversion Utility
In COBOL, `CALL 'COBDATFT' USING CODATECN-REC` calls a mainframe utility program.
In Java, `DateConversionRecord.convert()` replaces this call entirely using `java.time.LocalDate`.

### CVTRA07Y — Report Structures
Unlike the other copybooks which define data records, CVTRA07Y defines **report layout structures** with hardcoded VALUE clauses. In Java these become static constants and static builder methods in the `ReportStructures` class.

---

## References
- Original copybooks: `src/copybooks/`
- Apache 2.0 License — original source: https://github.com/aws-samples/aws-mainframe-modernization-carddemo
