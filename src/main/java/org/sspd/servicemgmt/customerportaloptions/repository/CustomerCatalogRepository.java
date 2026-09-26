package org.sspd.servicemgmt.customerportaloptions.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Repository;
import org.sspd.servicemgmt.customerportaloptions.dto.CustomerCatalogPageDTO;
import org.sspd.servicemgmt.customerportaloptions.dto.CustomerCatalogProductDTO;
import org.sspd.servicemgmt.customerportaloptions.dto.CustomerCatalogVideoDTO;
import java.util.*;

/** Bounded scalar reads; no product/photo blobs or collection-join pagination. */
@Repository
@RequiredArgsConstructor
public class CustomerCatalogRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public CustomerCatalogPageDTO search(int page, int size, String search, Integer categoryId,
                                         Integer brandId, String type, String sort) {
        if (page < 0 || size < 1 || size > 40) throw new IllegalArgumentException("Invalid catalog page (size 1-40)");
        String order = switch (sort == null ? "name" : sort) {
            case "name" -> "p.name asc, p.id asc";
            case "price_asc" -> "p.selling_price asc, p.id asc";
            case "price_desc" -> "p.selling_price desc, p.id asc";
            case "newest" -> "p.id desc";
            default -> throw new IllegalArgumentException("Invalid catalog sort");
        };
        var args = new MapSqlParameterSource().addValue("limit", size).addValue("offset", (long) page * size);
        String from = " from products p left join categories c on c.id=p.category_id "
                + "left join categories parent on parent.id=c.parent_id left join brands b on b.id=p.brand_id ";
        StringBuilder where = new StringBuilder(" where p.archived=false ");
        if (search != null && !search.isBlank()) {
            String term = search.trim().toLowerCase(Locale.ROOT);
            if (term.length() > 120) throw new IllegalArgumentException("Search is too long");
            args.addValue("q", "%" + term.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%");
            where.append(" and (lower(p.name) like :q escape '!' or lower(p.product_code) like :q escape '!'"
                    + " or lower(b.name) like :q escape '!' or lower(c.name) like :q escape '!'"
                    + " or lower(parent.name) like :q escape '!') ");
        }
        if (categoryId != null) {
            where.append(" and (p.category_id=:category or c.parent_id=:category) ");
            args.addValue("category", categoryId);
        }
        if (brandId != null) { where.append(" and p.brand_id=:brand "); args.addValue("brand", brandId); }
        if (type != null && !type.isBlank()) {
            if (!Set.of("New", "Second").contains(type)) throw new IllegalArgumentException("Invalid product type");
            where.append("Second".equals(type) ? " and p.product_type in ('Second','Second_New') " : " and coalesce(p.product_type,'New')=:type ");
            args.addValue("type", type);
        }
        Long total = jdbc.queryForObject("select count(*)" + from + where, args, Long.class);
        var products = jdbc.query("""
                select p.id,p.name,p.product_code,p.product_type,p.selling_price,p.warranty_months,p.warranty_terms,p.remark,p.specifications,
                       p.category_id,c.name category_name,c.parent_id,parent.name parent_name,b.name brand_name,
                       p.has_serial,p.stock_qty,p.quarantined_qty,p.customer_reserved_qty,p.image_path,p.thumbnail_path
                """ + from + where + " order by " + order + " limit :limit offset :offset", args, (rs, row) -> {
            var dto = new CustomerCatalogProductDTO();
            dto.setId(rs.getInt("id")); dto.setName(rs.getString("name")); dto.setProductCode(rs.getString("product_code"));
            dto.setProductType(Objects.requireNonNullElse(rs.getString("product_type"), "New"));
            dto.setSellingPrice(rs.getBigDecimal("selling_price")); dto.setWarrantyMonths(rs.getInt("warranty_months"));
            dto.setWarrantyTerms(rs.getString("warranty_terms"));
            dto.setRemark(rs.getString("remark")); dto.setSpecifications(rs.getString("specifications"));
            dto.setCategoryId((Integer) rs.getObject("category_id"));
            dto.setCategoryName(rs.getString("category_name")); dto.setParentCategoryId((Integer) rs.getObject("parent_id"));
            dto.setParentCategoryName(rs.getString("parent_name")); dto.setBrandName(rs.getString("brand_name"));
            String image = nonblank(rs.getString("image_path"), rs.getString("thumbnail_path"));
            if (image != null) dto.setPhotoUrls(new ArrayList<>(List.of(image)));
            dto.setThumbnailUrl(nonblank(rs.getString("thumbnail_path"), image));
            return new Entry(dto, rs.getBoolean("has_serial"), rs.getInt("stock_qty"), rs.getInt("quarantined_qty"), rs.getInt("customer_reserved_qty"));
        });
        if (!products.isEmpty()) {
            var ids = products.stream().map(e -> e.dto().getId()).toList();
            var pageArgs = new MapSqlParameterSource("ids", ids);
            Map<Integer, Integer> available = new HashMap<>();
            jdbc.query("select product_id,count(*) qty from product_serials where product_id in (:ids) and status='Available' group by product_id",
                    pageArgs, (RowCallbackHandler) rs -> available.put(rs.getInt("product_id"), rs.getInt("qty")));
            Map<Integer, List<String>> photos = new HashMap<>();
            Map<Integer, String> thumbnails = new HashMap<>();
            jdbc.query("select product_id,image_path,thumbnail_path from product_photos where product_id in (:ids) order by product_id,slot,id",
                    pageArgs, (RowCallbackHandler) rs -> {
                int id = rs.getInt("product_id");
                String image = nonblank(rs.getString("image_path"), rs.getString("thumbnail_path"));
                if (image != null) {
                    var urls = photos.computeIfAbsent(id, ignored -> new ArrayList<>());
                    if (urls.size() < 3) urls.add(image);
                    thumbnails.putIfAbsent(id, nonblank(rs.getString("thumbnail_path"), image));
                }
            });
            for (var entry : products) {
                var dto = entry.dto();
                int stock = Math.max(0, (entry.serial() ? available.getOrDefault(dto.getId(), 0)
                        : Math.max(0, entry.stock() - entry.quarantined())) - entry.reserved());
                dto.setStockQty(stock); dto.setInStock(stock > 0);
                if (photos.containsKey(dto.getId())) { dto.setPhotoUrls(photos.get(dto.getId())); dto.setThumbnailUrl(thumbnails.get(dto.getId())); }
            }
        }
        attachVideos(products.stream().map(Entry::dto).toList());
        attachReviewSummary(products.stream().map(Entry::dto).toList());
        long count = total == null ? 0 : total;
        return new CustomerCatalogPageDTO(products.stream().map(Entry::dto).toList(), page, size, count, ((long) page + 1) * size < count);
    }
    public void attachVideos(List<CustomerCatalogProductDTO> products) {
        if (products.isEmpty()) return;
        var byId = new HashMap<Integer, CustomerCatalogProductDTO>();
        products.forEach(product -> byId.put(product.getId(), product));
        var args = new MapSqlParameterSource("ids", byId.keySet());
        jdbc.query("select product_id, bunny_video_guid, title, display_order from product_video " +
                "where product_id in (:ids) order by product_id, display_order, bunny_video_guid",
                args, (RowCallbackHandler) rs -> {
                    var product = byId.get(rs.getInt("product_id"));
                    if (product != null) {
                        product.getVideos().add(new CustomerCatalogVideoDTO(
                                rs.getString("bunny_video_guid"), null, "BUNNY", rs.getString("title"), rs.getInt("display_order")));
                    }
                });
        attachB2Videos(byId, args);
        attachExternalVideos(byId, args);
    }
    private void attachB2Videos(Map<Integer, CustomerCatalogProductDTO> byId,
                                MapSqlParameterSource args) {
        jdbc.query("select id, product_id, title, display_order from product_b2_video " +
                "where product_id in (:ids) order by product_id, display_order, id",
                args, (RowCallbackHandler) rs -> {
                    var product = byId.get(rs.getInt("product_id"));
                    if (product != null) {
                        product.getVideos().add(new CustomerCatalogVideoDTO(
                                null, rs.getLong("id"), "B2",
                                rs.getString("title"), rs.getInt("display_order")));
                    }
                });
    }
    private void attachExternalVideos(Map<Integer, CustomerCatalogProductDTO> byId,
                                      MapSqlParameterSource args) {
        jdbc.query("""
                select product_id, provider, provider_video_id, source_url, title, display_order
                from product_external_video
                where product_id in (:ids)
                order by product_id, display_order, id
                """, args, (RowCallbackHandler) rs -> {
            var product = byId.get(rs.getInt("product_id"));
            if (product != null) {
                product.getVideos().add(new CustomerCatalogVideoDTO(
                        null, null, rs.getString("provider"),
                        rs.getString("title"), rs.getInt("display_order"),
                        rs.getString("provider_video_id"), rs.getString("source_url")));
            }
        });
    }

    /** Fills reviewCount + reviewRating (approved only) in one batched aggregate query. */
    public void attachReviewSummary(List<CustomerCatalogProductDTO> products) {
        if (products.isEmpty()) return;
        var byId = new HashMap<Integer, CustomerCatalogProductDTO>();
        products.forEach(product -> byId.put(product.getId(), product));
        var args = new MapSqlParameterSource("ids", byId.keySet());
        jdbc.query("select product_id, count(*) cnt, avg(rating) avg_rating from product_reviews "
                + "where product_id in (:ids) and approved=1 group by product_id",
                args, (RowCallbackHandler) rs -> {
                    var product = byId.get(rs.getInt("product_id"));
                    if (product != null) {
                        product.setReviewCount(rs.getInt("cnt"));
                        java.math.BigDecimal avg = rs.getBigDecimal("avg_rating");
                        product.setReviewRating(avg == null ? null : avg.doubleValue());
                    }
                });
    }

    private static String nonblank(String preferred, String fallback) {
        if (preferred != null && !preferred.isBlank()) return preferred;
        return fallback == null || fallback.isBlank() ? null : fallback;
    }
    private record Entry(CustomerCatalogProductDTO dto, boolean serial, int stock, int quarantined, int reserved) {}
}
