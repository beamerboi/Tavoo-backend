# Architecture exam notes

## A. Architecture summary

Tavoo is a layered Spring Boot REST backend:

```text
Controller -> Service -> Repository -> JPA/Hibernate -> PostgreSQL
```

DTOs form the API boundary, entities form the persistence/domain model, services own business rules and transactions, and centralized advice translates exceptions to consistent HTTP errors.

## B. Package responsibilities

| Package | Responsibility |
|---|---|
| `org.tavoo.controller` | HTTP routes, request validation trigger, status codes, delegation |
| `org.tavoo.service` | Use cases, business rules, calculations, transactions, entity-to-DTO mapping |
| `org.tavoo.repository` | Spring Data persistence and locked reads |
| `org.tavoo.entity` | Domain state, JPA mapping, small state-change methods |
| `org.tavoo.dto` | Request/response contracts and validation constraints |
| `org.tavoo.exception` | Domain failures and exception-to-HTTP mapping |

## C. Main class responsibilities

- `TavooApplication`: application bootstrap and component-scan root.
- `MenuController` / `MenuService`: available-menu query and menu creation.
- `LocationController` / `LocationService`: restaurant-area creation and query.
- `RestaurantTableController` / `RestaurantTableService`: table creation and state query.
- `OrderController` / `OrderService`: open, add item, pay, and retrieve order use cases.
- Five repositories: persistence for users, menu items, locations, restaurant tables, and orders.
- `GlobalExceptionHandler`: consistent 400/404/409/500 responses.

There is no `OrderItemRepository` because items are persisted as part of the `Order` aggregate.

## D. Important design decisions

1. Constructor injection makes dependencies mandatory and testable; no production field injection is used.
2. DTOs prevent lazy JPA graphs and password data from leaking into JSON.
3. `BigDecimal` and explicit string tax rates avoid floating-point errors.
4. Service methods define transaction boundaries around complete business operations.
5. Pessimistic row locks protect table and order state from concurrent requests.
6. Enum names are stored as strings for readability and ordinal safety.
7. SQL-safe names avoid PostgreSQL keywords: `RestaurantTable` maps to `restaurant_tables` and `Order` maps to `customer_orders`.
8. Cascade and orphan removal apply only to order items, whose lifecycle belongs to an order.
9. Open Session in View is disabled; DTO mapping occurs within service transactions.
10. Security is explicitly out of the current scope rather than partially simulated.

## E. Framework annotations and impact

- `@SpringBootApplication` starts auto-configuration and component scanning.
- `@RestController` registers JSON HTTP handlers.
- HTTP mapping annotations select paths and verbs.
- `@Service` registers application/business objects.
- `@Transactional` provides atomic use cases, rollback, flushing, and dirty checking.
- `@Entity`, `@Id`, and `@GeneratedValue` define managed identity-bearing rows.
- `@ManyToOne` plus `@JoinColumn` define foreign-key ownership.
- `@OneToMany(mappedBy=...)` defines inverse collections.
- `@Enumerated(STRING)` stores stable readable enum values.
- `@Valid` triggers DTO constraints.
- `@RestControllerAdvice` centralizes API error translation.

`@Autowired` and `@Repository` are absent in production for specific reasons: single-constructor injection is implicit, and Spring Data automatically implements repository interfaces.

See [backend-annotations.md](backend-annotations.md) for the complete quick reference.

## F. Database and JPA mapping

| Table | Important columns / keys |
|---|---|
| `app_users` | identity PK; unique username; string role |
| `menu_items` | identity PK; unique name; `NUMERIC(12,2)` price; category; availability |
| `locations` | identity PK; unique area name; inside/outside type |
| `restaurant_tables` | identity PK; unique table number; positive seat count; location FK; status |
| `customer_orders` | table FK; waiter FK; status; `NUMERIC(12,2)` total |
| `order_items` | order FK; menu-item FK; positive quantity; notes |

The many-to-one side owns each foreign key. Inverse `mappedBy` collections do not create join tables. To-one relationships are lazy. Hibernate dirty checking updates a managed table when `occupy()` or `free()` changes its state.

## G. Business rules

- An occupied table cannot receive another order.
- Only the authenticated `WAITER` can own and modify a new order.
- Only open orders accept items or payment.
- Quantity and price must be positive.
- A menu item must exist and be available.
- FOOD, DESSERT, and BEVERAGE tax is 10%; ALCOHOL tax is 15%.
- Payment calculates the total, marks the order paid, and frees the table atomically.

## H. Important request flows

### Open order

Controller validates the table and initial items. Service resolves the authenticated waiter, locks the table, checks `FREE`, validates the menu items, occupies the table, creates/saves an open order, maps a DTO, and commits all changes.

### Add item

Service rejects invalid quantity, locks the order, checks `OPEN`, loads and validates the menu item, creates an `OrderItem`, and saves the order. Cascade inserts the new item.

### Pay order

Service locks the order, checks `OPEN`, iterates items, calculates subtotal and category tax with `BigDecimal`, rounds to two decimals, marks the order `PAID`, frees the managed table, and commits both updates.

The precise interactions appear in `docs/uml/sequence-*.puml`.

## I. Testing architecture

Service tests use JUnit 5 and Mockito. Real services are constructed with mocked repositories, so tax and state rules run without PostgreSQL. The test suite covers all requested order scenarios and direct-service validation guards.

Standalone MockMvc tests verify every implemented endpoint, Bean Validation, success statuses, JSON serialization, and centralized 409 handling while keeping services mocked.

Automated tests deliberately avoid the developer's local PostgreSQL database. The opt-in Testcontainers suite runs Flyway and JPA against an isolated PostgreSQL 16 database, so it cannot modify local restaurant data.

## J. Potential weaknesses and limitations

- ADMIN authorization for menu creation is enforced by Spring Security method authorization.
- Password encoding and user lifecycle management are not implemented.
- Schema evolution currently uses Hibernate `update`; production should use versioned migrations and `validate`.
- The API has no administration endpoints for users or restaurant tables; these require database seeding.
- PostgreSQL integration tests require Docker and run explicitly with `mvn verify -Pintegration`.
- There is no menu update API. If one is added, `OrderItem` should snapshot price/category to keep historical bills stable.
- The API has no pagination because its current query surfaces are intentionally small.

## K. Likely professor questions and suggested answers

### Why separate controllers, services, and repositories?

Each layer changes for a different reason. Controllers change with HTTP contracts, services with business policy, and repositories with persistence needs. The separation also makes service rules testable with mocked persistence.

### Why should business logic be in services?

A use case may involve several entities and repositories and must remain independent of HTTP. The service is also the correct place for the transaction that makes those changes atomic.

### What does `@RestController` provide?

It combines controller registration with response-body semantics. Spring MVC discovers mapped methods, invokes them for matching requests, and serializes returned DTOs as JSON.

### What is the effect of `@Service`?

Component scanning registers the class as a Spring bean. The stereotype communicates its architectural role and lets Spring apply constructor injection and transactional proxies.

### Why use `JpaRepository`?

It supplies typed CRUD operations and Spring Data query generation while keeping services independent of `EntityManager` boilerplate. Spring creates the concrete proxy at runtime.

### Why is `@Repository` missing from repository interfaces?

Spring Data discovers interfaces extending `JpaRepository`, creates proxy beans, and applies exception translation. Adding the stereotype would be redundant.

### What does `@Entity` mean?

It makes a class part of the JPA persistence model. Hibernate tracks instances in a persistence context and maps their annotated state to relational rows.

### How does Hibernate map the model to PostgreSQL?

Entity/table annotations select tables and columns; IDs use identity generation; `@JoinColumn` fields become foreign keys; enum strings and decimal precision determine column representation. Hibernate generates SQL through the PostgreSQL JDBC driver.

### Why use `@ManyToOne` for an order's table and waiter?

Many historical orders can reference one table and one user, while each order has exactly one of each. The order table contains those foreign keys, so the many-to-one side owns the relationships.

### What does `@JoinColumn` represent?

It names the column on the owning table that stores the referenced entity's primary key, such as `waiter_id` or `restaurant_table_id`.

### Why is `OrderItem` a separate entity?

It resolves the many-to-many business relationship between orders and menu items and stores relationship-specific data: quantity and notes. It also gives each line persistent identity.

### Why cascade from Order to OrderItem only?

An order item has no meaning without its order, so lifecycle propagation and orphan removal are appropriate. Users, tables, menu items, and orders have independent histories and must not be deleted transitively.

### Where is dependency injection used?

Spring injects services into controller constructors and repository proxies into service constructors. The sole-constructor convention means `@Autowired` is unnecessary.

### Why is payment transactional?

It must update the order total/status and table status together. If either write fails, rollback prevents a paid order with an occupied table or the reverse.

### What happens when an occupied table is requested?

The service loads the table with a write lock, detects `OCCUPIED`, throws `TableOccupiedException`, and advice returns HTTP 409. No order is inserted.

### Why use pessimistic locks?

A transaction alone does not stop two transactions from reading the same `FREE` state. The row lock serializes them so only one can occupy the table. Order locks similarly prevent add/pay races.

### Where is tax calculated?

Inside `OrderService.payOrder`. Each line subtotal is multiplied by the explicit 0.10 or 0.15 constant selected from the menu category, then the final amount is rounded to two decimal places.

### How does paying change persistent state without `tableRepository.save(table)`?

The table association is loaded inside the transaction and is a managed entity. Calling `free()` changes managed state; Hibernate dirty checking generates the update at flush/commit.

### Why use DTOs rather than exposing entities?

DTOs stabilize the API, allow request validation, prevent password leakage, avoid recursive bidirectional JSON graphs, and stop web clients from depending on lazy persistence behavior.

### What are the advantages and disadvantages of this architecture?

Advantages are clarity, testability, familiar framework integration, and clear transaction ownership. Disadvantages are some mapping/boilerplate and the risk that services become too large as the domain grows.

### What would change if the system became much larger?

Introduce audit fields, pagination, immutable product-name/category snapshots, broader end-to-end API tests, and use-case-focused services. Split modules only when team/domain boundaries justify the additional complexity.

## L. Short defense

The central design choice is that HTTP, business policy, and persistence are separated, while each business use case remains one explainable transactional path. The code uses framework features where they change runtime behavior—component registration, validation, mapping, locking, and transaction management—without decorative annotations or unused abstractions.
