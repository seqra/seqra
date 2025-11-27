package org.slf4j;

import javax.servlet.http.HttpServletRequest;

public interface Logger {
    public void info(String msg);

    static Logger create() {
        return new Logger.Impl();
    }

    class Impl implements Logger {
        @Override
        public void info(String msg) {
        }
    }
}
