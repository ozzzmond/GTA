#!/usr/bin/env python3
"""Deploy Web to Production (Convenience wrapper for deploy.py web)."""
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent
sys.path.insert(0, str(ROOT))

from deploy import main

if __name__ == "__main__":
    sys.exit(main(["web", *sys.argv[1:]]))
