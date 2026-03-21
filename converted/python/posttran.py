"""
posttran.py
-----------
Python equivalent of POSTTRAN.jcl

JCL Original Purpose:
    Process and load daily transaction file, create transaction
    category balance and update transaction master VSAM.

JCL Mapping:
    JOB  : POSTTRAN         -> Python script entry point
    STEP : STEP15            -> run_step15()
    PGM  : CBTRN02C          -> calls cbtrn02c.py (to be converted separately)

    DD Names mapped to file path config:
        TRANFILE  -> AWS.M2.CARDDEMO.TRANSACT.VSAM.KSDS   -> transaction master file
        DALYTRAN  -> AWS.M2.CARDDEMO.DALYTRAN.PS          -> daily transaction input file
        XREFFILE  -> AWS.M2.CARDDEMO.CARDXREF.VSAM.KSDS  -> card cross-reference file
        DALYREJS  -> AWS.M2.CARDDEMO.DALYREJS(+1)         -> daily rejects output file (new GDG)
        ACCTFILE  -> AWS.M2.CARDDEMO.ACCTDATA.VSAM.KSDS  -> account data file
        TCATBALF  -> AWS.M2.CARDDEMO.TCATBALF.VSAM.KSDS  -> transaction category balance file

Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
Licensed under the Apache License, Version 2.0
"""

import logging
import subprocess
import sys
import os
from datetime import datetime

# ---------------------------------------------------------------------------
# Logging setup  (replaces SYSPRINT / SYSOUT DD statements)
# ---------------------------------------------------------------------------
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
    handlers=[
        logging.StreamHandler(sys.stdout)
    ]
)
logger = logging.getLogger("POSTTRAN")


# ---------------------------------------------------------------------------
# File path configuration  (replaces DD DSN= statements)
# In a real environment these would come from environment variables or a
# config file — mirroring how mainframe DSNs are managed per environment.
# ---------------------------------------------------------------------------
FILE_CONFIG = {
    # Shared input/output files (DISP=SHR)
    "TRANFILE":  os.getenv("TRANFILE",  "data/TRANSACT.dat"),      # Transaction master
    "DALYTRAN":  os.getenv("DALYTRAN",  "data/DALYTRAN.dat"),      # Daily transaction input
    "XREFFILE":  os.getenv("XREFFILE",  "data/CARDXREF.dat"),      # Card cross-reference
    "ACCTFILE":  os.getenv("ACCTFILE",  "data/ACCTDATA.dat"),      # Account data
    "TCATBALF":  os.getenv("TCATBALF",  "data/TCATBALF.dat"),      # Transaction category balance

    # New output file (DISP=NEW,CATLG,DELETE) — GDG (+1) generation
    # Simulated here by timestamping the filename
    "DALYREJS":  os.getenv("DALYREJS",  f"data/DALYREJS_{datetime.now().strftime('%Y%m%d')}.dat"),
}

# Record format for DALYREJS: RECFM=F, LRECL=430  (fixed length 430 bytes)
DALYREJS_LRECL = 430


# ---------------------------------------------------------------------------
# Step execution helper
# ---------------------------------------------------------------------------
def run_program(program_name: str, file_config: dict) -> int:
    """
    Simulates JCL EXEC PGM= by calling the equivalent Python program.
    Passes file paths via environment variables (mirrors DD name passing).

    In JCL:
        //STEP15 EXEC PGM=CBTRN02C
        //TRANFILE DD DISP=SHR, DSN=...

    In Python:
        Calls cbtrn02c.py as a subprocess with env vars set per DD mapping.
    """
    logger.info(f"Executing program: {program_name}")

    # Build environment — pass DD file paths as env vars to the program
    env = os.environ.copy()
    env.update({dd_name: path for dd_name, path in file_config.items()})

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
            [sys.executable, program_file],
            env=env,
            capture_output=False   # let stdout/stderr flow through (like SYSOUT=*)
        )
        return result.returncode

    except FileNotFoundError:
        # Program not yet converted — log and return simulated success for now
        logger.warning(
            f"Program {program_name.lower()}.py not found. "
            f"Skipping — will be replaced with converted COBOL program."
        )
        return 0


# ---------------------------------------------------------------------------
# STEP15 — mirrors //STEP15 EXEC PGM=CBTRN02C
# ---------------------------------------------------------------------------
def run_step15() -> int:
    """
    JCL Equivalent:
        //STEP15 EXEC PGM=CBTRN02C

    Purpose:
        Process and load daily transaction file.
        Create transaction category balance.
        Update transaction master VSAM.

    Returns:
        Return code (0 = success, non-zero = failure)
    """
    logger.info("=" * 60)
    logger.info("STEP15 - START - PGM=CBTRN02C")
    logger.info("=" * 60)

    # Log DD allocations (mirrors JCL DD statements in job log)
    logger.info("DD Allocations:")
    for dd_name, path in FILE_CONFIG.items():
        logger.info(f"  {dd_name:<12} -> {path}")

    rc = run_program("CBTRN02C", FILE_CONFIG)

    if rc == 0:
        logger.info("STEP15 - COMPLETED SUCCESSFULLY - RC=0000")
    else:
        logger.error(f"STEP15 - FAILED - RC={rc:04d}")

    return rc


# ---------------------------------------------------------------------------
# Job entry point — mirrors //POSTTRAN JOB statement
# ---------------------------------------------------------------------------
def main():
    """
    JCL Equivalent:
        //POSTTRAN JOB 'POSTTRAN',CLASS=A,MSGCLASS=0,NOTIFY=&SYSUID

    Orchestrates all steps in sequence.
    Mirrors JCL behaviour: if a step fails (RC > 0), job abends.
    """
    logger.info("*" * 60)
    logger.info("JOB POSTTRAN - START")
    logger.info("Process and load daily transaction file")
    logger.info("*" * 60)

    # Run STEP15
    rc = run_step15()

    if rc != 0:
        logger.error(f"JOB POSTTRAN - ABENDED AT STEP15 - RC={rc:04d}")
        sys.exit(rc)

    logger.info("*" * 60)
    logger.info("JOB POSTTRAN - COMPLETED SUCCESSFULLY")
    logger.info("*" * 60)
    sys.exit(0)


if __name__ == "__main__":
    main()
