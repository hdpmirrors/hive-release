SELECT 'Upgrading MetaStore schema from 2.1.1000 to 2.1.2000';

-- \i 043-HIVE-16997.postgres.sql;
ALTER TABLE "PART_COL_STATS" ADD COLUMN "BIT_VECTOR" BYTEA;
ALTER TABLE "TAB_COL_STATS" ADD COLUMN "BIT_VECTOR" BYTEA;

-- \i 044-HIVE-16886.postgres.sql;
INSERT INTO "NOTIFICATION_SEQUENCE" ("NNI_ID", "NEXT_EVENT_ID") SELECT 1,1 WHERE NOT EXISTS ( SELECT "NEXT_EVENT_ID" FROM "NOTIFICATION_SEQUENCE");

UPDATE "VERSION" SET "SCHEMA_VERSION"='2.1.2000', "VERSION_COMMENT"='Hive release version 2.1.2000' where "VER_ID"=1;
SELECT 'Finished upgrading MetaStore schema from 2.1.1000 to 2.1.2000';

