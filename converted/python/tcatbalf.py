"""
tcatbalf.py
-----------
Python equivalent of TCATBALF.jcl

JCL Original Purpose:
    1. Delete Transaction Category Balance VSAM file if it exists  (STEP05)
    2. Define/create a new Transaction Category Balance VSAM file  (STEP10)
    3. Copy data from flat file into the new VSAM file             (STEP15)

JCL Mapping:
    JOB  : TCATBALF          -> Python script entry point main()
    STEP05 EXEC PGM=IDCAMS   -> step05_delete_vsam()   - delete existing file
    STEP10 EXEC PGM=IDCAMS   -> step10_define_vsam()   - create new file
    STEP15 EXEC PGM=IDCAMS   -> step15_repro_data()    - copy flat file to VSAM

    IDCAMS Commands mapped:
        DELETE  CLUSTER      -> os.remove() - unconditional delete (SET MAXCC=0)
        DEFINE  CLUSTER      -> file metadata stored in JSON sidecar
        REPRO   INFILE->OUTFILE -> shutil.copyfile()

Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
Licensed under the Apache License, Version 2.0
"""

import logging
import sys
import os
import json
import shutil
from datetime import datetime

# ---------------------------------------------------------------------------
# Logging setup (replaces SYSPRINT DD SYSOUT=*)
# ---------------------------------------------------------------------------
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
    handlers=[logging.StreamHandler(sys.stdout)]
)
logger = logging.getLogger("TCATBALF")


# ---------------------------------------------------------------------------
# File path configuration (replaces DD DSN= statements)
# ---------------------------------------------------------------------------
FILE_CONFIG = {
    # Flat file source (input) — DISP=SHR
    "TCATBAL":   os.getenv("TCATBAL",   "data/TCATBALF.PS"),

    # VSAM KSDS target file — DISP=OLD (exclusive access)
    "TCATBALV":  os.getenv("TCATBALV",  "data/TCATBALF.VSAM.KSDS"),

    # Metadata sidecar — stores VSAM DEFINE cluster properties
    "TCATMETA":  os.getenv("TCATMETA",  "data/TCATBALF.VSAM.KSDS.meta.json"),
}

# VSAM DEFINE CLUSTER properties from JCL STEP10
# KEYS(17 0)         -> key length=17, offset=0
# RECORDSIZE(50 50)  -> fixed length 50 bytes
VSAM_CLUSTER_DEF = {
    "name":         "AWS.M2.CARDDEMO.TCATBALF.VSAM.KSDS",
    "type":         "KSDS",
    "key_length":   17,
    "key_offset":   0,
    "record_size":  50,
    "share_options": "2 3",
    "created_at":   None   # set at runtime
}


# ---------------------------------------------------------------------------
# STEP05 — mirrors //STEP05 EXEC PGM=IDCAMS (DELETE CLUSTER)
# ---------------------------------------------------------------------------
def step05_delete_vsam() -> int:
    """
    JCL Equivalent:
        DELETE AWS.M2.CARDDEMO.TCATBALF.VSAM.KSDS CLUSTER
        SET MAXCC = 0

    Note: Unlike ACCTFILE which uses IF MAXCC LE 08,
    this JCL uses unconditional SET MAXCC=0 — delete always succeeds
    regardless of whether the file existed or not.
    """
    logger.info("=" * 60)
    logger.info("STEP05 - START - DELETE TRANSACTION CATEGORY BALANCE VSAM FILE")
    logger.info("=" * 60)

    vsam_file = FILE_CONFIG["TCATBALV"]
    meta_file = FILE_CONFIG["TCATMETA"]

    for f in [vsam_file, meta_file]:
        if os.path.exists(f):
            os.remove(f)
            logger.info(f"  DELETED: {f}")
        else:
            logger.info(f"  NOT FOUND (OK): {f}")

    # Unconditional SET MAXCC=0 — always return success
    logger.info("  SET MAXCC = 0 (unconditional)")
    logger.info("STEP05 - COMPLETED SUCCESSFULLY - RC=0000")
    return 0


# ---------------------------------------------------------------------------
# STEP10 — mirrors //STEP10 EXEC PGM=IDCAMS (DEFINE CLUSTER)
# ---------------------------------------------------------------------------
def step10_define_vsam() -> int:
    """
    JCL Equivalent:
        DEFINE CLUSTER (NAME(AWS.M2.CARDDEMO.TCATBALF.VSAM.KSDS)
               CYLINDERS(1 5)
               KEYS(17 0)
               RECORDSIZE(50 50)
               SHAREOPTIONS(2 3)
               ERASE INDEXED)
        DATA  (NAME(AWS.M2.CARDDEMO.TCATBALF.VSAM.KSDS.DATA))
        INDEX (NAME(AWS.M2.CARDDEMO.TCATBALF.VSAM.KSDS.INDEX))

    Creates an empty VSAM file and writes cluster definition to a
    JSON metadata sidecar (replaces VSAM catalog entry on mainframe).
    """
    logger.info("=" * 60)
    logger.info("STEP10 - START - DEFINE TRANSACTION CATEGORY BALANCE VSAM FILE")
    logger.info("=" * 60)

    vsam_file = FILE_CONFIG["TCATBALV"]
    meta_file = FILE_CONFIG["TCATMETA"]

    os.makedirs(os.path.dirname(vsam_file) if os.path.dirname(vsam_file) else ".", exist_ok=True)

    # Create empty VSAM file
    open(vsam_file, "wb").close()
    logger.info(f"  CREATED VSAM FILE: {vsam_file}")

    # Write cluster definition metadata
    cluster_def = VSAM_CLUSTER_DEF.copy()
    cluster_def["created_at"] = datetime.now().isoformat()
    with open(meta_file, "w") as mf:
        json.dump(cluster_def, mf, indent=2)
    logger.info(f"  CREATED METADATA:  {meta_file}")
    logger.info(f"  CLUSTER PROPERTIES: KEYS({cluster_def['key_length']} {cluster_def['key_offset']}) "
                f"RECORDSIZE({cluster_def['record_size']} {cluster_def['record_size']})")

    logger.info("STEP10 - COMPLETED SUCCESSFULLY - RC=0000")
    return 0


# ---------------------------------------------------------------------------
# STEP15 — mirrors //STEP15 EXEC PGM=IDCAMS (REPRO)
# ---------------------------------------------------------------------------
def step15_repro_data() -> int:
    """
    JCL Equivalent:
        REPRO INFILE(TCATBAL) OUTFILE(TCATBALV)

    Copies data from the flat file (TCATBALF.PS) into the VSAM KSDS file.
    Note: DISP=OLD on TCATBALV means exclusive write access.
    """
    logger.info("=" * 60)
    logger.info("STEP15 - START - COPY FLAT FILE TO VSAM (REPRO)")
    logger.info("=" * 60)

    src = FILE_CONFIG["TCATBAL"]
    dst = FILE_CONFIG["TCATBALV"]

    if not os.path.exists(src):
        logger.error(f"  INPUT FILE NOT FOUND: {src}")
        return 8  # RC=8 mirrors IDCAMS failure

    shutil.copyfile(src, dst)
    size = os.path.getsize(dst)
    logger.info(f"  REPRO: {src} -> {dst} ({size} bytes)")
    logger.info("STEP15 - COMPLETED SUCCESSFULLY - RC=0000")
    return 0


# ---------------------------------------------------------------------------
# Job entry point — mirrors //TCATBALF JOB statement
# ---------------------------------------------------------------------------
def main():
    """
    JCL Equivalent:
        //TCATBALF JOB 'DEFINE TRANCAT BAL',CLASS=A,MSGCLASS=0

    Runs all steps in sequence. Abends on any non-zero return code.
    """
    logger.info("*" * 60)
    logger.info("JOB TCATBALF - START")
    logger.info("Delete, Define and Load Transaction Category Balance VSAM File")
    logger.info("*" * 60)

    steps = [
        ("STEP05", step05_delete_vsam),
        ("STEP10", step10_define_vsam),
        ("STEP15", step15_repro_data),
    ]

    for step_name, step_fn in steps:
        rc = step_fn()
        if rc != 0:
            logger.error(f"JOB TCATBALF - ABENDED AT {step_name} - RC={rc:04d}")
            sys.exit(rc)

    logger.info("*" * 60)
    logger.info("JOB TCATBALF - COMPLETED SUCCESSFULLY")
    logger.info("*" * 60)
    sys.exit(0)


if __name__ == "__main__":
    main()
