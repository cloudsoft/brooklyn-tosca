package alien4cloud.brooklyn;

import javax.validation.constraints.NotNull;

import alien4cloud.ui.form.annotation.FormPropertyConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * Configuration for the connection to an Apache Brooklyn Cloud.
 */
@Getter
@Setter
public class Configuration {
    @FormPropertyConstraint(pattern = "http\\:.+(?:\\d+)")
    @NotNull
    private String url = "http://localhost:8888";
    private String username = "brooklyn";
    private String password = "kktkecBomw";
}
