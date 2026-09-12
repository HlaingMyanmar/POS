package org.sspd.servicemgmt.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.sspd.servicemgmt.customerportaloptions.repository.CustomerCatalogRepository;
import org.sspd.servicemgmt.support.AbstractMysqlIntegrationTest;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("it")
@Import(CustomerCatalogRepository.class)
class CustomerCatalogIT extends AbstractMysqlIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired CustomerCatalogRepository catalog;
    private String prefix;
    private int root, child, brand;

    @BeforeEach
    void fixture() {
        prefix = "catalog-" + UUID.randomUUID().toString().substring(0, 8);
        jdbc.update("insert into categories(name,is_active) values (?,true)", prefix);
        root = jdbc.queryForObject("select id from categories where name=?", Integer.class, prefix);
        jdbc.update("insert into categories(name,is_active,parent_id) values (?,true,?)", prefix + "-child", root);
        child = jdbc.queryForObject("select id from categories where name=?", Integer.class, prefix + "-child");
        jdbc.update("insert into brands(name,is_active) values (?,true)", prefix);
        brand = jdbc.queryForObject("select id from brands where name=?", Integer.class, prefix);
        for (int i = 0; i < 50; i++) {
            jdbc.update("""
                insert into products(name,product_code,product_type,archived,has_serial,stock_qty,
                    quarantined_qty,customer_reserved_qty,selling_price,category_id,brand_id)
                values (?,?, 'New',false,false,10,2,3,?,?,?)
                """, prefix + String.format("-%03d", i), "CAT-" + i, i / 2, i % 2 == 0 ? root : child, brand);
        }
    }

    @Test
    void pagesAreBoundedStableAndFilterAcrossChildren() {
        var first = catalog.search(0, 24, null, root, brand, null, "price_asc");
        var second = catalog.search(1, 24, null, root, brand, null, "price_asc");
        var last = catalog.search(2, 24, null, root, brand, null, "price_asc");
        assertEquals(50, first.totalElements()); assertEquals(24, first.content().size());
        assertTrue(first.hasNext()); assertEquals(2, last.content().size()); assertFalse(last.hasNext());
        Set<Integer> ids = new HashSet<>();
        first.content().forEach(p -> assertTrue(ids.add(p.getId())));
        second.content().forEach(p -> assertTrue(ids.add(p.getId())));
        last.content().forEach(p -> assertTrue(ids.add(p.getId())));
        assertEquals(50, ids.size());
        assertEquals(25, catalog.search(0, 24, null, child, brand, null, "name").totalElements());
        assertEquals(0, catalog.search(0, 24, null, root, -1, null, "name").totalElements());
        assertThrows(IllegalArgumentException.class, () -> catalog.search(-1, 24, null, null, null, null, "name"));
        assertThrows(IllegalArgumentException.class, () -> catalog.search(0, 41, null, null, null, null, "name"));
        assertThrows(IllegalArgumentException.class, () -> catalog.search(0, 24, null, null, null, null, "name; drop table products"));
    }

    @Test
    void searchEscapesWildcardsAndExcludesArchivedProducts() {
        int id = jdbc.queryForObject("select id from products where name=?", Integer.class, prefix + "-000");
        jdbc.update("update products set name=?, product_code=? where id=?", prefix + "-100%_off", "TEST_%", id);
        assertEquals(1, catalog.search(0,24,"100%_",root,null,null,"name").totalElements());
        assertEquals(1, catalog.search(0,24,"test_%",root,null,null,"name").totalElements());
        jdbc.update("update products set archived=true where id=?", id);
        assertEquals(0, catalog.search(0,24,"100%_",root,null,null,"name").totalElements());
        assertEquals(49, catalog.search(0,24,null,root,null,"New","newest").totalElements());
        assertEquals(0, catalog.search(0,24,null,root,null,"Second","name").totalElements());
    }

    @Test
    void preservesStockReservationsAndUsesThumbnailsWithoutPhotoBlobs() {
        int id = jdbc.queryForObject("select id from products where name=?", Integer.class, prefix + "-000");
        jdbc.update("update products set has_serial=true,customer_reserved_qty=1 where id=?", id);
        for (int i=0;i<4;i++) jdbc.update("insert into product_serials(serial_number,status,product_id) values (?,?,?)",
                prefix + "-SN-" + i, i < 3 ? "Available" : "Sold", id);
        jdbc.update("insert into product_photos(product_id,slot,image_path,thumbnail_path,data_url) values (?,1,?,?,?)",
                id, "/full.jpg", "/thumb.jpg", "large-legacy-blob");
        var result = catalog.search(0,24,null,root,brand,null,"name");
        var serial = result.content().stream().filter(p -> p.getId() == id).findFirst().orElseThrow();
        assertEquals(2, serial.getStockQty()); assertTrue(serial.isInStock());
        assertEquals("/thumb.jpg", serial.getThumbnailUrl()); assertEquals(List.of("/full.jpg"), serial.getPhotoUrls());
        var regular = result.content().stream().filter(p -> p.getId() != id).findFirst().orElseThrow();
        assertEquals(5, regular.getStockQty());
    }

    private long selects() {
        return Long.parseLong(jdbc.queryForMap("SHOW SESSION STATUS LIKE 'Com_select'").get("Value").toString());
    }

    @Test
    void queryCountStaysConstantAndRecordsTimingAndPlans() {
        var rows = new ArrayList<Object[]>();
        for (int i=0;i<3000;i++) rows.add(new Object[]{prefix + "-bulk-" + i, "B-" + i, root, brand, i});
        jdbc.batchUpdate("""
            insert into products(name,product_code,category_id,brand_id,selling_price,archived,has_serial,
                stock_qty,quarantined_qty,customer_reserved_qty)
            values (?,?,?,?,?,false,false,10,0,0)
            """, rows);
        for (int size : List.of(24,40)) {
            long before = selects();
            var result = catalog.search(0,size,null,root,brand,null,"price_asc");
            assertEquals(4, selects() - before, "Catalog must use four SELECTs regardless of page size");
            assertEquals(size, result.content().size()); assertEquals(3050, result.totalElements());
        }
        List<Double> millis = new ArrayList<>();
        for (int i=0;i<12;i++) {
            long start=System.nanoTime();
            catalog.search(0,24,null,root,brand,null,"price_asc");
            if(i>=2) millis.add((System.nanoTime()-start)/1_000_000.0);
        }
        Collections.sort(millis);
        System.out.printf(Locale.ROOT,"CATALOG_BENCH rows=3050 page=24 selects=4 p50=%.2fms p95=%.2fms%n",
                millis.get(5),millis.get(9));
        System.out.println("CATALOG_PRICE_PLAN " + jdbc.queryForList(
                "EXPLAIN select id,name,selling_price from products where archived=false order by selling_price,id limit 24"));
        System.out.println("CATALOG_SERIAL_PLAN " + jdbc.queryForList(
                "EXPLAIN select product_id,count(*) from product_serials where product_id in (1,2,3) and status='Available' group by product_id"));
    }
}
