package com.djomkam.catalogsense.ingestion.validation;

import com.djomkam.catalogsense.ingestion.ProductPayload;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.List;

/** Syntax-only check. A later phase can verify that the URL actually responds. */
@Component
@Order(4)
public class ImageUrlRule implements ValidationRule {

    private static final String FIELD = "imageUrl";

    @Override
    public RuleId id() {
        return RuleId.IMAGE_URL;
    }

    @Override
    public List<Violation> validate(ProductPayload product) {
        String imageUrl = product.imageUrl();
        if (imageUrl == null || imageUrl.isBlank()) {
            return List.of(warning(FIELD, "No image. Products with images get far more clicks; add one."));
        }
        URI uri;
        try {
            uri = URI.create(imageUrl.trim());
        } catch (IllegalArgumentException e) {
            return List.of(warning(FIELD, "Image URL '" + imageUrl + "' is malformed."));
        }
        if (!isHttpUrl(uri)) {
            return List.of(warning(FIELD, "Image URL '" + imageUrl + "' is not an http(s) URL."));
        }
        if ("http".equalsIgnoreCase(uri.getScheme())) {
            return List.of(warning(FIELD, "Image URL uses http; browsers may block it on https storefronts."));
        }
        return List.of();
    }

    private static boolean isHttpUrl(URI uri) {
        String scheme = uri.getScheme();
        return uri.getHost() != null && ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme));
    }
}
