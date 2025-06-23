package com.dhruv.pcConnect;

import java.io.BufferedReader;
import java.io.PrintWriter;
import java.net.Socket;

public class ClientSocket {
  public Socket socket;
  public BufferedReader reader;
  public PrintWriter writer;

  public ClientSocket(Socket socket, BufferedReader reader,
                      PrintWriter writer) {
    this.socket = socket;
    this.writer = writer;
    this.reader = reader;
  }
}
