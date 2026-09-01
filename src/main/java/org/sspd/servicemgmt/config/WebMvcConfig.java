package org.sspd.servicemgmt.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Value("${app.apk.storage-dir}")
    private String apkStorageDir;

    @Value("${app.booking-photo.storage-dir}")
    private String bookingPhotoStorageDir;

    @Value("${app.product-photo.storage-dir}")
    private String productPhotoStorageDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = apkStorageDir.replace("\\", "/");
        if (!location.endsWith("/")) location += "/";
        registry.addResourceHandler("/app/**")
                .addResourceLocations("file:" + location);

        String bookingPhotoLocation = bookingPhotoStorageDir.replace("\\", "/");
        if (!bookingPhotoLocation.endsWith("/")) bookingPhotoLocation += "/";
        registry.addResourceHandler("/uploads/booking-photos/**")
            .addResourceLocations("file:" + bookingPhotoLocation);

        String productPhotoLocation = productPhotoStorageDir.replace("\\", "/");
        if (!productPhotoLocation.endsWith("/")) productPhotoLocation += "/";
        registry.addResourceHandler("/uploads/product-photos/**")
            .addResourceLocations("file:" + productPhotoLocation);
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/").setViewName("forward:/index.html");
        registry.addViewController("/{path:[^\\.]*}").setViewName("forward:/index.html");
    }
}
