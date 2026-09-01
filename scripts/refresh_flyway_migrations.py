#!/usr/bin/env python3
"""Rebuild Flyway migrations for a fresh empty database (no legacy warehouse/booking churn)."""

from __future__ import annotations

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MIGRATION_DIR = ROOT / "src/main/resources/db/migration"
ARCHIVE_DIR = MIGRATION_DIR / "_archive_pre_refresh"

TABLES_TO_DROP = {
    "booking_attachments",
    "booking_details",
    "booking_device_infos",
    "booking_devices",
    "bookings",
    "warehouse_transfers",
    "warehouses",
}

CREATE_BLOCK = re.compile(
    r"/\*!40101 SET @saved_cs_client\s*=\s*@@character_set_client \*/;\s*"
    r"/\*!50503 SET character_set_client = utf8mb4 \*/;\s*"
    r"CREATE TABLE `(?P<table>[^`]+)` \((?P<body>.*?)\) ENGINE=InnoDB[^;]*;",
    re.DOTALL,
)


def strip_legacy_tables(sql: str) -> str:
    def replacer(match: re.Match[str]) -> str:
        table = match.group("table")
        return "" if table in TABLES_TO_DROP else match.group(0)

    sql = CREATE_BLOCK.sub(replacer, sql)
    sql = re.sub(r"  `warehouse_name` varchar\(120\) DEFAULT NULL,\n", "", sql)
    return sql


def read(name: str) -> str:
    return (MIGRATION_DIR / name).read_text(encoding="utf-8")


def write(name: str, content: str) -> None:
    (MIGRATION_DIR / name).write_text(content.rstrip() + "\n", encoding="utf-8")


def v13_ddl_only() -> str:
    lines = read("V13__service_job_team_assignments.sql").splitlines()
    return "\n".join(line for line in lines if not line.startswith("INSERT INTO service_job_assignments"))


def main() -> None:
    ARCHIVE_DIR.mkdir(parents=True, exist_ok=True)
    for path in sorted(MIGRATION_DIR.glob("V*.sql")):
        path.rename(ARCHIVE_DIR / path.name)

    baseline = strip_legacy_tables(read("_archive_pre_refresh/V1__baseline.sql"))
    baseline = baseline.replace(
        "-- Future schema changes must use V2 and later migrations.",
        "-- Fresh Flyway baseline. Incremental changes use V2 and later migrations.",
        1,
    )
    write("V1__baseline.sql", baseline)
    write("V2__technician_app_version.sql", read("_archive_pre_refresh/V2__technician_app_version.sql"))
    write("V3__videos.sql", read("_archive_pre_refresh/V3__videos.sql"))
    write("V4__video_app_placements.sql", read("_archive_pre_refresh/V4__video_app_placements.sql"))
    write(
        "V5__service_job_settlement_revenue_split.sql",
        read("_archive_pre_refresh/V9__service_job_settlement_revenue_split.sql"),
    )
    write(
        "V6__booking_module.sql",
        read("_archive_pre_refresh/V12__new_booking_module.sql").rstrip()
        + "\n\n"
        + read("_archive_pre_refresh/V14__booking_item_photos.sql"),
    )
    write("V7__service_job_team_assignments.sql", v13_ddl_only())

    print("Flyway migrations refreshed:")
    for path in sorted(MIGRATION_DIR.glob("V*.sql")):
        print(f"  - {path.name}")


if __name__ == "__main__":
    main()
