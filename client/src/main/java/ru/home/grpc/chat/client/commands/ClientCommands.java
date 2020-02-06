package ru.home.grpc.chat.client.commands;

import io.grpc.StatusRuntimeException;
import org.jline.reader.UserInterruptException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;
import org.springframework.util.StringUtils;
import ru.geekbrains.dreamworkerln.spring2.shell_lib.shell.InputReader;
import ru.geekbrains.dreamworkerln.spring2.shell_lib.shell.ShellHelper;
import ru.home.grpc.chat.ServerMessage;
import ru.home.grpc.chat.client.ChatClientApplication;
import ru.home.grpc.chat.client.service.ChatClient;
import javax.annotation.PostConstruct;
import java.lang.invoke.MethodHandles;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.AccessDeniedException;
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

    String padding;

    Map<Integer,URI> hostList;

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

    @ShellMethod("Connect to server")
    public void connect(@ShellOption(defaultValue = ShellOption.NULL)String host) throws Exception {


        String login = null;
        try {

//            if(client.isConnected()) {
//                shellHelper.printWarning("Already connected");
//                return;
//            }


            URI server;

            server = getHostByNumber(host);

            if (server == null) {
                server = getHostByString(host);
            }

            // Тут не оставит пользователя в покое, пока не введет правильно или забьет
            if (server == null) {
                server = getHostFromTable();
            }

            // get login/password -------------------------------------------------

            login = inputReader.prompt( "login: ");
            String password = inputReader.prompt("password: ", "secret", false);

            //shellHelper.printInfo("connected to " + host + " using: " + login + "/" + password);


            // Synchronous connecting - authenticate ------------------------------------------


            shellHelper.printInfo("Connecting to server " + printURI(server));
            client.connect(server.getHost(), server.getPort(), login, password);
            
        }
        // handling gRPC Errors
        catch (StatusRuntimeException e) {
            shellHelper.printError(e.getStatus().getCode().name());
            log.info("gRPC Auth error", e);
        }
        // handling Ctrl+C
        catch (UserInterruptException e) {
            System.out.println("^C");
        }
        catch (AccessDeniedException e) {
            shellHelper.printError(e.getMessage());
            log.info("gRPC Auth error", e);
        }
        // ?????
        catch (Exception e) {
            log.info("gRPC Auth error", e);
        }

        // exit if have errors ---------------------------------------------------------------

        if(!client.isConnected()) {
            ChatClientApplication.fakeStdIn();
            return;
        }

        // -------------------------------------------------------------------------------------------
        // Async chatting ----------------------------------------------------------------------------

        //padding = new String(new char[login.length()]).replace("\0", " ");
        System.out.println("Chat is online");
        //System.out.print(padding);


        // Async chatting -------------------------------------------------------------------
        try {
            while (client.isConnected()) {
                //String message = inputReader.prompt("> " + padding);
                String message = inputReader.prompt();
                client.sendMessage(message);
            }
        }
        // handling gRPC Errors
        catch (StatusRuntimeException e){

            System.out.println("ОПАПОПАОПАОПА ОПА ОПА ОПА ОПА ОПА ОПА ОПА!!!!");
            shellHelper.printError(e.getStatus().getCode().name());
            log.error("gRPC error", e);
        }
        // handling Ctrl+C
        catch (UserInterruptException e){
            System.out.println("^C");
        }

        catch (Exception e) {
            shellHelper.printError(e.getMessage());
        }
        finally {
            client.shutdown();
        }

        // swallow all errors
        ChatClientApplication.fakeStdIn();
    }

    // -------------------------------------------------------------


    @ShellMethod("Disconnect from server")
    public void disconnect() throws InterruptedException {
        client.shutdown();
    }




    @PostConstruct
    protected void postConstruct() {

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




    @Override
    public void onMessage(ServerMessage message)  {
        System.out.println(message.getFrom() + ": " + message.getMessage());
        //System.out.print(padding);
        ChatClientApplication.fakeStdIn();
    }

    @Override
    public void onInfo(String message) {
        log.info(message);
    }

    @Override
    public void onError(Throwable t) {

        if (t instanceof StatusRuntimeException) {
            shellHelper.printError(((StatusRuntimeException)t).getStatus().getCode().name());
        }
        log.info("gRPC error", t);
        ChatClientApplication.fakeStdIn();
    }

    @Override
    public void onOpen(String message) {

    }

    @Override
    public void onClose() {
        System.out.println("Disconnected");
        ChatClientApplication.fakeStdIn();
    }













    // ===========================================================================================


    private URI getHostByNumber(String value) throws URISyntaxException {

        URI result = null;

        Integer index = tryGetInteger(value);

        if (hostList.containsKey(index)) {
            result = hostList.get(index);
        }
        return result;
    }




    private URI getHostByString(String host) throws URISyntaxException {



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
            String value = inputReader.prompt("Select host [N]: ");

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


