ADR: Error-handling & Retrying
=============

Rationale
---------
- TODO: Add a table explaining back-off fn & it's possible values
  - `(/ (reduce + (map default-retry-delay-sec (range 27))) 86400.0)`
- Reuse same schedule queue for retrying jobs
    - Semantics of `scheduled jobs` & `failed jobs due for retry` are same
    - Half load on redis
    - No duplication in code, even at abstraction level
- User can specify retry-queue
  - To reduce or increase priority of jobs due for retry
- Error & Death handlers
    - Give users the flexibility to inject their own error-service or custom code
- Storing entire error object for auditing purpose

Avoided Designs
---------

- Different queue to schedule jobs due for retry
    - It increases load on redis
