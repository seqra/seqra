package org.slf4j;

public class LoggerFactory {
    public static Logger getLogger(Class<?> clazz) {
      return Logger.create();
    }
}
