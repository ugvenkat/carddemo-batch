/**
 * CbTrn02Service.java  (CORRECTED - uses copybook record classes)
 * -------------------
 * Java equivalent of CBTRN02C.CBL
 *
 * COBOL Program  : CBTRN02C.CBL
 * Application    : CardDemo
 * Type           : Batch COBOL Program
 * Function       : Validate and post daily transactions, update account
 *                  balances and transaction category balances, write rejects.
 *
 * Copybooks used (now as proper Java classes):
 *   COPY CVTRA06Y  -> DailyTranRecord   (daily transaction input)
 *   COPY CVTRA05Y  -> TranRecord        (transaction master output)
 *   COPY CVACT03Y  -> CardXrefRecord    (card cross-reference)
 *   COPY CVACT01Y  -> AccountRecord     (account data)
 *   COPY CVTRA01Y  -> TranCatBalRecord  (transaction category balance)
 *
 * Corrections from original version:
 *   - Removed all inner record classes
 *   - Fixed DailyTranRecord field names:
 *       tran.tranId        -> tran.dalyTranId
 *       tran.cardNum       -> tran.dalyTranCardNum
 *       tran.typeCd        -> tran.dalyTranTypeCd
 *       tran.catCd         -> tran.dalyTranCatCd
 *       tran.source        -> tran.dalyTranSource
 *       tran.desc          -> tran.dalyTranDesc
 *       tran.amt           -> tran.dalyTranAmt
 *       tran.merchantId    -> tran.dalyTranMerchantId
 *       tran.merchantName  -> tran.dalyTranMerchantName
 *       tran.merchantCity  -> tran.dalyTranMerchantCity
 *       tran.merchantZip   -> tran.dalyTranMerchantZip
 *       tran.origTs        -> tran.dalyTranOrigTs
 *   - Fixed CardXrefRecord field names:
 *       xref.acctId        -> xref.xrefAcctId
 *       xref.cardNum       -> xref.xrefCardNum
 *   - Fixed TranCatBalRecord field names:
 *       tc.acctId          -> tc.tranCatAcctId
 *       tc.typeCd          -> tc.tranCatTypeCd
 *       tc.catCd           -> tc.tranCatCd
 *       tc.balance         -> tc.tranCatBal
 *   - Fixed TranRecord field names:
 *       tr.typeCd          -> tr.tranTypeCd
 *       tr.catCd           -> tr.tranCatCd
 *       tr.source          -> tr.tranSource
 *       tr.desc            -> tr.tranDesc
 *       tr.amt             -> tr.tranAmt
 *       tr.merchantId      -> tr.tranMerchantId
 *       tr.merchantName    -> tr.tranMerchantName
 *       tr.merchantCity    -> tr.tranMerchantCity
 *       tr.merchantZip     -> tr.tranMerchantZip
 *       tr.cardNum         -> tr.tranCardNum
 *       tr.origTs          -> tr.tranOrigTs
 *       tr.procTs          -> tr.tranProcTs
 *
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * Licensed under the Apache License, Version 2.0
 */

import java.io.*;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.logging.*;

public class CbTrn02Service {

    private static final Logger logger = Logger.getLogger(CbTrn02Service.class.getName());

    // Validation reason codes — mirror COBOL WS-VALIDATION-FAIL-REASON
    private static final int VALID            = 0;
    private static final int INVALID_CARD_NUM = 100;
    private static final int ACCOUNT_NOT_FOUND = 101;
    private static final int OVER_LIMIT        = 102;
    private static final int ACCOUNT_EXPIRED   = 103;

    // -----------------------------------------------------------------------
    // File paths
    // -----------------------------------------------------------------------
    private final String dalyTranPath;  // DALYTRAN — input
    private final String tranFilePath;  // TRANFILE  — transaction master output
    private final String xrefFilePath;  // XREFFILE  — card xref
    private final String dalyRejsPath;  // DALYREJS  — rejects output
    private final String acctFilePath;  // ACCTFILE  — account (I-O)
    private final String tcatBalfPath;  // TCATBALF  — tran category balance (I-O)

    // -----------------------------------------------------------------------
    // Working storage
    // -----------------------------------------------------------------------
    private boolean endOfFile        = false;
    private int     transactionCount = 0;  // WS-TRANSACTION-COUNT
    private int     rejectCount      = 0;  // WS-REJECT-COUNT

    // In-memory VSAM maps — keyed by copybook record key fields
    private final Map<String, CardXrefRecord>   xrefIndex    = new HashMap<>(); // CVACT03Y
    private final Map<Long,   AccountRecord>    accountIndex = new HashMap<>(); // CVACT01Y
    private final Map<String, TranCatBalRecord> tcatBalIndex = new HashMap<>(); // CVTRA01Y

    private BufferedReader dalyTranReader;

    // -----------------------------------------------------------------------
    // Validation trailer — mirrors WS-VALIDATION-TRAILER
    // -----------------------------------------------------------------------
    static class ValidationTrailer {
        int    failReason;       // WS-VALIDATION-FAIL-REASON      PIC 9(04)
        String failReasonDesc;   // WS-VALIDATION-FAIL-REASON-DESC PIC X(76)
    }

    // -----------------------------------------------------------------------
    // Constructor
    // -----------------------------------------------------------------------
    public CbTrn02Service() {
        dalyTranPath = getEnv("DALYTRAN", "data/DALYTRAN.dat");
        tranFilePath = getEnv("TRANFILE",  "data/TRANSACT.dat");
        xrefFilePath = getEnv("XREFFILE",  "data/CARDXREF.dat");
        dalyRejsPath = getEnv("DALYREJS",  "data/DALYREJS.dat");
        acctFilePath = getEnv("ACCTFILE",  "data/ACCTDATA.dat");
        tcatBalfPath = getEnv("TCATBALF",  "data/TCATBALF.dat");
    }

    // -----------------------------------------------------------------------
    // PROCEDURE DIVISION
    // -----------------------------------------------------------------------
    public void execute() {
        logger.info("START OF EXECUTION OF PROGRAM CBTRN02C");
        openFiles();

        while (!endOfFile) {
            // 1000-DALYTRAN-GET-NEXT — DailyTranRecord (CVTRA06Y)
            DailyTranRecord tran = getNextDailyTran();
            if (tran == null) break;

            transactionCount++;

            ValidationTrailer vt = validateTran(tran); // 1500-VALIDATE-TRAN

            if (vt.failReason == VALID) {
                postTransaction(tran);          // 2000-POST-TRANSACTION
            } else {
                rejectCount++;
                writeRejectRecord(tran, vt);    // 2500-WRITE-REJECT-REC
            }
        }

        closeFiles();

        logger.info(String.format("TRANSACTIONS PROCESSED : %09d", transactionCount));
        logger.info(String.format("TRANSACTIONS REJECTED  : %09d", rejectCount));
        if (rejectCount > 0) logger.warning("RETURN CODE 4 - rejects exist");
        logger.info("END OF EXECUTION OF PROGRAM CBTRN02C");
    }

    // -----------------------------------------------------------------------
    // Open files
    // -----------------------------------------------------------------------
    private void openFiles() {
        // CardXrefRecord (CVACT03Y) — key = xrefCardNum
        loadIndex(xrefFilePath, line -> {
            CardXrefRecord r = CardXrefRecord.fromLine(line);
            if (r != null) xrefIndex.put(r.key(), r);
        }, "XREFFILE");

        // AccountRecord (CVACT01Y) — key = acctId
        loadIndex(acctFilePath, line -> {
            AccountRecord r = AccountRecord.fromLine(line);
            if (r != null) accountIndex.put(r.acctId, r);
        }, "ACCTFILE");

        // TranCatBalRecord (CVTRA01Y) — key = tranCatAcctId+tranCatTypeCd+tranCatCd
        loadIndex(tcatBalfPath, line -> {
            TranCatBalRecord r = TranCatBalRecord.fromLine(line);
            if (r != null) tcatBalIndex.put(r.key(), r);
        }, "TCATBALF");

        logger.info("All files opened");
    }

    private void loadIndex(String path, java.util.function.Consumer<String> loader, String name) {
        try (BufferedReader r = Files.newBufferedReader(Paths.get(path))) {
            String line;
            while ((line = r.readLine()) != null) loader.accept(line);
            logger.info(name + " loaded");
        } catch (IOException e) { logger.warning(name + " not found: " + path); }
    }

    // -----------------------------------------------------------------------
    // Close files — flush updated records back to disk
    // -----------------------------------------------------------------------
    private void closeFiles() {
        flushAccountFile();
        flushTcatBalfFile();
        try { if (dalyTranReader != null) dalyTranReader.close(); } catch (IOException ignored) {}
        logger.info("All files closed");
    }

    private void flushAccountFile() {
        try (BufferedWriter w = Files.newBufferedWriter(Paths.get(acctFilePath))) {
            for (AccountRecord r : accountIndex.values()) { w.write(r.toLine()); w.newLine(); }
        } catch (IOException e) { logger.warning("Error flushing ACCTFILE"); }
    }

    private void flushTcatBalfFile() {
        try (BufferedWriter w = Files.newBufferedWriter(Paths.get(tcatBalfPath))) {
            for (TranCatBalRecord r : tcatBalIndex.values()) { w.write(r.toLine()); w.newLine(); }
        } catch (IOException e) { logger.warning("Error flushing TCATBALF"); }
    }

    // -----------------------------------------------------------------------
    // 1000-DALYTRAN-GET-NEXT — DailyTranRecord (CVTRA06Y)
    // -----------------------------------------------------------------------
    private DailyTranRecord getNextDailyTran() {
        try {
            if (dalyTranReader == null)
                dalyTranReader = Files.newBufferedReader(Paths.get(dalyTranPath));
            String line = dalyTranReader.readLine();
            if (line == null) { endOfFile = true; return null; }
            return DailyTranRecord.fromLine(line);
        } catch (IOException e) { abend("ERROR READING DALYTRAN: " + e.getMessage()); return null; }
    }

    // -----------------------------------------------------------------------
    // 1500-VALIDATE-TRAN
    // -----------------------------------------------------------------------
    private ValidationTrailer validateTran(DailyTranRecord tran) {
        ValidationTrailer vt = new ValidationTrailer();
        vt.failReason = VALID;
        vt.failReasonDesc = "";

        // 1500-A — CardXrefRecord (CVACT03Y), key = xrefCardNum
        CardXrefRecord xref = xrefIndex.get(
            tran.dalyTranCardNum == null ? "" : tran.dalyTranCardNum.trim());
        if (xref == null) {
            vt.failReason = INVALID_CARD_NUM;
            vt.failReasonDesc = "INVALID CARD NUMBER FOUND";
            return vt;
        }

        // 1500-B — AccountRecord (CVACT01Y), key = acctId
        AccountRecord acct = accountIndex.get(xref.xrefAcctId);
        if (acct == null) {
            vt.failReason = ACCOUNT_NOT_FOUND;
            vt.failReasonDesc = "ACCOUNT RECORD NOT FOUND";
            return vt;
        }

        // Credit limit check — uses AccountRecord (CVACT01Y) fields
        BigDecimal tempBal = acct.currCycCredit
            .subtract(acct.currCycDebit)
            .add(tran.dalyTranAmt);
        if (acct.creditLimit.compareTo(tempBal) < 0) {
            vt.failReason = OVER_LIMIT;
            vt.failReasonDesc = "OVERLIMIT TRANSACTION";
            return vt;
        }

        // Expiry check — uses AccountRecord.expirationDate and DailyTranRecord.dalyTranOrigTs
        String tranDate = tran.dalyTranOrigTs != null && tran.dalyTranOrigTs.length() >= 10
            ? tran.dalyTranOrigTs.substring(0, 10) : "";
        if (!tranDate.isEmpty() && acct.expirationDate != null
                && acct.expirationDate.trim().compareTo(tranDate) < 0) {
            vt.failReason = ACCOUNT_EXPIRED;
            vt.failReasonDesc = "TRANSACTION RECEIVED AFTER ACCT EXPIRATION";
        }

        return vt;
    }

    // -----------------------------------------------------------------------
    // 2000-POST-TRANSACTION
    // -----------------------------------------------------------------------
    private void postTransaction(DailyTranRecord tran) {
        CardXrefRecord xref = xrefIndex.get(
            tran.dalyTranCardNum == null ? "" : tran.dalyTranCardNum.trim());

        // Build TranRecord (CVTRA05Y) from DailyTranRecord (CVTRA06Y)
        // Mirrors COBOL MOVEs from DALYTRAN-xxx TO TRAN-xxx
        TranRecord tr = new TranRecord();
        tr.tranId           = tran.dalyTranId;
        tr.tranTypeCd       = tran.dalyTranTypeCd;
        tr.tranCatCd        = tran.dalyTranCatCd;
        tr.tranSource       = tran.dalyTranSource;
        tr.tranDesc         = tran.dalyTranDesc;
        tr.tranAmt          = tran.dalyTranAmt;
        tr.tranMerchantId   = tran.dalyTranMerchantId;
        tr.tranMerchantName = tran.dalyTranMerchantName;
        tr.tranMerchantCity = tran.dalyTranMerchantCity;
        tr.tranMerchantZip  = tran.dalyTranMerchantZip;
        tr.tranCardNum      = tran.dalyTranCardNum;
        tr.tranOrigTs       = tran.dalyTranOrigTs;
        tr.tranProcTs       = getDb2FormatTimestamp(); // Z-GET-DB2-FORMAT-TIMESTAMP

        updateTcatBal(xref, tran);    // 2700-UPDATE-TCATBAL
        updateAccountRec(xref, tran); // 2800-UPDATE-ACCOUNT-REC
        writeTransactionFile(tr);     // 2900-WRITE-TRANSACTION-FILE
    }

    // -----------------------------------------------------------------------
    // 2700-UPDATE-TCATBAL — TranCatBalRecord (CVTRA01Y)
    // -----------------------------------------------------------------------
    private void updateTcatBal(CardXrefRecord xref, DailyTranRecord tran) {
        // Build key from CVTRA01Y fields: tranCatAcctId + tranCatTypeCd + tranCatCd
        TranCatBalRecord tc = new TranCatBalRecord();
        tc.tranCatAcctId = xref.xrefAcctId;         // CVACT03Y: xrefAcctId
        tc.tranCatTypeCd = tran.dalyTranTypeCd;     // CVTRA06Y: dalyTranTypeCd
        tc.tranCatCd     = tran.dalyTranCatCd;      // CVTRA06Y: dalyTranCatCd

        TranCatBalRecord existing = tcatBalIndex.get(tc.key());
        if (existing == null) {
            logger.info("TCATBAL record not found for key: " + tc.key() + ".. Creating.");
            tc.tranCatBal = tran.dalyTranAmt;
            tcatBalIndex.put(tc.key(), tc);
        } else {
            // 2700-B-UPDATE-TCATBAL-REC — update balance
            existing.tranCatBal = existing.tranCatBal.add(tran.dalyTranAmt);
        }
    }

    // -----------------------------------------------------------------------
    // 2800-UPDATE-ACCOUNT-REC — AccountRecord (CVACT01Y)
    // -----------------------------------------------------------------------
    private void updateAccountRec(CardXrefRecord xref, DailyTranRecord tran) {
        AccountRecord acct = accountIndex.get(xref.xrefAcctId); // CVACT03Y: xrefAcctId
        if (acct != null) {
            // Mirrors: ADD DALYTRAN-AMT TO ACCT-CURR-CYC-DEBIT
            acct.currCycDebit = acct.currCycDebit.add(tran.dalyTranAmt); // CVACT01Y: currCycDebit
        }
    }

    // -----------------------------------------------------------------------
    // 2900-WRITE-TRANSACTION-FILE — TranRecord (CVTRA05Y)
    // -----------------------------------------------------------------------
    private void writeTransactionFile(TranRecord tr) {
        try (BufferedWriter w = Files.newBufferedWriter(
                Paths.get(tranFilePath), StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            w.write(tr.toLine());
            w.newLine();
        } catch (IOException e) { abend("ERROR WRITING TRANSACTION FILE: " + e.getMessage()); }
    }

    // -----------------------------------------------------------------------
    // 2500-WRITE-REJECT-REC — uses DailyTranRecord.rawLine
    // -----------------------------------------------------------------------
    private void writeRejectRecord(DailyTranRecord tran, ValidationTrailer vt) {
        try (BufferedWriter w = Files.newBufferedWriter(
                Paths.get(dalyRejsPath), StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            String line = String.format("%-350s%04d%-76s",
                tran.rawLine, vt.failReason, vt.failReasonDesc);
            w.write(line);
            w.newLine();
            logger.warning(String.format("REJECT: ID=%s REASON=%04d %s",
                tran.dalyTranId, vt.failReason, vt.failReasonDesc));
        } catch (IOException e) { abend("ERROR WRITING REJECT RECORD: " + e.getMessage()); }
    }

    // -----------------------------------------------------------------------
    // Z-GET-DB2-FORMAT-TIMESTAMP
    // -----------------------------------------------------------------------
    private String getDb2FormatTimestamp() {
        return DateTimeFormatter.ofPattern("yyyy-MM-dd-HH.mm.ss.SS'0000'")
            .format(LocalDateTime.now());
    }

    // -----------------------------------------------------------------------
    // Z-ABEND-PROGRAM
    // -----------------------------------------------------------------------
    private void abend(String reason) {
        logger.severe("ABENDING PROGRAM: " + reason);
        throw new RuntimeException("ABEND: " + reason);
    }

    private static String getEnv(String key, String def) {
        String v = System.getenv(key);
        return (v != null && !v.isEmpty()) ? v : def;
    }

    public static void main(String[] args) { new CbTrn02Service().execute(); }
}
