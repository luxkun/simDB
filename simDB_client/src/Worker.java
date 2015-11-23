import com.simdb.DS;
import com.simdb.client.Conf;
import com.simdb.client.Functions;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Properties;

/**
 * Created with IntelliJ IDEA.
 * User: lux
 * Date: 1/11/13
 * Time: 6:13 PM
 * To change this template use File | Settings | File Templates.
 */
public class Worker {
    public final static String EOL = System.getProperty("line.separator");

    public static Conf conf;
    public static Functions functions;

    public final static String default_conf_file = "simdbClient.conf";
    public final static String default_file_dir = "data";
    public final static String default_host = "127.0.0.1";
    public final static Integer default_port = 4444;
    public final static Boolean default_DEBUG = true;

    public Worker() {
        this(new Conf());
    }

    public Worker(Conf conf) {
        this.conf = conf;
        functions = new Functions(conf);
    }

    public boolean search(DS ds) {
        try {
            ArrayList<DS> results = functions.search(ds);
            System.out.printf(EOL + "- SEARCH RESULTS (found %d) -" + EOL, results.size());
            for (DS row : results)
                System.out.println(row);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean remove(String DSS_name, String seqNumber) {
        try {
            functions.remove(DSS_name, seqNumber);
            System.out.println(EOL + "- REMOVED DS -" + EOL);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean get(String DSS, String seqNumber) {
        try {
            DS ds = functions.get(DSS, seqNumber);
            System.out.printf(EOL + "- GET RESULTS -" + EOL + "%s" + EOL + "Note: the files are saved in '%s'" + EOL, ds.toString(), this.conf.file_dir);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public Integer insert(DS ds) {
        System.out.println(EOL + "- Inserting DS -");
        try {
            Integer SeqNum = functions.insert(ds);
            System.out.printf("Successful inserted new DS (Seq. Number: %d)" + EOL, SeqNum);
            return SeqNum;
        } catch (Exception e) {
            e.printStackTrace();
            System.err.printf("Error while inserting new DS." + EOL);
            return null;
        }
    }

    public boolean speclist() {
        try {
            ArrayList<String> speclist = functions.speclist();
            System.out.println(EOL + "- SPECLIST -");
            for (String row : speclist)
                System.out.println(row);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public void quit() {
        functions.quit();
    }

    public static Conf loadConfFile(String confFilePath, String file_dir, String host, Integer port, Boolean DEBUG) {
        // conf priorities: 0: default - 1: conf - 2: cmd line
        if (file_dir == null || host == null || port == null || DEBUG == null) {
            System.out.printf("loading Client configuration files from directory: %s\n", confFilePath);
            try {
                FileInputStream confFile = new FileInputStream(new File(confFilePath));
                Properties prop = new Properties();
                prop.load(confFile);
                if (file_dir == null)
                    file_dir = prop.getProperty("file_dir", default_file_dir);
                if (host == null)
                    host = prop.getProperty("host", default_host);
                if (port == null)
                    port = Integer.parseInt(prop.getProperty("port", Integer.toString(default_port)));
                if (DEBUG == null)
                    DEBUG = Boolean.parseBoolean(prop.getProperty("DEBUG", Boolean.toString(default_DEBUG)));
            } catch (IOException e) {
                System.err.printf("WARNING: can't read configuration file (%s), using default and command line's argument.\n", confFilePath);
                e.printStackTrace();
                if (file_dir == null)
                    file_dir = default_file_dir;
                if (host == null)
                    host = default_host;
                if (port == null)
                    port = default_port;
                if (DEBUG == null)
                    DEBUG = default_DEBUG;
            }
        }
        if (DEBUG)
            System.out.printf("-- Client configuration --\nfile_dir=%s\nport=%d\nhost=%s\nport=%d\nDEBUG=%s\n\n",
                    file_dir, port, host, port, DEBUG);
        return new Conf(file_dir, host, port, DEBUG);
    }

    // n

    public static boolean parseArgs(String[] args) throws Exception {
        String help_string = String.format("simDB client v%s" + EOL + "\t-? print this help" + EOL +
                "\t-s \"DSS_name [Fn.name Fn.type]\" search a DS" + EOL +
                "\t-g \"DSS_name Seq_number\" returns the SD with Seq_number in DSS_name" + EOL +
                "\t-i \"DSS_name SD SD_length Fn.name Fn.type DIFILES DIFILES_length DIn.name DIfn.name\" inserts new DS" + EOL +
                "\t-l prints the spec list" + EOL +
                "\t-r \"DSS_name Seq_number\" removes a DS " + EOL +
                "\t-o \"path\" saves output files from get command to 'path' directory" + EOL +
                "\t-h \"host\" sets server's IP" + EOL +
                "\t-p \"port\" sets server's port" + EOL +
                "\t-d disable DEBUGGING (kind of verbose mode)" + EOL +
                "\t-c \"path\" load configuration file from 'path' (default: simdbClient.conf in working directory)" + EOL +
                "Note: you can do multiple commands and also even more than once the same command", Main.version);

        // true if the option needs an argument // else false
        HashMap<Character, Boolean> opts = new HashMap<Character, Boolean>();
        opts.put('?', false); opts.put('s', true); opts.put('g', true); opts.put('i', true); opts.put('o', true);
        opts.put('l', false); opts.put('o', true); opts.put('h', true); opts.put('p', true); opts.put('r', true);
        opts.put('d', false); opts.put('c', true);

        HashMap<Character, ArrayList<String>> workToDo = new HashMap<Character, ArrayList<String>>();
        workToDo.put('s', new ArrayList<String>()); workToDo.put('g', new ArrayList<String>());
        workToDo.put('i', new ArrayList<String>()); workToDo.put('l', new ArrayList<String>());
        workToDo.put('r', new ArrayList<String>());

        String confFilePath = default_conf_file;
        String file_dir = null;
        String host = null;
        Integer port = null;
        Boolean DEBUG = null;

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
                        case 's':
                            workToDo.get('s').add(value); works++;
                            break;
                        case 'g':
                            workToDo.get('g').add(value); works++;
                            break;
                        case 'i':
                            workToDo.get('i').add(value); works++;
                            break;
                        case 'l':
                            workToDo.get('l').add(null); works++;
                            break;
                        case 'r':
                            workToDo.get('r').add(value); works++;
                            break;
                        case 'o':
                            file_dir = value;
                            break;
                        case 'h':
                            host = value;
                            break;
                        case 'p':
                            try {
                                port = Integer.parseInt(value);
                            } catch (NumberFormatException e) {
                                System.err.println("Invalid port format.");
                                return false;
                            }
                            break;
                        case 'd':
                            DEBUG = false;
                            break;
                        case 'c':
                            confFilePath = value;
                            break;
                    }
                    break;
                default:
                    throw new IllegalArgumentException("Unknown option: " + args[i]);
            }
        if (args.length == 0)
            System.out.println(help_string);
        if (works == 0)
            return true;

        Conf _conf = Worker.loadConfFile(confFilePath, file_dir, host, port, DEBUG);

        Worker worker = new Worker(_conf);

        boolean return_status = true;
        for (Character job : workToDo.keySet()) {
            ArrayList<String> values = workToDo.get(job);
            String[] tmp;
            String DSS_name;
            for (String value : values)
                switch (job) {
                    case 's':
                        tmp = value.split(" ");
                        DSS_name = tmp[0];
                        ArrayList<String[]> SD = new ArrayList<String[]>(tmp.length);
                        for (int j=1; j < tmp.length; j++) {
                            String[] tmp_sd = new String[2];
                            tmp_sd[0] = tmp[j++];
                            tmp_sd[1] = tmp[j];
                            SD.add(tmp_sd);
                        }
                        DS ds = new DS(DSS_name, null);
                        ds.SD = SD;
                        if (!worker.search(ds))
                            return_status = false;
                        break;
                    case 'g':
                        tmp = value.split(" ");
                        if (!worker.get(tmp[0], tmp[1]))
                            return_status = false;
                        break;
                    case 'i':
                        tmp = value.split(" ");
                        DSS_name = tmp[0];
                        Integer SD_length_to_go = 0;
                        Integer DIFiles_length_to_go = 0;
                        DS ds_to_insert = new DS(DSS_name, null);
                        for (int j=1; j < tmp.length; j++) {
                            if (SD_length_to_go > 0) {
                                String[] tmp_sd = new String[2];
                                tmp_sd[0] = tmp[j++];
                                tmp_sd[1] = tmp[j];
                                ds_to_insert.SD.add(tmp_sd);
                                SD_length_to_go--;
                            } else if (DIFiles_length_to_go > 0) {
                                String[] tmp_difile = new String[2];
                                tmp_difile[0] = tmp[j++];
                                tmp_difile[1] = new File(tmp[j]).getAbsolutePath();
                                ds_to_insert.DI.add(tmp_difile);
                                DIFiles_length_to_go--;
                            } else if (tmp[j].equals("SD"))
                                SD_length_to_go = Integer.parseInt(tmp[++j]);
                            else if (tmp[j].equals("DIFILES"))
                                DIFiles_length_to_go = Integer.parseInt(tmp[++j]);
                            else
                                throw new IllegalArgumentException("Unexpected argument data: " + tmp[j]);
                        }
                        Integer seq_number = worker.insert(ds_to_insert);
                        if (seq_number == null)
                            return_status = false;
                        break;
                    case 'l':
                        if (!worker.speclist())
                            return_status = false;
                        break;
                    case 'r':
                        tmp = value.split(" ");
                        if (!worker.remove(tmp[0], tmp[1]))
                            return_status = false;
                        break;
                }
        }
        worker.quit();
        return return_status;
    }
}
