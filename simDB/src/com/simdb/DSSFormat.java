package com.simdb;

import java.io.*;
import java.util.HashMap;

/**
 * Created with IntelliJ IDEA.
 * User: lux
 * Date: 1/20/13
 * Time: 6:11 PM
 * To change this template use File | Settings | File Templates.
 */
public class DSSFormat {
    public final static String EOL = System.getProperty("line.separator");
    public String name;

    private final static HashMap<String, Integer> sd_types = new HashMap<String, Integer>() {{
        put("string", 0);
        put("int", 1);
        put("float", 2);
        put("date", 3);
    }};
    public HashMap<String, Integer> SD; // name [str], type [int]

    private final static HashMap<String, Integer> di_types = new HashMap<String, Integer>() {{
        put("file", 0);
    }};
    public HashMap<String, Object[]> DI; // name [str], type [int], necessary [boolean], father_name [str] (name, 'Input', 'Output')
    public DSSFormat(String name) {
        this.name = name;
        this.SD = new HashMap<String, Integer>();
        this.DI = new HashMap<String, Object[]>();
    }
    public DSSFormat(String name, HashMap<String, Integer> SD, HashMap<String, Object[]> DI) {
        this.name = name;
        this.SD = SD;
        this.DI = DI;
    }
    public static HashMap<String, DSSFormat> loadFromDirectory(String directory) {
        Boolean parsingSD = false;
        Boolean parsingDI = false;

        File confFile = new File(directory);
        File[] listDir = confFile.listFiles();
        if (listDir == null) {
            System.err.printf("Cannot open conf files directory: %s" + EOL, confFile.getAbsolutePath());
            return null;
        }
        HashMap<String, DSSFormat> results = new HashMap<String, DSSFormat>(listDir.length);

        for (File file : listDir) {
            String fileName = file.getName();
            String extension = "";
            int i = fileName.lastIndexOf('.');

            if (i > fileName.lastIndexOf(File.pathSeparator)) {
                extension = fileName.substring(i + 1);
                fileName = fileName.substring(0, i);
            }
            if (file.isDirectory() || !extension.equals("conf"))
                continue;
            DSSFormat dssFormat = new DSSFormat(fileName);
            try {
                BufferedReader fin = new BufferedReader(new FileReader(file));
                String line;
                while ((line = fin.readLine()) != null) {
                    String tmp[] = line.split("\\s+");
                    if (tmp.length == 1 && tmp[0].length() > 0) {
                        if (tmp[0].equals("SD")) {
                            parsingSD = true;
                            parsingDI = false;
                        } else if (tmp[0].equals("DI")) {
                            parsingSD = false;
                            parsingDI = true;
                        } else {
                            System.err.printf("Error parsing conf file (0): %s" + EOL + "\tLine: '%s'" + EOL, file.getAbsolutePath(), line);
                            return null;
                        }
                    } else if (tmp.length > 1) {
                        if (!parsingSD && !parsingDI) {
                            System.err.printf("Error parsing conf file (1): %s" + EOL, file.getAbsolutePath());
                            return null;
                        } else if (parsingSD) {
                            if (!sd_types.containsKey(tmp[1])) {
                                System.err.printf("Error parsing conf file (2): %s" + EOL + "\tLine: '%s'" + EOL, file.getAbsolutePath(), line);
                                return null;
                            }
                            dssFormat.SD.put(tmp[0], sd_types.get(tmp[1]));
                        } else {
                            if (!di_types.containsKey(tmp[1]) || (!tmp[2].equals("N") && !tmp[2].equals("U"))) {
                                System.err.printf("Error parsing conf file (3): %s" + EOL + "\tLine: '%s'" + EOL, file.getAbsolutePath(), line);
                                return null;
                            }
                            dssFormat.DI.put(tmp[0], new Object[]{di_types.get(tmp[1]), tmp[2].equals("N"), tmp[3]});
                        }
                    }
                }
                fin.close();
                results.put(fileName, dssFormat);
            } catch (FileNotFoundException e) { // impossibile...
            } catch (IOException e) {
                System.err.println("Cannot read conf file: " + file.getAbsolutePath());
                return null;
            }
        }
        return results;
    }
}
