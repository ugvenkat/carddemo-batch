/**
 * TransactionRecords.java
 * -----------------------
 * Java equivalents of all CVTRA* and CODATECN COBOL copybooks
 *
 * This single file contains all transaction-related record classes
 * converted from the following copybooks:
 *
 *   CVTRA01Y.cpy -> TranCatBalRecord    (transaction category balance, 50 bytes)
 *   CVTRA03Y.cpy -> TranTypeRecord      (transaction type, 60 bytes)
 *   CVTRA04Y.cpy -> TranCatRecord       (transaction category, 60 bytes)
 *   CVTRA05Y.cpy -> TranRecord          (transaction master, 350 bytes)
 *   CVTRA06Y.cpy -> DailyTranRecord     (daily transaction input, 350 bytes)
 *   CVTRA07Y.cpy -> ReportStructures    (report headers and detail lines, 133 bytes)
 *   CODATECN.cpy -> DateConversionRecord (date format conversion utility)
 *
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * Licensed under the Apache License, Version 2.0
 */

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

// =============================================================================
// CVTRA01Y.cpy -> TranCatBalRecord
// Record Name  : TRAN-CAT-BAL-RECORD
// Record Length: 50 bytes
// Used By      : CBTRN02C
// =============================================================================
class TranCatBalRecord {

    public static final int RECORD_LENGTH = 50;

    // Field offsets
    private static final int OFF_ACCT_ID  = 0;   // TRANCAT-ACCT-ID  PIC 9(11)
    private static final int OFF_TYPE_CD  = 11;  // TRANCAT-TYPE-CD  PIC X(02)
    private static final int OFF_CAT_CD   = 13;  // TRANCAT-CD       PIC 9(04)
    private static final int OFF_CAT_BAL  = 17;  // TRAN-CAT-BAL     PIC S9(09)V99 -> 12 chars

    // Fields — mirror COBOL 01 TRAN-CAT-BAL-RECORD
    public long       tranCatAcctId;   // TRANCAT-ACCT-ID (part of TRAN-CAT-KEY)
    public String     tranCatTypeCd;   // TRANCAT-TYPE-CD (part of TRAN-CAT-KEY)
    public int        tranCatCd;       // TRANCAT-CD      (part of TRAN-CAT-KEY)
    public BigDecimal tranCatBal;      // TRAN-CAT-BAL

    // Key method — mirrors COBOL RECORD KEY IS FD-TRAN-CAT-KEY
    public String key() {
        return String.format("%011d%-2s%04d", tranCatAcctId, nvl(tranCatTypeCd), tranCatCd);
    }

    public static TranCatBalRecord fromLine(String line) {
        if (line == null || line.length() < 17) return null;
        TranCatBalRecord r = new TranCatBalRecord();
        r.tranCatAcctId = parseLong(sub(line, OFF_ACCT_ID, 11));
        r.tranCatTypeCd = sub(line, OFF_TYPE_CD, 2);
        r.tranCatCd     = parseInt(sub(line, OFF_CAT_CD, 4));
        r.tranCatBal    = parseDec(sub(line, OFF_CAT_BAL, 12));
        return r;
    }

    public String toLine() {
        return String.format("%011d%-2s%04d%+12.2f%-22s",
            tranCatAcctId, nvl(tranCatTypeCd), tranCatCd,
            nvlDec(tranCatBal), "");
    }

    @Override
    public String toString() {
        return String.format("TranCatBalRecord{key=%s, bal=%s}", key(), tranCatBal);
    }
}

// =============================================================================
// CVTRA03Y.cpy -> TranTypeRecord
// Record Name  : TRAN-TYPE-RECORD
// Record Length: 60 bytes
// Used By      : CBTRN03C
// =============================================================================
class TranTypeRecord {

    public static final int RECORD_LENGTH = 60;

    private static final int OFF_TRAN_TYPE      = 0;   // TRAN-TYPE      PIC X(02)
    private static final int OFF_TRAN_TYPE_DESC = 2;   // TRAN-TYPE-DESC PIC X(50)

    // Fields
    public String tranType;      // TRAN-TYPE      — primary key
    public String tranTypeDesc;  // TRAN-TYPE-DESC

    public static TranTypeRecord fromLine(String line) {
        if (line == null || line.length() < 2) return null;
        TranTypeRecord r = new TranTypeRecord();
        r.tranType     = sub(line, OFF_TRAN_TYPE,      2);
        r.tranTypeDesc = sub(line, OFF_TRAN_TYPE_DESC, 50);
        return r;
    }

    public String toLine() {
        return String.format("%-2s%-50s%-8s", nvl(tranType), nvl(tranTypeDesc), "");
    }

    // Key method — mirrors COBOL RECORD KEY IS FD-TRAN-TYPE
    public String key() { return tranType == null ? "" : tranType.trim(); }

    @Override
    public String toString() {
        return String.format("TranTypeRecord{type=%s, desc=%s}", tranType, tranTypeDesc);
    }
}

// =============================================================================
// CVTRA04Y.cpy -> TranCatRecord
// Record Name  : TRAN-CAT-RECORD
// Record Length: 60 bytes
// Used By      : CBTRN03C
// =============================================================================
class TranCatRecord {

    public static final int RECORD_LENGTH = 60;

    private static final int OFF_TRAN_TYPE_CD       = 0;   // TRAN-TYPE-CD       PIC X(02)
    private static final int OFF_TRAN_CAT_CD        = 2;   // TRAN-CAT-CD        PIC 9(04)
    private static final int OFF_TRAN_CAT_TYPE_DESC = 6;   // TRAN-CAT-TYPE-DESC PIC X(50)

    // Fields — mirror COBOL 01 TRAN-CAT-RECORD
    public String tranTypeCd;        // TRAN-TYPE-CD  (part of TRAN-CAT-KEY)
    public int    tranCatCd;         // TRAN-CAT-CD   (part of TRAN-CAT-KEY)
    public String tranCatTypeDesc;   // TRAN-CAT-TYPE-DESC

    // Key method — mirrors COBOL RECORD KEY IS FD-TRAN-CAT-KEY
    public String key() {
        return String.format("%-2s%04d", nvl(tranTypeCd), tranCatCd);
    }

    public static TranCatRecord fromLine(String line) {
        if (line == null || line.length() < 6) return null;
        TranCatRecord r = new TranCatRecord();
        r.tranTypeCd      = sub(line, OFF_TRAN_TYPE_CD,       2);
        r.tranCatCd       = parseInt(sub(line, OFF_TRAN_CAT_CD, 4));
        r.tranCatTypeDesc = sub(line, OFF_TRAN_CAT_TYPE_DESC, 50);
        return r;
    }

    public String toLine() {
        return String.format("%-2s%04d%-50s%-4s",
            nvl(tranTypeCd), tranCatCd, nvl(tranCatTypeDesc), "");
    }

    @Override
    public String toString() {
        return String.format("TranCatRecord{key=%s, desc=%s}", key(), tranCatTypeDesc);
    }
}

// =============================================================================
// CVTRA05Y.cpy -> TranRecord
// Record Name  : TRAN-RECORD
// Record Length: 350 bytes
// Used By      : CBTRN01C, CBTRN02C, CBTRN03C
// =============================================================================
class TranRecord {

    public static final int RECORD_LENGTH = 350;

    // Field offsets — exact COBOL PIC positions
    private static final int OFF_TRAN_ID            = 0;    // PIC X(16)
    private static final int OFF_TRAN_TYPE_CD       = 16;   // PIC X(02)
    private static final int OFF_TRAN_CAT_CD        = 18;   // PIC 9(04)
    private static final int OFF_TRAN_SOURCE        = 22;   // PIC X(10)
    private static final int OFF_TRAN_DESC          = 32;   // PIC X(100)
    private static final int OFF_TRAN_AMT           = 132;  // PIC S9(09)V99 -> 12 chars
    private static final int OFF_TRAN_MERCHANT_ID   = 144;  // PIC 9(09)
    private static final int OFF_TRAN_MERCHANT_NAME = 153;  // PIC X(50)
    private static final int OFF_TRAN_MERCHANT_CITY = 203;  // PIC X(50)
    private static final int OFF_TRAN_MERCHANT_ZIP  = 253;  // PIC X(10)
    private static final int OFF_TRAN_CARD_NUM      = 263;  // PIC X(16)
    private static final int OFF_TRAN_ORIG_TS       = 279;  // PIC X(26)
    private static final int OFF_TRAN_PROC_TS       = 305;  // PIC X(26)

    // Fields — mirror COBOL 01 TRAN-RECORD level-05 fields
    public String     tranId;           // TRAN-ID
    public String     tranTypeCd;       // TRAN-TYPE-CD
    public int        tranCatCd;        // TRAN-CAT-CD
    public String     tranSource;       // TRAN-SOURCE
    public String     tranDesc;         // TRAN-DESC
    public BigDecimal tranAmt;          // TRAN-AMT
    public long       tranMerchantId;   // TRAN-MERCHANT-ID
    public String     tranMerchantName; // TRAN-MERCHANT-NAME
    public String     tranMerchantCity; // TRAN-MERCHANT-CITY
    public String     tranMerchantZip;  // TRAN-MERCHANT-ZIP
    public String     tranCardNum;      // TRAN-CARD-NUM
    public String     tranOrigTs;       // TRAN-ORIG-TS
    public String     tranProcTs;       // TRAN-PROC-TS

    // Key method — mirrors COBOL RECORD KEY IS FD-TRANS-ID
    public String key() { return tranId == null ? "" : tranId.trim(); }

    public static TranRecord fromLine(String line) {
        if (line == null || line.length() < 18) return null;
        TranRecord r = new TranRecord();
        r.tranId           = sub(line, OFF_TRAN_ID,            16);
        r.tranTypeCd       = sub(line, OFF_TRAN_TYPE_CD,        2);
        r.tranCatCd        = parseInt(sub(line, OFF_TRAN_CAT_CD, 4));
        r.tranSource       = sub(line, OFF_TRAN_SOURCE,        10);
        r.tranDesc         = sub(line, OFF_TRAN_DESC,         100);
        r.tranAmt          = parseDec(sub(line, OFF_TRAN_AMT,  12));
        r.tranMerchantId   = parseLong(sub(line, OFF_TRAN_MERCHANT_ID, 9));
        r.tranMerchantName = sub(line, OFF_TRAN_MERCHANT_NAME, 50);
        r.tranMerchantCity = sub(line, OFF_TRAN_MERCHANT_CITY, 50);
        r.tranMerchantZip  = sub(line, OFF_TRAN_MERCHANT_ZIP,  10);
        r.tranCardNum      = sub(line, OFF_TRAN_CARD_NUM,      16);
        r.tranOrigTs       = sub(line, OFF_TRAN_ORIG_TS,       26);
        r.tranProcTs       = sub(line, OFF_TRAN_PROC_TS,       26);
        return r;
    }

    public String toLine() {
        return String.format("%-16s%-2s%04d%-10s%-100s%+12.2f%09d%-50s%-50s%-10s%-16s%-26s%-26s%-20s",
            nvl(tranId), nvl(tranTypeCd), tranCatCd, nvl(tranSource), nvl(tranDesc),
            nvlDec(tranAmt), tranMerchantId,
            nvl(tranMerchantName), nvl(tranMerchantCity), nvl(tranMerchantZip),
            nvl(tranCardNum), nvl(tranOrigTs), nvl(tranProcTs), "");
    }

    @Override
    public String toString() {
        return String.format("TranRecord{id=%s, type=%s, cat=%04d, amt=%s, card=%s}",
            tranId, tranTypeCd, tranCatCd, tranAmt, tranCardNum);
    }
}

// =============================================================================
// CVTRA06Y.cpy -> DailyTranRecord
// Record Name  : DALYTRAN-RECORD
// Record Length: 350 bytes
// Used By      : CBTRN01C, CBTRN02C
// Note: Same layout as CVTRA05Y (TranRecord) but used for daily input file
// =============================================================================
class DailyTranRecord {

    public static final int RECORD_LENGTH = 350;

    // Field offsets — same layout as CVTRA05Y
    private static final int OFF_DALYTRAN_ID            = 0;
    private static final int OFF_DALYTRAN_TYPE_CD       = 16;
    private static final int OFF_DALYTRAN_CAT_CD        = 18;
    private static final int OFF_DALYTRAN_SOURCE        = 22;
    private static final int OFF_DALYTRAN_DESC          = 32;
    private static final int OFF_DALYTRAN_AMT           = 132;
    private static final int OFF_DALYTRAN_MERCHANT_ID   = 144;
    private static final int OFF_DALYTRAN_MERCHANT_NAME = 153;
    private static final int OFF_DALYTRAN_MERCHANT_CITY = 203;
    private static final int OFF_DALYTRAN_MERCHANT_ZIP  = 253;
    private static final int OFF_DALYTRAN_CARD_NUM      = 263;
    private static final int OFF_DALYTRAN_ORIG_TS       = 279;
    private static final int OFF_DALYTRAN_PROC_TS       = 305;

    // Fields — mirror COBOL 01 DALYTRAN-RECORD level-05 fields
    public String     dalyTranId;           // DALYTRAN-ID
    public String     dalyTranTypeCd;       // DALYTRAN-TYPE-CD
    public int        dalyTranCatCd;        // DALYTRAN-CAT-CD
    public String     dalyTranSource;       // DALYTRAN-SOURCE
    public String     dalyTranDesc;         // DALYTRAN-DESC
    public BigDecimal dalyTranAmt;          // DALYTRAN-AMT
    public long       dalyTranMerchantId;   // DALYTRAN-MERCHANT-ID
    public String     dalyTranMerchantName; // DALYTRAN-MERCHANT-NAME
    public String     dalyTranMerchantCity; // DALYTRAN-MERCHANT-CITY
    public String     dalyTranMerchantZip;  // DALYTRAN-MERCHANT-ZIP
    public String     dalyTranCardNum;      // DALYTRAN-CARD-NUM
    public String     dalyTranOrigTs;       // DALYTRAN-ORIG-TS
    public String     dalyTranProcTs;       // DALYTRAN-PROC-TS
    public String     rawLine;              // original line for reject writing

    public static DailyTranRecord fromLine(String line) {
        if (line == null || line.length() < 18) return null;
        DailyTranRecord r = new DailyTranRecord();
        r.rawLine              = line;
        r.dalyTranId           = sub(line, OFF_DALYTRAN_ID,            16);
        r.dalyTranTypeCd       = sub(line, OFF_DALYTRAN_TYPE_CD,        2);
        r.dalyTranCatCd        = parseInt(sub(line, OFF_DALYTRAN_CAT_CD, 4));
        r.dalyTranSource       = sub(line, OFF_DALYTRAN_SOURCE,        10);
        r.dalyTranDesc         = sub(line, OFF_DALYTRAN_DESC,         100);
        r.dalyTranAmt          = parseDec(sub(line, OFF_DALYTRAN_AMT,  12));
        r.dalyTranMerchantId   = parseLong(sub(line, OFF_DALYTRAN_MERCHANT_ID, 9));
        r.dalyTranMerchantName = sub(line, OFF_DALYTRAN_MERCHANT_NAME, 50);
        r.dalyTranMerchantCity = sub(line, OFF_DALYTRAN_MERCHANT_CITY, 50);
        r.dalyTranMerchantZip  = sub(line, OFF_DALYTRAN_MERCHANT_ZIP,  10);
        r.dalyTranCardNum      = sub(line, OFF_DALYTRAN_CARD_NUM,      16);
        r.dalyTranOrigTs       = sub(line, OFF_DALYTRAN_ORIG_TS,       26);
        r.dalyTranProcTs       = sub(line, OFF_DALYTRAN_PROC_TS,       26);
        return r;
    }

    @Override
    public String toString() {
        return String.format("DailyTranRecord{id=%s, card=%s, amt=%s}",
            dalyTranId, dalyTranCardNum, dalyTranAmt);
    }
}

// =============================================================================
// CVTRA07Y.cpy -> ReportStructures
// Record Names : REPORT-NAME-HEADER, TRANSACTION-DETAIL-REPORT,
//                TRANSACTION-HEADER-1, TRANSACTION-HEADER-2,
//                REPORT-PAGE-TOTALS, REPORT-ACCOUNT-TOTALS, REPORT-GRAND-TOTALS
// Used By      : CBTRN03C
// =============================================================================
class ReportStructures {

    public static final int REPORT_LINE_WIDTH = 133;

    // -----------------------------------------------------------------------
    // REPORT-NAME-HEADER — mirrors COBOL 01 REPORT-NAME-HEADER
    // -----------------------------------------------------------------------
    public static String buildReportNameHeader(String startDate, String endDate) {
        return String.format("%-38s%-41sDate Range: %-10s to %-10s",
            "DALYREPT", "Daily Transaction Report",
            nvl(startDate), nvl(endDate));
    }

    // -----------------------------------------------------------------------
    // TRANSACTION-HEADER-1 — mirrors COBOL 01 TRANSACTION-HEADER-1
    // -----------------------------------------------------------------------
    public static final String TRANSACTION_HEADER_1 =
        String.format("%-17s%-12s%-19s%-35s%-14s %-16s",
            "Transaction ID", "Account ID", "Transaction Type",
            "Tran Category", "Tran Source", "        Amount");

    // -----------------------------------------------------------------------
    // TRANSACTION-HEADER-2 — mirrors COBOL 01 TRANSACTION-HEADER-2 PIC X(133) VALUE ALL '-'
    // -----------------------------------------------------------------------
    public static final String TRANSACTION_HEADER_2 = "-".repeat(REPORT_LINE_WIDTH);

    // -----------------------------------------------------------------------
    // TRANSACTION-DETAIL-REPORT — mirrors COBOL 01 TRANSACTION-DETAIL-REPORT
    // -----------------------------------------------------------------------
    public static String buildDetailLine(
            String tranId, String accountId, String typeCd, String typeDesc,
            int catCd, String catDesc, String source, BigDecimal amt) {
        return String.format("%-16s %-11s %-2s-%-15s %04d-%-29s %-10s    %,14.2f  ",
            nvl(tranId), nvl(accountId),
            nvl(typeCd), nvl(typeDesc),
            catCd, nvl(catDesc),
            nvl(source), amt == null ? BigDecimal.ZERO : amt);
    }

    // -----------------------------------------------------------------------
    // REPORT-PAGE-TOTALS — mirrors COBOL 01 REPORT-PAGE-TOTALS
    // -----------------------------------------------------------------------
    public static String buildPageTotals(BigDecimal pageTotal) {
        return String.format("%-11s%-86s%+14.2f",
            "Page Total", ".".repeat(86),
            pageTotal == null ? BigDecimal.ZERO : pageTotal);
    }

    // -----------------------------------------------------------------------
    // REPORT-ACCOUNT-TOTALS — mirrors COBOL 01 REPORT-ACCOUNT-TOTALS
    // -----------------------------------------------------------------------
    public static String buildAccountTotals(BigDecimal accountTotal) {
        return String.format("%-13s%-84s%+14.2f",
            "Account Total", ".".repeat(84),
            accountTotal == null ? BigDecimal.ZERO : accountTotal);
    }

    // -----------------------------------------------------------------------
    // REPORT-GRAND-TOTALS — mirrors COBOL 01 REPORT-GRAND-TOTALS
    // -----------------------------------------------------------------------
    public static String buildGrandTotals(BigDecimal grandTotal) {
        return String.format("%-11s%-86s%+14.2f",
            "Grand Total", ".".repeat(86),
            grandTotal == null ? BigDecimal.ZERO : grandTotal);
    }

    private static String nvl(String s) { return s == null ? "" : s; }
}

// =============================================================================
// CODATECN.cpy -> DateConversionRecord
// Record Name  : CODATECN-REC
// Used By      : CBACT01C (via CALL 'COBDATFT')
// Simulates the mainframe date conversion utility
// =============================================================================
class DateConversionRecord {

    // Input type codes — mirrors COBOL 88 level conditions
    public static final String YYYYMMDD_IN    = "1";  // input is YYYYMMDD
    public static final String YYYY_MM_DD_IN  = "2";  // input is YYYY-MM-DD

    // Output type codes
    public static final String YYYY_MM_DD_OP  = "1";  // output as YYYY-MM-DD
    public static final String YYYYMMDD_OP    = "2";  // output as YYYYMMDD

    // Fields — mirror COBOL 01 CODATECN-REC
    public String inputType;    // CODATECN-TYPE    PIC X
    public String inputDate;    // CODATECN-INP-DATE PIC X(20)
    public String outputType;   // CODATECN-OUTTYPE  PIC X
    public String outputDate;   // CODATECN-0UT-DATE PIC X(20)
    public String errorMsg;     // CODATECN-ERROR-MSG PIC X(38)

    // -----------------------------------------------------------------------
    // convert() — simulates CALL 'COBDATFT' USING CODATECN-REC
    // Converts date between YYYYMMDD and YYYY-MM-DD formats
    // -----------------------------------------------------------------------
    public void convert() {
        errorMsg   = "";
        outputDate = "";

        if (inputDate == null || inputDate.isBlank()) {
            errorMsg = "INPUT DATE IS BLANK";
            return;
        }

        String cleaned = inputDate.trim();

        try {
            LocalDate date;

            if (YYYYMMDD_IN.equals(inputType)) {
                // Input: YYYYMMDD (8 chars)
                date = LocalDate.parse(cleaned.substring(0, 8),
                    DateTimeFormatter.BASIC_ISO_DATE);
            } else {
                // Input: YYYY-MM-DD (10 chars)
                date = LocalDate.parse(cleaned.substring(0, 10),
                    DateTimeFormatter.ISO_LOCAL_DATE);
            }

            if (YYYYMMDD_OP.equals(outputType)) {
                outputDate = date.format(DateTimeFormatter.BASIC_ISO_DATE);
            } else {
                outputDate = date.format(DateTimeFormatter.ISO_LOCAL_DATE);
            }

        } catch (DateTimeParseException e) {
            errorMsg = "INVALID DATE FORMAT: " + e.getMessage().substring(0,
                Math.min(30, e.getMessage().length()));
        }
    }

    @Override
    public String toString() {
        return String.format("DateConversionRecord{inputType=%s, input=%s, outputType=%s, output=%s}",
            inputType, inputDate, outputType, outputDate);
    }
}

// =============================================================================
// Shared utility methods used by all record classes above
// =============================================================================
class RecordUtils {

    public static String sub(String s, int start, int length) {
        if (s == null) s = "";
        int end = Math.min(start + length, s.length());
        if (start >= s.length()) return String.format("%-" + length + "s", "");
        return String.format("%-" + length + "s", s.substring(start, end));
    }

    public static long parseLong(String s) {
        if (s == null) return 0L;
        s = s.trim();
        if (s.isEmpty()) return 0L;
        try { return Long.parseLong(s); } catch (NumberFormatException e) { return 0L; }
    }

    public static int parseInt(String s) {
        if (s == null) return 0;
        s = s.trim();
        if (s.isEmpty()) return 0;
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return 0; }
    }

    public static BigDecimal parseDec(String s) {
        if (s == null) return BigDecimal.ZERO;
        s = s.trim();
        if (s.isEmpty()) return BigDecimal.ZERO;
        try { return new BigDecimal(s); } catch (NumberFormatException e) { return BigDecimal.ZERO; }
    }

    public static String nvl(String s)            { return s == null ? "" : s; }
    public static BigDecimal nvlDec(BigDecimal d)  { return d == null ? BigDecimal.ZERO : d; }
}

// Helper stubs so individual classes compile standalone
// (In a real project these would be in RecordUtils and shared via import)
class Helpers {
    static String sub(String s, int start, int length) { return RecordUtils.sub(s, start, length); }
    static long parseLong(String s)      { return RecordUtils.parseLong(s); }
    static int  parseInt(String s)       { return RecordUtils.parseInt(s); }
    static BigDecimal parseDec(String s) { return RecordUtils.parseDec(s); }
    static String nvl(String s)          { return RecordUtils.nvl(s); }
    static BigDecimal nvlDec(BigDecimal d){ return RecordUtils.nvlDec(d); }
}
