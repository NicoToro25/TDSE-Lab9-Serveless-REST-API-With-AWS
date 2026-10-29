package edu.eci.tdse.orders.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import edu.eci.tdse.orders.exception.InvalidOrderException;
import edu.eci.tdse.orders.exception.OrderNotFoundException;
import edu.eci.tdse.orders.model.CreateOrderRequest;
import edu.eci.tdse.orders.model.Order;
import edu.eci.tdse.orders.service.OrderService;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

/**
 * HTTP adapter (Architecture B). It is the only class that knows about API
 * Gateway events, HTTP status codes, headers and JSON serialization. All
 * business rules live in OrderService.
 */
public class OrdersApiHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

	private static final Gson GSON = new Gson();

	private final OrderService service;

	/** Used by the Lambda runtime. */
	public OrdersApiHandler() {
		this(new OrderService());
	}

	/** Used by tests, to inject a service. */
	public OrdersApiHandler(OrderService service) {
		this.service = service;
	}

	@Override
	public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context context) {
		try {
			return route(event);
		} catch (OrderNotFoundException e) {
			return error(404, "ORDER_NOT_FOUND", e.getMessage());
		} catch (InvalidOrderException e) {
			return error(400, "INVALID_REQUEST", e.getMessage());
		} catch (JsonParseException e) {
			return error(400, "INVALID_REQUEST", "Request body is not valid JSON");
		} catch (Exception e) {
			log(context, e);
			return error(500, "INTERNAL_ERROR", "Unexpected error");
		}
	}

	// ---------- routing ----------

	private APIGatewayProxyResponseEvent route(APIGatewayProxyRequestEvent event) {
		String method = event.getHttpMethod();
		String resource = event.getResource();

		if ("POST".equals(method) && "/orders".equals(resource)) {
			return createOrder(event);
		}
		if ("GET".equals(method) && "/orders".equals(resource)) {
			return findOrdersByCustomer(event);
		}
		if ("GET".equals(method) && "/orders/{id}".equals(resource)) {
			return getOrder(event);
		}
		if ("DELETE".equals(method) && "/orders/{id}".equals(resource)) {
			return deleteOrder(event);
		}
		return error(404, "ROUTE_NOT_FOUND", "No route for " + method + " " + resource);
	}

	// ---------- operations: HTTP in, application call, HTTP out ----------

	private APIGatewayProxyResponseEvent createOrder(APIGatewayProxyRequestEvent event) {
		CreateOrderRequest request = GSON.fromJson(event.getBody(), CreateOrderRequest.class);
		Order order = service.createOrder(request);
		Map<String, String> headers = Map.of("Location", collectionPath(event) + "/" + order.getId());
		return json(201, order, headers);
	}

	private APIGatewayProxyResponseEvent getOrder(APIGatewayProxyRequestEvent event) {
		String id = valueOf(event.getPathParameters(), "id");
		return json(200, service.getOrder(id), Map.of());
	}

	private APIGatewayProxyResponseEvent findOrdersByCustomer(APIGatewayProxyRequestEvent event) {
		String customerId = valueOf(event.getQueryStringParameters(), "customerId");
		return json(200, service.getOrdersByCustomer(customerId), Map.of());
	}

	private APIGatewayProxyResponseEvent deleteOrder(APIGatewayProxyRequestEvent event) {
		String id = valueOf(event.getPathParameters(), "id");
		service.deleteOrder(id);
		return new APIGatewayProxyResponseEvent().withStatusCode(204);
	}

	// ---------- HTTP helpers ----------

	/**
	 * pathParameters and queryStringParameters are null (not empty) when absent.
	 */
	private String valueOf(Map<String, String> parameters, String name) {
		return parameters == null ? null : parameters.get(name);
	}

	/**
	 * requestContext.path includes the stage (/dev/orders); path alone does not.
	 */
	private String collectionPath(APIGatewayProxyRequestEvent event) {
		String path = event.getPath();
		if (event.getRequestContext() != null && event.getRequestContext().getPath() != null) {
			path = event.getRequestContext().getPath();
		}
		return path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
	}

	private APIGatewayProxyResponseEvent json(int status, Object body, Map<String, String> extraHeaders) {
		Map<String, String> headers = new HashMap<>();
		headers.put("Content-Type", "application/json");
		headers.putAll(extraHeaders);
		return new APIGatewayProxyResponseEvent().withStatusCode(status).withHeaders(headers)
				.withBody(GSON.toJson(body));
	}

	private APIGatewayProxyResponseEvent error(int status, String code, String message) {
		return json(status, new ErrorBody(code, message), Map.of());
	}

	private void log(Context context, Exception e) {
		StringWriter trace = new StringWriter();
		e.printStackTrace(new PrintWriter(trace));
		if (context != null) {
			context.getLogger().log(trace.toString());
		} else {
			System.err.println(trace);
		}
	}

	/** JSON error document defined in the API contract. */
	private static class ErrorBody {
		private final String error;
		private final String message;

		ErrorBody(String error, String message) {
			this.error = error;
			this.message = message;
		}
	}
}