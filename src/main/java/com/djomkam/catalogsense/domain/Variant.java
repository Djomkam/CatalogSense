package com.djomkam.catalogsense.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "variant")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(exclude = "product")
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Variant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Setter(AccessLevel.NONE)
    @EqualsAndHashCode.Include
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id")
    @Setter(AccessLevel.PACKAGE)
    private Product product;

    @Column(nullable = false)
    private String sku;

    private String color;
    private String size;

    /** Optional override of the product price. */
    private BigDecimal price;

    @Column(name = "in_stock", nullable = false)
    private boolean inStock;

    public Variant(String sku, String color, String size, BigDecimal price, boolean inStock) {
        this.sku = sku;
        this.color = color;
        this.size = size;
        this.price = price;
        this.inStock = inStock;
    }
}
