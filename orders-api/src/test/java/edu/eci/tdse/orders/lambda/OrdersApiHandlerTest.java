package edu.eci.tdse.orders.lambda;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import edu.eci.tdse.orders.model.Order;
import edu.eci.tdse.orders.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class OrdersApiHandlerTest {

	private OrdersApiHandler handler;

	@BeforeEach
	void setUp() {
		handler = new OrdersApiHandler(new OrderService());
	}

	// ---------- helpers ----------

	private APIGatewayProxyRequestEvent request(String method, String resource, String path) {
		return new APIGatewayProxyRequestEvent().withHttpMethod(method).withResource(resource).withPath(path);
	}

	private APIGatewayProxyResponseEvent call(APIGatewayProxyRequestEvent event) {
		return handler.handleRequest(event, null);
	}

	private JsonObject jsonBody(APIGatewayProxyResponseEvent response) {
		return JsonParser.parseString(response.getBody()).getAsJsonObject();
	}

	private APIGatewayProxyRequestEvent post(String body) {
		return request("POST", "/orders", "/orders").withBody(body);
	}

	// ---------- GET /orders/{id} ----------

	@Test
	void getOrder_existing_returns200WithOrder() {
		APIGatewayProxyResponseEvent response = call(
				request("GET", "/orders/{id}", "/orders/ORD-42").withPathParameters(Map.of("id", "ORD-42")));

		assertEquals(200, response.getStatusCode());
		assertEquals("application/json", response.getHeaders().get("Content-Type"));
		assertEquals("ORD-42", jsonBody(response).get("id").getAsString());
		assertEquals("Keyboard", jsonBody(response).get("product").getAsString());
	}

	@Test
	void getOrder_missing_returns404WithErrorDocument() {
		APIGatewayProxyResponseEvent response = call(
				request("GET", "/orders/{id}", "/orders/ORD-999").withPathParameters(Map.of("id", "ORD-999")));

		assertEquals(404, response.getStatusCode());
		assertEquals("ORDER_NOT_FOUND", jsonBody(response).get("error").getAsString());
		assertEquals("Order ORD-999 does not exist", jsonBody(response).get("message").getAsString());
	}

	// ---------- POST /orders ----------

	@Test
	void createOrder_valid_returns201WithLocationHeader() {
		APIGatewayProxyResponseEvent response = call(
				post("{\"customerId\":\"CUST-42\",\"product\":\"MacBook Air\",\"quantity\":1}"));

		assertEquals(201, response.getStatusCode());
		assertEquals("/orders/ORD-43", response.getHeaders().get("Location"));
		assertEquals("CUST-42", jsonBody(response).get("customerId").getAsString());
	}

	@Test
	void createOrder_thenGet_sharesStateInsideTheSameHandler() {
		call(post("{\"customerId\":\"CUST-9\",\"product\":\"Monitor\",\"quantity\":2}"));

		APIGatewayProxyResponseEvent response = call(
				request("GET", "/orders/{id}", "/orders/ORD-43").withPathParameters(Map.of("id", "ORD-43")));

		assertEquals(200, response.getStatusCode());
		assertEquals("Monitor", jsonBody(response).get("product").getAsString());
	}

	@Test
	void createOrder_invalidQuantity_returns400() {
		APIGatewayProxyResponseEvent response = call(
				post("{\"customerId\":\"CUST-42\",\"product\":\"MacBook Air\",\"quantity\":0}"));

		assertEquals(400, response.getStatusCode());
		assertEquals("INVALID_REQUEST", jsonBody(response).get("error").getAsString());
	}

	@Test
	void createOrder_malformedJson_returns400() {
		APIGatewayProxyResponseEvent response = call(post("{ this is not json"));

		assertEquals(400, response.getStatusCode());
		assertEquals("Request body is not valid JSON", jsonBody(response).get("message").getAsString());
	}

	@Test
	void createOrder_wrongFieldType_returns400() {
		APIGatewayProxyResponseEvent response = call(
				post("{\"customerId\":\"CUST-42\",\"product\":\"Mouse\",\"quantity\":\"abc\"}"));

		assertEquals(400, response.getStatusCode());
	}

	@Test
	void createOrder_missingBody_returns400() {
		APIGatewayProxyResponseEvent response = call(post(null));

		assertEquals(400, response.getStatusCode());
		assertEquals("INVALID_REQUEST", jsonBody(response).get("error").getAsString());
	}

	// ---------- GET /orders?customerId= ----------

	@Test
	void search_withResults_returns200WithArray() {
		APIGatewayProxyResponseEvent response = call(
				request("GET", "/orders", "/orders").withQueryStringParameters(Map.of("customerId", "CUST-42")));

		assertEquals(200, response.getStatusCode());
		JsonArray array = JsonParser.parseString(response.getBody()).getAsJsonArray();
		assertEquals(1, array.size());
		assertEquals("ORD-001", array.get(0).getAsJsonObject().get("id").getAsString());
	}

	@Test
	void search_customerWithoutOrders_returns200WithEmptyArray() {
		APIGatewayProxyResponseEvent response = call(
				request("GET", "/orders", "/orders").withQueryStringParameters(Map.of("customerId", "CUST-100")));

		assertEquals(200, response.getStatusCode());
		assertEquals("[]", response.getBody());
	}

	@Test
	void search_withoutQueryParameters_returns400() {
		// API Gateway sends queryStringParameters = null when there is no query string
		APIGatewayProxyResponseEvent response = call(request("GET", "/orders", "/orders"));

		assertEquals(400, response.getStatusCode());
		assertEquals("INVALID_REQUEST", jsonBody(response).get("error").getAsString());
	}

	// ---------- DELETE /orders/{id} ----------

	private APIGatewayProxyRequestEvent delete(String id) {
		return request("DELETE", "/orders/{id}", "/orders/" + id).withPathParameters(Map.of("id", id));
	}

	@Test
	void deleteOrder_existing_returns204WithoutBody() {
		APIGatewayProxyResponseEvent response = call(delete("ORD-42"));

		assertEquals(204, response.getStatusCode());
		assertNull(response.getBody());
	}

	@Test
	void deleteOrder_missing_returns404WithErrorDocument() {
		APIGatewayProxyResponseEvent response = call(delete("ORD-999"));

		assertEquals(404, response.getStatusCode());
		assertEquals("ORDER_NOT_FOUND", jsonBody(response).get("error").getAsString());
	}

	@Test
	void deleteOrder_thenGet_returns404() {
		call(delete("ORD-42"));

		APIGatewayProxyResponseEvent response = call(
				request("GET", "/orders/{id}", "/orders/ORD-42").withPathParameters(Map.of("id", "ORD-42")));

		assertEquals(404, response.getStatusCode());
	}

	@Test
	void deleteOrder_withoutPathParameter_returns400() {
		// pathParameters is null when absent
		APIGatewayProxyResponseEvent response = call(request("DELETE", "/orders/{id}", "/orders/"));

		assertEquals(400, response.getStatusCode());
		assertEquals("INVALID_REQUEST", jsonBody(response).get("error").getAsString());
	}

	@Test
	void deleteOrder_unexpectedFailure_returns500WithoutLeakingDetails() {
		OrderService failing = new OrderService() {
			@Override
			public void deleteOrder(String id) {
				throw new IllegalStateException("secret internal detail");
			}
		};
		OrdersApiHandler failingHandler = new OrdersApiHandler(failing);

		APIGatewayProxyResponseEvent response = failingHandler.handleRequest(delete("ORD-42"), null);

		assertEquals(500, response.getStatusCode());
		assertFalse(response.getBody().contains("secret internal detail"));
	}

	// ---------- routing and unexpected failures ----------

	@Test
	void unknownRoute_returns404() {
		APIGatewayProxyResponseEvent response = call(request("PATCH", "/orders/{id}", "/orders/ORD-42"));

		assertEquals(404, response.getStatusCode());
		assertEquals("ROUTE_NOT_FOUND", jsonBody(response).get("error").getAsString());
	}

	@Test
	void unexpectedFailure_returns500WithoutLeakingDetails() {
		OrderService failing = new OrderService() {
			@Override
			public Order getOrder(String id) {
				throw new IllegalStateException("secret internal detail");
			}
		};
		OrdersApiHandler failingHandler = new OrdersApiHandler(failing);

		APIGatewayProxyResponseEvent response = failingHandler.handleRequest(
				request("GET", "/orders/{id}", "/orders/ORD-42").withPathParameters(Map.of("id", "ORD-42")), null);

		assertEquals(500, response.getStatusCode());
		assertEquals("INTERNAL_ERROR", jsonBody(response).get("error").getAsString());
		assertFalse(response.getBody().contains("secret internal detail"));
	}
}