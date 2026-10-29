# Prediction — DELETE /orders/{id}

Written **before** asking the AI assistant to implement anything.
Target architecture: **Architecture B** (Lambda proxy integration + HTTP adapter), since it
is the final API of the workshop.

Expected behavior: `DELETE /orders/ORD-42` returns `204 No Content` when the order exists,
and `404 Not Found` with the standard JSON error document when it does not.

## 1. Which Java classes should change?

I predict three classes would change:

- **`OrdersApiHandler`** (HTTP adapter): it needs a new route for `DELETE /orders/{id}`,
  which extracts the `id` path parameter, calls the application and builds the HTTP response.
- **`OrderService`**: it needs a new operation to remove an order, since it is the class that
  holds the order collection.

## 2. Do I need a new Lambda function?

**No.** In Architecture B a single Lambda serves the whole API and the adapter decides what to
do from the HTTP method and the resource path. A new operation is a new route inside that
same function, not a new function.

## 3. Does API Gateway configuration need to change?

**Yes.** A `DELETE` method must be created on the `/orders/{id}` resource, with Lambda proxy
integration pointing to the same function. Without it, API Gateway would reject the request
before it ever reaches the Lambda. The API must then be deployed again to the `dev` stage.
No mapping templates or response definitions are needed, because it uses proxy integration.

## 4. Where should the deletion logic reside?

I predict it resides in the **handler** (`OrdersApiHandler`) and in the **service** (`OrderService`), which is the place where each
operation of the API is wired and executed.

## 5. Where should the decision to return 204 or 404 reside?

In the **handler**. Status codes are an HTTP concern, and in this architecture all HTTP
concerns live in the adapter, not in the business logic. This is consistent with the existing
design: the handler already translates `OrderNotFoundException` into 404 for `GET`.

## 6. Which tests should be added?
*(This answer was drafted by the AI assistant at my request; I reviewed it.)*

**In `OrderServiceTest` (application logic, no HTTP):**
- Deleting an existing order succeeds, and afterwards `getOrder` for that id throws
  `OrderNotFoundException`.
- Deleting a non-existent order throws `OrderNotFoundException`.
- Deleting with a blank or null id throws `InvalidOrderException`.
- Deleting the same order twice: the second attempt fails with `OrderNotFoundException`.
- After deleting an order, it no longer appears in `getOrdersByCustomer`.
- An order created with `createOrder` can be deleted.

**In `OrdersApiHandlerTest` (HTTP adapter):**
- `DELETE` of an existing order returns `204` with no body.
- `DELETE` of a non-existent order returns `404` with `ORDER_NOT_FOUND`.
- `DELETE` followed by `GET` of the same id returns `404`.
- `DELETE` with a missing path parameter returns `400`.
- An unexpected failure during deletion returns `500` without leaking internal details.

**On the deployed API (manual, with `curl.exe -i`):**
- `DELETE /orders/ORD-42` → `204`.
- `DELETE /orders/ORD-999` → `404`.
- Create an order, delete it, then `GET` it → `404`.