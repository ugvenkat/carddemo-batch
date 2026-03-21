/**
 * AccountRecord.java
 * ------------------
 * Java equivalent of CVACT01Y.cpy COBOL copybook
 *
 * Copybook      : CVACT01Y.cpy
 * Record Name   : ACCOUNT-RECORD
 * Record Length : 300 bytes
 * Used By       : CBTRN01C, CBTRN02C, CBACT01C
 *
 * COBOL to Java field mapping:
 *   ACCT-ID                PIC 9(11)      -> long   acctId
 *   ACCT-ACTIVE-STATUS     PIC X(01)      -> String activeStatus
 *   ACCT-CURR-BAL          PIC S9(10)V99  -> BigDecimal currBal
 *   ACCT-CREDIT-LIMIT      PIC S9(10)V99  -> BigDecimal creditLimit
 *   ACCT-CASH-CREDIT-LIMIT PIC S9(10)V99  -> BigDecimal cashCreditLimit
 *   ACCT-OPEN-DATE         PIC X(10)      -> String openDate
 *   ACCT-EXPIRAION-DATE    PIC X(10)      -> String expirationDate
 *   ACCT-REISSUE-DATE      PIC X(10)      -> String reissueDate
 *   ACCT-CURR-CYC-CREDIT   PIC S9(10)V99  -> BigDecimal currCycCredit
 *   ACCT-CURR-CYC-DEBIT    PIC S9(10)V99  -> BigDecimal currCycDebit
 *   ACCT-ADDR-ZIP          PIC X(10)      -> String addrZip
 *   ACCT-GROUP-ID          PIC X(10)      -> String groupId
 *   FILLER                 PIC X(178)     -> (skipped)
 *
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * Licensed under the Apache License, Version 2.0
 */

import java.math.BigDecimal;

public class AccountRecord {

    // Fixed record length from copybook
    public static final int RECORD_LENGTH = 300;

    // Field offsets matching exact COBOL PIC positions
    private static final int OFF_ACCT_ID            = 0;   // PIC 9(11)
    private static final int OFF_ACTIVE_STATUS      = 11;  // PIC X(01)
    private static final int OFF_CURR_BAL           = 12;  // PIC S9(10)V99 -> 13 chars
    private static final int OFF_CREDIT_LIMIT       = 25;  // PIC S9(10)V99 -> 13 chars
    private static final int OFF_CASH_CREDIT_LIMIT  = 38;  // PIC S9(10)V99 -> 13 chars
    private static final int OFF_OPEN_DATE          = 51;  // PIC X(10)
    private static final int OFF_EXPIRATION_DATE    = 61;  // PIC X(10)
    private static final int OFF_REISSUE_DATE       = 71;  // PIC X(10)
    private static final int OFF_CURR_CYC_CREDIT    = 81;  // PIC S9(10)V99 -> 13 chars
    private static final int OFF_CURR_CYC_DEBIT     = 94;  // PIC S9(10)V99 -> 13 chars
    private static final int OFF_ADDR_ZIP           = 107; // PIC X(10)
    private static final int OFF_GROUP_ID           = 117; // PIC X(10)

    // Fields — mirror COBOL 01 ACCOUNT-RECORD level-05 fields
    public long       acctId;           // ACCT-ID
    public String     activeStatus;     // ACCT-ACTIVE-STATUS
    public BigDecimal currBal;          // ACCT-CURR-BAL
    public BigDecimal creditLimit;      // ACCT-CREDIT-LIMIT
    public BigDecimal cashCreditLimit;  // ACCT-CASH-CREDIT-LIMIT
    public String     openDate;         // ACCT-OPEN-DATE
    public String     expirationDate;   // ACCT-EXPIRAION-DATE (note: COBOL typo preserved)
    public String     reissueDate;      // ACCT-REISSUE-DATE
    public BigDecimal currCycCredit;    // ACCT-CURR-CYC-CREDIT
    public BigDecimal currCycDebit;     // ACCT-CURR-CYC-DEBIT
    public String     addrZip;          // ACCT-ADDR-ZIP
    public String     groupId;          // ACCT-GROUP-ID

    // -----------------------------------------------------------------------
    // Parse from fixed-length string — mirrors COBOL READ ... INTO ACCOUNT-RECORD
    // -----------------------------------------------------------------------
    public static AccountRecord fromLine(String line) {
        if (line == null || line.length() < 11) return null;
        AccountRecord r = new AccountRecord();
        r.acctId          = parseLong(sub(line, OFF_ACCT_ID,           11));
        r.activeStatus    = sub(line, OFF_ACTIVE_STATUS,     1);
        r.currBal         = parseDec(sub(line, OFF_CURR_BAL,          13));
        r.creditLimit     = parseDec(sub(line, OFF_CREDIT_LIMIT,      13));
        r.cashCreditLimit = parseDec(sub(line, OFF_CASH_CREDIT_LIMIT, 13));
        r.openDate        = sub(line, OFF_OPEN_DATE,        10);
        r.expirationDate  = sub(line, OFF_EXPIRATION_DATE,  10);
        r.reissueDate     = sub(line, OFF_REISSUE_DATE,     10);
        r.currCycCredit   = parseDec(sub(line, OFF_CURR_CYC_CREDIT,   13));
        r.currCycDebit    = parseDec(sub(line, OFF_CURR_CYC_DEBIT,    13));
        r.addrZip         = sub(line, OFF_ADDR_ZIP,         10);
        r.groupId         = sub(line, OFF_GROUP_ID,         10);
        return r;
    }

    // -----------------------------------------------------------------------
    // Serialize to fixed-length string — mirrors COBOL WRITE ... FROM ACCOUNT-RECORD
    // -----------------------------------------------------------------------
    public String toLine() {
        return String.format("%011d%-1s%+13.2f%+13.2f%+13.2f%-10s%-10s%-10s%+13.2f%+13.2f%-10s%-10s%-178s",
            acctId, nvl(activeStatus),
            nvlDec(currBal), nvlDec(creditLimit), nvlDec(cashCreditLimit),
            nvl(openDate), nvl(expirationDate), nvl(reissueDate),
            nvlDec(currCycCredit), nvlDec(currCycDebit),
            nvl(addrZip), nvl(groupId), "");
    }

    @Override
    public String toString() {
        return String.format("AccountRecord{acctId=%011d, status=%s, currBal=%s, creditLimit=%s}",
            acctId, activeStatus, currBal, creditLimit);
    }

    // -----------------------------------------------------------------------
    // Utility helpers
    // -----------------------------------------------------------------------
    private static String sub(String s, int start, int length) {
        if (s == null) s = "";
        int end = Math.min(start + length, s.length());
        if (start >= s.length()) return String.format("%-" + length + "s", "");
        return String.format("%-" + length + "s", s.substring(start, end));
    }

    private static long parseLong(String s) {
        if (s == null) return 0L;
        s = s.trim();
        if (s.isEmpty()) return 0L;
        try { return Long.parseLong(s); } catch (NumberFormatException e) { return 0L; }
    }

    private static BigDecimal parseDec(String s) {
        if (s == null) return BigDecimal.ZERO;
        s = s.trim();
        if (s.isEmpty()) return BigDecimal.ZERO;
        try { return new BigDecimal(s); } catch (NumberFormatException e) { return BigDecimal.ZERO; }
    }

    private static String nvl(String s)         { return s == null ? "" : s; }
    private static BigDecimal nvlDec(BigDecimal d) { return d == null ? BigDecimal.ZERO : d; }
}
