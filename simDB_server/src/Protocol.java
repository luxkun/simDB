import com.simdb.DS;
import com.simdb.Errors;
import com.simdb.server.Conf;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.StringTokenizer;

/**
 * Created with IntelliJ IDEA.
 * User: lux
 * Date: 1/20/13
 * Time: 3:00 PM
 * To change this template use File | Settings | File Templates.
 */
public class Protocol {
    public final static String EOL = System.getProperty("line.separator");
    private ArrayList<Object> reqData;
    private Object reqObj;
    private Integer reqType;
    private Object[] reqErrorCallback; // String className, Class[] classParams, String methodName, Class[] methodParams, Object[] args

    private ServerThread serverThread;
    public Protocol(ServerThread serverThread) {
        this.serverThread = serverThread;
        reqData = new ArrayList<Object>();
        reqType = -1;
        reqErrorCallback = null;
    }

    private void reset() {
        reqType = -1;
        reqData.clear();
        reqErrorCallback = null;
    }

    protected Object[] processInput(String theInput) {
        if (theInput == null)
            return null;
        String theOutput = null;
        Object[] result = new Object[2]; // int, String
        result[0] = null;
        result[1] = null;
        StringTokenizer tk = new StringTokenizer(theInput);
        String x, a, b;
        // -1: nothing
        //  0: remove_0 - returns
        //  1: get_0 - returns
        //  2: insert_0
        //  3: search_0
        //  4: search_1 (parse SD size)
        //  5: search_2 (parse SD) - returns
        //  6: insert_1 (parse SD size)
        //  7: insert_2 (parse SD)
        //  8: insert_3 (parse DIFILES size)
        //  9: insert_4 (parse DIFILES)
        //  10: insert_5 (parse DIFILE[x])
        //  11: insert_6 (get DIFILE[x] data) - returns on last one
        final int REMOVE_0 = 0, GET_0 = 1, INSERT_0 = 2, SEARCH_0 = 3, SEARCH_1 = 4, SEARCH_2 = 5, INSERT_1 = 6, INSERT_2 = 7,
                INSERT_3 = 8, INSERT_4 = 9, INSERT_5 = 10, INSERT_6 = 11, INSERT_7 = 12;
        Integer oldType = reqType;
        try {
            switch (reqType) {
                case REMOVE_0: // remove_0
                case GET_0: // get_0
                    // SSS %s
                    if (!tk.nextElement().toString().equals("SSS"))
                        result[0] = 99;
                    else {
                        String DSS_name = tk.nextElement().toString();
                        String seqNumber = (String) reqData.get(0);
                        if (reqType == REMOVE_0) {
                            serverThread.server.printf("removing DS: %s %s", DSS_name, seqNumber);
                            serverThread.db.remove(DSS_name, seqNumber);
                            result[0] = 0;
                        } else if (reqType == GET_0) {
                            DS ds = serverThread.db.get(DSS_name, seqNumber);
                            StringBuilder tmp = new StringBuilder("SD ").append(ds.SD.size()).append('\n');
                            for (String[] sd : ds.SD)
                                tmp.append(sd[0]).append(' ').append(sd[1]).append('\n');
                            tmp.append("DI ").append(ds.DI.size()).append('\n');
                            for (String[] di : ds.DI)
                                tmp.append(di[0]).append(' ').append(new File(di[1]).getName()).append('\n');
                            tmp.append("DIFILES ").append(ds.DI.size()).append('\n');
                            for (String[] di : ds.DI) {
                                String encodedData = ds.getEncodedDI(ds.DI.indexOf(di));

                                tmp.append(new File(di[1]).getName()).append(' ').append(encodedData.length()).append('\n');
                                tmp.append(encodedData).append('\n');
                            }
                            result[0] = 0;
                            result[1] = tmp.toString();
                        }
                    }
                    reset();
                    break;
                case INSERT_0: // insert_0
                    // DSS %s
                    if (!tk.nextElement().toString().equals("SSS"))
                        result[0] = 99;
                    else {
                        reqType = INSERT_1;
                        reqData.clear();
                        reqData.add(tk.nextElement().toString());
                    }
                    break;
                case SEARCH_1: // search_1
                case INSERT_1: // insert_1
                    // SD %d
                    if (!tk.nextElement().toString().equals("SD")) {
                        result[0] = 99;
                        reset();
                    } else {
                        reqType = reqType == SEARCH_1 ? SEARCH_2 : INSERT_2; // 4 search ; 6 insert
                        reqData.add(Integer.parseInt(tk.nextElement().toString()));
                        reqData.add(new ArrayList<String[]>());
                    }
                    break;
                case INSERT_2: // insert_2
                    // F1.name F1.value
                    ArrayList<String[]> arr = ((ArrayList) reqData.get(2));
                    a = tk.nextElement().toString();
                    b = tk.nextElement().toString();
                    arr.add(new String[]{a, b});
                    if (arr.size() >= (Integer) reqData.get(1))
                        reqType = INSERT_3;
                    break;
                case INSERT_3: // insert_3
                    // DIFILES %d
                    // dovrebbe essere DIFILES dalle specifiche
                    if (!tk.nextElement().toString().equals("DI")) {
                        result[0] = 99;
                        reset();
                    } else {
                        reqObj = 0;
                        reqType = INSERT_4;
                        reqData.add(Integer.parseInt(tk.nextElement().toString()));
                        reqData.add(new ArrayList<String[]>());
                    }
                    break;
                case INSERT_4: // insert_4
                    // DIf1.name DIf1.filename
                    ArrayList<String[]> tmparr = (ArrayList) reqData.get(4);
                    a = tk.nextElement().toString();
                    b = tk.nextElement().toString();
                    String[] obj = new String[]{a, b};
                    tmparr.add(obj);
                    //reqObj = (Integer) reqObj + Integer.parseInt(obj[1]);
                    String fileName = obj[1];
                    if (tmparr.size() >= (Integer) reqData.get(3)) {
                        String DSS_name = (String) reqData.get(0);
                        DS _ds = new DS(DSS_name, serverThread.db.makeSeqNumber(true));
                        _ds.SD = (ArrayList) reqData.get(2);
                        _ds.DI = new ArrayList<String[]>(tmparr.size());
                        for (String[] di : tmparr) {
                            File tmpFile = _ds.getNewDIFilePath(serverThread.server.conf.data_dir, di);
                            _ds.DI.add(new String[]{di[0], tmpFile.getAbsolutePath()});
                            //if (tmpFile.getUsableSpace() < (Integer) reqObj)
                            //    throw new Errors.TooMuchData();
                        }

                        HashMap<String, Integer> fileMap = new HashMap<String, Integer>();
                        for (String[] di : _ds.DI)
                            fileMap.put(di[0], _ds.DI.indexOf(di));
                        _ds.seqNumber = serverThread.db.insertTMP(_ds, _ds.seqNumber);
                        reqData.clear();
                        reqData.add(_ds);
                        reqData.add(fileMap);
                        reqType = INSERT_5;
                        result[0] = 0;
                        reqErrorCallback = new Object[]{
                                "com.simdb.server.DB", new Class[]{Conf.class}, new Object[]{serverThread.server.conf},
                                "removeTMP", new Class[]{String.class, String.class}, new Object[]{_ds.name, _ds.seqNumber}};
                    }
                    break;
                case INSERT_5: // insert_5
                    // DIFILES %d
                    // non c'e' proprio nelle specifiche
                    if (!tk.nextElement().toString().equals("DIFILES")) {
                        result[0] = 99;
                        reset();
                    } else {
                        reqObj = 0;
                        reqType = INSERT_6;
                    }
                    break;
                case INSERT_6: // insert_6
                    // DIf1.name nbyte1
                    a = tk.nextElement().toString();
                    HashMap<String, Integer> fmap = (HashMap<String, Integer>) reqData.get(1);
                    if (!fmap.containsKey(a))
                        throw new Errors.GenericError();
                    reqType = INSERT_7;
                    reqObj = new Object[]{a, Integer.parseInt(tk.nextElement().toString())};
                    break;
                case INSERT_7: // insert_7
                    // <data_1>
                    a = tk.nextElement().toString();
                    DS tmpDS = (DS) reqData.get(0);
                    Object[] tmpObj = (Object[]) reqObj;
                    String DIFile = (String) tmpObj[0];
                    Integer nbytes = (Integer) tmpObj[1];

                    HashMap<String, Integer> fmap2 = (HashMap<String, Integer>) reqData.get(1);
                    serverThread.server.printf("writing tmp file: %s", DIFile);
                    tmpDS.saveEncodedDIFile(fmap2.get(DIFile), nbytes, a);
                    fmap2.remove(DIFile);
                    if (fmap2.size() == 0) {
                        serverThread.db.makeDSPermanent(tmpDS.seqNumber);
                        result[0] = -1;
                        result[1] = "0 OK " + tmpDS.seqNumber;
                        serverThread.server.printf("succesfully inserted new DS row (DSS: '%s', SeqNumber: %s)", tmpDS.name, tmpDS.seqNumber);
                        reset();
                    } else {
                        reqType = INSERT_6;
                        result[0] = 0;
                    }
                    break;
                case SEARCH_0: // search_0
                    // SSS %s
                    if (!tk.nextElement().toString().equals("SSS")) {
                        result[0] = 99;
                        reset();
                    } else {
                        reqType = SEARCH_1;
                        reqData.clear();
                        reqData.add(tk.nextElement().toString());
                    }
                    break;
                case SEARCH_2: // search_2
                    // F1.name F1.value
                    ArrayList<String[]> tmparr2 = ((ArrayList<String[]>) reqData.get(2));
                    a = tk.nextElement().toString();
                    b = tk.nextElement().toString();
                    tmparr2.add(new String[]{a, b});
                    break;
                default:
                    if ((x = tk.nextElement().toString()).equals("QUIT")) {
                        result[0] = -2;
                    } else if (x.equals("SPECLIST")) {
                        ArrayList<String> speclist = serverThread.db.speclist();
                        StringBuilder tmp = new StringBuilder("FOUND ").append(speclist.size()).append('\n');
                        for (String row : speclist)
                            tmp.append(row).append('\n');
                        result[0] = 0;
                        result[1] = tmp.toString();
                    } else if (x.equals("REMOVE")) {
                        String seqNumber = tk.nextElement().toString();
                        reqType = REMOVE_0;
                        reqData.clear();
                        reqData.add(seqNumber);
                    } else if (x.equals("GET")) {
                        String seqNumber = tk.nextElement().toString();
                        reqType = GET_0;
                        reqData.clear();
                        reqData.add(seqNumber);
                    } else if (x.equals("INSERT")) {
                        reqType = INSERT_0;
                    } else if (x.equals("SEARCH")) {
                        reqType = SEARCH_0;
                    } else {
                        result[0] = 99;
                    }
            }
            if (oldType == SEARCH_1 || oldType == SEARCH_2) {
                ArrayList<String[]> tmparr = ((ArrayList<String[]>) reqData.get(2));
                if (tmparr.size() >= (Integer) reqData.get(1)) {
                    String DSS_name = (String) reqData.get(0);
                    DS _ds = new DS(DSS_name, null);
                    for (String[] dsObj : (ArrayList<String[]>) tmparr.clone())
                        if (dsObj[0].equals("SeqNum")) {
                            _ds.seqNumber = dsObj[1];
                            tmparr.remove(dsObj);
                        }
                    _ds.SD = tmparr;
                    ArrayList<DS> DSList = serverThread.db.search(_ds);
                    serverThread.server.printf("search results (found: %d), request: %s", DSList.size(), _ds.toInLineString());
                    StringBuilder tmp = new StringBuilder("FOUND ").append(DSList.size()).append('\n');
                    for (DS ds : DSList) {
                        tmp.append("SD ").append(ds.SD.size() + 1).append('\n');
                        tmp.append("SeqNum ").append(ds.seqNumber).append('\n');
                        for (String[] sd : ds.SD)
                            tmp.append(sd[0]).append(' ').append(sd[1]).append('\n');
                    }
                    result[0] = 0;
                    result[1] = tmp.toString();
                    reset();
                }
            }
        } catch (Exception e) {
            if (e instanceof Errors.WrongAuthentication)
                result[0] = 1;
            else if (e instanceof Errors.IncompleteSet)
                result[0] = 2;
            else if (e instanceof Errors.NoSuchSpecifier)
                result[0] = 3;
            else if (e instanceof Errors.TooMuchData)
                result[0] = 4;
            else if (e instanceof Errors.NoSuchSet)
                result[0] = 5;
            else if (e instanceof Errors.WrongType)
                result[0] = 6;
            else if (e instanceof Errors.UnknownName)
                result[0] = 7;
            else if (e instanceof Errors.UnknownField)
                result[0] = 8;
            else if (e instanceof Errors.GenericError)
                result[0] = 99;
            else {
                result[0] = 99;
                e.printStackTrace();
            }
            invokeErrorCallback();
            reset();
        }
        if (result[1] == null)
            result[1] = "";
        if (result[0] != null && (Integer) result[0] > 0)
            serverThread.server.printerr("err (%d:%d) on line: %s", oldType, reqType, theInput);
        return result;
    }

    protected void invokeErrorCallback() {
        if (reqErrorCallback != null)
            try {
                String className = (String) reqErrorCallback[0];
                Object[] classArgs = (Object[]) reqErrorCallback[2];
                String methodName = (String) reqErrorCallback[3];
                Object[] methodArgs = (Object[]) reqErrorCallback[5];
                serverThread.server.printerr("calling error callback: new %s(%s).%s(%s);",
                        className, Arrays.toString(classArgs), methodName, Arrays.toString(methodArgs));
                Class cl = Class.forName(className);
                Constructor c = cl.getConstructor((Class[]) reqErrorCallback[1]);
                Method m = cl.getDeclaredMethod(methodName, (Class[]) reqErrorCallback[4]);
                Object i = c.newInstance(classArgs);
                Object r = m.invoke(i, methodArgs);
            } catch (Exception exc) {
                exc.printStackTrace();
            }
    }
}
