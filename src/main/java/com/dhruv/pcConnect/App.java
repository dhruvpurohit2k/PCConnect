package com.dhruv.pcConnect;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;
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

  private int DEFAULT_PORT = 12345;
  private String serviceType = "_datashare._tcp.local.";
  private Map<String, ServiceInfo> availableDevices = new ConcurrentHashMap<>();

  public App() throws IOException {
    this.sc = new Scanner(System.in);
    this.serverSocket = new ServerSocket(DEFAULT_PORT);
    this.jmdns = JmDNS.create(InetAddress.getLocalHost());
    this.serviceInfo =
        ServiceInfo.create(this.serviceType, this.userName, this.DEFAULT_PORT,
                           "A Device ready to send or recieve data");
    jmdns.registerService(this.serviceInfo);
  }

  private class MyServiceListener implements ServiceListener {
    @Override
    public void serviceAdded(ServiceEvent event) {
      jmdns.requestServiceInfo(event.getType(), event.getName(), 1000);
    }

    @Override
    public void serviceResolved(ServiceEvent event) {
      ServiceInfo info = event.getInfo();
      availableDevices.put(info.getName(), info);
    }

    @Override
    public void serviceRemoved(ServiceEvent event) {
      availableDevices.remove(event.getName());
    }
  }

  public void start() throws IOException {
    MyServiceListener serviceListener = new MyServiceListener();
    jmdns.addServiceListener(this.serviceType, serviceListener);
    boolean flag = true;
    while (flag) {
      System.out.println("List of Available Devices are - ");
      for (String deviceName : availableDevices.keySet()) {
        System.out.printf("-> %s\n", deviceName);
      }
      System.out.println("Type q to quit are any other key to refresh");
      String command = sc.nextLine();
      if (command.equalsIgnoreCase("q")) {
        System.out.println("Closing....");
        flag = false;
        this.shutdown();
      } else {
        continue;
      }
    }
  }

  public void shutdown() throws IOException {
    this.sc.close();
    this.serverSocket.close();
    this.jmdns.unregisterAllServices();
    this.jmdns.close();
  }

  public static void main(String[] args) throws IOException {
    App app = new App();
    app.start();
    return;
  }
}
