import com.simdb.server.DB;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.HashMap;

/**
 * Created with IntelliJ IDEA.
 * User: lux
 * Date: 1/20/13
 * Time: 2:56 PM
 * To change this template use File | Settings | File Templates.
 */
public class ServerThread extends Thread {
    private Socket socket = null;
    protected Server server = null;
    protected DB db;

    private Protocol prot;
    private PrintWriter out;
    private BufferedReader in;

    private HashMap<Integer, String> statusMap;

    public ServerThread(Server server, Socket socket) {
        this.server = server;
        this.socket = socket;
        try {
            this.db = new DB(server.conf);
        }  catch (UnknownHostException e) {
            System.err.println("Cannot connect to mongodb.");
            System.exit(-1);
        }

        statusMap = new HashMap<Integer, String>();
        // da controllare
        statusMap.put(0, "OK"); statusMap.put(1, "Wrong authentication"); statusMap.put(2, "Incomplete set");
        statusMap.put(3, "No such specifier"); statusMap.put(4, "Too much data"); statusMap.put(5, "No such set");
        statusMap.put(6, "Wrong type");  statusMap.put(7, "Unknown name"); statusMap.put(8, "Unknown field");
        statusMap.put(99, "Generic error");
    }

    public Boolean send(Integer status, String message) {
        Boolean return_status = true;
        if (status != null && status == -2) {
            status = 0;
            return_status = false;
        }
        String message0 = null;
        if (status != null && status != -1)
            message0 = status + " " + statusMap.get(status);
        if ((message0 != null && message0.length() > 0)
                || (message != null && message.length() > 0)) {
            String tmp = message == null ? "null" : message.replace("\n", "\\n");
            server.printf("[id: %d, %s:%d] sending (status: %d): %s\\n%s",
                Thread.currentThread().getId(), socket.getInetAddress().toString(), socket.getLocalPort(),
                status, message0, tmp.length() > 450 ?
                    tmp.replace("\n", "\\n").substring(0, 437) + "... [TRIMMED]" : tmp);
        }
        if (message0 != null && message0.length() > 0)
            out.println(message0);
        if (message != null && message.length() > 0)
            out.println(message);
        return return_status;
    }

    @Override
    public void run() {
        server.printf("spawned new thread [id: %d], serving %s:%d",
                Thread.currentThread().getId(), socket.getInetAddress().toString(), socket.getLocalPort());
        prot = new Protocol(this);
        try {
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(
                    new InputStreamReader(
                            socket.getInputStream()));

            String inputLine, outputLine;
            Integer outputStatus;
            Object[] outputResult;

            outputResult = prot.processInput(null);
            if (outputResult != null) {
                outputStatus = (Integer) outputResult[0];
                outputLine = (String) outputResult[1];
                if (!send(outputStatus, outputLine)) {
                    interrupt();
                    close();
                    return;
                }
            }

            while ((inputLine = in.readLine()) != null) {
                if (interrupted())
                    break;
                server.printf("[id: %d, %s:%d] received: %s",
                        Thread.currentThread().getId(), socket.getInetAddress().toString(), socket.getLocalPort(),
                        inputLine.length() > 450 ?
                                inputLine.replace("\n", "\\n").substring(0, 437) + "... [TRIMMED]" : inputLine.replace("\n", "\\n"));
                outputResult = prot.processInput(inputLine);
                if (outputResult != null) {
                    outputStatus = (Integer) outputResult[0];
                    outputLine = (String) outputResult[1];
                    if (!send(outputStatus, outputLine)) {
                        interrupt();
                        break;
                    }
                }
                if (interrupted())
                    break;
            }
            //sleep(100);
            close();
        } catch (Exception e) {
            e.printStackTrace();
            close();
        }
    }

    public void close() {
        try {
            if (prot != null)
                prot.invokeErrorCallback();
            if (out != null)
                out.close();
            if (in != null)
                in.close();
            if (socket != null)
                socket.close();
        } catch (IOException e) {}
        server.printf("closing thread [id: %d]", Thread.currentThread().getId());
    }
}
