package alien4cloud.brooklyn;

import javax.validation.constraints.NotNull;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import alien4cloud.ui.form.annotation.FormLabel;
import alien4cloud.ui.form.annotation.FormProperties;
import alien4cloud.ui.form.annotation.FormPropertyConstraint;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@FormProperties({ "url", "user", "password" })
public class Configuration {

    @FormLabel("Brooklyn URL")
    @FormPropertyConstraint(pattern = "http\\:.+(?:\\d+)")
    @NotNull
    private String url = "http://localhost:8081/";
    
    @FormLabel("Brooklyn user")
    private String user = "brooklyn";
    
    @FormLabel("Brooklyn password")
    private String password = "brooklyn";
    
}
