package com.simdb.client;

import java.io.IOException;

class RequesterReader extends Thread {
    private Requester requester;
    public RequesterReader(Requester requester) {
        this.requester = requester;
    }

    public void run() {
        String line;
        try {
            while ((line = requester.in.readLine()) != null) {
                if (isInterrupted())
                    break;
                if (requester.DEBUG) // won't print big lines/files
                    System.out.println("echo: " + (line.length() > 450 ?
                            line.substring(0, 437) + "... [TRIMMED]" : line));
                try {
                    requester.readQueue.put(line);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    break;
                }
                if (isInterrupted())
                    break;
            }
        } catch (IOException ioException) {
        }
    }
}