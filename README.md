# [spread](https://github.com/fbielejec/spread)

[![CircleCI](https://circleci.com/gh/fbielejec/spread/tree/master.svg?style=svg&circle-token=d17b2167dc7180da1a984417b8de235c9412cb42)](https://circleci.com/gh/fbielejec/spread/tree/master)

Spread is a web application for analyzing and visualizing pathogen phylodynamic reconstructions resulting from Bayesian inference of sequence and trait evolutionary processes.

## Table of Contents

- [Technical Overview](#technical-overview)
- [Development](#development)
- [Contributors](#contributors)
- [License](#license)

## Technical Overview

To be able to easily pick up stack used to build spread, one should be familiar with following topics:
* [Java](https://www.java.com/)
* [Clojure](https://clojure.org/)
* [ClojureScript](https://clojurescript.org/)
* [GraphQL](https://graphql.org/)
* [MySQL](https://www.mysql.com/)
* [Docker](https://www.docker.com/)
* [S3](https://aws.amazon.com/s3/)
* [SQS](https://aws.amazon.com/sqs/)

The diagram below presents an overview of the architecture of spread:

![alt text](https://github.com/fbielejec/spread/blob/master/docs/system_architecture.png?raw=true)

- API is a gateway service, exposing [graphql](https://graphql.org/) endpoints and publishing messages to the [SQS queue](https://aws.amazon.com/sqs/).
- Worker is a messaging service wrapping the phylogeographic tree graphs parsing library [libspread](https://github.com/fbielejec/spread/tree/master/src/main/java/com/spread), multiple workers compete for the messages published by the API.
- Relational Database and S3 object storage are used for persistance.
- User-facing interface facilitates communicating with the API from the client.
- Visualization engine accepts S3 stored output, animates and displays it on the geographical map.

## Development

### Backend services

Make sure you have [docker](https://docs.docker.com/get-docker/) and [docker-compose](https://docs.docker.com/compose/install/) installed.
You will also need [maven](https://maven.apache.org/install.html) and clojure [cli-tool](https://clojure.org/guides/getting_started).

Source the environment variables and start the dev infrastructure:

```bash
source env/dev
docker-compose -f deployments/dev/docker-compose.yml up
```

Deploy the database schema changes:

```bash
cd services/db-migration
source ../../env/dev
mvn package
mvn liquibase:update
```

Compile and package libspread:

```bash
mvn clean package
```

Start an instance of the worker-service:

```bash
clj -A:run-worker
```

Start an instance of the api-service from the comand-line:

```bash
clj -A:run-api
```

Start an instance of the api-service from the REPL:

```
M+x cider-jack-in
C-c M-n-n api.main
(restart)
```

In the default `dev` environment a GraphQL IDE is started at:
http://127.0.0.1:3001/ide

### Browser client

Make sure you have [yarn](https://yarnpkg.com/getting-started/install) installed.

Install dependencies:
```bash
yarn deps
```

Start watcher and local server

```bash
yarn watch
```

Open in browser:
http://localhost:8020

To get the cljs REPL:

```clojure
M+x cider-connect-cljs
```

Select `localhost` and the nREPL port printed by the watcher (e.g. 46000), select `shadow` and `:ui` as the build.


## Tests

Start a watcher on the libspread test suite:

```bash
mvn fizzed-watcher:run
```

Start a watcher on the spread's integration test suite:

```
bin/kaocha --watch
```

### Contributors

This project exists thanks to all the people who contribute.

[![](https://github.com/fbielejec.png?size=50)](https://github.com/fbielejec)
[![](https://github.com/jpmonettas.png?size=50)](https://github.com/jpmonettas)
[![](https://github.com/plemey.png?size=50)](https://github.com/plemey)

## License

[MIT](LICENSE) © Filip Bielejec
