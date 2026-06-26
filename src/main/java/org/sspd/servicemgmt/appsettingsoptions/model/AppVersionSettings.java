package org.sspd.servicemgmt.appsettingsoptions.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "app_version_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppVersionSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "version_code", nullable = false)
    @Builder.Default
    private Integer versionCode = 5;

    @Column(name = "version_name", nullable = false, length = 50)
    @Builder.Default
    private String versionName = "1.2.0";

    @Column(name = "force_update", nullable = false)
    @Builder.Default
    private boolean forceUpdate = false;

    @Column(name = "changelog", length = 2000)
    @Builder.Default
    private String changelog = "";
}
