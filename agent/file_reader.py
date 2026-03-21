"""
file_reader.py
--------------
Reads source files from the src/ directory.
Handles cobol/, copybooks/, jcl/ subdirectories.
"""

from pathlib import Path
import logging

logger = logging.getLogger("FileReader")


class FileReader:

    def __init__(self, src_dir: str):
        self.src = Path(src_dir)
        self._validate()

    def _validate(self):
        if not self.src.exists():
            raise FileNotFoundError(f"Source directory not found: {self.src}")
        logger.info(f"Source directory: {self.src.resolve()}")

    def get_copybooks(self) -> list:
        """Returns all .cpy files sorted by name."""
        cpy_dir = self.src / "copybooks"
        if not cpy_dir.exists():
            logger.warning(f"No copybooks directory found at {cpy_dir}")
            return []
        files = sorted(cpy_dir.glob("*.cpy")) + sorted(cpy_dir.glob("*.CPY"))
        logger.info(f"Found {len(files)} copybook(s) in {cpy_dir}")
        return files

    def get_cobol_files(self) -> list:
        """Returns all .cbl files sorted by name."""
        cbl_dir = self.src / "cobol"
        if not cbl_dir.exists():
            logger.warning(f"No cobol directory found at {cbl_dir}")
            return []
        files = sorted(cbl_dir.glob("*.cbl")) + sorted(cbl_dir.glob("*.CBL"))
        logger.info(f"Found {len(files)} COBOL file(s) in {cbl_dir}")
        return files

    def get_jcl_files(self) -> list:
        """Returns all .jcl files sorted by name."""
        jcl_dir = self.src / "jcl"
        if not jcl_dir.exists():
            logger.warning(f"No jcl directory found at {jcl_dir}")
            return []
        files = sorted(jcl_dir.glob("*.jcl")) + sorted(jcl_dir.glob("*.JCL"))
        logger.info(f"Found {len(files)} JCL file(s) in {jcl_dir}")
        return files
