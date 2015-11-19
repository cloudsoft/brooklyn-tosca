package alien4cloud.brooklyn;

import javax.validation.constraints.NotNull;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import alien4cloud.ui.form.annotation.FormLabel;
import alien4cloud.ui.form.annotation.FormProperties;
import alien4cloud.ui.form.annotation.FormPropertyConstraint;
import alien4cloud.ui.form.annotation.FormPropertyDefinition;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@FormProperties({ "url", "user", "password", "location", "providers" })
public class Configuration {

    @FormLabel("Brooklyn URL")
    @FormPropertyConstraint(pattern = "http\\:.+(?:\\d+)")
    @NotNull
    private String url;

    @FormLabel("Brooklyn User")
    private String user;

    @FormLabel("Brooklyn Password")
    @FormPropertyDefinition(type = "string", isPassword = true)
    private String password;

    @FormLabel("Default Brooklyn Location")
    private String location;

    @FormLabel("Tosca Metadata Providers")
    private List<String> providers;
}
