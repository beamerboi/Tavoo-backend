# Backend annotation quick reference

This file lists annotations that are actually present in the backend. Architectural context and removal effects are discussed in [architecture.md](architecture.md).

## Spring Boot and web

| Annotation | Actual location | Effect |
|---|---|---|
| `@SpringBootApplication` | `TavooApplication` | Enables configuration, auto-configuration, and component scanning. |
| `@RestController` | Four controllers | Registers request handlers and serializes return values as JSON. |
| `@RequestMapping` | Four controllers | Defines `/api/menu`, `/api/locations`, `/api/tables`, and `/api/orders` base paths. |
| `@GetMapping` | Menu, location, table, and order query methods | Maps HTTP GET operations. |
| `@PostMapping` | Menu/location/table creation, order opening, payment | Maps HTTP POST operations. |
| `@PutMapping` | Add-order-item operation | Maps HTTP PUT. |
| `@RequestBody` | JSON request DTO parameters | Invokes HTTP-message conversion from JSON. |
| `@PathVariable` | Order IDs | Binds an URI segment to a Java argument. |
| `@RestControllerAdvice` | `GlobalExceptionHandler` | Applies centralized error handling to all controllers. |
| `@ExceptionHandler` | Handler methods | Selects exception-to-status mappings. |

## Dependency injection and service layer

| Annotation | Actual location | Effect |
|---|---|---|
| `@Service` | Menu, location, table, and order services | Registers business-layer beans. |
| `@Validated` | `OrderController` | Enables method-parameter constraints such as positive path IDs. |

Constructor injection is used throughout. `@Autowired` is not present because Spring automatically selects a component's single constructor.

Spring Data repository interfaces do not contain `@Repository`. Extending `JpaRepository` is enough for repository scanning, proxy generation, and persistence-exception translation.

## Transactions and repository locking

| Annotation | Actual location | Effect |
|---|---|---|
| `@Transactional` | Mutating service methods | Makes each complete use case atomic and enables rollback/dirty checking. |
| `@Transactional(readOnly = true)` | Query service methods | Keeps lazy reads inside a transaction and expresses non-mutating intent. |
| `@Lock(PESSIMISTIC_WRITE)` | Table/order `findByIdForUpdate` methods | Acquires database row locks to serialize conflicting state changes. |
| `@Query` | Two lock queries | Defines explicit JPQL used by the locking reads. |
| `@Param` | Lock query IDs | Binds method arguments to JPQL named parameters. |

## JPA and Hibernate

| Annotation | Actual use | Effect |
|---|---|---|
| `@Entity` | Six domain classes | Makes each class a JPA managed type. |
| `@Table` | Six entities | Maps explicit names including `locations` and `restaurant_tables`. |
| `@Id` | Every entity ID | Declares the primary key. |
| `@GeneratedValue(IDENTITY)` | Every entity ID | Uses database identity generation. |
| `@Column` | Persistent fields | Defines SQL names, nullability, uniqueness, length, precision, and scale. |
| `@Enumerated(STRING)` | Role, category, location type, table status, order status | Persists enum names rather than fragile ordinals. |
| `@ManyToOne` | Table-to-location, order-to-table/waiter, item-to-order/menu | Maps each foreign-key-owning to-one association. |
| `@JoinColumn` | Each many-to-one field | Names the corresponding foreign key column. |
| `@OneToMany(mappedBy=...)` | Inverse collections | Defines the inverse one-to-many side without join tables. |

`Order.items` additionally uses `cascade = CascadeType.ALL` and `orphanRemoval = true` because an order item has no lifecycle outside its order. No other relationship cascades.

## Bean Validation

| Annotation | Actual use |
|---|---|
| `@Valid` | Controller request bodies |
| `@NotNull` | Required DTO/entity references and values |
| `@NotBlank` | Names, usernames, passwords |
| `@Positive` | IDs, quantities, table numbers |
| `@DecimalMin("0.01")` | Menu price |
| `@Digits(integer = 10, fraction = 2)` | Money precision |
| `@Size` | API text-length limits |

Validation annotations reject malformed API input before service invocation. Critical state and monetary rules are also checked inside services.

## Testing

| Annotation | Actual location | Effect |
|---|---|---|
| `@Test` | Test methods | Marks JUnit 5 test cases. |
| `@ExtendWith(MockitoExtension.class)` | Service test classes | Initializes Mockito for JUnit 5. |
| `@Mock` | Repository fields | Creates repository test doubles. |
| `@InjectMocks` | Service fields | Constructs real services with mocked repositories. |

## Security annotations

- `@EnableMethodSecurity` activates controller method authorization.
- `@PreAuthorize` restricts ADMIN, WAITER, and KITCHEN endpoints.

## Deliberately absent annotations

- No `@Repository` is needed on Spring Data interfaces.
- No production field injection uses `@Autowired`.
- No JPA relationships use `EAGER`; DTO mapping happens inside service transactions.
