package org.sspd.servicemgmt.stockoptions.productoptions.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.sspd.servicemgmt.stockoptions.productoptions.model.Product;


import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Integer> {


    @EntityGraph(attributePaths = {"category", "brand", "unit"})
    List<Product> findAll();



    Optional<Product> findByProductCode(String productCode);


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
}