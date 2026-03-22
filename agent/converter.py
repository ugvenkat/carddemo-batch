"""
converter.py
------------
Handles all Claude API calls for conversion.
Contains carefully crafted prompts with all learnings
from manual COBOL/JCL to Java/Python conversion.

Key learnings baked into prompts:
  1. Copybooks: inline utility methods in each class (no shared utils)
  2. Copybooks: PIC S9(n)V99 = scaled integer n+3 chars (sign + n digits + 2 decimal digits)
  3. Copybooks: record length enforced with String.format("%-Ns").substring(0,N)
  4. COBOL: use shared copybook classes — NO inner record classes
  5. COBOL: field names must EXACTLY match copybook Java class field names
  6. COBOL: VSAM KSDS = HashMap loaded at open, flushed at close
  7. JCL: check os.path.exists() BEFORE subprocess.run() — don't rely on FileNotFoundError
  8. JCL: use $env: PowerShell syntax note in README
  9. JCL: IDCAMS DELETE = os.remove() with exists check
  10. JCL: IDCAMS DEFINE = create empty file + JSON metadata sidecar
  11. JCL: IDCAMS REPRO = shutil.copyfile()
  12. JCL: GDG (+1) = timestamp-suffixed filename
"""

import time
import logging
import anthropic

logger = logging.getLogger("Converter")

# Claude model to use
MODEL = "claude-opus-4-5"

# Max tokens per response
MAX_TOKENS = 8192


class Converter:

    def __init__(self, api_key: str, rate_limit_delay: int = 5):
        self.client = anthropic.Anthropic(api_key=api_key)
        self.delay  = rate_limit_delay

    # -----------------------------------------------------------------------
    # PHASE 1: Convert COBOL Copybook -> Java Record Class
    # -----------------------------------------------------------------------
    def convert_copybook(self, filename: str, source: str) -> str:
        """Converts a COBOL copybook to a Java record class."""

        # Lookup exact class name for this copybook
        copybook_class_map = {
            "CVACT01Y": "AccountRecord",
            "CVACT02Y": "CardRecord",
            "CVACT03Y": "CardXrefRecord",
            "CVCUS01Y": "CustomerRecord",
            "CVTRA01Y": "TranCatBalRecord",
            "CVTRA03Y": "TranTypeRecord",
            "CVTRA04Y": "TranCatRecord",
            "CVTRA05Y": "TranRecord",
            "CVTRA06Y": "DailyTranRecord",
            "CVTRA07Y": "ReportStructures",
            "CODATECN": "DateConversionRecord",
        }
        stem = filename.replace(".cpy", "").replace(".CPY", "").upper()
        exact_class_name = copybook_class_map.get(stem, stem.title())

        prompt = f"""You are an expert mainframe modernization engineer converting COBOL copybooks to Java.

Convert the following COBOL copybook to a Java class.

COPYBOOK FILENAME: {filename}
COPYBOOK SOURCE:
{source}

STRICT RULES — these come from hard-won experience fixing bugs:

0. CLASS NAMING — MOST CRITICAL RULE:
   The class name MUST exactly match the filename (without .java).
   Use ONLY these exact class names — DO NOT expand abbreviations:
     CVACT01Y.cpy  -> AccountRecord
     CVACT02Y.cpy  -> CardRecord
     CVACT03Y.cpy  -> CardXrefRecord
     CVCUS01Y.cpy  -> CustomerRecord
     CVTRA01Y.cpy  -> TranCatBalRecord
     CVTRA03Y.cpy  -> TranTypeRecord
     CVTRA04Y.cpy  -> TranCatRecord
     CVTRA05Y.cpy  -> TranRecord
     CVTRA06Y.cpy  -> DailyTranRecord
     CVTRA07Y.cpy  -> ReportStructures
     CODATECN.cpy  -> DateConversionRecord
   The class name for {filename} is: {exact_class_name}
   Your class declaration MUST be: public class {exact_class_name} {{
   WRONG: public class TransactionRecord    (expanded name — NOT allowed)
   WRONG: public class DailyTransactionRecord (expanded name — NOT allowed)
   RIGHT: public class TranRecord           (exact mapping above)
   RIGHT: public class DailyTranRecord      (exact mapping above)

1. CLASS STRUCTURE:
   - One Java class per copybook
   - Class name derived from copybook name using the exact mapping in rule 0 above
   - No package declaration — standalone class file
   - Import only java.math.BigDecimal and java.time.* if needed

2. FIELD MAPPING (CRITICAL — get these exactly right):
   - PIC X(n)        -> public String fieldName;   (padded to exactly n chars)
   - PIC 9(n)        -> public long fieldName;     (if n > 4)
   - PIC 9(n)        -> public int fieldName;      (if n <= 4)
   - PIC S9(n)V99    -> public BigDecimal fieldName;
   - PIC S9(n)V99 COMP-3 -> public BigDecimal fieldName;  (same — COMP-3 is storage only)
   - FILLER          -> SKIP — do not create a field
   - 88 level items  -> public static final String CONSTANT_NAME = "value";

3. RECORD LENGTH:
   - Calculate exact record length from all PIC fields (excluding FILLER)
   - Add FILLER bytes back to get total
   - PIC S9(n)V99 = n+3 chars (sign=1 + n digits + 2 decimal digits, NO decimal point in file)
   - Example: PIC S9(10)V99 = 13 chars
   - Example: PIC S9(9)V99  = 12 chars
   - Declare: public static final int RECORD_LENGTH = N;

4. REQUIRED METHODS — implement ALL of these:
   a) fromLine(String line) — parse fixed-length string into record fields
      - Use substring with exact offsets calculated from PIC sizes
      - For S9(n)V99 fields: stored as scaled integer WITHOUT decimal point
        e.g. 1000.00 stored as "+00000100000" (12 chars for S9(9)V99)
        Parse with: new BigDecimal(longValue).divide(new BigDecimal("100"))
   b) toLine() — serialize record to fixed-length string
      - For S9(n)V99 fields: multiply by 100, format as signed integer
        e.g. 1000.00 -> multiply by 100 -> 100000 -> format as "+00000100000"
      - Pad to exact RECORD_LENGTH: String.format("%-" + RECORD_LENGTH + "s", rec).substring(0, RECORD_LENGTH)
   c) key() — return the VSAM key field(s) as a String (for HashMap lookup)
   d) toString() — human readable summary

5. INLINE UTILITY METHODS — CRITICAL:
   Each class MUST have these as private static methods INSIDE the class.
   DO NOT put them in a separate utility class — Java cannot share private methods between top-level classes.
   - private static String sub(String s, int start, int length)
   - private static long parseLong(String s)
   - private static int parseInt(String s)
   - private static BigDecimal parseDec(String s)
   - private static BigDecimal parseScaledDec(String s)  // for S9(n)V99 fields
   - private static String nvl(String s)
   - private static BigDecimal nvlDec(BigDecimal d)

6. parseScaledDec IMPLEMENTATION (copy exactly):
   private static BigDecimal parseScaledDec(String s) {{
       if(s==null)return BigDecimal.ZERO;
       s=s.trim();
       if(s.isEmpty())return BigDecimal.ZERO;
       if(s.contains(".")) {{
           try{{return new BigDecimal(s);}}catch(NumberFormatException e){{return BigDecimal.ZERO;}}
       }}
       try{{
           return new BigDecimal(Long.parseLong(s)).divide(new java.math.BigDecimal("100"));
       }}catch(NumberFormatException e){{return BigDecimal.ZERO;}}
   }}

7. toLine() AMT FORMAT (copy exactly for S9(n)V99 fields):
   // S9(9)V99 = 12 chars total. Multiply by 100, format as 12-char signed integer:
   long amtScaled = nvlDec(tranAmt).multiply(new java.math.BigDecimal("100")).longValue();
   String amtStr = String.format("%+012d", amtScaled);  // 12 chars for S9(9)V99
   // S9(10)V99 = 13 chars total:
   long amtScaled = nvlDec(currBal).multiply(new java.math.BigDecimal("100")).longValue();
   String amtStr = String.format("%+013d", amtScaled);  // 13 chars for S9(10)V99

8. toLine() RECORD LENGTH — ALWAYS pad to exact length:
   String rec = String.format("%-16s%-2s%04d...", field1, field2, field3...);
   return String.format("%-" + RECORD_LENGTH + "s", rec).substring(0, RECORD_LENGTH);
   // NEVER use empty string "" as last format arg for filler — always use substring

9. COMPLETE toLine() EXAMPLE for a record with S9(9)V99 AMT field:
   public String toLine() {{
       long amtScaled = nvlDec(tranAmt).multiply(new java.math.BigDecimal("100")).longValue();
       String amtStr = String.format("%+012d", amtScaled);
       String rec = String.format("%-16s%-2s%04d%-10s%-100s%-12s%09d%-50s%-50s%-10s%-16s%-26s%-26s",
           nvl(tranId), nvl(tranTypeCd), tranCatCd, nvl(tranSource), nvl(tranDesc),
           amtStr, tranMerchantId,
           nvl(tranMerchantName), nvl(tranMerchantCity), nvl(tranMerchantZip),
           nvl(tranCardNum), nvl(tranOrigTs), nvl(tranProcTs));
       return String.format("%-" + RECORD_LENGTH + "s", rec).substring(0, RECORD_LENGTH);
   }}

Return ONLY the Java class code. No explanation, no markdown, no ```java blocks.
Start directly with the class javadoc comment and end with the closing brace.
"""
        return self._call_api(prompt, f"copybook {filename}")

    # -----------------------------------------------------------------------
    # PHASE 2: Convert COBOL Program -> Java Service Class
    # -----------------------------------------------------------------------
    def convert_cobol(self, filename: str, source: str,
                      used_copybooks: dict, converted_records: dict,
                      java_class_content: dict = None) -> str:
        """Converts a COBOL program to a Java service class."""

        # Build copybook context — include ACTUAL Java class content so Claude knows exact field names
        copybook_section = ""
        if java_class_content:
            copybook_section += "ACTUAL JAVA RECORD CLASSES — use EXACT field names shown below:\n"
            for java_class_name, java_src in java_class_content.items():
                import re
                fields = re.findall(r"public\s+(?:static\s+final\s+)?\w+\s+\w+[^(]", java_src)
                copybook_section += f"\n=== {java_class_name}.java ===\n"
                for f in fields[:30]:
                    copybook_section += f"  {f.strip()};\n"
        else:
            for cpy_name, cpy_source in used_copybooks.items():
                java_class = converted_records.get(cpy_name, cpy_name)
                copybook_section += f"\n--- {cpy_name}.cpy (Java class: {java_class}) ---\n{cpy_source}\n"

        prompt = f"""You are an expert mainframe modernization engineer converting COBOL batch programs to Java.

Convert the following COBOL program to a Java service class.

COBOL FILENAME: {filename}
COBOL SOURCE:
{source}

COPYBOOKS USED — EXACT JAVA CLASS FIELD NAMES (CRITICAL — use these EXACT names, no variations):
{copybook_section if copybook_section else "None"}

CONVERTED COPYBOOK CLASS NAMES:
{dict(converted_records)}

STRICT RULES — these come from hard-won experience fixing bugs:

0. FIELD ACCESS — MOST CRITICAL RULE:
   - All record class fields are PUBLIC — access them DIRECTLY, never use getters
   - WRONG: dalyTranRecord.getDalyTranCardNum()   <- NO getters!
   - RIGHT: dalyTranRecord.dalytranCardNum         <- direct field access
   - WRONG: accountRecord.getAcctId()              <- NO getters!
   - RIGHT: accountRecord.acctId                   <- direct field access
   - Use EXACTLY the field names shown in the Java class definitions above
   - Do NOT invent new field names or rename them

1. CLASS STRUCTURE:
   - Class name MUST use this EXACT mapping — do NOT deviate:
       CBTRN01C.cbl -> CbTrn01Service
       CBTRN02C.cbl -> CbTrn02Service
       CBTRN03C.cbl -> CbTrn03Service
       CBACT01C.cbl -> CbAct01Service
   - No package declaration — standalone class file
   - No inner record classes — use the shared copybook Java classes listed above
   - Imports: java.io.*, java.math.BigDecimal, java.nio.file.*, java.util.*, java.util.logging.*
   - Add: import java.time.LocalDateTime; import java.time.format.DateTimeFormatter; if timestamps needed

2. FIELD NAME RULES — CRITICAL:
   - Use EXACTLY the field names shown in the Java class definitions above
   - Do NOT invent your own field names, do NOT rename, do NOT add prefixes
   - The actual field names are shown in the COPYBOOKS section above — use those EXACTLY
   - Do NOT use getter methods like getXxxYyy() — all fields are public, access directly
   - WRONG: record.getFieldName()   RIGHT: record.fieldName
   - WRONG: inventing a field name  RIGHT: copy exact name from Java class shown above

3. FILE HANDLING:
   - File paths resolved from System.getenv(DD_NAME) with fallback default
   - VSAM KSDS OPEN INPUT  = load entire file into HashMap<KeyType, RecordClass> at open
   - VSAM KSDS OPEN I-O    = load into HashMap, flush back to disk at close
   - VSAM KSDS OPEN OUTPUT = BufferedWriter
   - Sequential OPEN INPUT = BufferedReader, read line by line
   - INVALID KEY           = map.get() returns null
   - REWRITE               = map.put() to update in place

4. PROCEDURE DIVISION MAPPING:
   - MAIN-PARA / PROCEDURE DIVISION = public void execute()
   - Each PERFORM paragraph = private method
   - GOBACK = return from execute()
   - CALL 'CEE3ABD' = throw new RuntimeException("ABEND: " + reason)
   - DISPLAY 'message' = logger.info("message")

5. JCL DD NAME MAPPING:
   - Each DD name becomes an env var key in constructor
   - Example: SELECT DALYTRAN-FILE ASSIGN TO DALYTRAN
     -> private final String dalyTranPath = getEnv("DALYTRAN", "data/DALYTRAN.dat");

6. TIMESTAMP (Z-GET-DB2-FORMAT-TIMESTAMP):
   - Use: DateTimeFormatter.ofPattern("yyyy-MM-dd-HH.mm.ss.SS'0000'").format(LocalDateTime.now())

7. RECORD PROCESSING:
   - Use RecordClass.fromLine(line) to parse each line read
   - Use record.toLine() to serialize when writing

8. UTILITY METHODS to include:
   private static String getEnv(String key, String def) {{
       String v = System.getenv(key);
       return (v != null && !v.isEmpty()) ? v : def;
   }}

9. Add detailed comments mapping each method back to the original COBOL paragraph name

10. REPORTING PROGRAM SPECIFIC RULES (for CBTRN03C):

    a) AMOUNT FORMATTING in detail line:
       When setting amount in report detail record use String.format:
       detail.tranReportAmt = tranRecord.tranAmt != null
           ? String.format("%.2f", tranRecord.tranAmt) : "0.00";
       NEVER use tranRecord.tranAmt.toString() — it omits decimal formatting

    b) NO DOUBLE COUNTING of amounts at EOF:
       The main loop has TWO paths:
         - Normal path: calls writeTransactionReport() which adds tranAmt to wsPageTotal
         - EOF path: should ONLY write totals, NEVER add tranAmt again
       WRONG pattern — causes double counting:
         }} else {{
             wsPageTotal = wsPageTotal.add(tranRecord.tranAmt);  // BUG — already added!
             writePageTotals();
         }}
       CORRECT pattern:
         }} else {{
             writePageTotals();    // just write — do NOT add tranAmt again
             writeGrandTotals();
         }}

    c) GRAND TOTAL accumulation:
       Grand total should be accumulated inside writePageTotals() from wsPageTotal
       before resetting wsPageTotal to ZERO:
         wsGrandTotal = wsGrandTotal.add(wsPageTotal);  // accumulate first
         wsPageTotal = BigDecimal.ZERO;                  // then reset

11. SPECIAL CLASS NOTES — READ CAREFULLY:

    a) ReportStructures (CVTRA07Y):
       This class has INNER STATIC CLASSES — use them like this:
       ReportStructures.ReportNameHeader header = new ReportStructures.ReportNameHeader();
       header.reptStartDate = wsStartDate;
       header.reptEndDate = wsEndDate;
       writeReportRec(header.toLine());

       ReportStructures.TransactionDetailReport detail = new ReportStructures.TransactionDetailReport();
       detail.tranReportTransId = tran.tranId;
       detail.tranReportAccountId = String.valueOf(xref.xrefAcctId);
       writeReportRec(detail.toLine());

       For separator line use: "-".repeat(133)
       For header 1 use: ReportStructures.TransactionHeader inner class
       For totals use: ReportStructures.ReportPageTotals, ReportAccountTotals, ReportGrandTotals

    b) DateConversionRecord (CODATECN):
       EXACT field names (zero not letter O in codatecn0utDate):
         codatecnType      = input type ("1"=YYYYMMDD, "2"=YYYY-MM-DD)
         codatecnInpDate   = input date string
         codatecnOuttype   = output type ("1"=YYYY-MM-DD, "2"=YYYYMMDD)
         codatecn0utDate   = output date string (NOTE: zero not letter O)
       NO convert() method exists — set fields manually and read codatecn0utDate
       For date conversion in CbAct01Service, use java.time.LocalDate instead:
         String converted = LocalDate.parse(inputDate.trim(),
             DateTimeFormatter.BASIC_ISO_DATE).format(DateTimeFormatter.ISO_LOCAL_DATE);

Return ONLY the Java class code. No explanation, no markdown, no ```java blocks.
Start directly with the class javadoc comment and end with the closing brace.
"""
        return self._call_api(prompt, f"COBOL {filename}")

    # -----------------------------------------------------------------------
    # PHASE 3: Convert JCL -> Python Script
    # -----------------------------------------------------------------------
    def convert_jcl(self, filename: str, source: str) -> str:
        """Converts a JCL job to a Python script."""

        prompt = f"""You are an expert mainframe modernization engineer converting JCL batch jobs to Python.

Convert the following JCL job to a Python script.

JCL FILENAME: {filename}
JCL SOURCE:
{source}

STRICT RULES — these come from hard-won experience fixing bugs:

1. SCRIPT STRUCTURE:
   - Script name = jcl filename lowercased (e.g. POSTTRAN.jcl -> posttran.py)
   - Job = main() function
   - Each EXEC step = separate function (e.g. step05_delete_vsam(), run_step15())
   - Use Python logging module (replaces SYSPRINT/SYSOUT=*)
   - Use sys.exit(rc) to exit with return code

2. DD NAME MAPPING:
   - Each DD DSN= becomes an environment variable
   - Read with: os.getenv("DD_NAME", "data/default_filename")
   - Log all DD allocations at start of each step

3. IDCAMS COMMANDS:
   - DELETE CLUSTER    = os.remove() with os.path.exists() check first
   - IF MAXCC LE 08    = file not found is OK, continue
   - SET MAXCC = 0     = unconditional success, always return 0
   - DEFINE CLUSTER    = create empty file + write JSON metadata sidecar
   - REPRO INFILE->OUTFILE = shutil.copyfile(src, dst)

4. EXEC PGM= CALLS — CRITICAL FIX:
   Use this pattern EXACTLY — check file exists BEFORE subprocess.run():

   program_file = f"{{program_name.lower()}}.py"
   if not os.path.exists(program_file):
       logger.warning(f"Program {{program_file}} not found. Skipping.")
       return 0
   try:
       result = subprocess.run([sys.executable, program_file], env=env)
       return result.returncode
   except Exception as e:
       logger.error(f"Error running {{program_file}}: {{e}}")
       return 8

   DO NOT use try/except FileNotFoundError alone — it does NOT catch the case
   where Python finds the interpreter but cannot open the script file.

5. GDG (+1) SIMULATION:
   - DSN=FILENAME(+1) = timestamped filename
   - Example: AWS.M2.CARDDEMO.DALYREJS(+1)
     -> f"data/DALYREJS_{{datetime.now().strftime('%Y%m%d_%H%M%S')}}.dat"

6. VSAM CLUSTER METADATA:
   When simulating IDCAMS DEFINE CLUSTER, write a JSON sidecar file:
   {{
     "name": "original.vsam.name",
     "type": "KSDS",
     "key_length": N,
     "key_offset": 0,
     "record_size": N,
     "created_at": "ISO timestamp"
   }}

7. PARM= VALUES:
   - EXEC PGM=PROGRAM,PARM='value' -> pass as env var PARM and CLI arg
   - Default to today's date if PARM is a date

8. DELAYS:
   - No artificial delays needed in JCL conversion
   - Delays are handled by the agent orchestrator

9. Add comments mapping each step back to the original JCL step name and PGM

Return ONLY the Python script code. No explanation, no markdown, no ```python blocks.
Start directly with the module docstring and end with the if __name__ == "__main__" block.
"""
        return self._call_api(prompt, f"JCL {filename}")

    # -----------------------------------------------------------------------
    # Generate JUnit Test for Java class
    # -----------------------------------------------------------------------
    def generate_junit_test(self, class_name: str, java_source: str, kind: str) -> str:
        """Generates a JUnit 5 test class for a Java record or service class."""

        prompt = f"""You are an expert Java developer writing JUnit 5 tests.

Write a JUnit 5 test class for the following Java {'record' if kind == 'record' else 'service'} class.

CLASS NAME: {class_name}
KIND: {kind}
JAVA SOURCE:
{java_source[:3000]}  

RULES:
1. Use JUnit 5 (@Test, @BeforeEach, Assertions.*)
2. No package declaration
3. Import: import org.junit.jupiter.api.*;
4. For record classes:
   - Test fromLine() with valid fixed-length input
   - Test toLine() roundtrip (fromLine then toLine should give same length)
   - Test key() returns correct key
   - Test null/empty input handling
5. For service classes:
   - Test with temp files using @TempDir
   - Test happy path — valid input produces correct output
   - Mock missing files gracefully
6. Use descriptive test method names
7. Add a comment explaining what each test verifies

Return ONLY the Java test class code. No explanation, no markdown blocks.
"""
        return self._call_api(prompt, f"JUnit test for {class_name}")

    # -----------------------------------------------------------------------
    # Generate pytest Test for Python script
    # -----------------------------------------------------------------------
    def generate_pytest(self, script_name: str, python_source: str, jcl_filename: str) -> str:
        """Generates a pytest test module for a Python JCL script."""

        prompt = f"""You are an expert Python developer writing pytest tests.

Write pytest tests for the following Python JCL conversion script.

SCRIPT NAME: {script_name}.py
ORIGINAL JCL: {jcl_filename}
PYTHON SOURCE:
{python_source[:3000]}

RULES:
1. Use pytest with tmp_path fixture for temp files
2. Use monkeypatch to set environment variables
3. Test each step function individually
4. Test happy path — valid input produces RC=0
5. Test missing input file returns appropriate RC
6. Test that output files are created correctly
7. No external dependencies beyond pytest and standard library
8. Add docstrings explaining what each test verifies

Return ONLY the pytest test module code. No explanation, no markdown blocks.
Start with imports and end with the last test function.
"""
        return self._call_api(prompt, f"pytest for {script_name}")

    # -----------------------------------------------------------------------
    # Internal API call with retry and rate limiting
    # -----------------------------------------------------------------------
    def _call_api(self, prompt: str, description: str) -> str:
        """
        Makes a Claude API call with retry logic.
        Respects rate limits with configurable delay.
        """
        max_retries = 3
        retry_delay = 30  # seconds to wait on rate limit error

        for attempt in range(1, max_retries + 1):
            try:
                logger.info(f"  API call for {description} (attempt {attempt}/{max_retries})")

                message = self.client.messages.create(
                    model=MODEL,
                    max_tokens=MAX_TOKENS,
                    messages=[
                        {"role": "user", "content": prompt}
                    ]
                )

                result = message.content[0].text.strip()

                # Strip markdown code fences if Claude added them
                result = self._strip_code_fences(result)

                logger.info(f"  API call successful — {len(result)} chars returned")
                # Warn if output looks truncated
                if result and not result.rstrip().endswith('}'):
                    logger.warning(f"  Output may be truncated — does not end with }}")
                return result

            except anthropic.RateLimitError as e:
                logger.warning(f"  Rate limit hit! Waiting {retry_delay}s before retry...")
                time.sleep(retry_delay)
                retry_delay *= 2  # exponential backoff

            except anthropic.APIError as e:
                logger.error(f"  API error on attempt {attempt}: {e}")
                if attempt < max_retries:
                    time.sleep(self.delay * 2)
                else:
                    logger.error(f"  All retries exhausted for {description}")
                    return None

        return None

    def _strip_code_fences(self, text: str) -> str:
        """Removes markdown code fences if present in API response."""
        lines = text.splitlines()
        if lines and lines[0].startswith("```"):
            lines = lines[1:]
        if lines and lines[-1].strip() == "```":
            lines = lines[:-1]
        return "\n".join(lines)
