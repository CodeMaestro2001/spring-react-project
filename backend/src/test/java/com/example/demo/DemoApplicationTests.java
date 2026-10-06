package com.example.demo;

import com.example.demo.dto.CartResponse;
import com.example.demo.dto.OrderResponse;
import com.example.demo.dto.ProductUpsertRequest;
import com.example.demo.model.Account;
import com.example.demo.model.AccountRole;
import com.example.demo.model.OrderStatus;
import com.example.demo.model.Product;
import com.example.demo.repo.AccountRepository;
import com.example.demo.repo.ProductRepository;
import com.example.demo.service.CartService;
import com.example.demo.service.OrderService;
import com.example.demo.service.ProductService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:h2:mem:supplycart;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;INIT=CREATE DOMAIN IF NOT EXISTS TIMESTAMPTZ AS TIMESTAMP WITH TIME ZONE",
		"spring.datasource.username=sa",
		"spring.datasource.password=",
		"spring.datasource.driver-class-name=org.h2.Driver",
		"spring.flyway.locations=classpath:db/h2-migration",
		"spring.session.store-type=none",
		"spring.cache.type=simple",
		"management.health.redis.enabled=false",
		"management.endpoint.health.group.readiness.include=readinessState,db"
}, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DemoApplicationTests {

	@Autowired
	private Environment environment;

	@Autowired
	private AccountRepository accountRepository;

	@Autowired
	private ProductRepository productRepository;

	@Autowired
	private CartService cartService;

	@Autowired
	private OrderService orderService;

	@Autowired
	private ProductService productService;

	@Test
	void healthEndpointReportsReadyAfterMigrationsAndSchemaValidation() throws Exception {
		int port = environment.getRequiredProperty("local.server.port", Integer.class);
		HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create("http://localhost:" + port + "/actuator/health"))
				.GET()
				.build();

		HttpResponse<String> response = HttpClient.newHttpClient()
				.send(request, HttpResponse.BodyHandlers.ofString());

		assertEquals(200, response.statusCode());
		assertTrue(response.body().contains("\"status\":\"UP\""));
		assertTrue(response.headers().firstValue("X-Request-Id").isPresent());

		HttpResponse<String> readinessResponse = HttpClient.newHttpClient().send(
				HttpRequest.newBuilder().uri(URI.create("http://localhost:" + port + "/actuator/health/readiness"))
						.GET().build(), HttpResponse.BodyHandlers.ofString());
		assertEquals(200, readinessResponse.statusCode());
		assertTrue(readinessResponse.body().contains("\"status\":\"UP\""));

		HttpResponse<String> livenessResponse = HttpClient.newHttpClient().send(
				HttpRequest.newBuilder().uri(URI.create("http://localhost:" + port + "/actuator/health/liveness"))
						.GET().build(), HttpResponse.BodyHandlers.ofString());
		assertEquals(200, livenessResponse.statusCode());
		assertTrue(livenessResponse.body().contains("\"status\":\"UP\""));

		HttpResponse<String> protectedCartResponse = HttpClient.newHttpClient().send(
				HttpRequest.newBuilder().uri(URI.create("http://localhost:" + port + "/api/cart"))
						.GET().build(), HttpResponse.BodyHandlers.ofString());
		assertEquals(401, protectedCartResponse.statusCode());
		assertTrue(protectedCartResponse.headers().firstValue("X-Request-Id").isPresent());
	}

	@Test
	@Transactional
	void checkoutConsumesStockAndRetriesReturnTheSamePriceSnapshotOrder() {
		Account account = createCustomer();
		Product product = createProduct("Restaurant Flour", "24.50", 5);
		cartService.add(account.getId(), product.getId(), 2);

		OrderResponse placedOrder = orderService.checkout(account.getId(), "checkout-key-1");

		assertEquals(OrderStatus.PLACED, placedOrder.status());
		assertEquals(new BigDecimal("49.00"), placedOrder.totalAmount());
		assertEquals("Restaurant Flour", placedOrder.items().getFirst().productName());
		assertEquals(3, productRepository.findById(product.getId()).orElseThrow().getStockQuantity());
		assertTrue(cartService.get(account.getId()).items().isEmpty());

		product.update(product.getSku(), "Renamed Flour", product.getDescription(), product.getImageUrl(), product.getCategory(),
				product.getUnit(), new BigDecimal("99.00"), product.getCurrencyCode());
		productRepository.flush();
		OrderResponse retry = orderService.checkout(account.getId(), "checkout-key-1");

		assertEquals(placedOrder.id(), retry.id());
		assertEquals(new BigDecimal("49.00"), retry.totalAmount());
		assertEquals("Restaurant Flour", retry.items().getFirst().productName());
		assertEquals(new BigDecimal("24.50"), retry.items().getFirst().unitPrice());
		assertEquals(3, productRepository.findById(product.getId()).orElseThrow().getStockQuantity());
	}

	@Test
	@Transactional
	void cancellingAnUnfulfilledOrderRestoresItsStock() {
		Account account = createCustomer();
		Product product = createProduct("Kitchen Rice", "18.00", 7);
		cartService.add(account.getId(), product.getId(), 3);
		OrderResponse placedOrder = orderService.checkout(account.getId(), "checkout-key-2");

		assertEquals(4, productRepository.findById(product.getId()).orElseThrow().getStockQuantity());
		OrderResponse cancelledOrder = orderService.updateStatus(placedOrder.id(), OrderStatus.CANCELLED);

		assertEquals(OrderStatus.CANCELLED, cancelledOrder.status());
		assertEquals(7, productRepository.findById(product.getId()).orElseThrow().getStockQuantity());
	}

	@Test
	void cartRejectsQuantitiesAboveAvailableStock() {
		Account account = createCustomer();
		Product product = createProduct("Limited Stock Beans", "10.00", 1);

		assertThrows(ResponseStatusException.class,
				() -> cartService.add(account.getId(), product.getId(), 2));

		assertEquals(1, productRepository.findById(product.getId()).orElseThrow().getStockQuantity());
		assertTrue(cartService.get(account.getId()).items().isEmpty());
	}

	@Test
	void orderCannotSkipProcessingAndFailedTransitionDoesNotChangeIt() {
		Account account = createCustomer();
		Product product = createProduct("Order Lifecycle Beans", "12.00", 4);
		cartService.add(account.getId(), product.getId(), 1);
		OrderResponse placedOrder = orderService.checkout(account.getId(), "checkout-key-" + UUID.randomUUID());

		assertThrows(ResponseStatusException.class,
				() -> orderService.updateStatus(placedOrder.id(), OrderStatus.COMPLETED));

		assertEquals(OrderStatus.PLACED,
				orderService.customerOrder(account.getId(), placedOrder.id()).status());
		assertEquals(3, productRepository.findById(product.getId()).orElseThrow().getStockQuantity());
	}

	@Test
	void categoryCacheIsEvictedWhenProductIsCreated() {
		String category = "Cache-" + UUID.randomUUID();
		assertFalse(productService.categories().contains(category));
		productService.create(new ProductUpsertRequest("CACHE-TEST", "Cached Category Item", null,
				null, category, "case", new BigDecimal("12.00"), "LKR"));

		assertTrue(productService.categories().contains(category));
	}

	private Account createCustomer() {
		String email = UUID.randomUUID() + "@example.com";
		return accountRepository.saveAndFlush(new Account(email, "Test Customer", "test-hash", AccountRole.CUSTOMER));
	}

	private Product createProduct(String name, String price, int stockQuantity) {
		Product product = new Product("SKU-" + UUID.randomUUID(), name, "Test product", "Pantry", "case",
				new BigDecimal(price), "LKR");
		product.setStockQuantity(stockQuantity);
		return productRepository.saveAndFlush(product);
	}
}
