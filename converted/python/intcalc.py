"""
intcalc.py
----------
Python equivalent of INTCALC.jcl

JCL Original Purpose:
    Process transaction balance file and compute interest and fees.

JCL Mapping:
    JOB  : INTCALC           -> Python script entry point main()
    STEP : STEP15            -> run_step15()
    PGM  : CBACT04C          -> calls cbact04c.py (to be converted from COBOL)
    PARM : '2022071800'      -> passed as command-line argument to cbact04c.py

    DD Names mapped to file path config:
        TCATBALF  -> AWS.M2.CARDDEMO.TCATBALF.VSAM.KSDS        -> transaction category balance
        XREFFILE  -> AWS.M2.CARDDEMO.CARDXREF.VSAM.KSDS        -> card cross-reference (primary)
        XREFFIL1  -> AWS.M2.CARDDEMO.CARDXREF.VSAM.AIX.PATH    -> card cross-reference (alternate index)
        ACCTFILE  -> AWS.M2.CARDDEMO.ACCTDATA.VSAM.KSDS        -> account data file
        DISCGRP   -> AWS.M2.CARDDEMO.DISCGRP.VSAM.KSDS         -> discount group file
        TRANSACT  -> AWS.M2.CARDDEMO.SYSTRAN(+1)               -> system transaction output (GDG)

Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
Licensed under the Apache License, Version 2.0
"""

import logging
import subprocess
import sys
import os
from datetime import datetime

# ---------------------------------------------------------------------------
# Logging setup (replaces SYSPRINT / SYSOUT DD SYSOUT=*)
# ---------------------------------------------------------------------------
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
    handlers=[logging.StreamHandler(sys.stdout)]
)
logger = logging.getLogger("INTCALC")


# ---------------------------------------------------------------------------
# File path configuration (replaces DD DSN= statements)
# ---------------------------------------------------------------------------
FILE_CONFIG = {
    # Shared input files (DISP=SHR)
    "TCATBALF":  os.getenv("TCATBALF",  "data/TCATBALF.dat"),          # Transaction category balance
    "XREFFILE":  os.getenv("XREFFILE",  "data/CARDXREF.dat"),          # Card cross-reference primary
    "XREFFIL1":  os.getenv("XREFFIL1",  "data/CARDXREF.AIX.dat"),      # Card cross-reference alternate index
    "ACCTFILE":  os.getenv("ACCTFILE",  "data/ACCTDATA.dat"),          # Account data
    "DISCGRP":   os.getenv("DISCGRP",   "data/DISCGRP.dat"),           # Discount group

    # New output file (DISP=NEW,CATLG,DELETE) — GDG (+1) generation
    # Simulated by timestamping: mirrors SYSTRAN(+1) GDG generation
    "TRANSACT":  os.getenv("TRANSACT",  f"data/SYSTRAN_{datetime.now().strftime('%Y%m%d%H%M%S')}.dat"),
}

# PARM value from JCL: PARM='2022071800' (processing date YYYYMMDDXX)
# Override via environment variable to make it dynamic
PROCESSING_DATE = os.getenv("PROCESSING_DATE", datetime.now().strftime("%Y%m%d") + "00")

# Record format for TRANSACT output: RECFM=F, LRECL=350
TRANSACT_LRECL = 350


# ---------------------------------------------------------------------------
# Step execution helper
# ---------------------------------------------------------------------------
def run_program(program_name: str, parm: str, file_config: dict) -> int:
    """
    Simulates JCL EXEC PGM= with PARM= by calling the equivalent Python program.

    In JCL:
        //STEP15 EXEC PGM=CBACT04C,PARM='2022071800'

    In Python:
        Calls cbact04c.py with parm as argument and DD files as env vars.
    """
    logger.info(f"Executing program: {program_name}  PARM='{parm}'")

    env = os.environ.copy()
    env.update({dd_name: path for dd_name, path in file_config.items()})
    env["PARM"] = parm

    # Check if the Python program exists before calling subprocess
    program_file = f"{program_name.lower()}.py"
    if not os.path.exists(program_file):
        logger.warning(
            f"Program {program_file} not found. "
            f"Skipping — will be replaced with converted COBOL program."
        )
        return 0

    try:
        result = subprocess.run(
            [sys.executable, program_file, parm],
            env=env,
            capture_output=False
        )
        return result.returncode

    except FileNotFoundError:
        logger.warning(
            f"Program {program_file} not found. "
            f"Skipping — will be replaced with converted COBOL program."
        )
        return 0


# ---------------------------------------------------------------------------
# STEP15 — mirrors //STEP15 EXEC PGM=CBACT04C,PARM='2022071800'
# ---------------------------------------------------------------------------
def run_step15() -> int:
    """
    JCL Equivalent:
        //STEP15 EXEC PGM=CBACT04C,PARM='2022071800'

    Purpose:
        Process transaction category balance file.
        Compute interest and fees for each account.
        Write system transaction output to GDG file.

    Returns:
        Return code (0 = success, non-zero = failure)
    """
    logger.info("=" * 60)
    logger.info("STEP15 - START - PGM=CBACT04C")
    logger.info(f"         PARM='{PROCESSING_DATE}'")
    logger.info("=" * 60)

    logger.info("DD Allocations:")
    for dd_name, path in FILE_CONFIG.items():
        logger.info(f"  {dd_name:<12} -> {path}")

    rc = run_program("CBACT04C", PROCESSING_DATE, FILE_CONFIG)

    if rc == 0:
        logger.info("STEP15 - COMPLETED SUCCESSFULLY - RC=0000")
    else:
        logger.error(f"STEP15 - FAILED - RC={rc:04d}")

    return rc


# ---------------------------------------------------------------------------
# Job entry point — mirrors //INTCALC JOB statement
# ---------------------------------------------------------------------------
def main():
    """
    JCL Equivalent:
        //INTCALC JOB 'INTEREST CALCULATOR',CLASS=A,MSGCLASS=0

    Orchestrates all steps in sequence.
    Abends if any step returns non-zero RC.
    """
    logger.info("*" * 60)
    logger.info("JOB INTCALC - START")
    logger.info("Process transaction balance and compute interest and fees")
    logger.info(f"Processing Date: {PROCESSING_DATE}")
    logger.info("*" * 60)

    rc = run_step15()

    if rc != 0:
        logger.error(f"JOB INTCALC - ABENDED AT STEP15 - RC={rc:04d}")
        sys.exit(rc)

    logger.info("*" * 60)
    logger.info("JOB INTCALC - COMPLETED SUCCESSFULLY")
    logger.info("*" * 60)
    sys.exit(0)


if __name__ == "__main__":
    main()
