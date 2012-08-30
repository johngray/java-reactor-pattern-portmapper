package ru.pmapper;

import ru.pmapper.util.properties.ProxyProperty;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.*;
import java.nio.channels.spi.SelectorProvider;

/**
 * Класс-обертка для неблокирующего серверного сокета
 * User: johngray
 * Date: 22.08.2012
 */
public class NonBlockingServerSocketProxyChannel extends SelectableChannel {

    //Канал, через который будет происходить обмен данными
    private final ServerSocketChannel serverSocketChannel;

    //Насйтроки для канала, содержит локальный пот, на котором будет идти прослушивание и хост, порт
    //с которым будет идти обмен данными
    private final ProxyProperty proxyProperty;

    /**
     * Создает неблокирующий канал серверного сокета
     * @param proxyProperty - настройка для канала, в котором содержатся: порт, который будет слушать сокет, а
     *                        также адрес (хост, порт) с которым будет происходить обмен данными
     * @throws IOException
     */
    public NonBlockingServerSocketProxyChannel(final ProxyProperty proxyProperty) throws IOException {

        this.serverSocketChannel = ServerSocketChannel.open();

        this.proxyProperty = proxyProperty;

        this.serverSocketChannel.configureBlocking(false);
        InetSocketAddress addressToBind = new InetSocketAddress(proxyProperty.getLocalPort());
        this.serverSocketChannel.socket().bind(addressToBind);
    }


    @Override
    public SelectorProvider provider() {
        return this.serverSocketChannel.provider();
    }

    @Override
    public int validOps() {
        return this.serverSocketChannel.validOps();
    }

    @Override
    public boolean isRegistered() {
        return this.serverSocketChannel.isRegistered();
    }

    @Override
    public SelectionKey keyFor(Selector sel) {
        return this.serverSocketChannel.keyFor(sel);
    }

    @Override
    public SelectionKey register(Selector sel, int ops, Object att) throws ClosedChannelException {
        return this.serverSocketChannel.register(sel, ops, att);
    }

    @Override
    public SelectableChannel configureBlocking(boolean block) throws IOException {
        return this.serverSocketChannel.configureBlocking(block);
    }

    @Override
    public boolean isBlocking() {
        return this.serverSocketChannel.isBlocking();
    }

    @Override
    public Object blockingLock() {
        return this.serverSocketChannel.blockingLock();
    }

    @Override
    protected void implCloseChannel() throws IOException {
        this.serverSocketChannel.close();
    }

    public ProxyProperty getProxyProperty() {
        return proxyProperty;
    }
}
