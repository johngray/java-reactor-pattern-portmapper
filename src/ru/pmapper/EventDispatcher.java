package ru.pmapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.pmapper.util.PlatformDependent;
import ru.pmapper.util.properties.ProxyProperty;

import java.io.IOException;
import java.nio.channels.*;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/**
 * Класс, регистрирующий события принятия входящего подключения на каналы серверных сокетов и инициирующий
 * обработку этих события используя пул потоков обработчиков фиксированного размера.
 *
 * Размер пула потоков вычисляется исходя из того соображения, что наша задача не связана с
 * серьезными вычислениями на процессоре (CPU bound), а намного больше зависит от операция ввода/вывода (IO bound).
 * Тем самым, вполне можно допустить превышение числа потоков по сравнению с числом ядер процессора.
 * User: johngray
 * Date: 21.08.2012
 */
public class EventDispatcher implements Runnable {

    private static final Logger LOGGER = LoggerFactory.getLogger(EventDispatcher.class);

    //Список каналов серверных сокетов
    private final List<NonBlockingServerSocketProxyChannel> proxyChannels;

    //Селектор событий каналов
    private final Selector eventSelector;

    //Пул потоков, которые будут обрабаотывать входящие подключения
    private final ExecutorService eventHandlerPool;

    public EventDispatcher(final List<NonBlockingServerSocketProxyChannel> proxyChannels) throws IOException,
            IllegalArgumentException {

        if (proxyChannels.isEmpty()) {
            throw new IllegalArgumentException("There is no channels to listen to.");
        }

        this.proxyChannels = proxyChannels;

        this.eventHandlerPool = Executors.newFixedThreadPool(PlatformDependent.getSuitablePoolSizeForIOBoundTasks());

        this.eventSelector = this.configureListenerForAcceptEvent();

        LOGGER.info("Main event dispatcher has been successfully configured\nHit Ctrl-C to exit...");
    }


    /**
     * Открываем NIO селектор и регистрируем в нем каналы серверных сокетов
     * @return Сконфигурированный NIO селектор
     * @throws IOException
     */
    private Selector configureListenerForAcceptEvent() throws IOException {

        final Selector eventSelector = Selector.open();

        for (final NonBlockingServerSocketProxyChannel channel : this.proxyChannels) {
            final ProxyProperty proxyProperty = channel.getProxyProperty();
            LOGGER.info("Registering event listener for " + proxyProperty.getLocalPort() + " port");

            channel.register(eventSelector, SelectionKey.OP_ACCEPT, proxyProperty);
        }

        return eventSelector;
    }

    @Override
    public void run() {
        try {
            while (this.eventSelector.isOpen()) {

                this.eventSelector.select();

                final Iterator<SelectionKey> events = this.eventSelector.selectedKeys().iterator();

                while (events.hasNext()) {

                    final SelectionKey event = events.next();
                    events.remove();

                    processEvent(event);
                }
            }

        } catch (Exception e) {
            LOGGER.error("Unexpected error during event processing", e);
        } finally {
            shutdownProcessing();
        }
    }

    private void shutdownProcessing() {
        try {
            this.eventHandlerPool.shutdown();

            for (final NonBlockingServerSocketProxyChannel channel : this.proxyChannels) {
                channel.close();
            }

            this.eventSelector.close();
        } catch (Exception e) {
            LOGGER.error("Unexpected error during shutdown of the main event processing", e);
        }
    }

    /**
     * Обработка события готовности канала принять входящее подключение от клиента
     * @param event событие, уведомляющее о готовности приянть входящее соединение
     * @throws IOException
     */
    private void processEvent(final SelectionKey event) throws IOException {
        if (event.isValid()) {

            if (event.isAcceptable()) {

                final SocketChannel socketChannel = this.acceptConnection(event);
                final ProxyProperty proxyProperty = (ProxyProperty)event.attachment();

                LOGGER.info("Connection request from " + socketChannel.socket() + " has been accepted");

                this.dispatchEventHandling(socketChannel, proxyProperty);
            }
        }
    }

    /**
     * Принять входящее подключение от клиента.
     * @param event событие, уведомляющее о готовности приянть входящее соединение
     * @return канал сокета ассоциированный с принятым соединением.
     * @throws IOException
     */
    private SocketChannel acceptConnection(final SelectionKey event) throws IOException {

        final ServerSocketChannel serverSocketChannel =  (ServerSocketChannel) event.channel();

        final SocketChannel socketChannel = serverSocketChannel.accept();
        socketChannel.configureBlocking(false);

        return socketChannel;
    }

    /**
     * Обработка приянтого входящего подключения от клиента. Обрабатывает события записи-чтения между клиентом и сервером
     * @param socketChannel канал сокета ассоциированный с принятым соединением.
     * @param proxyProperty настройка для канала, в котором содержатся: порт, который будет слушать сокет
     *                      (в даном случае, нас не интересует), а также адрес (хост, порт) с которым будет
     *                      происходить обмен данными
     * @throws IOException
     */
    private void dispatchEventHandling(final SocketChannel socketChannel, final ProxyProperty proxyProperty) {

        try {
            this.eventHandlerPool.execute(new EventHandler(socketChannel, proxyProperty));
        } catch (IOException e) {

            LOGGER.error("Cannot execute the task now. Proceed to the next event", e);
        }
    }
}
