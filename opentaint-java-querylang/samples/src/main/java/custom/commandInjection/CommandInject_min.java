package custom.commandInjection;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.regex.Pattern;

@RestController
public class CommandInject_min {
    private static final Pattern FILTER_PATTERN = Pattern.compile("^[a-zA-Z0-9_/\\.-]+$");

    public static String cmdFilter(String input) {
        if (!FILTER_PATTERN.matcher(input).matches()) {
            return null;
        }
        return input;
    }

    @GetMapping("/codeinject")
    public String codeInject(String filepath) {
        String result = "";
        try {
            String[] cmdList = new String[]{"sh", "-c", "ls -la " + filepath};
            ProcessBuilder builder = new ProcessBuilder(cmdList);
            Process process = builder.start();
        }
        finally {
          return result;
        }
    }

    @GetMapping("/codeinject/sec")
    public String codeInjectSec(String filepath) {
        String result = "";
        try {
            String filterFilePath = cmdFilter(filepath);
            if (null == filterFilePath) {
                return "Filtered";
            }
            String[] cmdList = new String[]{"sh", "-c", "ls -la " + filterFilePath};
            ProcessBuilder builder = new ProcessBuilder(cmdList);
            Process process = builder.start();
        }
        finally {
            return result;
        }
    }
}
