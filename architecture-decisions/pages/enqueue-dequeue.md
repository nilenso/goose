ADR: Enqueue-Dequeue 
=============

Rationale
---------
- Using a redis list because enqueue-dequeue operations are O(1)
- Using `BRPOPLPUSH` because of reliability

Avoided Designs
---------
- During execution of a job, `resolve` isn't memoized because hot-reloading becomes a pain
