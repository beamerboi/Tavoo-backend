# Backend architecture

## Architectural style

Tavoo uses a conventional layered architecture:

```text
Controller -> Service -> Repository -> JPA/Hibernate -> PostgreSQL
```

Dependencies point inward from delivery concerns toward the domain and persistence abstractions. A controller depends on a service but never calls a repository. A service coordinates repositories and entities. Repository interfaces depend on entities and Spring Data. Entities contain small state-changing methods but no HTTP or repository logic.

This structure is intentionally simple: every important use case can be traced through one controller method, one service transaction, repository operations, entity state changes, and a database commit.

## Layer responsibilities

### Controller / API layer

`MenuController`, `LocationController`, `RestaurantTableController`, and `OrderController` declare URLs, deserialize DTOs, trigger Bean Validation, delegate to services, and select success status codes. They contain no tax, state-transition, or persistence logic.

### Application / business layer

`MenuService`, `LocationService`, `RestaurantTableService`, and `OrderService` enforce use-case rules. `OrderService` is responsible for table availability, waiter ownership, order state checks, menu-item validation, preparation-priority groups, course release/delivery, tax calculation, payment, check projection, and the daily paid-order archive.

DTO conversion occurs before a service transaction closes. This allows lazy relationships to be read safely while keeping JPA entities out of JSON serialization.

### Persistence layer

The five repository interfaces extend `JpaRepository`. Spring Data generates their implementations and translates persistence exceptions. Derived-query method names implement menu/location/table queries; explicit JPQL queries acquire pessimistic write locks for state-changing order operations.

There is no separate `OrderItemRepository`: order items are part of the `Order` aggregate and are persisted through the order's cascade. An unused repository would add abstraction without responsibility.

### Domain / model layer

Six JPA entities model the database. Enums are stored as strings so database values remain understandable and are not coupled to enum ordinal positions.

`Order.addItem`, `Order.markPaid`, `RestaurantTable.occupy`, and `RestaurantTable.free` express valid state changes. Services decide when those changes are allowed.

### Infrastructure

`TavooApplication` bootstraps Spring. `application.yml` configures the PostgreSQL data source through environment variables, disables Open Session in View, and configures Hibernate schema handling.

Disabling Open Session in View ensures controllers do not accidentally trigger persistence queries. Services map entities to DTOs within transactions instead.

## Dependency injection

All controllers and services use one public constructor. Spring automatically injects the required beans; `@Autowired` is unnecessary when a component has one constructor. Final fields make dependencies explicit and prevent them from being replaced after construction.

## Transactions and concurrency

`@Transactional` is placed on service use cases because a use case—not an individual repository call—is the atomic unit.

- Opening an order locks the table row, checks `FREE`, changes it to `OCCUPIED`, and inserts the order in one transaction.
- Adding an item locks the order row, verifies `OPEN`, and persists the new item through cascade.
- Course commands lock the order and atomically transition every item with the same preparation priority, preventing partially released or partially delivered groups.
- Paying locks the order row, calculates the total, records `POS` or `CHECK` plus the payment instant, changes it to `PAID`, and frees the table in one transaction.

If a runtime exception occurs, Spring rolls the transaction back. Hibernate dirty checking persists changes to managed `RestaurantTable` entities even though no explicit `save(table)` call is needed.

`PESSIMISTIC_WRITE` locks serialize competing modifications. Without the table lock, two concurrent requests could both observe `FREE` and open two orders. Without the order lock, add and pay requests could race.

Future preparation groups use `ON_HOLD`. The first numeric priority is released to `ORDERED`; the kitchen moves the group to `IN_PREPARATION` (visible as “in making”) and `READY`, the waiter moves it to `SERVED`, and only then may the waiter release the next priority. Beverage and alcohol lines bypass kitchen tickets, carry a null priority, and start `READY`. The frontend groups and visually separates kitchen lines by the returned `preparationPriority`; presentation is not encoded in the backend.

Read methods use `@Transactional(readOnly = true)` to communicate intent and allow persistence optimizations.

## JPA relationship ownership

| Relationship | Owning side / foreign key | Cascade decision |
|---|---|---|
| `Location 1 -> many RestaurantTable` | `RestaurantTable.location`, `location_id` | None; areas and tables have independent lifecycles |
| `RestaurantTable 1 -> many Order` | `Order.table`, `restaurant_table_id` | None; histories and tables have independent lifecycles |
| `User 1 -> many Order` | `Order.waiter`, `waiter_id` | None; deleting a user must not delete bills |
| `Order 1 -> many OrderItem` | `OrderItem.order`, `order_id` | `ALL` plus orphan removal; items are order-owned components |
| `MenuItem 1 -> many OrderItem` | `OrderItem.menuItem`, `menu_item_id` | None; menu lifecycle is independent of order history |

All to-one associations are lazy. The many-side owns each relationship because its table contains the foreign key. `mappedBy` marks the inverse collection and prevents a redundant join table.

## Monetary calculation

Money uses `BigDecimal`, never binary floating point. When an item is ordered, its unit price and applicable tax rate are copied to `OrderItem`. Order creation also copies the submitted coperto count and the current persistent ADMIN-managed coperto price. `OrderService` calculates each line subtotal and tax from those snapshots, adds the coperto total, and rounds monetary values to two decimals with `HALF_UP`. Consequently, receipt previews and archived paid receipts remain stable even if the menu or restaurant settings change later.

## Validation and errors

Request DTOs use Bean Validation for required values, text length, positive identifiers/quantities, and money precision. Services repeat critical business guards such as positive quantity and price so correctness does not depend exclusively on HTTP invocation.

`GlobalExceptionHandler` maps exceptions consistently:

- `ResourceNotFoundException` -> 404
- `TableOccupiedException` and `InvalidOrderStateException` -> 409
- Other business or validation failures -> 400
- Database constraint conflicts -> 409
- Unexpected failures -> a non-sensitive 500 response

## Important annotations and runtime impact

### Spring Boot and MVC

| Annotation | Where | Runtime impact |
|---|---|---|
| `@SpringBootApplication` | `TavooApplication` | Combines configuration, auto-configuration, and component scanning from `org.tavoo`. Without it, the web/JPA application context is not bootstrapped. |
| `@RestController` | Controller classes | Registers MVC handlers and writes return values as JSON. Removing it prevents endpoint discovery. |
| `@RequestMapping` | Controller classes | Defines the shared resource path. |
| `@GetMapping`, `@PostMapping`, `@PutMapping` | Controller methods | Bind an HTTP method and relative path to a Java method. |
| `@RequestBody` | Request parameters | Deserializes JSON into a DTO. |
| `@PathVariable` | Order identifiers | Binds a URI segment to the method argument. |
| `@RestControllerAdvice` / `@ExceptionHandler` | `GlobalExceptionHandler` | Apply centralized exception-to-HTTP translation across all controllers. |

### Dependency injection and services

| Annotation | Where | Runtime impact |
|---|---|---|
| `@Service` | Service classes | Registers business-layer beans. It is an architectural stereotype and makes constructor injection possible. |
| `@Validated` | `OrderController` | Enables constraint validation for method parameters such as positive path identifiers. |

`@Autowired` is deliberately absent because Spring implicitly uses a component's only constructor.

### Transactions

| Annotation | Where | Runtime impact |
|---|---|---|
| `@Transactional` | Service methods | Opens a transaction around the complete use case; runtime exceptions cause rollback and managed-entity changes flush atomically. |
| `@Transactional(readOnly = true)` | Query service methods | Marks non-mutating transactions and supports safe lazy reads during DTO mapping. |

Moving these boundaries into controllers would couple HTTP handling to persistence. Putting them only on repositories would split multi-repository use cases across separate transactions.

### JPA and Hibernate

| Annotation | Runtime impact |
|---|---|
| `@Entity` | Makes the class a managed persistent type. |
| `@Table` | Selects explicit SQL-safe table names. |
| `@Id` / `@GeneratedValue(IDENTITY)` | Defines PostgreSQL identity primary keys. |
| `@Column` | Defines nullability, uniqueness, names, lengths, and numeric precision. |
| `@Enumerated(STRING)` | Stores readable enum names and avoids ordinal fragility. |
| `@ManyToOne` / `@JoinColumn` | Maps the foreign-key-owning side. |
| `@OneToMany(mappedBy=...)` | Maps the inverse collection without a join table. |
| `cascade = ALL` / `orphanRemoval = true` | Propagates the order-item lifecycle only from its owning order. |
| `@Lock(PESSIMISTIC_WRITE)` | Produces row locks for concurrency-sensitive repository reads. |
| `@Query` / `@Param` | Define and bind the two explicit JPQL locking queries. |

Repository interfaces do not need `@Repository`: Spring Data discovers `JpaRepository` interfaces and creates repository beans with exception translation automatically.

### Bean Validation

`@NotNull`, `@NotBlank`, `@Positive`, `@DecimalMin`, `@Digits`, and `@Size` constrain DTOs and important entity fields. `@Valid` on controller request bodies triggers validation before the service runs.

### Testing

`@ExtendWith(MockitoExtension.class)` activates Mockito in service unit tests. `@Mock` creates repository substitutes and `@InjectMocks` constructs real services with those substitutes. `@Test` identifies JUnit cases.

## Security

Spring Security authenticates login credentials against BCrypt password hashes and issues HS256 JWT access tokens. Normal logins are short-lived; an explicit `rememberMe` request selects a separately configured longer lifetime. The resource-server filter validates bearer-token signatures, issuer, expiration, and role claims without creating an HTTP session. `@PreAuthorize` expresses ADMIN, WAITER, and KITCHEN permissions at controller boundaries. The waiter identity comes from the authenticated token rather than request data, preventing waiter impersonation during order creation or modification.

## Testing architecture

`OrderServiceTest` and `MenuServiceTest` are fast unit tests: real service logic plus mocked repositories. They cover state rules, validation guards, tax calculations, price snapshots, check data, daily archives, and payment side effects without a database.

`ApiControllerTest` uses standalone MockMvc with mocked services to verify endpoint mappings, request-body validation, success status codes, JSON responses, and centralized conflict handling.

Automated tests deliberately do not connect to the developer's local `tavoodb` database. Fast domain, service, and controller tests run with `mvn test`; `mvn verify` produces a JaCoCo coverage report. The opt-in `integration` Maven profile uses Testcontainers to run Flyway and the JPA repositories against an isolated PostgreSQL 16 database.

## Growth path

If the system grows, likely next steps are refresh-token rotation and revocation, asymmetric signing keys, archive pagination, immutable product-name/category snapshots, fiscal receipt integration, broader end-to-end API tests, and splitting large application services by use case.
