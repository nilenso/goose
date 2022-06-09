ADR: Scheduling
=============

Rationale
---------
- Jobs due for execution are enqueued at front of queue in Goose 
  - Sidekiq pushes them to the back
  - In case of a huge backlog on execution queue, Goose's scheduled jobs will have better SLAs
  - This implies newly due jobs will be executed before if older jobs are still in the queue
- Large batches of scheduled jobs are faster in Goose
  - Goose fetches 50 jobs (configurable) instead of 1
  - Sidekiq sleeps after fetching 1 job. Goose immediately polls to check if more jobs are due
- Moving a job from sorted set to execution queue is reliable
  - Goose performs this inside a transaction. In Sidekiq open source version, ZREM & LPUSH are 2 separate calls to Redis
    
Avoided Designs
---------

- Enqueue scheduled jobs to back of queue
  - Scheduled jobs should have higher priority to meet SLA than normal jobs
