package com.example.demo.service;

import com.example.demo.dto.ProductPageResponse;
import com.example.demo.dto.ProductResponse;
import com.example.demo.dto.ProductUpsertRequest;
import com.example.demo.model.Product;
import com.example.demo.repo.ProductRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class ProductService {

    private static final String ACTIVE_PRODUCT_CATEGORIES_CACHE = "active-product-categories";

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Transactional(readOnly = true)
    public ProductPageResponse browse(String query, String category, int page, int size) {
        return search(query, category, false, null, page, size);
    }

    @Transactional(readOnly = true)
    public ProductPageResponse manage(String query, String category, Boolean active, int page, int size) {
        return search(query, category, true, active, page, size);
    }

    @Transactional(readOnly = true)
    public ProductResponse getActiveProduct(UUID id) {
        return productRepository.findById(id)
                .filter(Product::isActive)
                .map(ProductResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found."));
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = ACTIVE_PRODUCT_CATEGORIES_CACHE)
    public List<String> categories() {
        return productRepository.findActiveCategories();
    }

    @Transactional
    @CacheEvict(cacheNames = ACTIVE_PRODUCT_CATEGORIES_CACHE, allEntries = true)
    public ProductResponse create(ProductUpsertRequest request) {
        String sku = normalizeSku(request.sku());
        if (productRepository.existsBySku(sku)) {
            throw new DuplicateSkuException();
        }
        try {
            return ProductResponse.from(productRepository.saveAndFlush(new Product(sku, request.name(),
                    request.description(), request.category(), request.unit(), request.price(), request.currencyCode())));
        } catch (DataIntegrityViolationException exception) {
            throw new DuplicateSkuException();
        }
    }

    @Transactional
    @CacheEvict(cacheNames = ACTIVE_PRODUCT_CATEGORIES_CACHE, allEntries = true)
    public ProductResponse update(UUID id, ProductUpsertRequest request) {
        Product product = findProductForUpdate(id);
        String sku = normalizeSku(request.sku());
        if (productRepository.existsBySkuAndIdNot(sku, id)) {
            throw new DuplicateSkuException();
        }
        product.update(sku, request.name(), request.description(), request.category(), request.unit(),
                request.price(), request.currencyCode());
        try {
            return ProductResponse.from(productRepository.saveAndFlush(product));
        } catch (DataIntegrityViolationException exception) {
            throw new DuplicateSkuException();
        }
    }

    @Transactional
    @CacheEvict(cacheNames = ACTIVE_PRODUCT_CATEGORIES_CACHE, allEntries = true)
    public void deactivate(UUID id) {
        Product product = findProductForUpdate(id);
        product.deactivate();
    }

    @Transactional
    @CacheEvict(cacheNames = ACTIVE_PRODUCT_CATEGORIES_CACHE, allEntries = true)
    public void setActive(UUID id, boolean active) {
        Product product = findProductForUpdate(id);
        product.setActive(active);
    }

    @Transactional
    public ProductResponse setStock(UUID id, int quantity) {
        Product product = productRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found."));
        product.setStockQuantity(quantity);
        return ProductResponse.from(product);
    }

    private ProductPageResponse search(String query, String category, boolean includeInactive,
                                       Boolean active, int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Page must be non-negative and size must be between 1 and 100.");
        }

        Specification<Product> specification = (root, criteriaQuery, criteriaBuilder) -> criteriaBuilder.conjunction();
        if (!includeInactive) {
            specification = specification.and((root, criteriaQuery, criteriaBuilder) ->
                    criteriaBuilder.isTrue(root.get("active")));
        }
        if (active != null) {
            specification = specification.and((root, criteriaQuery, criteriaBuilder) ->
                    criteriaBuilder.equal(root.get("active"), active));
        }
        if (category != null && !category.isBlank()) {
            String normalizedCategory = category.trim().toLowerCase(Locale.ROOT);
            specification = specification.and((root, criteriaQuery, criteriaBuilder) ->
                    criteriaBuilder.equal(criteriaBuilder.lower(root.get("category")), normalizedCategory));
        }
        if (query != null && !query.isBlank()) {
            String pattern = "%" + query.trim().toLowerCase(Locale.ROOT) + "%";
            specification = specification.and((root, criteriaQuery, criteriaBuilder) -> criteriaBuilder.or(
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("name")), pattern),
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("sku")), pattern),
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("description")), pattern),
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("category")), pattern)));
        }

        Page<ProductResponse> result = productRepository.findAll(specification,
                        PageRequest.of(page, size, Sort.by(Sort.Order.asc("name"), Sort.Order.asc("id"))))
                .map(ProductResponse::from);
        return ProductPageResponse.from(result);
    }

    private Product findProductForUpdate(UUID id) {
        return productRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found."));
    }

    private String normalizeSku(String sku) {
        return sku.trim().toUpperCase(Locale.ROOT);
    }
}
