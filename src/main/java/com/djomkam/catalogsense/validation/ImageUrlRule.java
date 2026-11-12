package com.djomkam.catalogsense.validation;

import com.djomkam.catalogsense.api.ProductPayload;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.List;

/** Syntax-only check. A later phase can verify that the URL actually responds. */
@Component
public class ImageUrlRule implements ValidationRule {

    @Override
    public String name() {
        return "IMAGE_URL";
    }

    @Override
    public List<Violation> validate(ProductPayload p) {
        String url = p.imageUrl();
        if (url == null || url.isBlank()) {
            return List.of(Violation.warning(name(), "imageUrl",
                    "No image. Products with images get far more clicks; add one."));
        }
        try {
            URI uri = URI.create(url.trim());
            String scheme = uri.getScheme();
            if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https")) || uri.getHost() == null) {
                return List.of(Violation.warning(name(), "imageUrl", "Image URL '" + url + "' is not an http(s) URL."));
            }
            if (scheme.equalsIgnoreCase("http")) {
                return List.of(Violation.warning(name(), "imageUrl", "Image URL uses http; browsers may block it on https storefronts."));
            }
            return List.of();
        } catch (IllegalArgumentException e) {
            return List.of(Violation.warning(name(), "imageUrl", "Image URL '" + url + "' is malformed."));
        }
    }
}
