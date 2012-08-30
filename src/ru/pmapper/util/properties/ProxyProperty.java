package ru.pmapper.util.properties;

/**
 * Настройка для прокси канала в которой содержится локальный порт, который будет слушать серверный канал,
 * а также адрес с которым будет проходить обмен данными
 * User: johngray
 * Date: 22.08.2012
 */
public class ProxyProperty {

    private int localPort;

    private int remotePort;

    private String remoteHost;

    public ProxyProperty() {
    }

    public ProxyProperty(int localPort, int remotePort, String remoteHost) {
        this.localPort = localPort;
        this.remotePort = remotePort;
        this.remoteHost = remoteHost;
    }

    public int getLocalPort() {
        return localPort;
    }

    public void setLocalPort(int localPort) {
        this.localPort = localPort;
    }

    public int getRemotePort() {
        return remotePort;
    }

    public void setRemotePort(int remotePort) {
        this.remotePort = remotePort;
    }

    public String getRemoteHost() {
        return remoteHost;
    }

    public void setRemoteHost(String remoteHost) {
        this.remoteHost = remoteHost;
    }

    @Override
    public String toString() {
        return "ProxyProperty{" +
                "localPort=" + localPort +
                ", remotePort=" + remotePort +
                ", remoteHost='" + remoteHost + '\'' +
                '}';
    }
}
