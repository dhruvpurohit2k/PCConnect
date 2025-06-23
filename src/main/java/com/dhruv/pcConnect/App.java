package com.dhruv.pcConnect;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Scanner;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import javax.jmdns.JmDNS;
import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceInfo;
import javax.jmdns.ServiceListener;

public class App {
  private String userName;
  private JmDNS jmdns;
  private Scanner sc;
  private ServerSocket serverSocket;
  private ServiceInfo serviceInfo;
  private Socket socket;
  private PrintWriter myWriter;
  private BufferedReader myReader;
  private boolean connected;
  private Queue<String> messagesList = new LinkedList<>();
  private String connectedDeviceName;
  private int port;
  private boolean running = true;
  private String serviceType = "_datashare._tcp.local.";
  private Map<String, ServiceInfo> availableDevices =
      new ConcurrentSkipListMap<>();
  private BlockingQueue<String> userCommands = new ArrayBlockingQueue<>(5);
  private Thread userInputReaderThread;
  private BlockingQueue<ClientRequest> clientRequests =
      new ArrayBlockingQueue<>(1);
  private ClientSocket connectedClient;
  private ClientRequest pendingRequest;

  public App() throws IOException, InterruptedException {
    this.sc = new Scanner(System.in);
    userInputReaderThread =
        new Thread(this::readUserCommands, "USER INPUT READER");
    userInputReaderThread.start();
    System.out.print("Enter user name : ");
    this.userName = userCommands.take();
    System.out.print("Enter port : ");
    this.port = Integer.parseInt(userCommands.take());
    this.serverSocket = new ServerSocket(this.port, 1);
    this.jmdns = JmDNS.create(InetAddress.getLocalHost());
    this.serviceInfo =
        ServiceInfo.create(this.serviceType, this.userName, this.port,
                           "A Device ready to send or recieve data");
    jmdns.registerService(this.serviceInfo);
    MyServiceListener serviceListener = new MyServiceListener();
    jmdns.addServiceListener(this.serviceType, serviceListener);

    // SeverSocket settings
    new Thread(this::listenForClients, "LISTEN FOR CLIENTS").start();
  }

  private void listenForClients() {
    while (running) {
      try {
        Socket clientSocket = this.serverSocket.accept();
        BufferedReader clientReader = new BufferedReader(
            new InputStreamReader(clientSocket.getInputStream(), "UTF-8"));
        PrintWriter clientWriter = new PrintWriter(
            new OutputStreamWriter(clientSocket.getOutputStream(), "UTF-8"),
            true);
        String firstRequest = clientReader.readLine();
        if (firstRequest.equals("CONNECTION_REQUEST")) {
          ClientRequest newRequest =
              new ClientRequest(clientSocket, new CompletableFuture<Boolean>());
          this.clientRequests.offer(newRequest);
          Boolean userAccept = newRequest.userResponse.get();
          if (userAccept) {
            clientWriter.println("ACCPET");
            this.connected = true;
            this.connectedClient =
                new ClientSocket(clientSocket, clientReader, clientWriter);
            new Thread(this::receiveMessages).start();
          }
        } else {
          System.out.println("INVALID INITIAL PACKET");
          clientSocket.close();
          clientReader.close();
          clientWriter.close();
        }
      } catch (IOException ioe) {
        ioe.printStackTrace();
      } catch (InterruptedException ine) {
        ine.printStackTrace();
      } catch (ExecutionException ee) {
        ee.printStackTrace();
      }
    }
  }

  private void readUserCommands() {
    try {
      while (running && !Thread.currentThread().isInterrupted()) {
        if (System.in.available() > 0) {
          String line = sc.nextLine();
          userCommands.add(line);
        } else {
          Thread.sleep(50);
        }
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  private class MyServiceListener implements ServiceListener {
    @Override
    public void serviceAdded(ServiceEvent event) {
      jmdns.requestServiceInfo(event.getType(), event.getName(), true);
    }

    @Override
    public void serviceResolved(ServiceEvent event) {
      ServiceInfo info = event.getInfo();
      if (!info.equals(serviceInfo))
        availableDevices.put(info.getName(), info);
    }

    @Override
    public void serviceRemoved(ServiceEvent event) {
      availableDevices.remove(event.getName());
    }
  }

  public void clearScreen() {
    System.out.print("\033[H\033[2J");
    System.out.flush();
  }

  public void start() throws IOException, InterruptedException {
    while (running) {
      if (connected = true && this.connectedClient != null) {
        clearScreen();
        for (String msg : messagesList) {
          System.out.println(msg);
        }
        System.out.print(">> ");
        String msg = this.userCommands.poll(3, TimeUnit.SECONDS);
        if (msg != null) {
          send(msg);
        }
      } else {

        ClientRequest newRequest =
            this.clientRequests.poll(10, TimeUnit.MILLISECONDS);
        if (newRequest != null) {
          if (pendingRequest == null) {
            pendingRequest = newRequest;
          } else {
            System.out.println(
                "ONE REQUEST IS ALREAY PROCESSING. REMOVING THIS ONE");
            newRequest.userResponse.complete(false);
          }
        }
        if (pendingRequest != null) {
          System.out.println("INCOMMING REQUEST. ACCPET? Y/n");
          String command = this.userCommands.poll(3, TimeUnit.SECONDS);
          if (command != null) {
            if (command.equalsIgnoreCase("Y")) {
              System.out.println("ACCEPTED");
              pendingRequest.userResponse.complete(true);
              pendingRequest = null;
              Thread.sleep(1000);
            } else if (command.equalsIgnoreCase("N")) {
              System.out.println("REJECTED");
              pendingRequest.userResponse.complete(false);
              pendingRequest = null;
              Thread.sleep(1000);
            } else {
              // clearScreen();
            }
          } else {
            // clearScreen();
          }
        } else {
          int i = 1;
          List<String> deviceName = new ArrayList<>();
          System.out.println("LIST OF AVAILABLE DEVICES->");
          for (String device : this.availableDevices.keySet()) {
            deviceName.add(device);
            System.out.printf("%d. %s\n", i++, device);
          }
          System.out.println(
              "Select the device number, r for refresh or q to quit");
          String command = this.userCommands.poll(10, TimeUnit.SECONDS);
          if (command != null) {
            if (command.equalsIgnoreCase("r")) {
              clearScreen();
              continue;
            } else if (command.equalsIgnoreCase("q")) {
              this.shutdown();
            } else {
              try {
                int d = Integer.parseInt(command);
                if (d > 0 && d <= deviceName.size()) {
                  connectTo(this.availableDevices.get(deviceName.get(d - 1)));
                }
              } catch (NumberFormatException e) {
                clearScreen();
                continue;
              }
            }
          } else {
            clearScreen();
          }
        }
      }
    }
  }

  private void send(String msg) {
    if (connected && this.connectedClient != null) {
      if (this.messagesList.size() > 10) {
        this.messagesList.poll();
      }
      this.messagesList.offer("CURRENT DEVICE ->" + msg);
      this.connectedClient.writer.println(msg);
    } else {
      System.out.println("Not Connceted to a chat room");
    }
  }

  private void connectTo(ServiceInfo info) throws IOException {
    String add = info.getHostAddresses()[0];
    int port = info.getPort();
    this.connectedDeviceName = info.getName();
    this.socket = new Socket(add, port);
    myReader = new BufferedReader(
        new InputStreamReader(socket.getInputStream(), "UTF-8"));
    myWriter = new PrintWriter(
        new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);
    myWriter.println("CONNECTION_REQUEST");
    System.out.println("WAITING FOR RESPNSE FROM THE DEVICE");
    String reponse = myReader.readLine();
    System.out.println("GOT RESPONSE FROM THE DEVICE");
    if (reponse.equals("ACCPET")) {
      System.out.println("ACCEPTED");
      this.connectedClient = new ClientSocket(socket, myReader, myWriter);
      new Thread(this::receiveMessages).start();
    } else {
      System.out.println("REJECTED");
    }
  }

  public void receiveMessages() {
    try {
      String message;
      while (connected &&
             (message = this.connectedClient.reader.readLine()) != null) {
        if (this.messagesList.size() > 10) {
          this.messagesList.poll();
        }
        this.messagesList.offer("OTHER DEVICE ->" + message);
      }

    } catch (Exception e) {
      e.printStackTrace();
    } finally {
      System.out.printf("DISCONNECTED\n");
    }
  }

  public void shutdown() throws IOException {
    if (this.socket != null)
      socket.close();
    if (userInputReaderThread != null && userInputReaderThread.isAlive()) {
      userInputReaderThread.interrupt();
    }
    this.sc.close();
    this.serverSocket.close();
    this.jmdns.unregisterAllServices();
    this.jmdns.close();
  }

  public static void main(String[] args)
      throws IOException, InterruptedException {
    App app = new App();
    app.start();
    return;
  }
}
