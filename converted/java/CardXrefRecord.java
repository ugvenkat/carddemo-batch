/**
 * CardXrefRecord.java
 * -------------------
 * Java equivalent of CVACT03Y.cpy COBOL copybook
 *
 * Copybook      : CVACT03Y.cpy
 * Record Name   : CARD-XREF-RECORD
 * Record Length : 50 bytes
 * Used By       : CBTRN01C, CBTRN02C, CBTRN03C
 *
 * COBOL to Java field mapping:
 *   XREF-CARD-NUM   PIC X(16)  -> String xrefCardNum
 *   XREF-CUST-ID    PIC 9(09)  -> long   xrefCustId
 *   XREF-ACCT-ID    PIC 9(11)  -> long   xrefAcctId
 *   FILLER          PIC X(14)  -> (skipped)
 *
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * Licensed under the Apache License, Version 2.0
 */

public class CardXrefRecord {

    public static final int RECORD_LENGTH = 50;

    // Field offsets matching exact COBOL PIC positions
    private static final int OFF_XREF_CARD_NUM = 0;   // PIC X(16)
    private static final int OFF_XREF_CUST_ID  = 16;  // PIC 9(09)
    private static final int OFF_XREF_ACCT_ID  = 25;  // PIC 9(11)

    // Fields — mirror COBOL 01 CARD-XREF-RECORD level-05 fields
    public String xrefCardNum;  // XREF-CARD-NUM  — primary key for VSAM KSDS
    public long   xrefCustId;   // XREF-CUST-ID
    public long   xrefAcctId;   // XREF-ACCT-ID

    // -----------------------------------------------------------------------
    // Parse from fixed-length string — mirrors READ XREF-FILE INTO CARD-XREF-RECORD
    // -----------------------------------------------------------------------
    public static CardXrefRecord fromLine(String line) {
        if (line == null || line.length() < 16) return null;
        CardXrefRecord r = new CardXrefRecord();
        r.xrefCardNum = sub(line, OFF_XREF_CARD_NUM, 16);
        r.xrefCustId  = parseLong(sub(line, OFF_XREF_CUST_ID, 9));
        r.xrefAcctId  = parseLong(sub(line, OFF_XREF_ACCT_ID, 11));
        return r;
    }

    // -----------------------------------------------------------------------
    // Serialize to fixed-length string
    // -----------------------------------------------------------------------
    public String toLine() {
        return String.format("%-16s%09d%011d%-14s",
            nvl(xrefCardNum), xrefCustId, xrefAcctId, "");
    }

    // Key method — mirrors COBOL RECORD KEY IS FD-XREF-CARD-NUM
    public String key() {
        return xrefCardNum == null ? "" : xrefCardNum.trim();
    }

    @Override
    public String toString() {
        return String.format("CardXrefRecord{cardNum=%s, custId=%09d, acctId=%011d}",
            xrefCardNum, xrefCustId, xrefAcctId);
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

    private static String nvl(String s) { return s == null ? "" : s; }
}
