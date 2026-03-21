/**
 * CardRecord.java
 * ---------------
 * Java equivalent of CVACT02Y.cpy COBOL copybook
 *
 * Copybook      : CVACT02Y.cpy
 * Record Name   : CARD-RECORD
 * Record Length : 150 bytes
 * Used By       : CBTRN01C
 *
 * COBOL to Java field mapping:
 *   CARD-NUM              PIC X(16)   -> String cardNum
 *   CARD-ACCT-ID          PIC 9(11)   -> long   cardAcctId
 *   CARD-CVV-CD           PIC 9(03)   -> int    cardCvvCd
 *   CARD-EMBOSSED-NAME    PIC X(50)   -> String cardEmbossedName
 *   CARD-EXPIRAION-DATE   PIC X(10)   -> String cardExpirationDate
 *   CARD-ACTIVE-STATUS    PIC X(01)   -> String cardActiveStatus
 *   FILLER                PIC X(59)   -> (skipped)
 *
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * Licensed under the Apache License, Version 2.0
 */

public class CardRecord {

    public static final int RECORD_LENGTH = 150;

    // Field offsets matching exact COBOL PIC positions
    private static final int OFF_CARD_NUM           = 0;   // PIC X(16)
    private static final int OFF_CARD_ACCT_ID       = 16;  // PIC 9(11)
    private static final int OFF_CARD_CVV_CD        = 27;  // PIC 9(03)
    private static final int OFF_CARD_EMBOSSED_NAME = 30;  // PIC X(50)
    private static final int OFF_CARD_EXPIRY_DATE   = 80;  // PIC X(10)
    private static final int OFF_CARD_ACTIVE_STATUS = 90;  // PIC X(01)

    // Fields — mirror COBOL 01 CARD-RECORD level-05 fields
    public String cardNum;              // CARD-NUM
    public long   cardAcctId;           // CARD-ACCT-ID
    public int    cardCvvCd;            // CARD-CVV-CD
    public String cardEmbossedName;     // CARD-EMBOSSED-NAME
    public String cardExpirationDate;   // CARD-EXPIRAION-DATE (COBOL typo preserved)
    public String cardActiveStatus;     // CARD-ACTIVE-STATUS

    // -----------------------------------------------------------------------
    // Parse from fixed-length string
    // -----------------------------------------------------------------------
    public static CardRecord fromLine(String line) {
        if (line == null || line.length() < 16) return null;
        CardRecord r = new CardRecord();
        r.cardNum            = sub(line, OFF_CARD_NUM,           16);
        r.cardAcctId         = parseLong(sub(line, OFF_CARD_ACCT_ID,       11));
        r.cardCvvCd          = parseInt(sub(line, OFF_CARD_CVV_CD,         3));
        r.cardEmbossedName   = sub(line, OFF_CARD_EMBOSSED_NAME, 50);
        r.cardExpirationDate = sub(line, OFF_CARD_EXPIRY_DATE,   10);
        r.cardActiveStatus   = sub(line, OFF_CARD_ACTIVE_STATUS,  1);
        return r;
    }

    // -----------------------------------------------------------------------
    // Serialize to fixed-length string
    // -----------------------------------------------------------------------
    public String toLine() {
        return String.format("%-16s%011d%03d%-50s%-10s%-1s%-59s",
            nvl(cardNum), cardAcctId, cardCvvCd,
            nvl(cardEmbossedName), nvl(cardExpirationDate),
            nvl(cardActiveStatus), "");
    }

    @Override
    public String toString() {
        return String.format("CardRecord{cardNum=%s, acctId=%011d, status=%s}",
            cardNum, cardAcctId, cardActiveStatus);
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
