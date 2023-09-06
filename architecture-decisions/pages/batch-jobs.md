ADR: Batch Jobs
=============

Sometimes, a task involves working on multiple units. Instead of performing these tasks sequentially, executing them in parallel can speed up the process. We decided to add Batching of jobs, so that users can track N Jobs collectively and be notified of their completion.

Rationale
---------

1. **API Design**
    - `perform-batch` follows same pattern as other functions' APIs, except for the args part.
    - Goose executes a job in following manner `(apply (u/require-resolve execute-fn-sym) args)`. Taking args as a variadic parameter allowed us to implicitly accept them as a list.
    - In the case of batch-jobs, we expect the args to be a list of list.
    - For instance, `((:foo :bar) (:too :baz) (:poo :bat))`
    - Number of elements in outer list will specify how many jobs a batch contains (in above example, 3).
    - All elements in inner list get passed to Job as args by using `apply`. (`:foo` & `:bar` are args for 1 job, and so on...)
    - We've provided a helper function to construct & accumulate batch-args, so a user doesn't have to wrap their head around the implementation details of batch-args.

1. **Batch Lifecycle**
    - Jobs in a batch follow the same lifecycle as a regular Job: `Enqueued -> Executing -> (Retrying) -> Success/Dead`. 
    - As Job executions proceed, all Job IDs are stored in 1 of 4 sets: `enqueued`, `retrying`, `success` &`dead`.
    - Job IDs are stored over simple count. This way, state management is less complex & stores more information.
    - When all the Jobs within a batch have completed execution, a batch transitions to any of 3 terminal states: `In-Progress -> Success/Dead/Partial-Success`.

1. **Executing callback at end of a batch**
    - When a batch reaches a terminal state, its callback will be executed.
    - A callback is treated like a Job: it is enqueued, executed & retried as per batch's `retry-opts` if it fails.
    - When more than 1 terminal jobs complete concurrently, a race condition can cause callback to be enqueued multiple times.
    - The approach to avoid multiple enqueue is:
        - In a conventional broker, the approach would be to update state, read value & enqueue callback within 1 transaction. 
        - However, semantics of transaction in a Redis are different from that of a relational database. You cannot *read* values within a transaction.
        - To circumvent this, we are updating & reading value within 1 transaction. This ensures only ONE of the terminal jobs will read a terminal status, and enqueue a callback.
        - If a worker dies after a callback is enqueued, and before a job is deleted from orphan-queue, a batch-job becomes orphan and is retried. Before enqueuing callback, we check if a batch isn't already in terminal state.
        - The approach of acquiring a lock before enqueuing a callback will require storing the lock key in Redis *forever*, or checking state of batch everytime before enqueuing. We wanted to avoid both of these, so went with the transaction approach.
            - Temporary acquisition of a lock & release post callback enqueue won't work when another terminal job completes together & tries to acquire a lock, right after it is released by another thread.

1. **Cleaning up a batch post callback completion**
    - After callback completion, Users have an option to store a batch in a broker, so that it is available for querying via `batch/status` API.
    - If users aren't using an the status API, they can set the expiration to 0 seconds, and batch data will be cleaned-up immediately post callback completion.

1. **Cleaning up a batch as part of `batch/delete` API**
    - Deleting a batch is a 3 step process:
        1. Traverse through ready-queue and delete all enqueued jobs.
        2. Traverse through scheduled/retry queue and delete all retrying jobs.
        3. Delete batch hash-map and sets of job IDs.
    - Deletion of a batch is rare, execution of a job is more frequent. We have chosen an expensive deletion process over simply invalidating a batch, because batch-invalidation would involve pre-execution checks for every batch-job. The trade-off we have made is to avoid any redis calls before a batch-job can execute for performance benefits.
    - Users must be aware that delete is an expensive process and use it sparingly.

Avoided Designs
---------

1. A batch can be created in only ONE call. Enqueuing jobs to a batch post creation is not allowed.
    - Single time enqueue reduces implementation complexity, and simplifies a batch-lifecycle from User's perspective.
    - All args/jobs of a batch can be stored in application memory until User submits them to Goose.
1. A batch will be executed asynchronously, it cannot be scheduled for execution at a later time. 
    - Although semantics scheduled batches are quite simple & straight-forward, we chose to not implement it to reduce scope. Scheduled batch-jobs can be achieved by creating a batch via a scheduled Job.
    - When interacting with users of Goose, we discovered that scheduled batches were not a pressing requirement for them. However, if multiple users request for this feature in future, we are open to implementing it.
1. In case of success/failure of job execution, result/error aren't stored inside a broker
    - Storing Job results for consumption by callback/status API will result in increased excess storage inside a broker, affecting performance
    - If required, results must be stored inside a dedicated database by the Job itself. 
1. There are no heirarchy within a batch (Parent/Child batches aren't supported).
