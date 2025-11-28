import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;


class HandleCustomer implements Runnable{
    Socket socket;
    Barista server;

    public HandleCustomer(Socket socket, Barista server) {this.socket = socket; this.server = server;}


    @Override
    public void run() {
        String name = "";
        int totalOrders = 0;

        try (BufferedReader reader = new BufferedReader( new InputStreamReader(socket.getInputStream()));
             PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);) {
            System.out.println("Incoming client...");
            try{ // Need this for the second Exception thrown and also have the chance to use writer and printer
                writer.println("Please input your name: ");
                name = reader.readLine();
                if (name.isEmpty()) {
                    throw new Exception("Name is empty");
                }
                server.registerClient(name, writer);
                System.out.println( "New connection made. Customer: " + name);
                writer.println("Welcome to Virtual Cafe!!");
                server.logServerState();

                while(true){
                    writer.println("\nWhat would you like to do " + name + ": ");
                    String userMessage =  reader.readLine();
                    if (userMessage.isEmpty()) break;
                    String[] keywords = userMessage.split(" ");

                    switch (keywords[0].toLowerCase()) {
                        case "order":
                            if (Objects.equals(keywords[1], "status")){
                                OrderStatus status = server.getCustomerOrderStatus(name);

                                OrderCount waiting = status.waiting();
                                OrderCount brewing = status.brewing();
                                OrderCount tray = status.tray();

                                if (waiting.isEmpty() && brewing.isEmpty() && tray.isEmpty()){
                                    writer.println(name +  " has not placed an order yet!.");
                                    break;
                                }

                                writer.println("\n\nOrder status for " + name + ":\n" +
                                        "- Waiting area: " + waiting.teaCount + (waiting.teaCount > 1 ? " teas, " : " tea, ") + waiting.coffeeCount + (waiting.coffeeCount > 1 ? " coffees" : " coffee") +"\n" +
                                        "- Brewing area: " + brewing.teaCount + (brewing.teaCount > 1 ? " teas, " : " tea, ") + brewing.coffeeCount + (brewing.coffeeCount > 1 ? " coffees" : " coffee") +"\n" +
                                        "- Tray: " + tray.teaCount + (tray.teaCount > 1 ? " teas, " : " tea, ") + tray.coffeeCount + (tray.coffeeCount > 1 ? " coffees" : " coffee"));
                                break;
                            }
                            for (int n = 0; n < Integer.parseInt(keywords[1]); n++){
                                server.addToWaiting(name, keywords[2]);
                                totalOrders++;
                            }
                            if (keywords.length > 3){
                                for (int n = 0; n < Integer.parseInt(keywords[4]); n++){
                                    server.addToWaiting(name, keywords[5]);
                                    totalOrders++;
                                }
                            }
                            server.logServerState();
                            String joined = String.join(" ", Arrays.copyOfRange(keywords, 1, keywords.length));
                            writer.println("\nOrder received for " + name + " (" + joined + ")\n");
                            break;
                        case "collect":
                            if (totalOrders != server.findTotalCustomerItemsInTray(name)){
                                writer.println("\nYour Order is not complete yet. Please wait!\n");
                                break;
                            }
                            server.removeFromTrayArea(name);
                            server.logServerState();
                            writer.println("\nOrder Collected. Thank you for your order!\n");
                            break;
                        case "exit":
                            server.exitedCustomer(name);
                            return;
                        default:
                            writer.println("\nUnknown command. '" + userMessage + "' Please try again!!\n");
                            break;
                    }
                }
            } catch (Exception e){
                writer.println( "\nProblem Occurred:\n" + e.getMessage());
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }finally{
            System.out.println("Connection with " + (name != null && !name.isEmpty() ? name : "") + " closed");
            server.exitedCustomer(name);
            server.logServerState();
        }
    }
}

record DrinkRequest(String customerName, DrinkType type) {

    @Override
    public String toString() {
        return "(" + customerName + ", " + type.toString() + ")";
    }

}

enum DrinkType{
    TEA, COFFEE;

    @Override
    public String toString() {
        return name().toLowerCase();
    }
}

class TeaBrewer implements Runnable{
    private final Barista server;

    TeaBrewer(Barista server){this.server = server;}


    @Override
    public void run() {

        DrinkRequest req;

        while (true) {
            if (server.getTeaBrewingArea().size() < 2) {
                req = server.getNextFromWaitingArea(DrinkType.TEA);
                if (req != null) {
                    server.addToTeaBrewingArea(req);
                    server.logServerState();
                    server.startBrewing(req, 30000, DrinkType.TEA);
                }
            }
        }
    }
}

class CoffeeBrewer implements Runnable{
    private final Barista server;

    CoffeeBrewer(Barista server){this.server = server;}

    @Override
    public void run() {

        DrinkRequest req = null;

        while (true) {
            if (server.getCoffeeBrewingArea().size() < 2) {
                req = server.getNextFromWaitingArea(DrinkType.COFFEE);
                if (req != null) {
                    server.addToCoffeeBrewingArea(req);
                    server.logServerState();
                    server.startBrewing(req, 45000, DrinkType.COFFEE);
                }
            }
        }
    }
}

class OrderCount{
    int teaCount = 0;
    int coffeeCount = 0;

    public boolean isEmpty(){
        return teaCount == 0 && coffeeCount == 0;
    }
}

record OrderStatus(OrderCount waiting, OrderCount brewing, OrderCount tray) {}

public class Barista {
    private final static int port = 8888;

    private final Queue<DrinkRequest> waitingArea = new ConcurrentLinkedQueue<>();
    private final List<DrinkRequest> teaBrewingArea = new ArrayList<>();
    private final List<DrinkRequest> coffeeBrewingArea = new ArrayList<>();
    private final Map<String, OrderCount> trayArea = new HashMap<>();
    private final Map<String, PrintWriter> connectedClients = new ConcurrentHashMap<>();


    public static void main(String[] args) {
        Barista server = new Barista();
        server.RunServer();
    }

    public OrderStatus getCustomerOrderStatus(String customerName){
        OrderCount waitingAreaCount = new OrderCount();
        OrderCount brewingAreaCount = new OrderCount();

//        Waiting area
        for (DrinkRequest req:  waitingArea){
            if (req.customerName().equals(customerName)){
                if (req.type() == DrinkType.TEA) waitingAreaCount.teaCount++;
                else waitingAreaCount.coffeeCount++;
            }
        }

//        Brewing Area
        for (DrinkRequest req: teaBrewingArea){
            if (req.customerName().equals(customerName)) brewingAreaCount.teaCount++;
        }
        for  (DrinkRequest req: coffeeBrewingArea){
            if (req.customerName().equals(customerName)) brewingAreaCount.coffeeCount++;
        }

//        Tray Area
        OrderCount trayAreaCount = trayArea.getOrDefault(customerName, new OrderCount());

        return new OrderStatus(waitingAreaCount, brewingAreaCount, trayAreaCount);
    }

    public synchronized List<DrinkRequest> getTeaBrewingArea() {
        return teaBrewingArea;
    }

    public synchronized void addToTeaBrewingArea(DrinkRequest tea){
        this.teaBrewingArea.add(tea);
    }

    public synchronized List<DrinkRequest> getCoffeeBrewingArea() {
        return coffeeBrewingArea;
    }

    public synchronized void addToCoffeeBrewingArea(DrinkRequest coffee){
        this.coffeeBrewingArea.add(coffee);
    }

    public synchronized DrinkRequest getNextFromWaitingArea(DrinkType type) {
        for (DrinkRequest req : waitingArea) {
            if (req.type() == type) {
                waitingArea.remove(req);
                return req;
            }
        }
        return null;
    }

    public synchronized void addToWaiting(String customerName, String drinkName){
        waitingArea.add(new DrinkRequest(customerName, parseDrink(drinkName)));
    }

    private DrinkType parseDrink(String drinkName){
        drinkName = drinkName.toLowerCase().replaceAll("s", "").strip();
        return switch (drinkName) {
            case "tea" -> DrinkType.TEA;
            case "coffee" -> DrinkType.COFFEE;
            default -> throw new RuntimeException("Unknown drink type " + drinkName);
        };
    }

    public synchronized void addToTrayArea(DrinkRequest req){
        OrderCount count = trayArea.get(req.customerName());

        if (count == null) {
            count = new OrderCount();
            trayArea.put(req.customerName(), count);
        }

        switch (req.type()) {
            case TEA -> count.teaCount++;
            case COFFEE -> count.coffeeCount++;
        }
    }

    public synchronized void removeFromTrayArea(String customerName){
        trayArea.remove(customerName);
    }

    public synchronized int findTotalCustomerItemsInTray(String customerName){
        OrderCount customerTotalReadyItems = trayArea.getOrDefault(customerName, new OrderCount());
        return customerTotalReadyItems.teaCount +  customerTotalReadyItems.coffeeCount;
    }

    public synchronized OrderCount getCustomerTotalItems(String customerName){
        // Check every area for all items belonging to the customer
        OrderCount orderTotal = new OrderCount();

        for (DrinkRequest req : waitingArea){
            if (req.customerName().equals(customerName)){
                switch (req.type()) {
                    case TEA -> orderTotal.teaCount++;
                    case COFFEE -> orderTotal.coffeeCount++;
                }
            };
        }
        for (DrinkRequest req : coffeeBrewingArea){
            if (req.customerName().equals(customerName)) orderTotal.coffeeCount++;
        }
        for (DrinkRequest req : teaBrewingArea){
            if (req.customerName().equals(customerName)) orderTotal.teaCount++;
        }
        orderTotal.teaCount += trayArea.get(customerName).teaCount;
        orderTotal.coffeeCount += trayArea.get(customerName).coffeeCount;

        return orderTotal;
    }

    public void checkIfOrderComplete(String customerName) {
        OrderCount totalOrder = getCustomerTotalItems(customerName);
        int total = totalOrder.teaCount + totalOrder.coffeeCount;
        int tray = findTotalCustomerItemsInTray(customerName);

        if (tray == total && total > 0) {
            PrintWriter writer = connectedClients.get(customerName);
            if (writer != null) {
                String order = " ( ";
                if (totalOrder.teaCount > 0) {
                    order += totalOrder.teaCount + (totalOrder.teaCount > 1 ? " teas " : " tea ");
                }
                if (totalOrder.coffeeCount > 0) {
                    order += " and " + totalOrder.coffeeCount + (totalOrder.coffeeCount > 1 ? " coffees " : " coffee ");
                }
                order += " ) ";
                writer.println("\nOrder for " + customerName + order + "is complete ! Please collect\n");
            }
        }
    }

    public void startBrewing(DrinkRequest req, int brewTimeMs, DrinkType type) {
        new Thread(() -> {
            try {
                Thread.sleep(brewTimeMs);
                synchronized (this) {
                    boolean removed;
                    if (type == DrinkType.TEA) {
                        removed = teaBrewingArea.remove(req);
                    } else {
                        removed = coffeeBrewingArea.remove(req);
                    }
                     // removed will be true if it is possible to get the request from the Brewing area.
                    // If user leaves early then it will return false
                    if  (!removed) return;

                    addToTrayArea(req);
                    logServerState();
                    checkIfOrderComplete(req.customerName());

                }
            } catch (InterruptedException e) {
                System.out.println("Brewing thread interrupted");;
            }
        }).start();
    }

    public synchronized void logServerState(){
        Set<String> waitingClients = new  HashSet<>();
        // Getting all waiting clients and items within each area
        OrderCount waitingItems = new OrderCount();
        OrderCount brewingItems = new OrderCount();
        OrderCount trayItems = new OrderCount();

//        Waiting area
        for (DrinkRequest req: waitingArea) {
            waitingClients.add(req.customerName());
            if (req.type() == DrinkType.TEA) waitingItems.teaCount++;
            else waitingItems.coffeeCount++;
        }

//        Tea brewing and coffee brewing (brewing area)
        for (DrinkRequest req: teaBrewingArea) {
            waitingClients.add(req.customerName());
            brewingItems.teaCount++;
        }
        for (DrinkRequest req: coffeeBrewingArea) {
            waitingClients.add(req.customerName());
            brewingItems.coffeeCount++;
        }

//        Tray area
        for (OrderCount count : trayArea.values()) {
            trayItems.teaCount += count.teaCount;
            trayItems.coffeeCount += count.coffeeCount;
        }

        System.out.println(
                "\n\n---------STATE UPDATE---------\n" +
                        "Clients in cafe: " + connectedClients.size() +
                        "\nClients waiting for their orders: " + waitingClients.size() +
                        "\n\nWaiting Area:\n" +
                        " - " + waitingItems.teaCount + (waitingItems.teaCount > 1 ? " teas\n" : " tea\n") +
                        " - " + waitingItems.coffeeCount + (waitingItems.coffeeCount > 1 ? " coffees" : " coffee") +
                        "\n\nBrewing Area:\n" +
                        " - " + brewingItems.teaCount + (brewingItems.teaCount > 1 ? " teas\n" : " tea\n") +
                        " - " + brewingItems.coffeeCount + (brewingItems.coffeeCount > 1 ? " coffees" : " coffee") +
                        "\n\nTray Area:\n" +
                        " - " + trayItems.teaCount + (trayItems.teaCount > 1 ? " teas\n" : " tea\n") +
                        " - " + trayItems.coffeeCount + (trayItems.coffeeCount > 1 ? " coffees" : " coffee") +
                        "\n---------------------"
        );

    }

    public synchronized void exitedCustomer(String customerName) {
        // Remove from waiting
        waitingArea.removeIf(req -> req.customerName().equals(customerName));
        // Remove from brewing
        teaBrewingArea.removeIf(req -> req.customerName().equals(customerName));
        coffeeBrewingArea.removeIf(req -> req.customerName().equals(customerName));
        // Remove from tray
        trayArea.entrySet().removeIf(entry -> entry.getKey().equals(customerName));
        // Unregister the client
        unregisterClient(customerName);
    }

    public synchronized void registerClient(String customerName, PrintWriter writer) {
        connectedClients.put(customerName, writer);
    }

    public synchronized void unregisterClient(String customerName) {
        connectedClients.remove(customerName);
    }

    public void RunServer(){
        ServerSocket serverSocket = null;
        try {
            serverSocket = new ServerSocket(port); // Created the passive socket
            System.out.println("Waiting for next Customer...");
            new Thread(new TeaBrewer(this)).start();
            new Thread(new CoffeeBrewer(this)).start();
            while (true) {
                Socket socket = serverSocket.accept(); // When a customer connects to the socket then accept them
                new Thread(new HandleCustomer(socket, this)).start();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
