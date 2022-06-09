ADR: Reliability
=============

Rationale
---------

- Abrupt shutdown of worker won't cause in-flight jobs to be lost
  - Goose uses `HEARTBEATs`, `IN-PROGRESS-JOB queues` & `ORPHAN-CHECKs` to ensure this
- Users can set Redis writetodisk config for more reliable infra


Avoided Designs
---------
