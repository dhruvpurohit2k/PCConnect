package com.dhruv.pcConnect;

import java.net.Socket;
import java.util.concurrent.CompletableFuture;

public class ClientRequest {
  Socket client;
  CompletableFuture<Boolean> userResponse;

  public ClientRequest(Socket client, CompletableFuture<Boolean> userResponse) {
    this.client = client;
    this.userResponse = userResponse;
  }
}
