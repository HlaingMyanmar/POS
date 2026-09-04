package org.sspd.servicemgmt.stockoptions.productoptions.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.sspd.servicemgmt.stockoptions.productoptions.model.Product;


import java.util.List;
import java.util.Optional;
import java.math.BigDecimal;

@Repository
public interface ProductRepository extends JpaRepository<Product, Integer> {


    @EntityGraph(attributePaths = {"category", "brand", "unit", "photos"})
    List<Product> findAll();

    @EntityGraph(attributePaths = {"category", "brand", "unit", "photos"})
    Optional<Product> findWithDetailsById(Integer id);



    Optional<Product> findByProductCode(String productCode);

    Optional<Product> findByName(String name);


    boolean existsByProductCode(String productCode);


    boolean existsByName(String name);

    Optional<Product> findFirstByOrderByIdDesc();

    long countByCategoryIdAndBrandId(Long categoryId, Long brandId);



    List<Product> findByCategoryId(Integer categoryId);


    List<Product> findByBrandId(Integer brandId);


    List<Product> findByNameContainingIgnoreCase(String name);

    @org.springframework.data.jpa.repository.Query(
        "select count(p) from Product p where p.hasSerial = false and p.stockQty <= :threshold and p.stockQty >= 0")
    long countLowStock(@org.springframework.data.repository.query.Param("threshold") int threshold);

    @org.springframework.data.jpa.repository.Query(
        "select p.name from Product p where p.hasSerial = false and p.stockQty <= :threshold and p.stockQty >= 0 order by p.stockQty asc")
    List<String> findLowStockNames(@org.springframework.data.repository.query.Param("threshold") int threshold);

    @org.springframework.data.jpa.repository.Query("select coalesce(sum(coalesce(p.costPrice, 0) * coalesce(p.stockQty, 0)), 0) from Product p")
    BigDecimal sumStockValue();

    @org.springframework.data.jpa.repository.Query(
        "select p from Product p " +
        "where coalesce(p.archived, false) = false " +
        "and coalesce(p.reorderLevel, 0) > 0 " +
        "and coalesce(p.stockQty, 0) <= p.reorderLevel " +
        "order by (coalesce(p.stockQty, 0) - p.reorderLevel) asc")
    java.util.List<Product> findReorderNeeded();
}