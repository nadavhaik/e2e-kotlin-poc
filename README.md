# e2e-kotlin-poc

A small proof of concept showing how to codegen **typed Kotlin clients** from
a **GraphQL schema** and an **OpenAPI schema**, and how to unit test code that
uses those generated clients - all wired up through a single Gradle build.

## What's in here

| Concern | Schema | Codegen | Generated client |
| --- | --- | --- | --- |
| GraphQL "Books" service | [`schemas/graphql/schema.graphqls`](schemas/graphql/schema.graphqls) | [Apollo Kotlin](https://www.apollographql.com/docs/kotlin) Gradle plugin | `com.example.graphql.*` (typed `Query` classes + models), used via `ApolloClient` |
| REST "Tasks" service | [`schemas/openapi/tasks-api.yaml`](schemas/openapi/tasks-api.yaml) | [OpenAPI Generator](https://openapi-generator.tech/) Gradle plugin (`kotlin` generator, `jvm-okhttp4` library) | `com.example.tasks.apis.TasksApi` + `com.example.tasks.models.*`, an OkHttp-backed REST client |

Schemas are intentionally kept separate from any client/source code, under a
top-level [`schemas/`](schemas) directory, so it's obvious what's a hand-written
contract vs. generated code:

```
schemas/
  graphql/
    schema.graphqls          # GraphQL schema (the contract)
  openapi/
    tasks-api.yaml            # OpenAPI schema (the contract)
src/main/graphql/com/example/graphql/
  BookDetails.graphql         # a reusable GraphQL fragment
  GetBook.graphql              # GraphQL operations (what the client queries)
  GetBooks.graphql
src/test/kotlin/com/example/e2e/
  GraphQLBookClientTest.kt     # tests for the generated GraphQL client
  TaskApiClientTest.kt         # tests for the generated REST client
build/generated/               # <-- codegen output lands here (gitignored)
  source/apollo/books/...
  openapi/src/main/kotlin/...
```

There is no hand-written "app" code in `src/main/kotlin` - the interesting
code is entirely generated from the two schemas, and the tests consume those
generated classes directly.

## GraphQL: schema, fragment, and operations

The GraphQL schema defines a single `Book` type and two queries:

```graphql
type Book {
    id: ID!
    title: String!
    author: String!
    publishedYear: Int
}

type Query {
    book(id: ID!): Book
    books: [Book!]!
}
```

Instead of listing `id`, `title`, `author`, `publishedYear` in every query,
both operations spread a shared **fragment**,
[`BookDetails.graphql`](src/main/graphql/com/example/graphql/BookDetails.graphql):

```graphql
fragment BookDetails on Book {
    id
    title
    author
    publishedYear
}
```

```graphql
query GetBook($id: ID!) {
    book(id: $id) {
        ...BookDetails
    }
}
```

Apollo's codegen turns the fragment into its own reusable `BookDetails` model
class, and both `GetBookQuery.Data.Book` and `GetBooksQuery.Data.Book` expose
it via a synthetic `.bookDetails` property (e.g.
`response.data?.book?.bookDetails?.title`). If the fragment's fields ever
change, every query using it regenerates consistently, so there's a single
source of truth for "what a book looks like".

## OpenAPI: schema and endpoints

The Tasks REST API (`schemas/openapi/tasks-api.yaml`) defines:

- `GET /tasks` - list tasks
- `GET /tasks/{id}` - fetch a single task (404 if missing)
- `POST /tasks` - create a task (request body `NewTask`, response `Task` with a server-assigned `id`)

OpenAPI Generator produces `TasksApi` (with `getTasks()`, `getTaskById(id)`,
`createTask(newTask)`) plus `Task`/`NewTask` data classes and an OkHttp-based
`ApiClient` whose base path can be pointed anywhere - which is what makes it
easy to test against a local mock server instead of a real backend.

## Running the codegens

Both codegens are wired into the normal Kotlin compilation, so you don't need
to run anything special:

```bash
./gradlew build
```

- Apollo auto-generates GraphQL sources into `build/generated/source/apollo/books` and hooks itself into `compileKotlin`.
- The `openApiGenerate` task generates the REST client into `build/generated/openapi/src/main/kotlin`, and `build.gradle.kts` adds that directory to the `main` source set plus makes `compileKotlin` depend on `openApiGenerate`.

You can also trigger each codegen individually:

```bash
./gradlew generateBooksApolloSources   # GraphQL client only
./gradlew openApiGenerate              # REST client only
```

## Running the tests

```bash
./gradlew test
```

Both test classes spin up an [OkHttp `MockWebServer`](https://github.com/square/okhttp/tree/master/mockwebserver)
to stand in for a real backend, point the generated client at it, and assert
on the parsed response (and, for the REST client, on the recorded outgoing
request):

- `GraphQLBookClientTest` - builds an `ApolloClient` pointed at the mock server, executes `GetBookQuery` / `GetBooksQuery`, and asserts on the fragment-derived fields.
- `TaskApiClientTest` - builds a `TasksApi(basePath = mockServerUrl)`, and calls `getTasks()`, `getTaskById(id)`, and `createTask(newTask)` against stubbed responses.

## Tooling versions

| Tool | Version |
| --- | --- |
| Gradle (wrapper) | 8.14.3 |
| Kotlin | 2.1.0 |
| Apollo Kotlin | 4.4.3 |
| OpenAPI Generator | 7.23.0 |
| JUnit | 5.10.2 |
| OkHttp / MockWebServer | 4.12.0 |
