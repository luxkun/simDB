import com.simdb.DSSFormat;
import com.simdb.server.Conf;
import com.simdb.server.DB;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.net.UnknownHostException;
import java.util.*;


public class Server {
    public final static String EOL = System.getProperty("line.separator");

    public static final String version = "0.1";
    private static final String default_data_dir = "data";
    private static final Integer default_port = 4444;
    private static final String defaulf_DSS_conf_path = "conf";
    private static final String default_db_name = "simDB";
    private static final String default_db_host = "127.0.01";
    private static final Integer default_db_port = 27017;
    private static final String default_conf_file = "simdbServer.conf";
    private static final Boolean default_DEBUG = true;
    private ServerSocket serverSocket;

    private ArrayList<ServerThread> threadList;

    public final Integer port;
    public final Conf conf;
    public final HashMap<String, DSSFormat> dssFormat;
    protected boolean listening;
    public Server(Conf conf, Integer port, String DSS_conf_path) throws IOException {
        this.conf = conf;
        this.port = port;
        printf("loading DSS configuration files from directory: %s", DSS_conf_path);
        this.dssFormat = DSSFormat.loadFromDirectory(DSS_conf_path);
        if (this.dssFormat == null)
            System.exit(-1);

        serverSocket = null;
        listening = true;

        try {
            serverSocket = new ServerSocket(port);
        } catch (IOException e) {
            System.err.println("Could not listen on port: " + port);
            System.exit(-1);
        }

        try {
            DB db = new DB(conf);
            db.initialize(dssFormat);
        }  catch (UnknownHostException e) {
            System.err.println("Cannot connect to mongodb.");
            System.exit(-1);
        }

        printf("listening on port %d", port);
        threadList = new ArrayList<ServerThread>();
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                printf("hooked server shutdown");
                close();
            }
        });
        while (listening) {
            ServerThread thread = new ServerThread(this, serverSocket.accept());
            threadList.add(thread);
            thread.start();
        }
        close();
    }

    protected void close() {
        for (Thread thread : threadList)
            thread.interrupt();
        for (Thread thread : threadList) {
            try {
                if (thread != null)
                    thread.join(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        try {
            if (serverSocket != null)
                serverSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void print(PrintStream stream, String message, Object... args) {
        if (conf.DEBUG) {
            Calendar now = Calendar.getInstance();
            stream.printf(String.format("[%d:%d:%d.%d] ", now.get(Calendar.HOUR_OF_DAY),
                    now.get(Calendar.MINUTE), now.get(Calendar.SECOND), now.get(Calendar.MILLISECOND))
                    + message + EOL, args);
        }
    }
    protected void printf(String message, Object... args) {
        print(System.out, message, args);
    }
    protected void printerr(String message, Object... args) {
        print(System.err, message, args);
    }


    public static Object[] loadConfFile(String confFilePath, Integer port, String data_dir, Boolean DEBUG, String db_name, String db_host, Integer db_port, String DSS_conf_path) {
        // conf priorities: 0: default - 1: conf - 2: cmd line
        if (data_dir == null || port == null || db_name == null || db_host == null || db_port == null || DEBUG == null || DSS_conf_path == null) {
            System.out.printf("loading Server configuration files from directory: %s\n", confFilePath);
            try {
                FileInputStream confFile = new FileInputStream(new File(confFilePath));
                Properties prop = new Properties();
                prop.load(confFile);
                if (data_dir == null)
                    data_dir = prop.getProperty("data_dir", default_data_dir);
                if (port == null)
                    port = Integer.parseInt(prop.getProperty("port", Integer.toString(default_port)));
                if (db_name == null)
                    db_name = prop.getProperty("db_name", default_db_name);
                if (db_host == null)
                    db_host = prop.getProperty("db_host", default_db_host);
                if (db_port == null)
                    db_port = Integer.parseInt(prop.getProperty("db_port", Integer.toString(default_db_port)));
                if (DEBUG == null)
                    DEBUG = Boolean.parseBoolean(prop.getProperty("DEBUG", Boolean.toString(default_DEBUG)));
                if (DSS_conf_path == null)
                    DSS_conf_path = prop.getProperty("DSS_conf_path", defaulf_DSS_conf_path);
            } catch (IOException e) {
                System.err.printf("WARNING: can't read configuration file (%s), using default and command line's argument.\n", confFilePath);
                e.printStackTrace();
                if (data_dir == null)
                    data_dir = default_data_dir;
                if (port == null)
                    port = default_port;
                if (db_name == null)
                    db_name = default_db_name;
                if (db_host == null)
                    db_host = default_db_host;
                if (db_port == null)
                    db_port = default_db_port;
                if (DEBUG == null)
                    DEBUG = default_DEBUG;
                if (DSS_conf_path == null)
                    DSS_conf_path = defaulf_DSS_conf_path;
            }
        }
        if (DEBUG)
            System.out.printf("-- Server configuration --\ndata_dir=%s\nport=%d\ndb_name=%s\ndb_host=%s\ndb_port=%d\nDEBUG=%s\nDSS_conf_path=%s\n\n",
                    data_dir, port, db_name, db_host, db_port, DEBUG, DSS_conf_path);
        return new Object[]{new Conf(data_dir, DEBUG, db_name, db_host, db_port), port, DSS_conf_path};
    }

    public static void main(String[] args) throws IOException {
        String help_string = String.format("simDB server v%s" + EOL + "\t-? print this help" + EOL +
                "\t-c \"path\" load DSS conf files from 'path' directory" + EOL +
                "\t-s \"path\" load Server conf files from 'path' directory (default simdbServer.conf)" + EOL +
                "\t-o \"path\" saves DI files to 'path' directory" + EOL +
                "\t-d disable DEBUGGING (kind of verbose mode)" + EOL +
                "\t-n \"name\" sets mongodb's db's name (default: 'simDB')" + EOL +
                "\t-x \"host\" sets mongodb's db's host (default: '127.0.0.1')" + EOL +
                "\t-y \"port\" sets mongodb's db's port (default: 27017)" + EOL +
                "\t-r reset the db" + EOL +
                "\t-p \"port\" sets server's port" + EOL, version);

        // true if the option needs an argument // else false
        HashMap<Character, Boolean> opts = new HashMap<Character, Boolean>();
        opts.put('?', false); opts.put('o', true); opts.put('p', true); opts.put('c', true); opts.put('r', false);
        opts.put('d', false); opts.put('n', true); opts.put('x', true); opts.put('y', true); opts.put('s', true);

        String confFilePath = default_conf_file;
        String data_dir = null;
        Integer port = null;
        Boolean DEBUG = null;
        String DSS_conf_path = null;
        String db_name = null;
        String db_host = null;
        Integer db_port = null;
        Boolean dropDB = null;


        int works = 0;
        for (int i=0; i < args.length; i++)
            switch (args[i].charAt(0)) {
                case '-':
                    char opt = args[i].charAt(1);
                    String value = null;
                    if (!opts.containsKey(opt))
                        throw new IllegalArgumentException("Unknown option: -" + opt);
                    if (opts.get(opt)) {
                        if (i+1 < args.length)
                            value = args[++i];
                        else
                            throw new IllegalArgumentException("Expected arg after: -" + opt);
                    }
                    switch (opt) {
                        case '?':
                            System.out.println(help_string);
                            break;
                        case 'r':
                            Scanner sc = new Scanner(System.in);
                            String answer;
                            do {
                                System.out.print("Are you sure you want to reset the db? [y/n] ");
                                answer = sc.nextLine().toLowerCase();
                                if (answer.equals("y"))
                                    dropDB = true;
                                else if (answer.equals("n"))
                                    dropDB = false;
                            } while (!answer.equals("y") && !answer.equals("n"));
                            break;
                        case 'c':
                            DSS_conf_path = value;
                            break;
                        case 'o':
                            data_dir = value;
                            break;
                        case 'd':
                            DEBUG = false;
                            break;
                        case 's':
                            confFilePath = value;
                            break;
                        case 'n':
                            db_name = value;
                            break;
                        case 'x':
                            db_host = value;
                            break;
                        case 'y':
                            try {
                                db_port = Integer.parseInt(value);
                            } catch (NumberFormatException e) {
                                System.err.println("Invalid port format.");
                                System.exit(-1);
                            }
                            break;
                        case 'p':
                            try {
                                port = Integer.parseInt(value);
                            } catch (NumberFormatException e) {
                                System.err.println("Invalid port format.");
                                System.exit(-1);
                            }
                            break;
                    }
                    break;
                default:
                    throw new IllegalArgumentException("Unknown option: " + args[i]);
            }
        Object[] conf_p = Server.loadConfFile(confFilePath, port, data_dir, DEBUG, db_name, db_host, db_port, DSS_conf_path);
        Conf _conf = (Conf) conf_p[0];
        port = (Integer) conf_p[1];
        DSS_conf_path = (String) conf_p[2];
        if (dropDB == null) {
            new Server(_conf, port, DSS_conf_path);
        } else if (dropDB) {
            System.out.println("Resetting the db and removing all files...");
            new DB(_conf).dropDB();
            System.out.println("Done.");
        }
    }
}
