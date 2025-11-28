
# Virtual Café

**Virtual Café** is a Java-based client-server application that simulates a virtual coffee shop environment. Multiple clients (customers) can connect to the server (barista), place drink orders (tea or coffee), monitor their order status, and collect their orders when ready.

---

## Features

- **Multi-client support** via Java Sockets  
- **Order drinks** with customizable quantities  
- **Live status updates**: Waiting, Brewing, and Tray (ready to collect)  
- **Simulated brewing times**: Tea (30s), Coffee (45s)  
- **Thread-safe queues and brewing areas** using `Concurrent` Java utilities  
- **Graceful exit** and resource cleanup  

---

## Technologies Used

- Multithreading
- Socket programming (`java.net`)
- Concurrency (Queues, Maps)

---

## Getting Started

### 1. Clone the repository


1. Compile the project
```bash
javac Barista.java Customer.java
```

3. Run the server (Barista)
```bash
java Barista
```
*server starts and listens on localhost:8888*

4. Run the client (Customer)

```bash
java Customer
```

*Open a new terminal for each client*

---

## How It Works
- When a customer connects, they're prompted to enter their name.

### To order a drink
- Use the order command in the following formats:
- order X ITEM
- order X ITEM and Y ITEM
- Accepted drink types: tea and coffee
Examples:
```bash
order 2 teas and 1 coffee
order 1 tea
```


### To check order status
```bash
order status
```
### To collect an order (when complete)
```bash
collect
```
*Clients are alerted when their order is ready*

### To exit
```bash
exit
```

## Limitations
- TBD

## Reflection

### File Overview
| File          |                                   Description                                   |
|:--------------|:-------------------------------------------------------------------------------:|
| Barista.java  | Server logic, manages order flow, brewing simulation, and client communication. |
| Customer.java |   Client interface that connects to the barista and sends/receives messages.    |

- The server does the heavy lifting of a Client-server application. The server is required to
accept multiple clients and individually maintain all clients by doing what they all want safely.

- Resources shared by threads should be kept private and controlled by using synchronized methods
or bugs will be created

- 