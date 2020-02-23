package ru.home.grpc.chat.client.shell.commands;

import io.grpc.ConnectivityState;
import io.grpc.StatusRuntimeException;
import org.jline.reader.UserInterruptException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;
import org.springframework.util.StringUtils;
import ru.geekbrains.dreamworkerln.spring2.shell_lib.shell.InputReader;
import ru.geekbrains.dreamworkerln.spring2.shell_lib.shell.ShellHelper;
import ru.home.grpc.chat.ServerMessage;
import ru.home.grpc.chat.client.service.ChatClient;
import ru.home.grpc.chat.client.utils.UnauthenticatedException;

import javax.annotation.PostConstruct;
import java.lang.invoke.MethodHandles;
import java.net.URI;
import java.util.AbstractMap;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@ShellComponent
public class ClientCommands implements ClientEvents {

    private final static Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private ShellHelper shellHelper;
    private InputReader inputReader;
    private ChatClient client;

    //private String padding;
    private Map<Integer,URI> hostList;

    private boolean interrupted;


    @Autowired
    public void setShellHelper(ShellHelper shellHelper) {
        this.shellHelper = shellHelper;
    }
    @Autowired
    public void setInputReader(InputReader inputReader) {
        this.inputReader = inputReader;
    }
    @Autowired
    public void setClient(ChatClient client) {
        this.client = client;
    }

    @ShellMethod("Connect to server.")
    public void connect(@ShellOption(defaultValue = ShellOption.NULL)String host) {

        String login;
        String password;
        URI server;

        try {
            server = getHostByNumber(host);

            if (server == null) {
                server = getHostByString(host);
            }

            // Тут не оставит пользователя в покое, пока не введет правильно или забьет
            if (server == null) {
                server = getHostFromTable();
            }

            // get login/password -------------------------------------------------

            login = "1";
            password ="1";

            login = inputReader.prompt("login: ");
            password = inputReader.prompt("password: ", "secret", false);



            // Synchronous connecting  ------------------------------------------

            shellHelper.printInfo("Connecting to server " + printURI(server));

            client.init(server.getHost(), server.getPort(), login, password);

            client.connect();

        }
        // handling gRPC Errors
        catch (StatusRuntimeException e) {
            shellHelper.printError(e.getStatus().getCode().name());
            //log.info("gRPC Auth error", e);
        }
        catch (UnauthenticatedException e) {
            shellHelper.printError(e.getMessage());
            //log.info("gRPC Auth error", e);
        }
        // handling Ctrl+C
        catch (UserInterruptException e) {
            System.out.println("^C");
            interrupted = true;
        }
        // ?????
        catch (Exception e) {
            log.info("STRANGE ERROR ???", e);
        }

        // exit chat if have errors ---------------------------------------------------------------

        if(!client.isOnline()) {
            interrupted = false;
            //client.shutdownNow();
            return;
        }

        // -------------------------------------------------------------------------------------------
        // Async chatting ----------------------------------------------------------------------------


        System.out.println("Chat is online");

        // Async chatting -------------------------------------------------------------------
        try {

            StringBuilder undelivered = new StringBuilder();

            //noinspection InfiniteLoopStatement
            while (true) {
                String message = inputReader.prompt();

                if (client.isOnline()) {

                    if(undelivered.length() > 0) {
                        // send undelivered messages
                        client.sendMessage(undelivered.toString());
                        undelivered.setLength(0);
                    }
                    // send current message
                    client.sendMessage(message);
                }
                else {
                    // keep undelivered message
                    undelivered.append(message).append("\n");
                }
            }
        }
        // handling gRPC Errors
        catch (StatusRuntimeException e){
            System.out.println("ОПАОПАОПА ОПА ОПА ОПА ОПА ОПА ОПА ОПА ПА ПА!!!");
            shellHelper.printError(e.getStatus().getCode().name());
            log.error("gRPC error", e);
        }
        // handling Ctrl+C
        catch (UserInterruptException e){
            System.out.println("^C");
            interrupted = true;
        }
        catch (Exception e) {
            log.error("gRPC error", e);
        }
        finally {
            client.shutdownNow();
        }
    }

    // -------------------------------------------------------------


//    @ShellMethod("Disconnect from server.")
//    public void disconnect() throws InterruptedException {
//        client.shutdown();
//    }


    @Override
    public void onMessage(ServerMessage message)  {
        System.out.println(message.getFrom() + ": " + message.getMessage());
    }


    @Override
    public void onError(Throwable t) {

        if (t instanceof StatusRuntimeException && !interrupted) {
            shellHelper.printError(((StatusRuntimeException)t).getStatus().getCode().name());
        }
    }

//    @Override
//    public void onReconnect() {
//        shellHelper.printInfo("Reconnecting to server ... " + printURI(server));
//    }


    @Override
    public void onStateChanged(ConnectivityState previous, ConnectivityState current) {

    }








    @PostConstruct
    protected void postConstruct() {

        // Fill hosts list
        Map<Integer,String> tmp = new HashMap<>();
        tmp.put(1, "localhost");
        tmp.put(2, "ya.ru");

        hostList =
            tmp.entrySet()
                .stream().map(e -> new AbstractMap.SimpleEntry<>(e.getKey(),
                tryGetURI(e.getValue() + ":" + ChatClient.DEFAULT_PORT)))
                .filter(e -> e.getValue() != null)
                .collect(Collectors.toMap(AbstractMap.SimpleEntry::getKey, AbstractMap.SimpleEntry::getValue));
    }



    // ===========================================================================================


    private URI getHostByNumber(String value) {

        URI result = null;

        Integer index = tryGetInteger(value);

        if (hostList.containsKey(index)) {
            result = hostList.get(index);
        }
        return result;
    }




    private URI getHostByString(String host) {



        URI result;

        // empty host
        if (StringUtils.isEmpty(host)) {
            return null;
        }

        // user added port
        result = tryGetURI(host);

        // default port
        if (result == null) {
            result = tryGetURI(host + ":" + ChatClient.DEFAULT_PORT);
        }
        return result;
    }


    private URI getHostFromTable() {

        URI result = null;
        // display available hosts -----------------------------------------


        for (Map.Entry<Integer, URI> entry : hostList.entrySet()) {
            shellHelper.printInfo(
                entry.getKey() + " - " +
                    entry.getValue().getHost() + ":" + entry.getValue().getPort());
        }


        // Until user select correct host -----------------------------------
        while (true) {
            String value = inputReader.prompt("Select host [Number]: ");

            try {
                if((result = getHostByNumber(value))!= null) {
                    break;
                }
            }
            catch (Exception ignore) {}

            shellHelper.printWarning("Invalid value");
        }
        return result;
    }



    private URI tryGetURI(String host) {

        URI result = null;

        try {
            result = new URI("grpc://" + host);

            if (result.getPort() == -1) {
                result = null;
            }
        }
        catch (Exception ignore) {}


        return result;
    }


    private Integer tryGetInteger(String value) {

        Integer result = null;
        try {
            result = Integer.parseInt(value);
        }
        catch (Exception ignore) {}

        return result;
    }

    private String printURI(URI uri) {

        return uri.getHost() + ":" + uri.getPort();

    }





}


//  shellHelper.printInfo("connected to " + host + " using: " + login + "/" + password);


//padding = new String(new char[login.length()]).replace("\0", " ");


//String message = inputReader.prompt("> " + padding);