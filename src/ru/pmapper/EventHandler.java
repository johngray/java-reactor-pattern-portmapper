package ru.pmapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.pmapper.util.PlatformDependent;
import ru.pmapper.util.properties.ProxyProperty;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.util.Iterator;

/**
 * Класс обработчик принятого соединения. Копирует данные между локальным и удаленным сокетом.
 * User: johngray
 * Date: 23.08.2012
 */
public class EventHandler implements Runnable {

    private static final Logger LOGGER = LoggerFactory.getLogger(EventHandler.class);

    //Размер буфера передачи данных по-умолчанию
    private static final int BYTE_BUFFER_DEFAULT_CAPACITY = 24588;

    //Буфер передачи данных
    private ByteBuffer transferBuffer;

    //Канал сокета, который представляет собой соединение клиент <--> маршрутизатор портов
    private final SocketChannel localSocketChannel;
    //Канал сокета, который представляет собой соединение маршрутизатор портов <--> внешний сервер
    private SocketChannel remoteSocketChannel;

    //Настройка для канала маршрутизации
    private final ProxyProperty proxyProperty;

    private final Selector eventSelector;

    /**
     * Инициализация обработчика событий, который будет обрабатывать события передачи данных через маршрутизатор портов
     * @param channel канал сокета, который представляет собой соединение клиент <--> маршрутизатор портов
     * @param property настройка для канала маршрутизации
     * @throws IOException
     */
    public EventHandler(final SocketChannel channel, final ProxyProperty property) throws IOException {

        this.proxyProperty = property;

        this.localSocketChannel = channel;
        this.remoteSocketChannel = configureAndGetOutputChannel();

        this.eventSelector = Selector.open();

        //Воспользуемся возможностью использовать нативный ввод/вывод, используя direct буфер
        this.transferBuffer = ByteBuffer.allocateDirect(getApplicationBufferSize());
    }

    private int getApplicationBufferSize() {
        return BYTE_BUFFER_DEFAULT_CAPACITY;
    }


    @Override
    public void run() {

        try {
            this.remoteSocketChannel.register(this.eventSelector, SelectionKey.OP_CONNECT);

            while (this.eventSelector.isOpen() && this.eventSelector.select() > 0) {

                final Iterator<SelectionKey> events = this.eventSelector.selectedKeys().iterator();

                while (events.hasNext()) {

                    final SelectionKey event = events.next();
                    events.remove();

                    processEvent(event);
                }
            }

            LOGGER.debug("End serving port mapping for " + this.remoteSocketChannel.socket());
        } catch (IOException e) {

            LOGGER.error("Unexpected error during event processing", e);
        } finally {
            shutdownProcessing();
        }
    }

    private void shutdownProcessing() {
        try {
            this.localSocketChannel.close();
            this.remoteSocketChannel.close();
            this.eventSelector.close();
        } catch (Exception e) {
            LOGGER.error("Unexpected error during shutdown of event processing", e);
        }
    }


    /**
     * Возврщает сконфигурированный канал сокета, который представляет собой соединение маршрутизатор портов <--> внешний сервер
     * @return сконфигурированный канал сокета
     * @throws IOException
     */
    private SocketChannel configureAndGetOutputChannel() throws IOException {

        final InetSocketAddress outputAddress
                = new InetSocketAddress(this.proxyProperty.getRemoteHost(), this.proxyProperty.getRemotePort());

        final SocketChannel outputChannel = SocketChannel.open();
        outputChannel.configureBlocking(false);
        outputChannel.connect(outputAddress);

        return outputChannel;
    }

    /**
     * Обработка события чтения из какого-либо канала сокетов
     * @param event событие
     * @throws IOException
     */
    private void processEvent(final SelectionKey event) throws IOException {

        if (event.isValid()) {
            final SocketChannel tmp = (SocketChannel) event.channel();

            if (event.isConnectable()){
                finishSocketChannelConnection(event, tmp);
            } else if(event.isReadable()) {
                LOGGER.debug("A valid readable event from " + tmp.socket() + " has been registered");

                final boolean exchangeResult = exchangeDataBetweenChannels(tmp);

                if (!exchangeResult) {

                    tmp.close();
                    event.cancel();
                    event.selector().close();
                }
            }
        }
    }

    /**
     * Обрабатываем событие готовности к завершению соединения с удаленным сервером
     * @param event событие заверешения соединения
     * @param tmp канал сокета, который вызвал событие
     * @throws IOException
     */
    private void finishSocketChannelConnection(final SelectionKey event, final SocketChannel tmp) throws IOException {
        LOGGER.debug("A valid connectable event from " + tmp.socket() + " has been registered");

        tmp.finishConnect();
        this.localSocketChannel.register(event.selector(), SelectionKey.OP_READ);
        event.interestOps(SelectionKey.OP_READ);

        LOGGER.debug(tmp + " was connected to remote entity");
    }


    /**
     * Передать данные между двумя каналами в обе стороны
     * @param tmp канал, вызвавший событие чтения
     * @return результат операции
     */
    private boolean exchangeDataBetweenChannels(final SocketChannel tmp) {

        if (tmp == this.localSocketChannel) {
            return this.transferData(tmp, this.remoteSocketChannel);
        } else if (tmp == this.remoteSocketChannel) {
            return this.transferData(tmp, this.localSocketChannel);
        }

        return false;
    }


    /**
     * Передает данные между двумя каналами
     * @return результат операции
     * @param inputChannel канал источник
     * @param outputChannel канал приемник
     */
    private boolean transferData(final SocketChannel inputChannel, final SocketChannel outputChannel) {

        int bytesRead;

        try {
            this.transferBuffer.clear();

            do {

                bytesRead = inputChannel.read(this.transferBuffer);

                if (bytesRead == -1) {
                    logGracefulShutdown(inputChannel);
                    return false;
                }

                this.transferBuffer.flip();
                outputChannel.write(this.transferBuffer);
                this.transferBuffer.compact();

            } while (bytesRead > 0);

            return true;

        } catch (IOException e) {
            logForceShutDown(inputChannel, e);
            return false;
        }
    }


    private void logForceShutDown(SocketChannel inputChannel, IOException e) {
        LOGGER.warn(inputChannel.socket() + " has been closed forcibly", e);
    }

    private void logGracefulShutdown(SocketChannel inputChannel) {
        LOGGER.info(inputChannel.socket() + " has been closed gracefully");
    }
}
