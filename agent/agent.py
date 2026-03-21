"""
agent.py
--------
Agentic AI Mainframe Modernization Converter
Converts COBOL copybooks, programs and JCL to Java and Python
using the Claude API (Anthropic).

Processing Order (learned from manual conversion):
  Phase 1: Copybooks  (.cpy) -> Java record classes  (records/)
  Phase 2: COBOL      (.cbl) -> Java service classes (services/)
  Phase 3: JCL        (.jcl) -> Python scripts       (python/)

Output Structure:
  converted-usingAgent/
    java/
      records/    <- copybook Java classes
      services/   <- COBOL Java service classes
    python/       <- JCL Python scripts
    tests/
      java/
        records/  <- JUnit tests for record classes
        services/ <- JUnit tests for service classes
      python/     <- pytest tests for Python scripts
    docs/         <- README per converted file

Usage:
  1. Set ANTHROPIC_API_KEY in .env file
  2. Run: python agent.py --src ./src --out ./converted-usingAgent

Copyright - Apache 2.0 - based on AWS CardDemo sample
"""

import os
import sys
import time
import argparse
import logging
from pathlib import Path
from dotenv import load_dotenv

from file_reader   import FileReader
from converter     import Converter
from file_writer   import FileWriter

# ---------------------------------------------------------------------------
# Logging
# ---------------------------------------------------------------------------
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
    handlers=[logging.StreamHandler(sys.stdout)]
)
logger = logging.getLogger("Agent")

# ---------------------------------------------------------------------------
# Rate limit delay (seconds between API calls)
# Adjust based on your Claude API tier:
#   Free tier:  5-6 seconds
#   Paid tier:  2-3 seconds
# ---------------------------------------------------------------------------
RATE_LIMIT_DELAY = 5


def run_agent(src_dir: str, out_dir: str, clean: bool = False):
    """
    Main agent orchestrator.
    Processes files in correct dependency order:
      Phase 1: Copybooks -> Java record classes
      Phase 2: COBOL     -> Java service classes (uses copybook classes)
      Phase 3: JCL       -> Python scripts
    """
    load_dotenv()
    api_key = os.getenv("ANTHROPIC_API_KEY")
    if not api_key:
        logger.error("ANTHROPIC_API_KEY not found in .env file!")
        logger.error("Create a .env file with: ANTHROPIC_API_KEY=sk-ant-api03-...")
        sys.exit(1)

    reader    = FileReader(src_dir)
    converter = Converter(api_key, RATE_LIMIT_DELAY)
    writer    = FileWriter(out_dir, clean)

    # ------------------------------------------------------------------
    # Phase 1: Copybooks -> Java record classes
    # MUST run first — COBOL services depend on these classes
    # ------------------------------------------------------------------
    logger.info("=" * 60)
    logger.info("PHASE 1 — COPYBOOKS -> JAVA RECORD CLASSES")
    logger.info("=" * 60)

    copybooks = reader.get_copybooks()
    logger.info(f"Found {len(copybooks)} copybook(s): {[f.name for f in copybooks]}")

    # Collect all copybook content — passed to COBOL phase for context
    copybook_context = {}
    converted_records = {}

    for cpy_file in copybooks:
        # Derive output path and skip if already converted
        class_name = derive_java_class_name(cpy_file.name, "record")
        out_path = Path(out_dir) / "java" / "records" / f"{class_name}.java"
        if out_path.exists():
            logger.info(f"Skipping {cpy_file.name} — already converted ({class_name}.java exists)")
            source = cpy_file.read_text(encoding="utf-8", errors="replace")
            copybook_context[cpy_file.stem] = source
            converted_records[cpy_file.stem] = class_name
            continue

        logger.info(f"Converting copybook: {cpy_file.name}")
        source = cpy_file.read_text(encoding="utf-8", errors="replace")

        java_class = converter.convert_copybook(cpy_file.name, source)

        if java_class:
            class_name = derive_java_class_name(cpy_file.name, "record")
            writer.write_java_record(class_name, java_class)
            copybook_context[cpy_file.stem] = source
            converted_records[cpy_file.stem] = class_name
            logger.info(f"  -> {class_name}.java written to java/records/")

            # Write JUnit test
            logger.info(f"  Generating JUnit test for {class_name}")
            junit_test = converter.generate_junit_test(class_name, java_class, "record")
            if junit_test:
                writer.write_java_test_record(f"{class_name}Test", junit_test)
                logger.info(f"  -> {class_name}Test.java written to tests/java/records/")
        else:
            logger.warning(f"  Skipped — no output from API for {cpy_file.name}")

        time.sleep(RATE_LIMIT_DELAY)

    # ------------------------------------------------------------------
    # Phase 2: COBOL -> Java service classes
    # Passes copybook context so field names are used correctly
    # ------------------------------------------------------------------
    logger.info("=" * 60)
    logger.info("PHASE 2 — COBOL -> JAVA SERVICE CLASSES")
    logger.info("=" * 60)

    cobol_files = reader.get_cobol_files()
    logger.info(f"Found {len(cobol_files)} COBOL file(s): {[f.name for f in cobol_files]}")

    for cbl_file in cobol_files:
        # Skip if already converted
        class_name = derive_java_class_name(cbl_file.name, "service")
        out_path = Path(out_dir) / "java" / "services" / f"{class_name}.java"
        if out_path.exists():
            logger.info(f"Skipping {cbl_file.name} — already converted ({class_name}.java exists)")
            continue

        logger.info(f"Converting COBOL: {cbl_file.name}")
        source = cbl_file.read_text(encoding="utf-8", errors="replace")

        # Find which copybooks this program uses
        used_copybooks = find_used_copybooks(source, copybook_context)
        logger.info(f"  Uses copybooks: {list(used_copybooks.keys())}")

        # Read the actual generated Java class content for each used copybook
        # This gives Claude the EXACT field names to use — critical for correct generation
        java_class_content = {}
        for cpy_stem in used_copybooks.keys():
            java_class_name = converted_records.get(cpy_stem, cpy_stem)
            java_file = Path(out_dir) / "java" / "records" / f"{java_class_name}.java"
            if java_file.exists():
                java_class_content[java_class_name] = java_file.read_text(encoding="utf-8")
                logger.info(f"  Loaded Java class: {java_class_name}.java")

        java_service = converter.convert_cobol(
            cbl_file.name, source, used_copybooks, converted_records, java_class_content
        )

        if java_service:
            class_name = derive_java_class_name(cbl_file.name, "service")
            writer.write_java_service(class_name, java_service)
            logger.info(f"  -> {class_name}.java written to java/services/")

            # Write JUnit test
            logger.info(f"  Generating JUnit test for {class_name}")
            junit_test = converter.generate_junit_test(class_name, java_service, "service")
            if junit_test:
                writer.write_java_test_service(f"{class_name}Test", junit_test)
                logger.info(f"  -> {class_name}Test.java written to tests/java/services/")
        else:
            logger.warning(f"  Skipped — no output from API for {cbl_file.name}")

        time.sleep(RATE_LIMIT_DELAY)

    # ------------------------------------------------------------------
    # Phase 3: JCL -> Python scripts
    # ------------------------------------------------------------------
    logger.info("=" * 60)
    logger.info("PHASE 3 — JCL -> PYTHON SCRIPTS")
    logger.info("=" * 60)

    jcl_files = reader.get_jcl_files()
    logger.info(f"Found {len(jcl_files)} JCL file(s): {[f.name for f in jcl_files]}")

    for jcl_file in jcl_files:
        # Skip if already converted
        script_name = jcl_file.stem.lower()
        out_path = Path(out_dir) / "python" / f"{script_name}.py"
        if out_path.exists():
            logger.info(f"Skipping {jcl_file.name} — already converted ({script_name}.py exists)")
            continue

        logger.info(f"Converting JCL: {jcl_file.name}")
        source = jcl_file.read_text(encoding="utf-8", errors="replace")

        python_script = converter.convert_jcl(jcl_file.name, source)

        if python_script:
            script_name = jcl_file.stem.lower()
            writer.write_python(script_name, python_script)
            logger.info(f"  -> {script_name}.py written to python/")

            # Write pytest test
            logger.info(f"  Generating pytest test for {script_name}")
            pytest_test = converter.generate_pytest(script_name, python_script, jcl_file.name)
            if pytest_test:
                writer.write_python_test(f"test_{script_name}", pytest_test)
                logger.info(f"  -> test_{script_name}.py written to tests/python/")
        else:
            logger.warning(f"  Skipped — no output from API for {jcl_file.name}")

        time.sleep(RATE_LIMIT_DELAY)

    # ------------------------------------------------------------------
    # Summary
    # ------------------------------------------------------------------
    logger.info("=" * 60)
    logger.info("AGENT COMPLETED SUCCESSFULLY")
    logger.info(f"Output written to: {out_dir}")
    logger.info("=" * 60)
    writer.print_summary()


def derive_java_class_name(filename: str, kind: str) -> str:
    """
    Derives Java class name from COBOL/copybook filename.
    Examples:
      CVACT01Y.cpy  -> AccountRecord     (kind=record)
      CBTRN01C.cbl  -> CbTrn01Service    (kind=service)
    """
    stem = Path(filename).stem.upper()

    # Copybook -> Record class name mappings (learned from manual conversion)
    copybook_map = {
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

    if kind == "record" and stem in copybook_map:
        return copybook_map[stem]

    # COBOL -> Service class name
    # CBTRN01C -> CbTrn01Service
    # CBACT01C -> CbAct01Service
    if kind == "service":
        service_map = {
            "CBTRN01C": "CbTrn01Service",
            "CBTRN02C": "CbTrn02Service",
            "CBTRN03C": "CbTrn03Service",
            "CBACT01C": "CbAct01Service",
        }
        if stem in service_map:
            return service_map[stem]
        return "Cb" + stem[2:5].title() + stem[5:7] + "Service"

    return stem.title().replace("_", "")


def find_used_copybooks(cobol_source: str, copybook_context: dict) -> dict:
    """
    Scans COBOL source for COPY statements and returns
    matching copybook content as context for the conversion prompt.
    """
    used = {}
    for line in cobol_source.splitlines():
        stripped = line.strip()
        if stripped.startswith("COPY "):
            # Extract copybook name: COPY CVACT01Y. -> CVACT01Y
            parts = stripped.replace(".", "").split()
            if len(parts) >= 2:
                cpy_name = parts[1].strip()
                if cpy_name in copybook_context:
                    used[cpy_name] = copybook_context[cpy_name]
    return used


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Agentic COBOL/JCL to Java/Python converter")
    parser.add_argument("--src", default="./src",                    help="Source directory containing cobol/, copybooks/, jcl/")
    parser.add_argument("--out", default="./converted-usingAgent",   help="Output directory")
    parser.add_argument("--clean", action="store_true", help="Clean output directory before running")
    args = parser.parse_args()

    run_agent(args.src, args.out, args.clean)
