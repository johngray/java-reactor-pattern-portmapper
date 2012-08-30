package ru.pmapper.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLEngine;

/**
 * Класс, позволяющий получать параметры зависимые напрямую от используемой вычислительной машины
 * User: johngray
 * Date: 24.08.2012
 */
public final class PlatformDependent {

    private static final Logger LOGGER = LoggerFactory.getLogger(PlatformDependent.class);

    private PlatformDependent() {}

    /**
     * Возвращает размер пула потоков в зависимости от количества процессоров
     *
     * @return число - размер пула потоков
     */
    public static int getSuitablePoolSizeForIOBoundTasks() {

        int availableProcessors = Runtime.getRuntime().availableProcessors();
        int poolSize = availableProcessors * 6;

        LOGGER.info("Detected " + availableProcessors + " available processor. Suitable pool size is - " + poolSize);

        return poolSize;
    }

    public static String getLineSeparator() {
        return System.lineSeparator();
    }
}
