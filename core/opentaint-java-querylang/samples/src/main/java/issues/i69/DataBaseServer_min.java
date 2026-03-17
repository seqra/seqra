package issues.i69;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
public class DataBaseServer_min {

    @GetMapping(value = "/resetdb")
    public String get() {
        return "ok";
    }

    @PostMapping(value = "/testdb")
    public String post() {
        return "ok";
    }
}
