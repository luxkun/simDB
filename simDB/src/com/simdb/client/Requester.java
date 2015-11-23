package com.simdb.client;

import com.simdb.Errors;

import java.io.*;
import java.net.*;
import java.util.concurrent.LinkedBlockingQueue;

public class Requester {
    protected LinkedBlockingQueue<String> readQueue;
    private RequesterReader requesterReader;
    private Socket requestSocket;
    protected InputStream inStream;
    protected PrintWriter out;
    protected BufferedReader in;

 	public String host;
 	public Integer port;

    public Boolean DEBUG = true;

    public Requester(String host, Integer port) {
        this.host = host;
        this.port = port;
        readQueue = new LinkedBlockingQueue<String>();
    }
    public Requester(String host, Integer port, Boolean DEBUG) {
        this(host, port);
        this.DEBUG = DEBUG;
    }

    public void run() {
        try {
            System.out.println(String.format("Connecting to %s in port %s", host, port));
            requestSocket = new Socket(host, port);
            out = new PrintWriter(requestSocket.getOutputStream(), true);
            inStream = requestSocket.getInputStream();
            in = new BufferedReader(new InputStreamReader(inStream));
            System.out.println(String.format("Successful connected to %s in port %s", host, port));
        } catch (UnknownHostException unknownHost) {
            System.err.println("You are trying to connect to an unknown host!");
        } catch (IOException ioException) {
            ioException.printStackTrace();
            System.exit(-1);
        }

        if (DEBUG)
            System.out.println("[debug] starting reader thread");
        RequesterReader requesterReader = new RequesterReader(this);
        requesterReader.start();
    }

    public Integer readStatus() throws Exception {
        return readStatus(readLine());
    }
    public Integer readStatus(String line) throws Exception {
        Integer return_code = Integer.parseInt(line.split(" ", 2)[0]);
        switch(return_code) {
            case 0:
                break;
            case 1:
                throw new Errors.WrongAuthentication();
            case 2:
                throw new Errors.IncompleteSet();
            case 3:
                throw new Errors.NoSuchSpecifier();
            case 4:
                throw new Errors.TooMuchData();
            case 5:
                throw new Errors.NoSuchSet();
            case 6:
                throw new Errors.WrongType();
            case 7:
                throw new Errors.UnknownName();
            case 8:
                throw new Errors.UnknownField();
            case 99:
                throw new Errors.GenericError();
            default:
                break;
        }
        return return_code;
    }

	public String readLine() throws Exception {
		return readQueue.take();

	}

	public void send(String msg) {
        if (DEBUG) {
            String msg1 = msg.replace("\n", "\\n");
            System.out.println("[debug] sending: " + (
                    msg1.length() > 450 ? msg1.substring(0, 437) + "... [TRIMMED]" : msg1));
        }
        out.print(msg);
        out.flush();
	}

	public void quit() {
		try {
            send("QUIT\n");
            if (requesterReader != null) {
                requesterReader.interrupt();
            }
			in.close();
			out.close();
			requestSocket.close();
		} catch (Exception e){
			e.printStackTrace();
        }
	}
}
