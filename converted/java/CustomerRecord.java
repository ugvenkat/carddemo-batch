/**
 * CustomerRecord.java
 * -------------------
 * Java equivalent of CVCUS01Y.cpy COBOL copybook
 *
 * Copybook      : CVCUS01Y.cpy
 * Record Name   : CUSTOMER-RECORD
 * Record Length : 500 bytes
 * Used By       : CBTRN01C
 *
 * COBOL to Java field mapping:
 *   CUST-ID                  PIC 9(09)  -> long   custId
 *   CUST-FIRST-NAME          PIC X(25)  -> String custFirstName
 *   CUST-MIDDLE-NAME         PIC X(25)  -> String custMiddleName
 *   CUST-LAST-NAME           PIC X(25)  -> String custLastName
 *   CUST-ADDR-LINE-1         PIC X(50)  -> String custAddrLine1
 *   CUST-ADDR-LINE-2         PIC X(50)  -> String custAddrLine2
 *   CUST-ADDR-LINE-3         PIC X(50)  -> String custAddrLine3
 *   CUST-ADDR-STATE-CD       PIC X(02)  -> String custAddrStateCd
 *   CUST-ADDR-COUNTRY-CD     PIC X(03)  -> String custAddrCountryCd
 *   CUST-ADDR-ZIP            PIC X(10)  -> String custAddrZip
 *   CUST-PHONE-NUM-1         PIC X(15)  -> String custPhoneNum1
 *   CUST-PHONE-NUM-2         PIC X(15)  -> String custPhoneNum2
 *   CUST-SSN                 PIC 9(09)  -> long   custSsn
 *   CUST-GOVT-ISSUED-ID      PIC X(20)  -> String custGovtIssuedId
 *   CUST-DOB-YYYY-MM-DD      PIC X(10)  -> String custDobYyyyMmDd
 *   CUST-EFT-ACCOUNT-ID      PIC X(10)  -> String custEftAccountId
 *   CUST-PRI-CARD-HOLDER-IND PIC X(01)  -> String custPriCardHolderInd
 *   CUST-FICO-CREDIT-SCORE   PIC 9(03)  -> int    custFicoCreditScore
 *   FILLER                   PIC X(168) -> (skipped)
 *
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * Licensed under the Apache License, Version 2.0
 */

public class CustomerRecord {

    public static final int RECORD_LENGTH = 500;

    // Field offsets matching exact COBOL PIC positions
    private static final int OFF_CUST_ID                   = 0;
    private static final int OFF_CUST_FIRST_NAME           = 9;
    private static final int OFF_CUST_MIDDLE_NAME          = 34;
    private static final int OFF_CUST_LAST_NAME            = 59;
    private static final int OFF_CUST_ADDR_LINE_1          = 84;
    private static final int OFF_CUST_ADDR_LINE_2          = 134;
    private static final int OFF_CUST_ADDR_LINE_3          = 184;
    private static final int OFF_CUST_ADDR_STATE_CD        = 234;
    private static final int OFF_CUST_ADDR_COUNTRY_CD      = 236;
    private static final int OFF_CUST_ADDR_ZIP             = 239;
    private static final int OFF_CUST_PHONE_NUM_1          = 249;
    private static final int OFF_CUST_PHONE_NUM_2          = 264;
    private static final int OFF_CUST_SSN                  = 279;
    private static final int OFF_CUST_GOVT_ISSUED_ID       = 288;
    private static final int OFF_CUST_DOB                  = 308;
    private static final int OFF_CUST_EFT_ACCOUNT_ID       = 318;
    private static final int OFF_CUST_PRI_CARD_HOLDER_IND  = 328;
    private static final int OFF_CUST_FICO_CREDIT_SCORE    = 329;

    // Fields — mirror COBOL 01 CUSTOMER-RECORD level-05 fields
    public long   custId;                // CUST-ID
    public String custFirstName;         // CUST-FIRST-NAME
    public String custMiddleName;        // CUST-MIDDLE-NAME
    public String custLastName;          // CUST-LAST-NAME
    public String custAddrLine1;         // CUST-ADDR-LINE-1
    public String custAddrLine2;         // CUST-ADDR-LINE-2
    public String custAddrLine3;         // CUST-ADDR-LINE-3
    public String custAddrStateCd;       // CUST-ADDR-STATE-CD
    public String custAddrCountryCd;     // CUST-ADDR-COUNTRY-CD
    public String custAddrZip;           // CUST-ADDR-ZIP
    public String custPhoneNum1;         // CUST-PHONE-NUM-1
    public String custPhoneNum2;         // CUST-PHONE-NUM-2
    public long   custSsn;               // CUST-SSN
    public String custGovtIssuedId;      // CUST-GOVT-ISSUED-ID
    public String custDobYyyyMmDd;       // CUST-DOB-YYYY-MM-DD
    public String custEftAccountId;      // CUST-EFT-ACCOUNT-ID
    public String custPriCardHolderInd;  // CUST-PRI-CARD-HOLDER-IND
    public int    custFicoCreditScore;   // CUST-FICO-CREDIT-SCORE

    // -----------------------------------------------------------------------
    // Parse from fixed-length string
    // -----------------------------------------------------------------------
    public static CustomerRecord fromLine(String line) {
        if (line == null || line.length() < 9) return null;
        CustomerRecord r = new CustomerRecord();
        r.custId               = parseLong(sub(line, OFF_CUST_ID,                  9));
        r.custFirstName        = sub(line, OFF_CUST_FIRST_NAME,          25);
        r.custMiddleName       = sub(line, OFF_CUST_MIDDLE_NAME,         25);
        r.custLastName         = sub(line, OFF_CUST_LAST_NAME,           25);
        r.custAddrLine1        = sub(line, OFF_CUST_ADDR_LINE_1,         50);
        r.custAddrLine2        = sub(line, OFF_CUST_ADDR_LINE_2,         50);
        r.custAddrLine3        = sub(line, OFF_CUST_ADDR_LINE_3,         50);
        r.custAddrStateCd      = sub(line, OFF_CUST_ADDR_STATE_CD,        2);
        r.custAddrCountryCd    = sub(line, OFF_CUST_ADDR_COUNTRY_CD,      3);
        r.custAddrZip          = sub(line, OFF_CUST_ADDR_ZIP,            10);
        r.custPhoneNum1        = sub(line, OFF_CUST_PHONE_NUM_1,         15);
        r.custPhoneNum2        = sub(line, OFF_CUST_PHONE_NUM_2,         15);
        r.custSsn              = parseLong(sub(line, OFF_CUST_SSN,        9));
        r.custGovtIssuedId     = sub(line, OFF_CUST_GOVT_ISSUED_ID,      20);
        r.custDobYyyyMmDd      = sub(line, OFF_CUST_DOB,                 10);
        r.custEftAccountId     = sub(line, OFF_CUST_EFT_ACCOUNT_ID,      10);
        r.custPriCardHolderInd = sub(line, OFF_CUST_PRI_CARD_HOLDER_IND,  1);
        r.custFicoCreditScore  = parseInt(sub(line, OFF_CUST_FICO_CREDIT_SCORE, 3));
        return r;
    }

    // -----------------------------------------------------------------------
    // Serialize to fixed-length string
    // -----------------------------------------------------------------------
    public String toLine() {
        return String.format(
            "%09d%-25s%-25s%-25s%-50s%-50s%-50s%-2s%-3s%-10s%-15s%-15s%09d%-20s%-10s%-10s%-1s%03d%-168s",
            custId, nvl(custFirstName), nvl(custMiddleName), nvl(custLastName),
            nvl(custAddrLine1), nvl(custAddrLine2), nvl(custAddrLine3),
            nvl(custAddrStateCd), nvl(custAddrCountryCd), nvl(custAddrZip),
            nvl(custPhoneNum1), nvl(custPhoneNum2),
            custSsn, nvl(custGovtIssuedId), nvl(custDobYyyyMmDd),
            nvl(custEftAccountId), nvl(custPriCardHolderInd),
            custFicoCreditScore, "");
    }

    @Override
    public String toString() {
        return String.format("CustomerRecord{custId=%09d, name=%s %s, zip=%s}",
            custId, custFirstName, custLastName, custAddrZip);
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

    private static int parseInt(String s) {
        if (s == null) return 0;
        s = s.trim();
        if (s.isEmpty()) return 0;
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return 0; }
    }

    private static String nvl(String s) { return s == null ? "" : s; }
}
