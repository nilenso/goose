ADR: Console
============

The Goose Console is a web interface that provides visual representation and control over Goose's job management features for async, scheduled, batch, periodic, and dead jobs.

Design
-------

The Goose Console is built using Hiccup for HTML templating, CSS for styling, and JavaScript for interactivity and dynamic updates.
The console's design and mockups can be found [here](https://docs.google.com/document/d/1DmSvsdNVhsKfQ0NgxQ6ONIz0cl5_I0NhPBSmDOkwhMM/edit?usp=sharing).

Web View Update
----------------

The console's web view is updated by refreshing the page to fetch the latest job data.
If jobs are missing since the last refresh, it is likely because they have moved to a different queue (checkout job [lifecyle](https://github.com/nilenso/goose/wiki/Job-Lifecycle))
or have been executed by a worker.

API Design
-----------

Goose does not provide a dedicated server to run the console.
Instead, the client application is expected to serve the console ui through `console/app-handler` function.
To start console, the client application should call the `app-handler` function with the appropriate `console-opts` (described below).

Console opts
----------------

The `console-opts` map consists of three required parameters:

- `route-prefix` (string):
    - It specifies the base url path to be used for matching and handling console routes.
    - This base path will be prepended to all console route paths handled by the app-handler function
    - It should not include a trailing slash at the end
    - Example: `route-prefix`  set to `"/console"`, the enqueued endpoint will be accessible at path `/console/enqueued`
- `broker` (map):
    - The Goose broker implementation, either for Redis or RabbitMQ.
    - Example: (redis/new-producer redis/default-opts)
- `app-name` (string):
    - Name of the application using Goose to be displayed in Navbar of UI.
    - Example: "Goose client"


Avoided Designs
----------------

1. **JavaScript over ClojureScript**:
   Goose uses JavaScript instead of ClojureScript for the console's frontend implementation since console does not have a lot of state to store on the frontend,
   and ClojureScript is a heavy dependency. A [spike](https://github.com/alishamohanty/html-js-spike-goose) was conducted to confirm that JavaScript is sufficient for incorporating the console functionalities.

2. **No Dedicated Server for the Console**:
   Goose does not require a dedicated server to run the console. Instead, the console functionality is exposed through the client application's server, reducing the overhead of managing an additional server.

3. **No Support for Automatic Polling**:
   The console does not support automatic polling for updates. Instead, users are expected to manually refresh the page to fetch the latest job data. This design decision was made to keep the console simple.
