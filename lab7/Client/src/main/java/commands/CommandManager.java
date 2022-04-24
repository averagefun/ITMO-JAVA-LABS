package commands;

import console.*;
import data.Movie;
import exceptions.*;
import network.CommandPacket;
import network.Common;

import java.io.IOException;
import java.io.Serializable;
import java.nio.channels.NoConnectionPendingException;
import java.util.*;
import java.util.function.Supplier;

/**
 * Class that operates Command
 */
public class CommandManager {
    private final Map<String, Command> commands;
    private final Map<String, String> descriptions;
    private final Map<String, Boolean> onlyAuthorized;
    private final Client client;

    private final Console console;
    private Supplier<String> valueGetter;

    private final HashSet<MyFile> fileHistory;


    public CommandManager(Scanner sc, Client client) {
        this.commands = new HashMap<>();
        this.descriptions = new HashMap<>();
        this.onlyAuthorized = new HashMap<>();
        this.client = client;

        this.console = new Console(sc, true);
        this.valueGetter = console::readLine;

        this.fileHistory = new HashSet<>();
        initCommands();
    }

    /**
     * Add new command
     * @param name name of command that used to execute command
     * @param description description of command showed in help mode
     * @param action what this command going to do (action of command)
     */
    public void putCommand(String name, boolean onlyAuthorized, String description, Command action){
        commands.put(name, action);
        descriptions.put(name, description);
        this.onlyAuthorized.put(name, onlyAuthorized);
    }

    /**
     * Get command by name
     * @param name the name of command that defined in {@link #putCommand(String, boolean, String, Command)}
     * @return Command
     */
    public Command getCommand(String name) {
        return commands.get(name);
    }

    /**
     * Run command by name defined in {@link #putCommand(String, boolean, String, Command)}
     * @param name command name
     * @param arg argument, given to command
     */
    public void runCommand(String name, String arg) {
        try {
            Command command = getCommand(name);
            if (command == null) throw new CommandNotFindException("Command not find.");
            if (onlyAuthorized.get(name) && !client.isAuthorized()) {
                Console.println("This command is allowed only for authorized users. " +
                        "(Try 'sign_up' to create new account, more information in 'help')");
            } else {
                String result = getCommand(name).run(name, arg);
                if (result != null) console.printlnMode(result);
            }
        } catch (NullPointerException e) {
            Console.println("Command did not run successfully, problem detected.");
        }
        catch (InvalidArgumentException | CommandInterruptedException |
                ExecuteScriptFailedException|CommandNotFindException e) {
            if (e.getMessage() != null) Console.println(e.getMessage());
        } catch (IOException e) {
            Console.println("Error: file not found.");
        } catch (NoConnectionPendingException e) {
            Console.println("Server is not responding. Try to run command later.");
        }
    }

    /**
     * Run command with no argument
     * @param name command name
     */
    public void runCommand(String name) {
        runCommand(name, null);
    }

    private Object getObjectFromServer(String name, int count) {
        return getObjectFromServer(name, count, null);
    }

    private <T extends Serializable> Object getObjectFromServer(String name, int count, T arg) {
        return client.sendThenReceive(new CommandPacket(name, count, arg, client.getUsername(), client.getPassword()));
    }

    private String getStringFromServer(String name, int count) {
        return (String) getObjectFromServer(name, count);
    }

    private <T extends Serializable> String getStringFromServer(String name, int count, T arg) {
        return (String) getObjectFromServer(name, count, arg);
    }

    private void setPrintMode() {
        console.setPrintMode(true);
        valueGetter = console.getSc()::nextLine;
    }

    private void offPrintMode(Queue<String> q) {
        console.setPrintMode(false);
        valueGetter = q::poll;
    }

    private int intValidator(String arg) throws InvalidArgumentException {
        try {
            return (int) new InputValidator(int.class, false, 0, Double.MAX_VALUE)
                    .validate(arg, null, true);
        }
        catch (ValidateException e) {
            Console.println(e.getMessage());
            throw new InvalidArgumentException();
        }
    }

    private void initCommands(){
        putCommand("help", false,"get information how to use app", (name, arg) -> {
            StringBuilder sb = new StringBuilder("All available commands:\n*");
            descriptions.keySet().stream()
                    .filter(key -> onlyAuthorized.get(key).compareTo(client.isAuthorized()) <= 0)
                    .sorted()
                    .forEach(key -> sb.append(key).append(": ").append(descriptions.get(key)).append("\n*"));
            Console.println(sb.substring(0,sb.length()-2));
            return null;
        });

        putCommand("info", false,"get information about movies collection (type, data, size)", (name, arg) -> {
            Console.println(getStringFromServer(name, 0));
            return null;
        });

        putCommand("show", false,"show all movies OR argument -> {id}, show specific movie", (name, arg) -> {
            Integer id = (arg!=null) ? intValidator(arg) : null;
            Console.println(getStringFromServer(name, 0, id));
            return null;
        });

        putCommand("exit", false,"exit the client program", (name, arg) -> {
            Console.println("Closing client app...");
            System.exit(0);
            return null;
        });

        putCommand("filter_less_than_oscars_count", false, "argument -> {oscarsCount}, display all movies where oscars count lower than current", (name, arg) -> {
            int oscarsCount = intValidator(arg);
            return getStringFromServer(name, oscarsCount);
        });

        putCommand("filter_greater_than_director", false,"argument -> {id}, display all movies where director greater than current", (name, arg) -> {
            int id = intValidator(arg);
            return getStringFromServer(name, id);
        });

        putCommand("print_unique_oscars_count", false,"print all unique values of oscars count in collection", (name, arg) ->
                getStringFromServer(name, 0));

        putCommand("sign_in", false,"sign in to account", (name, arg) -> {
            console.printMode("Enter username: ");
            String username = console.readLine();
            if (username.isEmpty()) {
                return "Username can't be empty.";
            }
            String answer = getStringFromServer(name, 0, username);
            if (!answer.equals("success")) {
                return answer;
            }
            client.setUsername(username);
            console.printMode("Enter password [keep empty if no password set]: ");
            client.setPassword(console.readLine());
            answer = getStringFromServer(name, 1, username + "::" + client.getPassword());
            if (answer.equals("success")) {
                client.setAuthorized(true);
                return "You have successfully logged in as " + username + "!";
            }
            return answer;
        });

        putCommand("sign_up", false,"create new account", (name, arg) -> {
                console.printMode("Create a username: ");
                String username;
                while (true) {
                    username = console.readLine();
                    if (username.isEmpty()) {
                        console.printlnMode("Username can't be empty.");
                    } else {
                        String answer = getStringFromServer(name, 0, username);
                        if (answer.equals("success")) break;
                        console.printlnMode(answer);
                    }
                    console.printMode("Create new username: ");
                }
                client.setUsername(username);
                console.printMode("Enter password [keep empty if you don't want password]: ");
                String password;
                while(true) {
                    password = console.readLine();
                    console.printMode("Repeat password [keep empty if you don't want password]: ");
                    if (console.readLine().equals(password)) {
                        client.setPassword(password);
                        break;
                    }
                    console.printMode("Passwords do not match. Enter password again: ");
                }
                String answer = getStringFromServer("sign_up", 1, username + "::" + password);
                if (answer.equals("success")) {
                    client.setAuthorized(true);
                    return "You have successfully create account and logged in as " + username + "!";
                }
                return answer;
        });

        putCommand("add", true,"add movie to collection", (name, arg) -> {
            console.printlnMode("To add movie lead the instruction below:");
            Movie movie = Common.inputAndUpdateMovie(false, null, console.isPrintMode(), valueGetter);
            return getStringFromServer(name, 0, movie);
        });

        putCommand("update", true,"argument -> {id}, update movie by id", (name, arg) -> {
            int id = intValidator(arg);
            Object response = getObjectFromServer(name, id);
            if (response instanceof String) {
                return (String) response;
            }
            else {
                Movie oldMovie = (Movie) response;
                console.printlnMode("To update movie lead the instruction below, to save previous value type '<':");
                Movie movie = Common.inputAndUpdateMovie(true, oldMovie, console.isPrintMode(), valueGetter);
                return getStringFromServer(name, 0, movie);
            }
        });

        putCommand("remove_by_id", true,"argument -> {id}, remove movie by id", (name, arg) -> {
            int id = intValidator(arg);
            return getStringFromServer(name, id);
        });

        putCommand("clear", true,"clear collection", (name, arg) ->
                getStringFromServer(name, 0));

        putCommand("execute_script", true,"argument -> {file_name}, execute script file", (name, arg) -> {
            String filePath;
            try {
                filePath = (String) new InputValidator(String.class, false)
                                    .validate(arg, null, true);
            } catch (ValidateException e) {
                Console.println(e.getMessage());
                throw new InvalidArgumentException();
            }

            MyFile myFile = new MyFile(filePath);
            if (!fileHistory.add(myFile)) {
                throw new FileRecursionException("File '" + filePath + "' referring to itself.");
            }
            Queue<String> q = FileManager.readCommandFile(filePath);
            offPrintMode(q);

            while (q.peek() != null) {
                String[] splitLine = q.poll().trim().split(" ");

                String command = null;
                String newArg = null;
                if (splitLine.length >= 1) {
                    command = splitLine[0];
                }
                if (splitLine.length >= 2) {
                    newArg = splitLine[1];
                }
                runCommand(command, newArg);
            }

            fileHistory.remove(myFile);
            setPrintMode();
            return null;
        });

        putCommand("add_if_min", true, "add movie if it oscars count lower that the other collection", (name, arg) -> {
            console.printlnMode("To add movie lead the instruction below:");
            Movie movie = Common.inputAndUpdateMovie(false, null, console.isPrintMode(), valueGetter);
            return getStringFromServer(name, 0, movie);
        });

        putCommand("remove_greater", true,"argument -> {id}, remove from collection all movies if its oscars count greater than current movie", (name, arg) -> {
            int id = intValidator(arg);
            return getStringFromServer(name, id);
        });

        putCommand("remove_lower", true,"argument -> {id}, remove from collection all movies if its oscars count lower than current movie", (name, arg) -> {
            int id = intValidator(arg);
            return getStringFromServer(name, id);
        });
    }
}
