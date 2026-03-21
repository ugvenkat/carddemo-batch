"""
acctfile.py
-----------
Python equivalent of ACCTFILE.jcl

JCL Original Purpose:
    1. Delete the Account VSAM file if it already exists  (STEP05)
    2. Define/create a new Account VSAM KSDS file         (STEP10)
    3. Copy data from flat file into the new VSAM file    (STEP15)

JCL Mapping:
    JOB  : ACCTFILE          -> Python script entry point main()
    STEP05 EXEC PGM=IDCAMS   -> step05_delete_vsam()   - delete existing file
    STEP10 EXEC PGM=IDCAMS   -> step10_define_vsam()   - create new file
    STEP15 EXEC PGM=IDCAMS   -> step15_repro_data()    - copy flat file to VSAM

    IDCAMS Commands mapped:
        DELETE  CLUSTER      -> os.remove() with existence check
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
logger = logging.getLogger("ACCTFILE")


# ---------------------------------------------------------------------------
# File path configuration (replaces DD DSN= statements)
# ---------------------------------------------------------------------------
FILE_CONFIG = {
    # Flat file source (input) — DISP=SHR
    "ACCTDATA":  os.getenv("ACCTDATA",  "data/ACCTDATA.PS"),

    # VSAM KSDS target file (output)
    "ACCTVSAM":  os.getenv("ACCTVSAM",  "data/ACCTDATA.VSAM.KSDS"),

    # Metadata sidecar — stores VSAM DEFINE cluster properties
    "ACCTMETA":  os.getenv("ACCTMETA",  "data/ACCTDATA.VSAM.KSDS.meta.json"),
}

# VSAM DEFINE CLUSTER properties from JCL STEP10
# In JCL: KEYS(11 0) means key length=11, offset=0
# RECORDSIZE(300 300) means min=300, max=300 (fixed)
VSAM_CLUSTER_DEF = {
    "name":         "AWS.M2.CARDDEMO.ACCTDATA.VSAM.KSDS",
    "type":         "KSDS",
    "key_length":   11,
    "key_offset":   0,
    "record_size":  300,
    "share_options": "2 3",
    "created_at":   None   # set at runtime
}


# ---------------------------------------------------------------------------
# STEP05 — mirrors //STEP05 EXEC PGM=IDCAMS (DELETE CLUSTER)
# ---------------------------------------------------------------------------
def step05_delete_vsam() -> int:
    """
    JCL Equivalent:
        DELETE AWS.M2.CARDDEMO.ACCTDATA.VSAM.KSDS CLUSTER
        IF MAXCC LE 08 THEN SET MAXCC = 0

    Deletes the VSAM file and its metadata sidecar if they exist.
    If file does not exist, that is not an error (mirrors MAXCC=0 reset).
    """
    logger.info("=" * 60)
    logger.info("STEP05 - START - DELETE ACCOUNT VSAM FILE IF EXISTS")
    logger.info("=" * 60)

    vsam_file = FILE_CONFIG["ACCTVSAM"]
    meta_file = FILE_CONFIG["ACCTMETA"]

    for f in [vsam_file, meta_file]:
        if os.path.exists(f):
            os.remove(f)
            logger.info(f"  DELETED: {f}")
        else:
            # Mirrors: IF MAXCC LE 08 THEN SET MAXCC = 0
            # File not found is acceptable — not an error
            logger.info(f"  NOT FOUND (OK): {f} - continuing")

    logger.info("STEP05 - COMPLETED SUCCESSFULLY - RC=0000")
    return 0


# ---------------------------------------------------------------------------
# STEP10 — mirrors //STEP10 EXEC PGM=IDCAMS (DEFINE CLUSTER)
# ---------------------------------------------------------------------------
def step10_define_vsam() -> int:
    """
    JCL Equivalent:
        DEFINE CLUSTER (NAME(AWS.M2.CARDDEMO.ACCTDATA.VSAM.KSDS)
               CYLINDERS(1 5)
               KEYS(11 0)
               RECORDSIZE(300 300)
               SHAREOPTIONS(2 3)
               ERASE INDEXED)
        DATA  (NAME(AWS.M2.CARDDEMO.ACCTDATA.VSAM.KSDS.DATA))
        INDEX (NAME(AWS.M2.CARDDEMO.ACCTDATA.VSAM.KSDS.INDEX))

    Creates an empty VSAM file and writes cluster definition to a
    JSON metadata sidecar (replaces VSAM catalog entry on mainframe).
    """
    logger.info("=" * 60)
    logger.info("STEP10 - START - DEFINE ACCOUNT VSAM FILE")
    logger.info("=" * 60)

    vsam_file = FILE_CONFIG["ACCTVSAM"]
    meta_file = FILE_CONFIG["ACCTMETA"]

    # Ensure data directory exists
    os.makedirs(os.path.dirname(vsam_file) if os.path.dirname(vsam_file) else ".", exist_ok=True)

    # Create empty VSAM file
    open(vsam_file, "wb").close()
    logger.info(f"  CREATED VSAM FILE: {vsam_file}")

    # Write cluster definition metadata (replaces mainframe VSAM catalog)
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
        REPRO INFILE(ACCTDATA) OUTFILE(ACCTVSAM)

    Copies data from the flat file (ACCTDATA.PS) into the VSAM file.
    Mirrors IDCAMS REPRO which loads a sequential file into a VSAM KSDS.
    """
    logger.info("=" * 60)
    logger.info("STEP15 - START - COPY FLAT FILE TO VSAM")
    logger.info("=" * 60)

    src = FILE_CONFIG["ACCTDATA"]
    dst = FILE_CONFIG["ACCTVSAM"]

    if not os.path.exists(src):
        logger.error(f"  INPUT FILE NOT FOUND: {src}")
        return 8  # RC=8 mirrors IDCAMS failure code

    shutil.copyfile(src, dst)
    size = os.path.getsize(dst)
    logger.info(f"  REPRO: {src} -> {dst} ({size} bytes)")
    logger.info("STEP15 - COMPLETED SUCCESSFULLY - RC=0000")
    return 0


# ---------------------------------------------------------------------------
# Job entry point — mirrors //ACCTFILE JOB statement
# ---------------------------------------------------------------------------
def main():
    """
    JCL Equivalent:
        //ACCTFILE JOB 'Delete define Account Data',CLASS=A,MSGCLASS=0

    Runs all steps in sequence. Abends on any non-zero return code.
    """
    logger.info("*" * 60)
    logger.info("JOB ACCTFILE - START")
    logger.info("Delete, Define and Load Account VSAM File")
    logger.info("*" * 60)

    steps = [
        ("STEP05", step05_delete_vsam),
        ("STEP10", step10_define_vsam),
        ("STEP15", step15_repro_data),
    ]

    for step_name, step_fn in steps:
        rc = step_fn()
        if rc != 0:
            logger.error(f"JOB ACCTFILE - ABENDED AT {step_name} - RC={rc:04d}")
            sys.exit(rc)

    logger.info("*" * 60)
    logger.info("JOB ACCTFILE - COMPLETED SUCCESSFULLY")
    logger.info("*" * 60)
    sys.exit(0)


if __name__ == "__main__":
    main()
