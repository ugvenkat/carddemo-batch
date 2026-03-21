/**
 * CbTrn03Service.java  (CORRECTED - uses copybook record classes)
 * -------------------
 * Java equivalent of CBTRN03C.CBL
 *
 * COBOL Program  : CBTRN03C.CBL
 * Application    : CardDemo
 * Type           : Batch COBOL Program
 * Function       : Print transaction detail report filtered by date range.
 *
 * Copybooks used (now as proper Java classes):
 *   COPY CVTRA05Y  -> TranRecord        (transaction master)
 *   COPY CVACT03Y  -> CardXrefRecord    (card cross-reference)
 *   COPY CVTRA03Y  -> TranTypeRecord    (transaction type descriptions)
 *   COPY CVTRA04Y  -> TranCatRecord     (transaction category descriptions)
 *   COPY CVTRA07Y  -> ReportStructures  (report headers and totals)
 *
 * Corrections from original version:
 *   - Removed all inner record classes
 *   - Fixed TranRecord field names:
 *       tran.typeCd    -> tran.tranTypeCd
 *       tran.catCd     -> tran.tranCatCd
 *       tran.amt       -> tran.tranAmt
 *       tran.cardNum   -> tran.tranCardNum
 *       tran.procTs    -> tran.tranProcTs
 *       tran.tranId    -> tran.tranId        (OK — same)
 *       tran.source    -> tran.tranSource
 *   - Fixed CardXrefRecord field names:
 *       xref.acctId    -> xref.xrefAcctId
 *   - Fixed TranTypeRecord field names:
 *       tt.typeCd      -> tt.tranType
 *       tt.typeDesc    -> tt.tranTypeDesc
 *   - Fixed TranCatRecord field names:
 *       tc.catDesc     -> tc.tranCatTypeDesc
 *   - Now uses ReportStructures class (CVTRA07Y) for all report line building
 *
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * Licensed under the Apache License, Version 2.0
 */

import java.io.*;
import java.math.BigDecimal;
import java.nio.file.*;
import java.util.*;
import java.util.logging.*;

public class CbTrn03Service {

    private static final Logger logger = Logger.getLogger(CbTrn03Service.class.getName());

    // -----------------------------------------------------------------------
    // File paths
    // -----------------------------------------------------------------------
    private final String tranFilePath;    // TRANFILE  — transaction master (sequential)
    private final String xrefFilePath;    // CARDXREF  — card cross-reference
    private final String tranTypePath;    // TRANTYPE  — transaction type descriptions
    private final String tranCatgPath;    // TRANCATG  — transaction category descriptions
    private final String reportFilePath;  // TRANREPT  — output report
    private final String dateParmPath;    // DATEPARM  — date range parameters

    // -----------------------------------------------------------------------
    // Working storage — mirrors COBOL WS-REPORT-VARS
    // -----------------------------------------------------------------------
    private boolean    endOfFile    = false;
    private boolean    firstTime    = true;              // WS-FIRST-TIME VALUE 'Y'
    private int        lineCounter  = 0;                 // WS-LINE-COUNTER
    private BigDecimal pageTotal    = BigDecimal.ZERO;   // WS-PAGE-TOTAL
    private BigDecimal accountTotal = BigDecimal.ZERO;   // WS-ACCOUNT-TOTAL
    private BigDecimal grandTotal   = BigDecimal.ZERO;   // WS-GRAND-TOTAL
    private String     currCardNum  = "";                // WS-CURR-CARD-NUM
    private String     wsStartDate  = "0000-00-00";      // WS-START-DATE
    private String     wsEndDate    = "9999-99-99";      // WS-END-DATE

    // In-memory VSAM maps — keyed by copybook record key fields
    private final Map<String, CardXrefRecord>  xrefIndex     = new HashMap<>(); // CVACT03Y
    private final Map<String, TranTypeRecord>  tranTypeIndex = new HashMap<>(); // CVTRA03Y
    private final Map<String, TranCatRecord>   tranCatgIndex = new HashMap<>(); // CVTRA04Y

    // Current lookup results
    private CardXrefRecord  currentXref;
    private TranTypeRecord  currentTranType;
    private TranCatRecord   currentTranCatg;

    private BufferedWriter reportWriter;
    private BufferedReader tranReader;

    // -----------------------------------------------------------------------
    // Constructor
    // -----------------------------------------------------------------------
    public CbTrn03Service() {
        tranFilePath   = getEnv("TRANFILE",  "data/TRANSACT.dat");
        xrefFilePath   = getEnv("CARDXREF",  "data/CARDXREF.dat");
        tranTypePath   = getEnv("TRANTYPE",  "data/TRANTYPE.dat");
        tranCatgPath   = getEnv("TRANCATG",  "data/TRANCATG.dat");
        reportFilePath = getEnv("TRANREPT",  "data/TRANREPT.txt");
        dateParmPath   = getEnv("DATEPARM",  "data/DATEPARM.dat");
    }

    // -----------------------------------------------------------------------
    // PROCEDURE DIVISION
    // -----------------------------------------------------------------------
    public void execute() {
        logger.info("START OF EXECUTION OF PROGRAM CBTRN03C");
        openFiles();
        readDateParams();  // 0550-DATEPARM-READ

        while (!endOfFile) {
            // 1000-TRANFILE-GET-NEXT — TranRecord (CVTRA05Y)
            TranRecord tran = getNextTransaction();
            if (tran == null) break;

            // Date range filter — TRAN-PROC-TS(1:10) >= WS-START-DATE
            String tranDate = tran.tranProcTs != null && tran.tranProcTs.length() >= 10
                ? tran.tranProcTs.substring(0, 10) : "";

            if (tranDate.isEmpty()
                    || tranDate.compareTo(wsStartDate) < 0
                    || tranDate.compareTo(wsEndDate)   > 0) {
                continue;  // NEXT SENTENCE — skip out-of-range transactions
            }

            logger.fine(tran.toString());  // DISPLAY TRAN-RECORD

            // Account break — WS-CURR-CARD-NUM NOT= TRAN-CARD-NUM
            if (!currCardNum.equals(tran.tranCardNum == null ? "" : tran.tranCardNum.trim())) {
                if (!firstTime) writeAccountTotals();  // 1120-WRITE-ACCOUNT-TOTALS
                currCardNum = tran.tranCardNum == null ? "" : tran.tranCardNum.trim();
                // 1500-A-LOOKUP-XREF — CardXrefRecord (CVACT03Y)
                currentXref = lookupXref(tran.tranCardNum);
                firstTime = false;
            }

            // 1500-B-LOOKUP-TRANTYPE — TranTypeRecord (CVTRA03Y)
            currentTranType = lookupTranType(tran.tranTypeCd);

            // 1500-C-LOOKUP-TRANCATG — TranCatRecord (CVTRA04Y)
            currentTranCatg = lookupTranCatg(tran.tranTypeCd, tran.tranCatCd);

            // 1100-WRITE-TRANSACTION-REPORT
            writeTransactionReport(tran);
        }

        // Write final totals
        if (!firstTime) {
            writeAccountTotals();
            writePageTotals();
            writeGrandTotals();
        }

        closeFiles();
        logger.info("END OF EXECUTION OF PROGRAM CBTRN03C");
    }

    // -----------------------------------------------------------------------
    // Open files
    // -----------------------------------------------------------------------
    private void openFiles() {
        try {
            reportWriter = Files.newBufferedWriter(Paths.get(reportFilePath));
        } catch (IOException e) { abend("ERROR OPENING REPORT FILE: " + e.getMessage()); }

        // CardXrefRecord (CVACT03Y) — key = xrefCardNum
        loadIndex(xrefFilePath, line -> {
            CardXrefRecord r = CardXrefRecord.fromLine(line);
            if (r != null) xrefIndex.put(r.key(), r);
        }, "CARDXREF");

        // TranTypeRecord (CVTRA03Y) — key = tranType
        loadIndex(tranTypePath, line -> {
            TranTypeRecord r = TranTypeRecord.fromLine(line);
            if (r != null) tranTypeIndex.put(r.key(), r);
        }, "TRANTYPE");

        // TranCatRecord (CVTRA04Y) — key = tranTypeCd+tranCatCd
        loadIndex(tranCatgPath, line -> {
            TranCatRecord r = TranCatRecord.fromLine(line);
            if (r != null) tranCatgIndex.put(r.key(), r);
        }, "TRANCATG");
    }

    private void loadIndex(String path, java.util.function.Consumer<String> loader, String name) {
        try (BufferedReader r = Files.newBufferedReader(Paths.get(path))) {
            String line;
            while ((line = r.readLine()) != null) loader.accept(line);
            logger.info(name + " loaded");
        } catch (IOException e) { logger.warning(name + " not found: " + path); }
    }

    private void closeFiles() {
        try { if (reportWriter != null) reportWriter.close(); } catch (IOException ignored) {}
        try { if (tranReader   != null) tranReader.close();   } catch (IOException ignored) {}
    }

    // -----------------------------------------------------------------------
    // 0550-DATEPARM-READ
    // -----------------------------------------------------------------------
    private void readDateParams() {
        try (BufferedReader r = Files.newBufferedReader(Paths.get(dateParmPath))) {
            String line = r.readLine();
            if (line != null && line.length() >= 21) {
                wsStartDate = line.substring(0, 10);
                wsEndDate   = line.substring(11, 21);
                logger.info("Reporting from " + wsStartDate + " to " + wsEndDate);
            }
        } catch (IOException e) {
            logger.warning("DATEPARM not found, using full range: " + dateParmPath);
        }
    }

    // -----------------------------------------------------------------------
    // 1000-TRANFILE-GET-NEXT — TranRecord (CVTRA05Y)
    // -----------------------------------------------------------------------
    private TranRecord getNextTransaction() {
        try {
            if (tranReader == null)
                tranReader = Files.newBufferedReader(Paths.get(tranFilePath));
            String line = tranReader.readLine();
            if (line == null) { endOfFile = true; return null; }
            return TranRecord.fromLine(line);
        } catch (IOException e) { abend("ERROR READING TRANFILE: " + e.getMessage()); return null; }
    }

    // -----------------------------------------------------------------------
    // Lookup methods — use copybook record key() methods
    // -----------------------------------------------------------------------

    // 1500-A — CardXrefRecord (CVACT03Y), key = xrefCardNum
    private CardXrefRecord lookupXref(String cardNum) {
        if (cardNum == null) return null;
        return xrefIndex.get(cardNum.trim());
    }

    // 1500-B — TranTypeRecord (CVTRA03Y), key = tranType
    private TranTypeRecord lookupTranType(String typeCd) {
        if (typeCd == null) return null;
        return tranTypeIndex.get(typeCd.trim());
    }

    // 1500-C — TranCatRecord (CVTRA04Y), key = tranTypeCd+tranCatCd
    private TranCatRecord lookupTranCatg(String typeCd, int catCd) {
        TranCatRecord probe = new TranCatRecord();
        probe.tranTypeCd = typeCd;
        probe.tranCatCd  = catCd;
        return tranCatgIndex.get(probe.key());
    }

    // -----------------------------------------------------------------------
    // Report writing — uses ReportStructures (CVTRA07Y)
    // -----------------------------------------------------------------------

    // 1100-WRITE-TRANSACTION-REPORT
    private void writeTransactionReport(TranRecord tran) {
        if (lineCounter == 0 || lineCounter >= 20) writeHeaders(); // WS-PAGE-SIZE = 20

        // 1120-WRITE-DETAIL — uses ReportStructures.buildDetailLine() (CVTRA07Y)
        String acctId   = currentXref     != null ? String.valueOf(currentXref.xrefAcctId) : "";
        String typeDesc = currentTranType != null ? currentTranType.tranTypeDesc.trim()     : "";
        String catDesc  = currentTranCatg != null ? currentTranCatg.tranCatTypeDesc.trim()  : "";

        writeLine(ReportStructures.buildDetailLine(
            tran.tranId,       // TRAN-REPORT-TRANS-ID
            acctId,            // TRAN-REPORT-ACCOUNT-ID  — from CardXrefRecord.xrefAcctId
            tran.tranTypeCd,   // TRAN-REPORT-TYPE-CD     — TranRecord.tranTypeCd (CVTRA05Y)
            typeDesc,          // TRAN-REPORT-TYPE-DESC   — TranTypeRecord.tranTypeDesc (CVTRA03Y)
            tran.tranCatCd,    // TRAN-REPORT-CAT-CD      — TranRecord.tranCatCd (CVTRA05Y)
            catDesc,           // TRAN-REPORT-CAT-DESC    — TranCatRecord.tranCatTypeDesc (CVTRA04Y)
            tran.tranSource,   // TRAN-REPORT-SOURCE      — TranRecord.tranSource (CVTRA05Y)
            tran.tranAmt));    // TRAN-REPORT-AMT         — TranRecord.tranAmt (CVTRA05Y)

        lineCounter++;
        pageTotal    = pageTotal.add(tran.tranAmt);
        accountTotal = accountTotal.add(tran.tranAmt);
        grandTotal   = grandTotal.add(tran.tranAmt);
    }

    // 1120-WRITE-HEADERS — uses ReportStructures (CVTRA07Y)
    private void writeHeaders() {
        writeLine(ReportStructures.buildReportNameHeader(wsStartDate, wsEndDate));
        writeLine(ReportStructures.TRANSACTION_HEADER_1);
        writeLine(ReportStructures.TRANSACTION_HEADER_2);
        lineCounter = 3;
    }

    // 1110-WRITE-PAGE-TOTALS — uses ReportStructures (CVTRA07Y)
    private void writePageTotals() {
        writeLine(ReportStructures.buildPageTotals(pageTotal));
        pageTotal   = BigDecimal.ZERO;
        lineCounter = 0;
    }

    // 1120-WRITE-ACCOUNT-TOTALS — uses ReportStructures (CVTRA07Y)
    private void writeAccountTotals() {
        writeLine(ReportStructures.buildAccountTotals(accountTotal));
        accountTotal = BigDecimal.ZERO;
        lineCounter++;
    }

    // 1110-WRITE-GRAND-TOTALS — uses ReportStructures (CVTRA07Y)
    private void writeGrandTotals() {
        writeLine(ReportStructures.buildGrandTotals(grandTotal));
    }

    // 1111-WRITE-REPORT-REC — WRITE FD-REPTFILE-REC
    private void writeLine(String line) {
        try {
            // Pad/trim to 133 chars — mirrors FD-REPTFILE-REC PIC X(133)
            String padded = String.format("%-" + ReportStructures.REPORT_LINE_WIDTH + "s", line);
            reportWriter.write(padded.substring(0, ReportStructures.REPORT_LINE_WIDTH));
            reportWriter.newLine();
        } catch (IOException e) { abend("ERROR WRITING REPORT: " + e.getMessage()); }
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

    public static void main(String[] args) { new CbTrn03Service().execute(); }
}
