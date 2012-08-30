package ru.pmapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.pmapper.util.properties.ProxyConfigurer;
import ru.pmapper.util.properties.ProxyProperty;

import java.io.IOException;
import java.net.BindException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Основная точка входа в приложение маршрутизатора портов. Проводит инициализацию приложения и стартует серверные потоки.
 * User: johngray
 * Date: 8.21.2012
**/
public class PortMapper {

    private static final Logger LOGGER = LoggerFactory.getLogger(PortMapper.class);

    public static void main(final String[] args) throws Exception {

        ProxyConfigurer proxyConfigurer = new ProxyConfigurer();

        final Map<String, ProxyProperty> proxyPropertyMap = proxyConfigurer.getProxyProperties("proxy.properties");

        final List<NonBlockingServerSocketProxyChannel> channels = configureProxyChannels(proxyPropertyMap);

        try {

            final Thread eventDispatcherThread = new Thread(new EventDispatcher(channels));
            eventDispatcherThread.start();
            eventDispatcherThread.join();
        } catch (Exception e) {
            LOGGER.error("An unrecoverable error occurred during listening. Shutdown all.", e);
        }
    }

    /**
     * Конфигурирует каналы серверных сокетов, которые будут слушать входящие соединения на порты
     * @param proxyPropertyMap список настроек для сокетов, в которых содержатся: порт, который будет слушать сокет, а
     *                         также адрес (хост, порт) с которым будет происходить обмен данными
     * @return список сконфигурировнных каналов серверных сокетов
     * @throws IOException
     */
    private static List<NonBlockingServerSocketProxyChannel> configureProxyChannels(final Map<String, ProxyProperty> proxyPropertyMap)
            throws IOException {

        final List<NonBlockingServerSocketProxyChannel> channels = new ArrayList<NonBlockingServerSocketProxyChannel>(2);

        for (final String channelName: proxyPropertyMap.keySet()) {

            addChannel(channels, proxyPropertyMap.get(channelName));
        }
        return channels;
    }

    /**
     * Создает канал серверного сокета, который будет слушать входящие подключения и добавляет его в
     * список сконфигурированных каналов.
     * @param channels - список каналов, в который будет добавлен новый канал
     * @param proxyProperty - настройка для канала, в котором содержатся: порт, который будет слушать сокет, а
     *                        также адрес (хост, порт) с которым будет происходить обмен данными
     */
    private static void addChannel(final List<NonBlockingServerSocketProxyChannel> channels,
                                   final ProxyProperty proxyProperty){

        try {
            channels.add(new NonBlockingServerSocketProxyChannel(proxyProperty));
        } catch (BindException e) {

            LOGGER.warn(proxyProperty.getLocalPort() + " is already in use. Port mapper will not listen on this port");
        } catch (IOException e) {

            LOGGER.error("An IO error occurred during initialization of listening on port "
                    + proxyProperty.getLocalPort() + " . Port mapper will not listen on this port", e);
        }
    }
}
