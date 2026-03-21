/**
 * CbAct01Service.java  (CORRECTED - uses copybook record classes)
 * -------------------
 * Java equivalent of CBACT01C.CBL
 *
 * COBOL Program  : CBACT01C.CBL
 * Application    : CardDemo
 * Type           : Batch COBOL Program
 * Function       : Read Account VSAM file sequentially and write to three
 *                  output files in different formats.
 *
 * Copybooks used (now as proper Java classes):
 *   COPY CVACT01Y  -> AccountRecord         (account data)
 *   COPY CODATECN  -> DateConversionRecord  (date format conversion utility)
 *
 * Corrections from original version:
 *   - Removed inner AccountRecord class
 *   - Now uses shared AccountRecord.java (CVACT01Y)
 *   - Fixed AccountRecord field names — all now match CVACT01Y exactly:
 *       acctId, activeStatus, currBal, creditLimit, cashCreditLimit,
 *       openDate, expirationDate, reissueDate, currCycCredit, currCycDebit,
 *       addrZip, groupId
 *   - Removed inline convertDate() method
 *   - Now uses DateConversionRecord.convert() (CODATECN) for date conversion
 *       mirrors: CALL 'COBDATFT' USING CODATECN-REC
 *
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * Licensed under the Apache License, Version 2.0
 */

import java.io.*;
import java.math.BigDecimal;
import java.nio.file.*;
import java.util.*;
import java.util.logging.*;

public class CbAct01Service {

    private static final Logger logger = Logger.getLogger(CbAct01Service.class.getName());

    // -----------------------------------------------------------------------
    // File paths — mirror COBOL FILE-CONTROL SELECT ... ASSIGN TO <ddname>
    // -----------------------------------------------------------------------
    private final String acctFilePath;  // ACCTFILE — account VSAM KSDS (sequential read)
    private final String outFilePath;   // OUTFILE  — formatted account output
    private final String arryFilePath;  // ARRYFILE — array-style output
    private final String vbrcFilePath;  // VBRCFILE — variable-length record output

    // -----------------------------------------------------------------------
    // Working storage
    // -----------------------------------------------------------------------
    private boolean endOfFile = false;  // END-OF-FILE PIC X VALUE 'N'
    private int     wsRecdLen = 0;      // WS-RECD-LEN PIC 9(04)

    // -----------------------------------------------------------------------
    // Output record structures — mirrors COBOL FD layouts
    // -----------------------------------------------------------------------

    /**
     * OUT-ACCT-REC — formatted sequential output (written to OUTFILE)
     * Populated in 1300-POPUL-ACCT-RECORD from AccountRecord (CVACT01Y)
     */
    static class OutAccountRecord {
        long       acctId;
        String     activeStatus;
        BigDecimal currBal;
        BigDecimal creditLimit;
        BigDecimal cashCreditLimit;
        String     openDate;
        String     expirationDate;
        String     reissueDate;       // converted via DateConversionRecord (CODATECN)
        BigDecimal currCycCredit;
        BigDecimal currCycDebit;
        String     groupId;

        String toLine() {
            return String.format("%011d%-1s%+13.2f%+13.2f%+13.2f%-10s%-10s%-10s%+13.2f%+13.2f%-10s",
                acctId, nvl(activeStatus),
                nvlD(currBal), nvlD(creditLimit), nvlD(cashCreditLimit),
                nvl(openDate), nvl(expirationDate), nvl(reissueDate),
                nvlD(currCycCredit), nvlD(currCycDebit), nvl(groupId));
        }
    }

    /**
     * ARR-ARRAY-REC — array output (written to ARRYFILE)
     * Mirrors: ARR-ACCT-BAL OCCURS 5 TIMES
     */
    static class ArrayAccountRecord {
        long         acctId;
        BigDecimal[] currBal      = new BigDecimal[5];
        BigDecimal[] currCycDebit = new BigDecimal[5];

        ArrayAccountRecord() {
            Arrays.fill(currBal,      BigDecimal.ZERO);
            Arrays.fill(currCycDebit, BigDecimal.ZERO);
        }

        String toLine() {
            StringBuilder sb = new StringBuilder(String.format("%011d", acctId));
            for (int i = 0; i < 5; i++)
                sb.append(String.format("%+13.2f%+13.2f", nvlD(currBal[i]), nvlD(currCycDebit[i])));
            sb.append(String.format("%-4s", ""));  // ARR-FILLER PIC X(04)
            return sb.toString();
        }
    }

    /**
     * VBRC-REC1 — short variable-length record (12 bytes)
     * Contains: VB1-ACCT-ID PIC 9(11) + VB1-ACCT-ACTIVE-STATUS PIC X(01)
     */
    static class VbrcRec1 {
        long   acctId;
        String activeStatus;
        String toFixedString() { return String.format("%011d%-1s", acctId, nvl(activeStatus)); }
    }

    /**
     * VBRC-REC2 — longer variable-length record (39 bytes)
     * Contains: VB2-ACCT-ID + VB2-ACCT-CURR-BAL + VB2-ACCT-CREDIT-LIMIT + VB2-ACCT-REISSUE-YYYY
     */
    static class VbrcRec2 {
        long       acctId;
        BigDecimal currBal;
        BigDecimal creditLimit;
        String     reissueYyyy;
        String toFixedString() {
            return String.format("%011d%+13.2f%+13.2f%-4s",
                acctId, nvlD(currBal), nvlD(creditLimit), nvl(reissueYyyy));
        }
    }

    // -----------------------------------------------------------------------
    // Constructor
    // -----------------------------------------------------------------------
    public CbAct01Service() {
        acctFilePath = getEnv("ACCTFILE",  "data/ACCTDATA.dat");
        outFilePath  = getEnv("OUTFILE",   "data/ACCTOUT.dat");
        arryFilePath = getEnv("ARRYFILE",  "data/ACCTARRY.dat");
        vbrcFilePath = getEnv("VBRCFILE",  "data/ACCTVBRC.dat");
    }

    // -----------------------------------------------------------------------
    // PROCEDURE DIVISION
    // -----------------------------------------------------------------------
    public void execute() {
        logger.info("START OF EXECUTION OF PROGRAM CBACT01C");
        openFiles();

        // PERFORM UNTIL END-OF-FILE = 'Y'
        while (!endOfFile) {
            // 1000-ACCTFILE-GET-NEXT — AccountRecord (CVACT01Y)
            AccountRecord acct = getNextAccount();
            if (acct == null) break;

            displayAccountRecord(acct);             // 1100-DISPLAY-ACCT-RECORD

            OutAccountRecord outRec = populateAccountRecord(acct);  // 1300-POPUL-ACCT-RECORD
            writeAccountRecord(outRec);             // 1350-WRITE-ACCT-RECORD

            ArrayAccountRecord arrRec = populateArrayRecord(acct);  // 1400-POPUL-ARRAY-RECORD
            writeArrayRecord(arrRec);               // 1450-WRITE-ARRY-RECORD

            VbrcRec1 vb1 = new VbrcRec1();
            VbrcRec2 vb2 = new VbrcRec2();
            populateVbrcRecord(acct, vb1, vb2);    // 1500-POPUL-VBRC-RECORD
            writeVb1Record(vb1);                   // 1550-WRITE-VB1-RECORD
            writeVb2Record(vb2);                   // 1575-WRITE-VB2-RECORD
        }

        closeFiles();
        logger.info("END OF EXECUTION OF PROGRAM CBACT01C");
    }

    // -----------------------------------------------------------------------
    // Open / close files
    // -----------------------------------------------------------------------
    private BufferedWriter outWriter, arryWriter, vbrcWriter;
    private BufferedReader acctReader;

    private void openFiles() {
        try {
            outWriter  = Files.newBufferedWriter(Paths.get(outFilePath));
            arryWriter = Files.newBufferedWriter(Paths.get(arryFilePath));
            vbrcWriter = Files.newBufferedWriter(Paths.get(vbrcFilePath));
            logger.info("All files opened");
        } catch (IOException e) { abend("ERROR OPENING OUTPUT FILES: " + e.getMessage()); }
    }

    private void closeFiles() {
        try { if (outWriter  != null) outWriter.close();  } catch (IOException ignored) {}
        try { if (arryWriter != null) arryWriter.close(); } catch (IOException ignored) {}
        try { if (vbrcWriter != null) vbrcWriter.close(); } catch (IOException ignored) {}
        try { if (acctReader != null) acctReader.close(); } catch (IOException ignored) {}
        logger.info("All files closed");
    }

    // -----------------------------------------------------------------------
    // 1000-ACCTFILE-GET-NEXT — AccountRecord (CVACT01Y)
    // -----------------------------------------------------------------------
    private AccountRecord getNextAccount() {
        try {
            if (acctReader == null)
                acctReader = Files.newBufferedReader(Paths.get(acctFilePath));
            String line = acctReader.readLine();
            if (line == null) { endOfFile = true; return null; }
            return AccountRecord.fromLine(line);  // CVACT01Y
        } catch (IOException e) { abend("ERROR READING ACCTFILE: " + e.getMessage()); return null; }
    }

    // -----------------------------------------------------------------------
    // 1100-DISPLAY-ACCT-RECORD — DISPLAY ACCOUNT-RECORD
    // Uses AccountRecord (CVACT01Y) fields
    // -----------------------------------------------------------------------
    private void displayAccountRecord(AccountRecord acct) {
        // All field names now match CVACT01Y exactly
        logger.info(String.format("ACCT-ID=%011d STATUS=%s BAL=%s CREDIT-LIMIT=%s",
            acct.acctId,          // CVACT01Y: acctId
            acct.activeStatus,    // CVACT01Y: activeStatus
            acct.currBal,         // CVACT01Y: currBal
            acct.creditLimit));   // CVACT01Y: creditLimit
    }

    // -----------------------------------------------------------------------
    // 1300-POPUL-ACCT-RECORD
    // Uses AccountRecord (CVACT01Y) and DateConversionRecord (CODATECN)
    // -----------------------------------------------------------------------
    private OutAccountRecord populateAccountRecord(AccountRecord acct) {
        OutAccountRecord out = new OutAccountRecord();

        // Direct field moves — all names match CVACT01Y
        out.acctId          = acct.acctId;           // ACCT-ID
        out.activeStatus    = acct.activeStatus;      // ACCT-ACTIVE-STATUS
        out.currBal         = acct.currBal;           // ACCT-CURR-BAL
        out.creditLimit     = acct.creditLimit;       // ACCT-CREDIT-LIMIT
        out.cashCreditLimit = acct.cashCreditLimit;   // ACCT-CASH-CREDIT-LIMIT
        out.openDate        = acct.openDate;          // ACCT-OPEN-DATE
        out.expirationDate  = acct.expirationDate;    // ACCT-EXPIRAION-DATE
        out.currCycCredit   = acct.currCycCredit;     // ACCT-CURR-CYC-CREDIT
        out.groupId         = acct.groupId;           // ACCT-GROUP-ID

        // Date conversion via DateConversionRecord (CODATECN)
        // Mirrors: CALL 'COBDATFT' USING CODATECN-REC
        out.reissueDate = convertReissueDate(acct.reissueDate);

        // Business rule: IF ACCT-CURR-CYC-DEBIT = ZERO MOVE 2525.00
        out.currCycDebit = acct.currCycDebit.compareTo(BigDecimal.ZERO) == 0
            ? new BigDecimal("2525.00")
            : acct.currCycDebit;

        return out;
    }

    /**
     * Mirrors CALL 'COBDATFT' USING CODATECN-REC
     * Uses DateConversionRecord (CODATECN copybook)
     */
    private String convertReissueDate(String reissueDate) {
        if (reissueDate == null || reissueDate.isBlank()) return "          ";

        // Set up DateConversionRecord (CODATECN)
        DateConversionRecord conv = new DateConversionRecord();
        conv.inputDate  = reissueDate.trim();
        conv.outputType = DateConversionRecord.YYYY_MM_DD_OP; // output as YYYY-MM-DD

        // Determine input type — mirrors CODATECN-TYPE 88 conditions
        if (reissueDate.trim().length() == 8 && !reissueDate.contains("-")) {
            conv.inputType = DateConversionRecord.YYYYMMDD_IN;   // "1"
        } else {
            conv.inputType = DateConversionRecord.YYYY_MM_DD_IN; // "2"
        }

        conv.convert();  // CALL 'COBDATFT'

        if (!conv.errorMsg.isEmpty()) {
            logger.warning("Date conversion error for [" + reissueDate + "]: " + conv.errorMsg);
            return String.format("%-10s", reissueDate.trim());
        }

        return conv.outputDate != null ? String.format("%-10s", conv.outputDate) : "          ";
    }

    // -----------------------------------------------------------------------
    // 1350-WRITE-ACCT-RECORD — WRITE OUT-ACCT-REC
    // -----------------------------------------------------------------------
    private void writeAccountRecord(OutAccountRecord out) {
        try { outWriter.write(out.toLine()); outWriter.newLine(); }
        catch (IOException e) { abend("ACCOUNT FILE WRITE ERROR: " + e.getMessage()); }
    }

    // -----------------------------------------------------------------------
    // 1400-POPUL-ARRAY-RECORD — uses AccountRecord (CVACT01Y) fields
    // -----------------------------------------------------------------------
    private ArrayAccountRecord populateArrayRecord(AccountRecord acct) {
        ArrayAccountRecord arr = new ArrayAccountRecord();
        arr.acctId = acct.acctId;                              // CVACT01Y: acctId

        arr.currBal[0]      = acct.currBal;                    // CVACT01Y: currBal
        arr.currCycDebit[0] = new BigDecimal("1005.00");

        arr.currBal[1]      = acct.currBal;                    // CVACT01Y: currBal
        arr.currCycDebit[1] = new BigDecimal("1525.00");

        arr.currBal[2]      = new BigDecimal("-1025.00");
        arr.currCycDebit[2] = new BigDecimal("-2500.00");

        return arr;
    }

    // -----------------------------------------------------------------------
    // 1450-WRITE-ARRY-RECORD
    // -----------------------------------------------------------------------
    private void writeArrayRecord(ArrayAccountRecord arr) {
        try { arryWriter.write(arr.toLine()); arryWriter.newLine(); }
        catch (IOException e) { abend("ARRAY FILE WRITE ERROR: " + e.getMessage()); }
    }

    // -----------------------------------------------------------------------
    // 1500-POPUL-VBRC-RECORD — uses AccountRecord (CVACT01Y) fields
    // -----------------------------------------------------------------------
    private void populateVbrcRecord(AccountRecord acct, VbrcRec1 vb1, VbrcRec2 vb2) {
        vb1.acctId       = acct.acctId;           // CVACT01Y: acctId
        vb1.activeStatus = acct.activeStatus;      // CVACT01Y: activeStatus

        vb2.acctId      = acct.acctId;            // CVACT01Y: acctId
        vb2.currBal     = acct.currBal;           // CVACT01Y: currBal
        vb2.creditLimit = acct.creditLimit;        // CVACT01Y: creditLimit
        // MOVE WS-ACCT-REISSUE-YYYY TO VB2-ACCT-REISSUE-YYYY
        // reissueDate from CVACT01Y, extract first 4 chars (YYYY)
        vb2.reissueYyyy = acct.reissueDate != null && acct.reissueDate.trim().length() >= 4
            ? acct.reissueDate.trim().substring(0, 4) : "    ";

        logger.info("VBRC-REC1: ACCT-ID=" + vb1.acctId + " STATUS=" + vb1.activeStatus);
        logger.info("VBRC-REC2: ACCT-ID=" + vb2.acctId + " BAL=" + vb2.currBal);
    }

    // -----------------------------------------------------------------------
    // 1550-WRITE-VB1-RECORD — MOVE 12 TO WS-RECD-LEN, WRITE VBR-REC
    // -----------------------------------------------------------------------
    private void writeVb1Record(VbrcRec1 vb1) {
        wsRecdLen = 12;
        writeVbrcLine("VB1", String.format("%-" + wsRecdLen + "s", vb1.toFixedString()));
    }

    // -----------------------------------------------------------------------
    // 1575-WRITE-VB2-RECORD — MOVE 39 TO WS-RECD-LEN, WRITE VBR-REC
    // -----------------------------------------------------------------------
    private void writeVb2Record(VbrcRec2 vb2) {
        wsRecdLen = 39;
        writeVbrcLine("VB2", String.format("%-" + wsRecdLen + "s", vb2.toFixedString()));
    }

    private void writeVbrcLine(String type, String record) {
        try {
            vbrcWriter.write(String.format("%s:%04d:%s", type, wsRecdLen, record));
            vbrcWriter.newLine();
        } catch (IOException e) { abend("VBRC FILE WRITE ERROR: " + e.getMessage()); }
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

    private static String nvl(String s)          { return s == null ? "" : s; }
    private static BigDecimal nvlD(BigDecimal d)  { return d == null ? BigDecimal.ZERO : d; }

    public static void main(String[] args) { new CbAct01Service().execute(); }
}
