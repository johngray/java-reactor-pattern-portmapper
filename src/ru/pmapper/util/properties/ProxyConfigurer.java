package ru.pmapper.util.properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.pmapper.PortMapper;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Класс, управляющий загрузкой свойств из файла настроек. Было решено не использовать
 * стандартный Java класс java.util.Properties так как в нем не было возможности работы с группами настроек и
 * нужно было обрабаотывать входную информацию несколько раз
 * User: johngray
 * Date: 21.08.2012
 */
public class ProxyConfigurer {

    //Набольший номер порта
    private static final int HI_PORT_NUMBER = 65535;

    //Общий паттерн для строки а файле настроек
    private static final Pattern PROPERTY_REGEX_PATTERN
            = Pattern.compile("^\\s*([a-zA-z]+)\\.(localPort|remotePort|remoteHost)\\s*=\\s*([0-9a-zA-Z\\.-]*)\\s*$");

    private static final Logger LOGGER = LoggerFactory.getLogger(ProxyConfigurer.class);

    //Map настроек -- имя группы --> настройка
    public final Map<String, ProxyProperty> proxyProperties = new HashMap<String, ProxyProperty>();


    /**
     * Парсинг файла настроек
     * @param propertiesFilename имя файла настроек
     * @return Map настроек -- имя группы --> настройка
     */
    public Map<String, ProxyProperty> getProxyProperties(final String propertiesFilename) {

        Integer lineNumber = 1;

        try {

            final File propertiesFile = getPropertiesFile(propertiesFilename);
            final BufferedReader fileReader = new BufferedReader(new FileReader(propertiesFile));

            String line;
            while ((line = fileReader.readLine()) != null) {

                processPropertyFileLine(lineNumber, line);
                lineNumber += 1;
            }
            return this.proxyProperties;

        } catch(IOException e) {

            LOGGER.error("Configuration file is missing! Please, provide a configuration file.");

            System.exit(1);
        }

        return null;
    }

    /**
     * Возврщает объект File для файла настроек
     * @param propertiesFilename имя файла настроек
     * @return
     */
    private File getPropertiesFile(final String propertiesFilename) {

        //Ищем в директории откуда запущена программа
        File temp = new File(System.getProperty("user.dir"), propertiesFilename);
        if (!temp.exists()) {
            //Ищем в директории, где лежит jar
            temp = new File(PortMapper.class.getProtectionDomain().getCodeSource().getLocation().getFile());
        }
        return temp;
    }

    /**
     * Обработка строки файла настроек
     * @param counter счетчик строк в файле. Используется для того, чтобы показать строку в файле, в которой содержится ошибка
     * @param line строка файла настроек
     */
    private void processPropertyFileLine(final Integer counter, final String line) {
        if (!line.isEmpty() && !isComment(line)) {

            final Matcher matcher = PROPERTY_REGEX_PATTERN.matcher(line);

            if (matcher.matches()) {

                final String groupKey = matcher.group(1);
                final String propertyKey = matcher.group(2);
                final String value = matcher.group(3);

                final ProxyProperty proxyProperty =  getProxyProperty(groupKey);
                this.proxyProperties.put(groupKey, proxyProperty);

                populateProxyProperty(propertyKey, value, proxyProperty);

            } else {

                LOGGER.error("Syntax error in properties file has been detected. Line - " + counter
                        + "\n" + line);
                System.exit(1);
            }
        }
    }

    /**
     * Возвращаем настройку для последующего заполнения информации. Если такой настройки(с такой группой)
     * еще не было зарегистрировано ранее, то возвращается пустой объект настройки. Если настройка с данной группой уже есть,
     * то она возвращается из списка. Это разрешает переопределения настроек дальше по файлу и задание настроек в любом порядке
     * @param groupKey имя группы настроек (напр. web)
     * @return настройка
     */
    private ProxyProperty getProxyProperty(String groupKey) {

        if (proxyProperties.containsKey(groupKey)) {
            return proxyProperties.get(groupKey);
        } else {
            return new ProxyProperty();
        }
    }

    /**
     * Проверка, является ли строка комментарием
     * @param line строка файла настроек
     * @return true, если является. false - если нет
     */
    private boolean isComment(final String line) {
        return line.startsWith("#");
    }

    /**
     * Заполняет объект класса PropxyProperty информацией полученной после обработки строки файла настроек
     * @param propertyKey наименование настройки (localPort, remotePort, remoteHost)
     * @param value значение настройки
     * @param proxyProperty объект, в который нужно записать значение настройки
     */
    private void populateProxyProperty(final String propertyKey,
                                       final String value, final ProxyProperty proxyProperty) {

        if (propertyKey.equals("localPort")) {
            processLocalPort(value, proxyProperty);
        } else if (propertyKey.equals("remotePort")) {
            processRemotePort(value, proxyProperty);
        } else if (propertyKey.equals("remoteHost")) {
            processRemoteHost(value, proxyProperty);
        }
    }


    /**
     * Заполняет поле локального порта в объекте класса PropxyProperty
     * @param value значение настройки
     * @param proxyProperty объект, в который нужно записать значение настройки
     */
    private void processLocalPort(final String value, final ProxyProperty proxyProperty) {

        if (isValidPort(value)) {
            proxyProperty.setLocalPort(Integer.parseInt(value));
        } else {
            throw new IllegalArgumentException("Port must be a valid integer in range <= 65535");
        }
    }

    /**
     * Заполняет поле удаленного порта в объекте класса PropxyProperty
     * @param value значение настройки
     * @param proxyProperty объект, в который нужно записать значение настройки
     */
    private void processRemotePort(final String value, final ProxyProperty proxyProperty) {

        if (isValidPort(value)) {
            proxyProperty.setRemotePort(Integer.parseInt(value));
        } else {
            throw new IllegalArgumentException("Port must be a valid integer in range <= 65535");
        }
    }

    /**
     * Заполняет поле имени хоста в объекте класса PropxyProperty
     * @param value значение настройки
     * @param proxyProperty объект, в который нужно записать значение настройки
     */
    private void processRemoteHost(final String value, final ProxyProperty proxyProperty) {

        if (isValidHost(value)) {
            proxyProperty.setRemoteHost(value);
        } else {
            throw new IllegalArgumentException("Hostname is invalid");
        }
    }

    /**
     * Проверка на валидность значения порта
     * @param value строковое значение порта
     * @return true - валидация пройдена, false - валидация не пройдена
     */
    private static boolean isValidPort(final String value) {

        if (value.matches("^\\d{1,5}$")) {
            final int intValue = Integer.parseInt(value);
            return intValue <= HI_PORT_NUMBER;
        }
        return false;
    }

    /**
     * Проверка на валидность значения хост. Хост может быть как IP адресом, так и валидным доменным именем
     * @param value значение хоста
     * @return true - валидация пройдена, false - валидация не пройдена
     */
    private static boolean isValidHost(final String value) {

        final String validIpAddressRegex
                = "^(([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])\\.)"
                + "{3}([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])$";

        final String validHostnameRegex
                = "^(([a-zA-Z]|[a-zA-Z][a-zA-Z0-9-]*[a-zA-Z0-9])\\.)"
                + "*([A-Za-z]|[A-Za-z][A-Za-z0-9-]*[A-Za-z0-9])$";

        return value.matches(validHostnameRegex) || value.matches(validIpAddressRegex);
    }
}
