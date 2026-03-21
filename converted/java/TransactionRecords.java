/**
 * TransactionRecords.java
 * -----------------------
 * Java equivalents of all CVTRA* and CODATECN COBOL copybooks
 *
 * Contains:
 *   CVTRA01Y.cpy -> TranCatBalRecord    (transaction category balance, 50 bytes)
 *   CVTRA03Y.cpy -> TranTypeRecord      (transaction type, 60 bytes)
 *   CVTRA04Y.cpy -> TranCatRecord       (transaction category, 60 bytes)
 *   CVTRA05Y.cpy -> TranRecord          (transaction master, 350 bytes)
 *   CVTRA06Y.cpy -> DailyTranRecord     (daily transaction input, 350 bytes)
 *   CVTRA07Y.cpy -> ReportStructures    (report headers and detail lines)
 *   CODATECN.cpy -> DateConversionRecord (date format conversion utility)
 *
 * Fix: utility methods (sub, nvl, nvlDec, parseLong, parseInt, parseDec)
 *      are now inlined into each class so Java can resolve them correctly.
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

    private static final int OFF_ACCT_ID = 0;
    private static final int OFF_TYPE_CD = 11;
    private static final int OFF_CAT_CD  = 13;
    private static final int OFF_CAT_BAL = 17;

    public long       tranCatAcctId;
    public String     tranCatTypeCd;
    public int        tranCatCd;
    public BigDecimal tranCatBal;

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
            tranCatAcctId, nvl(tranCatTypeCd), tranCatCd, nvlDec(tranCatBal), "");
    }

    @Override public String toString() {
        return String.format("TranCatBalRecord{key=%s, bal=%s}", key(), tranCatBal);
    }

    // --- inline utilities ---
    private static String sub(String s, int start, int length) {
        if (s == null) s = "";
        int end = Math.min(start + length, s.length());
        if (start >= s.length()) return String.format("%-" + length + "s", "");
        return String.format("%-" + length + "s", s.substring(start, end));
    }
    private static long      parseLong(String s) { if(s==null)return 0L; s=s.trim(); if(s.isEmpty())return 0L; try{return Long.parseLong(s);}catch(NumberFormatException e){return 0L;} }
    private static int       parseInt(String s)  { if(s==null)return 0;  s=s.trim(); if(s.isEmpty())return 0;  try{return Integer.parseInt(s);}catch(NumberFormatException e){return 0;} }
    private static BigDecimal parseDec(String s) { if(s==null)return BigDecimal.ZERO; s=s.trim(); if(s.isEmpty())return BigDecimal.ZERO; try{return new BigDecimal(s);}catch(NumberFormatException e){return BigDecimal.ZERO;} }
    private static String     nvl(String s)            { return s == null ? "" : s; }
    private static BigDecimal nvlDec(BigDecimal d)     { return d == null ? BigDecimal.ZERO : d; }
}

// =============================================================================
// CVTRA03Y.cpy -> TranTypeRecord
// Record Name  : TRAN-TYPE-RECORD
// Record Length: 60 bytes
// Used By      : CBTRN03C
// =============================================================================
class TranTypeRecord {

    public static final int RECORD_LENGTH = 60;

    private static final int OFF_TRAN_TYPE      = 0;
    private static final int OFF_TRAN_TYPE_DESC = 2;

    public String tranType;
    public String tranTypeDesc;

    public String key() { return tranType == null ? "" : tranType.trim(); }

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

    @Override public String toString() {
        return String.format("TranTypeRecord{type=%s, desc=%s}", tranType, tranTypeDesc);
    }

    // --- inline utilities ---
    private static String sub(String s, int start, int length) {
        if (s == null) s = "";
        int end = Math.min(start + length, s.length());
        if (start >= s.length()) return String.format("%-" + length + "s", "");
        return String.format("%-" + length + "s", s.substring(start, end));
    }
    private static String nvl(String s) { return s == null ? "" : s; }
}

// =============================================================================
// CVTRA04Y.cpy -> TranCatRecord
// Record Name  : TRAN-CAT-RECORD
// Record Length: 60 bytes
// Used By      : CBTRN03C
// =============================================================================
class TranCatRecord {

    public static final int RECORD_LENGTH = 60;

    private static final int OFF_TRAN_TYPE_CD       = 0;
    private static final int OFF_TRAN_CAT_CD        = 2;
    private static final int OFF_TRAN_CAT_TYPE_DESC = 6;

    public String tranTypeCd;
    public int    tranCatCd;
    public String tranCatTypeDesc;

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

    @Override public String toString() {
        return String.format("TranCatRecord{key=%s, desc=%s}", key(), tranCatTypeDesc);
    }

    // --- inline utilities ---
    private static String sub(String s, int start, int length) {
        if (s == null) s = "";
        int end = Math.min(start + length, s.length());
        if (start >= s.length()) return String.format("%-" + length + "s", "");
        return String.format("%-" + length + "s", s.substring(start, end));
    }
    private static int    parseInt(String s) { if(s==null)return 0; s=s.trim(); if(s.isEmpty())return 0; try{return Integer.parseInt(s);}catch(NumberFormatException e){return 0;} }
    private static String nvl(String s)      { return s == null ? "" : s; }
}

// =============================================================================
// CVTRA05Y.cpy -> TranRecord
// Record Name  : TRAN-RECORD
// Record Length: 350 bytes
// Used By      : CBTRN01C, CBTRN02C, CBTRN03C
// =============================================================================
class TranRecord {

    public static final int RECORD_LENGTH = 350;

    private static final int OFF_TRAN_ID            = 0;
    private static final int OFF_TRAN_TYPE_CD       = 16;
    private static final int OFF_TRAN_CAT_CD        = 18;
    private static final int OFF_TRAN_SOURCE        = 22;
    private static final int OFF_TRAN_DESC          = 32;
    private static final int OFF_TRAN_AMT           = 132;
    private static final int OFF_TRAN_MERCHANT_ID   = 144;
    private static final int OFF_TRAN_MERCHANT_NAME = 153;
    private static final int OFF_TRAN_MERCHANT_CITY = 203;
    private static final int OFF_TRAN_MERCHANT_ZIP  = 253;
    private static final int OFF_TRAN_CARD_NUM      = 263;
    private static final int OFF_TRAN_ORIG_TS       = 279;
    private static final int OFF_TRAN_PROC_TS       = 305;

    public String     tranId;
    public String     tranTypeCd;
    public int        tranCatCd;
    public String     tranSource;
    public String     tranDesc;
    public BigDecimal tranAmt;
    public long       tranMerchantId;
    public String     tranMerchantName;
    public String     tranMerchantCity;
    public String     tranMerchantZip;
    public String     tranCardNum;
    public String     tranOrigTs;
    public String     tranProcTs;

    public String key() { return tranId == null ? "" : tranId.trim(); }

    public static TranRecord fromLine(String line) {
        if (line == null || line.length() < 18) return null;
        TranRecord r = new TranRecord();
        r.tranId           = sub(line, OFF_TRAN_ID,            16);
        r.tranTypeCd       = sub(line, OFF_TRAN_TYPE_CD,        2);
        r.tranCatCd        = parseInt(sub(line, OFF_TRAN_CAT_CD, 4));
        r.tranSource       = sub(line, OFF_TRAN_SOURCE,        10);
        r.tranDesc         = sub(line, OFF_TRAN_DESC,         100);
        r.tranAmt          = parseScaledDec(sub(line, OFF_TRAN_AMT, 12));
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
        // AMT: S9(09)V99 = 12 chars, stored as signed integer-scaled (no decimal point)
        // e.g. 1000.00 -> +00000100000  (sign + 9 digits + 2 decimal digits = 12 chars)
        long amtScaled = nvlDec(tranAmt).multiply(new java.math.BigDecimal("100")).longValue();
        String amtStr = String.format("%+012d", amtScaled);
        String rec = String.format("%-16s%-2s%04d%-10s%-100s%-12s%09d%-50s%-50s%-10s%-16s%-26s%-26s",
            nvl(tranId), nvl(tranTypeCd), tranCatCd, nvl(tranSource), nvl(tranDesc),
            amtStr, tranMerchantId,
            nvl(tranMerchantName), nvl(tranMerchantCity), nvl(tranMerchantZip),
            nvl(tranCardNum), nvl(tranOrigTs), nvl(tranProcTs));
        // Pad to exactly 350 bytes (FILLER PIC X(19) to reach record length 350)
        return String.format("%-350s", rec).substring(0, 350);
    }

    @Override public String toString() {
        return String.format("TranRecord{id=%s, type=%s, cat=%04d, amt=%s, card=%s}",
            tranId, tranTypeCd, tranCatCd, tranAmt, tranCardNum);
    }

    // --- inline utilities ---
    private static String sub(String s, int start, int length) {
        if (s == null) s = "";
        int end = Math.min(start + length, s.length());
        if (start >= s.length()) return String.format("%-" + length + "s", "");
        return String.format("%-" + length + "s", s.substring(start, end));
    }
    private static long       parseLong(String s) { if(s==null)return 0L; s=s.trim(); if(s.isEmpty())return 0L; try{return Long.parseLong(s);}catch(NumberFormatException e){return 0L;} }
    private static int        parseInt(String s)  { if(s==null)return 0;  s=s.trim(); if(s.isEmpty())return 0;  try{return Integer.parseInt(s);}catch(NumberFormatException e){return 0;} }
    private static BigDecimal parseDec(String s)  { if(s==null)return BigDecimal.ZERO; s=s.trim(); if(s.isEmpty())return BigDecimal.ZERO; try{return new BigDecimal(s);}catch(NumberFormatException e){return BigDecimal.ZERO;} }
    private static BigDecimal parseScaledDec(String s) { if(s==null)return BigDecimal.ZERO; s=s.trim(); if(s.isEmpty())return BigDecimal.ZERO; if(s.contains(".")) { try{return new BigDecimal(s);}catch(NumberFormatException e){return BigDecimal.ZERO;} } try{ return new BigDecimal(Long.parseLong(s)).divide(new java.math.BigDecimal("100")); }catch(NumberFormatException e){return BigDecimal.ZERO;} }
    private static String     nvl(String s)           { return s == null ? "" : s; }
    private static BigDecimal nvlDec(BigDecimal d)    { return d == null ? BigDecimal.ZERO : d; }
}

// =============================================================================
// CVTRA06Y.cpy -> DailyTranRecord
// Record Name  : DALYTRAN-RECORD
// Record Length: 350 bytes
// Used By      : CBTRN01C, CBTRN02C
// =============================================================================
class DailyTranRecord {

    public static final int RECORD_LENGTH = 350;

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

    public String     dalyTranId;
    public String     dalyTranTypeCd;
    public int        dalyTranCatCd;
    public String     dalyTranSource;
    public String     dalyTranDesc;
    public BigDecimal dalyTranAmt;
    public long       dalyTranMerchantId;
    public String     dalyTranMerchantName;
    public String     dalyTranMerchantCity;
    public String     dalyTranMerchantZip;
    public String     dalyTranCardNum;
    public String     dalyTranOrigTs;
    public String     dalyTranProcTs;
    public String     rawLine;

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

    @Override public String toString() {
        return String.format("DailyTranRecord{id=%s, card=%s, amt=%s}",
            dalyTranId, dalyTranCardNum, dalyTranAmt);
    }

    // --- inline utilities ---
    private static String sub(String s, int start, int length) {
        if (s == null) s = "";
        int end = Math.min(start + length, s.length());
        if (start >= s.length()) return String.format("%-" + length + "s", "");
        return String.format("%-" + length + "s", s.substring(start, end));
    }
    private static long       parseLong(String s) { if(s==null)return 0L; s=s.trim(); if(s.isEmpty())return 0L; try{return Long.parseLong(s);}catch(NumberFormatException e){return 0L;} }
    private static int        parseInt(String s)  { if(s==null)return 0;  s=s.trim(); if(s.isEmpty())return 0;  try{return Integer.parseInt(s);}catch(NumberFormatException e){return 0;} }
    private static BigDecimal parseDec(String s)  { if(s==null)return BigDecimal.ZERO; s=s.trim(); if(s.isEmpty())return BigDecimal.ZERO; try{return new BigDecimal(s);}catch(NumberFormatException e){return BigDecimal.ZERO;} }
}

// =============================================================================
// CVTRA07Y.cpy -> ReportStructures
// Used By      : CBTRN03C
// =============================================================================
class ReportStructures {

    public static final int REPORT_LINE_WIDTH = 133;

    public static String buildReportNameHeader(String startDate, String endDate) {
        return String.format("%-38s%-41sDate Range: %-10s to %-10s",
            "DALYREPT", "Daily Transaction Report",
            nvl(startDate), nvl(endDate));
    }

    public static final String TRANSACTION_HEADER_1 =
        String.format("%-17s%-12s%-19s%-35s%-14s %-16s",
            "Transaction ID", "Account ID", "Transaction Type",
            "Tran Category", "Tran Source", "        Amount");

    public static final String TRANSACTION_HEADER_2 = "-".repeat(REPORT_LINE_WIDTH);

    public static String buildDetailLine(
            String tranId, String accountId, String typeCd, String typeDesc,
            int catCd, String catDesc, String source, BigDecimal amt) {
        return String.format("%-16s %-11s %-2s-%-15s %04d-%-29s %-10s    %,14.2f  ",
            nvl(tranId), nvl(accountId),
            nvl(typeCd), nvl(typeDesc),
            catCd, nvl(catDesc),
            nvl(source), amt == null ? BigDecimal.ZERO : amt);
    }

    public static String buildPageTotals(BigDecimal pageTotal) {
        return String.format("%-11s%-86s%+14.2f",
            "Page Total", ".".repeat(86),
            pageTotal == null ? BigDecimal.ZERO : pageTotal);
    }

    public static String buildAccountTotals(BigDecimal accountTotal) {
        return String.format("%-13s%-84s%+14.2f",
            "Account Total", ".".repeat(84),
            accountTotal == null ? BigDecimal.ZERO : accountTotal);
    }

    public static String buildGrandTotals(BigDecimal grandTotal) {
        return String.format("%-11s%-86s%+14.2f",
            "Grand Total", ".".repeat(86),
            grandTotal == null ? BigDecimal.ZERO : grandTotal);
    }

    private static String nvl(String s) { return s == null ? "" : s; }
}

// =============================================================================
// CODATECN.cpy -> DateConversionRecord
// Used By      : CBACT01C (via CALL 'COBDATFT')
// =============================================================================
class DateConversionRecord {

    public static final String YYYYMMDD_IN   = "1";
    public static final String YYYY_MM_DD_IN = "2";
    public static final String YYYY_MM_DD_OP = "1";
    public static final String YYYYMMDD_OP   = "2";

    public String inputType;
    public String inputDate;
    public String outputType;
    public String outputDate;
    public String errorMsg = "";

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
                date = LocalDate.parse(cleaned.length() >= 8 ? cleaned.substring(0, 8) : cleaned,
                    DateTimeFormatter.BASIC_ISO_DATE);
            } else {
                date = LocalDate.parse(cleaned.length() >= 10 ? cleaned.substring(0, 10) : cleaned,
                    DateTimeFormatter.ISO_LOCAL_DATE);
            }
            outputDate = YYYYMMDD_OP.equals(outputType)
                ? date.format(DateTimeFormatter.BASIC_ISO_DATE)
                : date.format(DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (DateTimeParseException e) {
            errorMsg = "INVALID DATE: " + cleaned;
        }
    }

    @Override public String toString() {
        return String.format("DateConversionRecord{in=%s, out=%s}", inputDate, outputDate);
    }
}
