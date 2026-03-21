/**
 * CbTrn01Service.java  (CORRECTED - uses copybook record classes)
 * -------------------
 * Java equivalent of CBTRN01C.CBL
 *
 * COBOL Program  : CBTRN01C.CBL
 * Application    : CardDemo
 * Type           : Batch COBOL Program
 * Function       : Read daily transaction file, look up card cross-reference
 *                  and account data, display/validate each transaction record.
 *
 * Copybooks used (now as proper Java classes):
 *   COPY CVTRA06Y  -> DailyTranRecord  (daily transaction input)
 *   COPY CVCUS01Y  -> CustomerRecord   (customer data)
 *   COPY CVACT03Y  -> CardXrefRecord   (card cross-reference)
 *   COPY CVACT02Y  -> CardRecord       (card data)
 *   COPY CVACT01Y  -> AccountRecord    (account data)
 *   COPY CVTRA05Y  -> TranRecord       (transaction master)
 *
 * Corrections from original version:
 *   - Removed inner XrefRecord, AccountRecord, CustomerRecord classes
 *   - Now uses shared copybook Java classes
 *   - Fixed field names:
 *       tran.cardNum   -> tran.dalyTranCardNum  (DailyTranRecord/CVTRA06Y)
 *       tran.tranId    -> tran.dalyTranId        (DailyTranRecord/CVTRA06Y)
 *       xref.cardNum   -> xref.xrefCardNum       (CardXrefRecord/CVACT03Y)
 *       xref.acctId    -> xref.xrefAcctId        (CardXrefRecord/CVACT03Y)
 *       acct.acctId    -> acct.acctId            (AccountRecord/CVACT01Y - OK)
 *
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * Licensed under the Apache License, Version 2.0
 */

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.logging.*;

public class CbTrn01Service {

    private static final Logger logger = Logger.getLogger(CbTrn01Service.class.getName());

    // -----------------------------------------------------------------------
    // File paths — mirror COBOL FILE-CONTROL SELECT ... ASSIGN TO <ddname>
    // -----------------------------------------------------------------------
    private final String dalyTranPath;  // DALYTRAN — daily transaction sequential file
    private final String custFilePath;  // CUSTFILE  — customer VSAM KSDS
    private final String xrefFilePath;  // XREFFILE  — card cross-reference VSAM KSDS
    private final String cardFilePath;  // CARDFILE  — card VSAM KSDS
    private final String acctFilePath;  // ACCTFILE  — account VSAM KSDS
    private final String tranFilePath;  // TRANFILE  — transaction master VSAM KSDS

    // -----------------------------------------------------------------------
    // Working storage — mirrors COBOL WORKING-STORAGE SECTION
    // -----------------------------------------------------------------------
    private boolean endOfDailyTransFile = false;  // END-OF-DAILY-TRANS-FILE
    private int     wsXrefReadStatus    = 0;      // WS-XREF-READ-STATUS
    private int     wsAcctReadStatus    = 0;      // WS-ACCT-READ-STATUS

    // In-memory VSAM maps — keyed by copybook record key fields
    private final Map<String, CardXrefRecord>  xrefIndex    = new HashMap<>(); // CVACT03Y key=xrefCardNum
    private final Map<Long,   AccountRecord>   accountIndex = new HashMap<>(); // CVACT01Y key=acctId
    private final Map<Long,   CustomerRecord>  custIndex    = new HashMap<>(); // CVCUS01Y key=custId
    private final Map<String, CardRecord>      cardIndex    = new HashMap<>(); // CVACT02Y key=cardNum

    private BufferedReader dalyTranReader;

    // -----------------------------------------------------------------------
    // Constructor
    // -----------------------------------------------------------------------
    public CbTrn01Service() {
        dalyTranPath = getEnv("DALYTRAN", "data/DALYTRAN.dat");
        custFilePath = getEnv("CUSTFILE",  "data/CUSTDATA.dat");
        xrefFilePath = getEnv("XREFFILE",  "data/CARDXREF.dat");
        cardFilePath = getEnv("CARDFILE",  "data/CARDDATA.dat");
        acctFilePath = getEnv("ACCTFILE",  "data/ACCTDATA.dat");
        tranFilePath = getEnv("TRANFILE",  "data/TRANSACT.dat");
    }

    // -----------------------------------------------------------------------
    // MAIN-PARA
    // -----------------------------------------------------------------------
    public void execute() {
        logger.info("START OF EXECUTION OF PROGRAM CBTRN01C");
        openFiles();

        while (!endOfDailyTransFile) {
            // 1000-DALYTRAN-GET-NEXT — returns DailyTranRecord (CVTRA06Y)
            DailyTranRecord tran = getNextDailyTran();
            if (tran == null) break;

            logger.info(tran.toString());  // DISPLAY DALYTRAN-RECORD

            // 2000-LOOKUP-XREF — CardXrefRecord (CVACT03Y)
            wsXrefReadStatus = 0;
            CardXrefRecord xref = lookupXref(tran.dalyTranCardNum); // CVTRA06Y: dalyTranCardNum

            if (xref == null) {
                wsXrefReadStatus = 1;
                logger.warning(String.format(
                    "CARD NUMBER %s COULD NOT BE VERIFIED. SKIPPING TRANSACTION ID-%s",
                    tran.dalyTranCardNum, tran.dalyTranId)); // CVTRA06Y fields
            } else {
                // 3000-READ-ACCOUNT — AccountRecord (CVACT01Y)
                wsAcctReadStatus = 0;
                AccountRecord acct = readAccount(xref.xrefAcctId); // CVACT03Y: xrefAcctId
                if (acct == null) {
                    wsAcctReadStatus = 1;
                    logger.warning(String.format(
                        "ACCOUNT %011d NOT FOUND", xref.xrefAcctId));
                }
            }
        }

        closeFiles();
        logger.info("END OF EXECUTION OF PROGRAM CBTRN01C");
    }

    // -----------------------------------------------------------------------
    // Open files — load VSAM KSDS files into HashMaps
    // -----------------------------------------------------------------------
    private void openFiles() {
        // XREFFILE  — CardXrefRecord (CVACT03Y), key = xrefCardNum
        loadIndex(xrefFilePath, line -> {
            CardXrefRecord r = CardXrefRecord.fromLine(line);
            if (r != null) xrefIndex.put(r.key(), r);
        }, "XREFFILE");

        // ACCTFILE  — AccountRecord (CVACT01Y), key = acctId
        loadIndex(acctFilePath, line -> {
            AccountRecord r = AccountRecord.fromLine(line);
            if (r != null) accountIndex.put(r.acctId, r);
        }, "ACCTFILE");

        // CARDFILE  — CardRecord (CVACT02Y), key = cardNum
        loadIndex(cardFilePath, line -> {
            CardRecord r = CardRecord.fromLine(line);
            if (r != null) cardIndex.put(r.cardNum == null ? "" : r.cardNum.trim(), r);
        }, "CARDFILE");

        // CUSTFILE  — CustomerRecord (CVCUS01Y), key = custId
        loadIndex(custFilePath, line -> {
            CustomerRecord r = CustomerRecord.fromLine(line);
            if (r != null) custIndex.put(r.custId, r);
        }, "CUSTFILE");

        logger.info("All files opened successfully");
    }

    private void loadIndex(String path, java.util.function.Consumer<String> loader, String name) {
        try (BufferedReader r = Files.newBufferedReader(Paths.get(path))) {
            String line;
            while ((line = r.readLine()) != null) loader.accept(line);
            logger.info(name + " loaded");
        } catch (IOException e) {
            logger.warning(name + " not found or empty: " + path);
        }
    }

    // -----------------------------------------------------------------------
    // Close files
    // -----------------------------------------------------------------------
    private void closeFiles() {
        xrefIndex.clear(); accountIndex.clear(); custIndex.clear(); cardIndex.clear();
        try { if (dalyTranReader != null) dalyTranReader.close(); } catch (IOException ignored) {}
        logger.info("All files closed successfully");
    }

    // -----------------------------------------------------------------------
    // 1000-DALYTRAN-GET-NEXT — returns DailyTranRecord (CVTRA06Y)
    // -----------------------------------------------------------------------
    private DailyTranRecord getNextDailyTran() {
        try {
            if (dalyTranReader == null)
                dalyTranReader = Files.newBufferedReader(Paths.get(dalyTranPath));
            String line = dalyTranReader.readLine();
            if (line == null) { endOfDailyTransFile = true; return null; }
            return DailyTranRecord.fromLine(line);
        } catch (IOException e) {
            abend("ERROR READING DALYTRAN FILE: " + e.getMessage());
            return null;
        }
    }

    // -----------------------------------------------------------------------
    // 2000-LOOKUP-XREF — CardXrefRecord (CVACT03Y), key = xrefCardNum
    // -----------------------------------------------------------------------
    private CardXrefRecord lookupXref(String cardNum) {
        if (cardNum == null) return null;
        return xrefIndex.get(cardNum.trim());
    }

    // -----------------------------------------------------------------------
    // 3000-READ-ACCOUNT — AccountRecord (CVACT01Y), key = acctId
    // -----------------------------------------------------------------------
    private AccountRecord readAccount(long acctId) {
        return accountIndex.get(acctId);
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

    public static void main(String[] args) { new CbTrn01Service().execute(); }
}
