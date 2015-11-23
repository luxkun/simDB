package com.simdb.server;

import com.mongodb.*;
import com.simdb.DS;
import com.simdb.DSSFormat;
import com.simdb.Errors;

import java.io.File;
import java.net.UnknownHostException;
import java.util.*;


public class DB {
    public Conf conf;
    public com.mongodb.DB db;

    public DB(Conf conf) throws UnknownHostException {
        this.conf = conf;
        MongoClient mongoClient = new MongoClient(conf.db_host, conf.db_port);
        db = mongoClient.getDB(conf.db_name);
    }

    public void initialize(HashMap<String, DSSFormat> dssFormat_list) {
        initialize_basic_index();
        for (String DSS_name : dssFormat_list.keySet()) {
            try {
                getDSS(DSS_name);
            } catch (Errors.NoSuchSpecifier e) {
                DSSFormat dssFormat = dssFormat_list.get(DSS_name);
                BasicDBObject sdObj = new BasicDBObject();
                sdObj.putAll(dssFormat.SD);
                BasicDBObject diObj = new BasicDBObject();
                diObj.putAll(dssFormat.DI);
                BasicDBObject dssObj = new BasicDBObject("name", DSS_name).append("sd", sdObj)
                        .append("di", diObj);
                db.getCollection("DSS").insert(dssObj);
            }
        }
    }

    private void initialize_basic_index() {
        db.getCollection("DSS").createIndex(new BasicDBObject("name", 1));
    }

    private void initialize_DS_index(DSSFormat dssFormat_list) {
        BasicDBObject keys = new BasicDBObject("dss_name", 1).append("seq_number", 1);
        for (String sd_name : dssFormat_list.SD.keySet())
            keys.append("SD_" + sd_name, 1);
        db.getCollection("DS_index").createIndex(keys);
    }

    public void dropDB() {
        for (DBObject ds : db.getCollection("DS").find())
            _remove_files(ds);
        for (DBObject ds : db.getCollection("DS_tmp").find())
            _remove_files(ds);
        db.dropDatabase();
    }

    public DBObject getDSS(String DSS_name)
            throws Errors.NoSuchSpecifier  {
        DBObject dss = db.getCollection("DSS").findOne(
                new BasicDBObject("name", DSS_name));
        if (dss == null)
            throw new Errors.NoSuchSpecifier();
        return dss;
    }
    public DBObject getDS(String DSS_name, String seqNumber)
            throws Errors.NoSuchSpecifier, Errors.NoSuchSet {
        getDSS(DSS_name);
        return getDS(seqNumber);
    }
    public DBObject getDS(String seqNumber)
            throws Errors.NoSuchSpecifier, Errors.NoSuchSet {
        return _getDS(seqNumber, "DS");
    }
    public DBObject getDSTMP(String DSS_name, String seqNumber)
            throws Errors.NoSuchSpecifier, Errors.NoSuchSet {
        getDSS(DSS_name);
        return getDSTMP(seqNumber);
    }
    public DBObject getDSTMP(String seqNumber)
            throws Errors.NoSuchSet  {
        return _getDS(seqNumber, "DS_tmp");
    }
    private DBObject _getDS(String seqNumber, String coll_name)
            throws Errors.NoSuchSet  {
        DBObject ds = db.getCollection(coll_name).findOne(
                new BasicDBObject("seq_number", seqNumber));
        if (ds == null)
            throw new Errors.NoSuchSet();
        return ds;
    }

    public String makeSeqNumber(Boolean saveSeqNumber) {
        // for potential/future purpose seqNumber is handled as String but is currently always an Integer in a String
        /*
        DBCursor dsList = db.getCollection("DS").find().sort(new BasicDBObject("created", -1));
        DBCursor dsList2 = db.getCollection("DS_tmp").find().sort(new BasicDBObject("created", -1));
        Integer ds = 0;
        if (dsList.count() > 0)
            ds = Integer.parseInt((String) dsList.limit(1).next().get("seq_number"));
        Integer ds_tmp = 0;
        if (dsList2.count() > 0)
            ds = Integer.parseInt((String) dsList2.limit(1).next().get("seq_number"));
        return Integer.toString(1 + max(ds, ds_tmp));*/
        DBCursor cursor = db.getCollection("seqNumbers").find().sort(new BasicDBObject("created", -1));
        String seqNumber = cursor.count() > 0 ?
                Integer.toString(1 + Integer.parseInt((String) cursor.limit(1).next().get("seq_number"))) : "0";
        if (saveSeqNumber)
            insertSeqNumber(seqNumber);
        System.out.println("seq: " + seqNumber + " " + saveSeqNumber);
        return seqNumber;
    }

    public void insertSeqNumber(String seqNumber) {
        db.getCollection("seqNumbers").insert(new BasicDBObject("seq_number", seqNumber).append("created", new Date()));
    }


    public ArrayList<String> speclist() {
        DBCollection DSS_coll = db.getCollection("DSS");
        DBCursor cursor = DSS_coll.find();
        ArrayList<String> results = new ArrayList<String>(cursor.count());
        try {
            while (cursor.hasNext()) {
                DBObject obj = cursor.next();
                results.add((String) obj.get("name"));
            }
        } finally {
            cursor.close();
        }
        return results;

    }

    private void _remove(String coll, DBObject ds, Boolean remove_index)
            throws Errors.NoSuchSpecifier, Errors.NoSuchSet  {
        _remove_files(ds);
        db.getCollection(coll).remove(ds);
        if (remove_index)
            db.getCollection(coll + "_index").remove(new BasicDBObject("ds", ds.get("_id")));
    }
    private void _remove_files(DBObject ds) {
        DBObject diObj = (DBObject) ds.get("di");
        for (String di : diObj.keySet()) {
            String fileName = (String) diObj.get(di);
            new File(fileName).delete();
        }
    }
    public void remove(String DSS_name, String seqNumber)
            throws Errors.NoSuchSpecifier, Errors.NoSuchSet  {
        _remove("DS", getDS(DSS_name, seqNumber), true);
    }
    public void remove(DS ds)
            throws Errors.NoSuchSpecifier, Errors.NoSuchSet  {
        remove(ds.name, ds.seqNumber);
    }

    public void removeTMP(String DSS_name, String seqNumber)
            throws Errors.NoSuchSpecifier, Errors.NoSuchSet  {
        _remove("DS_tmp", getDSTMP(DSS_name, seqNumber), false);
    }
    public void removeTMP(DS ds)
            throws Errors.NoSuchSpecifier, Errors.NoSuchSet  {
        removeTMP(ds.name, ds.seqNumber);
    }

    public DS get(String DSS_name, String seqNumber)
            throws Errors.NoSuchSpecifier, Errors.NoSuchSet {
        DBObject dsObj = getDS(DSS_name, seqNumber);
        DS ds = new DS((String) dsObj.get("dss_name"), (String) dsObj.get("seq_number"));

        DBObject sdObj = (DBObject) dsObj.get("sd");
        for (String sd_name : sdObj.keySet())
            ds.SD.add(new String[]{sd_name, (String) sdObj.get(sd_name)});

        DBObject diObj = (DBObject) dsObj.get("di");
        for (String di_name : diObj.keySet())
            ds.DI.add(new String[]{di_name, (String) diObj.get(di_name)});

        return ds;
    }

    private String _insert(DS ds, String coll_name, Boolean makeIndex, String seqNumber)
            throws Errors.NoSuchSpecifier, Errors.NoSuchSet, Errors.IncompleteSet {
        DBObject dss = getDSS(ds.name);
        Map<String, Integer> sdFormat = ((DBObject) dss.get("sd")).toMap();
        int i = 0;
        for (String[] sd : ds.SD) {
            if (!sdFormat.containsKey(sd[0])) {
                System.err.println(sd[0] + " unknown sd.");
                throw new Errors.IncompleteSet();
            }
            Boolean check = true;
            switch (sdFormat.get(sd[0])) {
                case 0: // String
                    break;
                case 1: // int
                    try {
                        Integer.parseInt(sd[1]);
                    } catch (NumberFormatException e) {
                        check = false;
                        System.err.println(sd[1] + " is not a valid int.");
                    }
                    break;
                case 2: // float
                    try {
                        Float.parseFloat(sd[1]);
                    } catch (NumberFormatException e) {
                        check = false;
                        System.err.println(sd[1] + " is not a valid float.");
                    }
                    break;
                case 3: // date
                    break;
            }
            if (!check)
                throw new Errors.IncompleteSet();
            i++;
        }
        if (sdFormat.size() != i) {
            System.err.println("SD list incomplete.");
            throw new Errors.IncompleteSet();
        }
        DBObject DI = ((DBObject) dss.get("di"));
        Set<String> diFormat = DI.keySet();
        for (String[] di : ds.DI) {
            if (!diFormat.contains(di[0])) {
                System.err.println(di[0] + " unknown DI.");
                throw new Errors.IncompleteSet();
            }
            diFormat.remove(di[0]);
        }
        for (String di : diFormat) // check if unsent DIFILES are necessary
            if ((Boolean) ((BasicDBList) DI.get(di)).get(1)) {
                System.err.println(di + " is missing.");
                throw new Errors.IncompleteSet();
            }

        DBObject dssObj = getDSS(ds.name);

        DBObject sdObj = new BasicDBObject();
        for (String[] sd : ds.SD)
            sdObj.put(sd[0], sd[1]);
        DBObject diObj = new BasicDBObject();
        for (String[] di : ds.DI)
            diObj.put(di[0], di[1]);

        if (seqNumber == null)
            seqNumber = makeSeqNumber(true);
        Date now = new Date();
        BasicDBObject dsObj = new BasicDBObject("dss_name", ds.name).append("dss", dssObj.get("_id"))
                .append("sd", sdObj).append("di", diObj).append("seq_number", seqNumber).append("created", now);
        db.getCollection(coll_name).insert(dsObj);
        //return dsObj.get("_id").toString();
        if (makeIndex)
            makeIndexDS(dsObj);
        return seqNumber;
    }
    public String insertTMP(DS ds, String seqNumber)
            throws Errors.NoSuchSpecifier, Errors.NoSuchSet, Errors.IncompleteSet {
        return _insert(ds, "DS_tmp", false, seqNumber);
    }
    public String insertTMP(DS ds)
            throws Errors.NoSuchSpecifier, Errors.NoSuchSet, Errors.IncompleteSet {
        return insertTMP(ds, null);
    }
    public String insert(DS ds, String seqNumber)
            throws Errors.NoSuchSpecifier, Errors.NoSuchSet, Errors.IncompleteSet {
        return _insert(ds, "DS", true, seqNumber);
    }
    public String insert(DS ds)
            throws Errors.NoSuchSpecifier, Errors.NoSuchSet, Errors.IncompleteSet {
        return insert(ds, null);
    }
    public DBObject makeIndexDS(DBObject dsObj) {
        BasicDBObject dsIndexObj = new BasicDBObject("dss_name", dsObj.get("dss_name")).append("dss", dsObj.get("dss"))
                .append("seq_number", dsObj.get("seq_number")).append("created", dsObj.get("created"))
                .append("ds", dsObj.get("_id"));
        DBObject SD = (DBObject) dsObj.get("sd");
        for (String sd_name : SD.keySet())
            dsIndexObj.append("SD_" + sd_name, SD.get(sd_name));
        DBObject DI = (DBObject) dsObj.get("di");
        for (String di_name : DI.keySet())
            dsIndexObj.append("DI_" + di_name, DI.get(di_name));

        db.getCollection("DS_index").insert(dsIndexObj);
        return dsIndexObj;
    }

    public String makeDSPermanent(DBObject dsTMPObj)
            throws Errors.NoSuchSet {
        db.getCollection("DS_tmp").remove(dsTMPObj);
        dsTMPObj.removeField("_id");
        dsTMPObj.removeField("DIFiles");
        db.getCollection("DS").insert(dsTMPObj);
        makeIndexDS(dsTMPObj);
        return (String) dsTMPObj.get("seq_number");
    }
    public String makeDSPermanent(String seqNumber)
            throws Errors.NoSuchSet {
       return  makeDSPermanent(getDSTMP(seqNumber));
    }

    /*public void flagDIFile(String seqNumber, String fileName)
            throws Errors.NoSuchSet, Errors.GenericError {
        DBObject dsTMPObj = getDSTMP(seqNumber);
        ArrayList<String> DIFiles;
        if (dsTMPObj.containsField("DIFiles")) {
            DIFiles = (ArrayList<String>) dsTMPObj.get("DIFiles");
            dsTMPObj.removeField("DIFiles");
        } else
            DIFiles = new ArrayList<String>(1);
        if (DIFiles.contains("fileName"))
            throw new Errors.GenericError();
        DIFiles.add(fileName);
        dsTMPObj.put("DIFiles", DIFiles);

        db.getCollection("DS_tmp").save(dsTMPObj);
    }  */

    public ArrayList<DS> search(DS ds)
            throws Errors.NoSuchSpecifier, Errors.NoSuchSet {
        //DBObject DSS = getDSS(ds.name);
        BasicDBObject query = new BasicDBObject("dss_name", ds.name);
        for (String[] sd : ds.SD)
            query.append("SD_" + sd[0], sd[1]);
        if (ds.seqNumber != null)
            query.append("seq_number", ds.seqNumber);
        DBCursor resultsCursor = db.getCollection("DS_index").find(query);
        DBCollection coll = db.getCollection("DS");
        ArrayList<DS> results = new ArrayList<DS>(resultsCursor.count());
        for (DBObject dsIndex : resultsCursor) {
            DBObject dsObj = coll.findOne(dsIndex.get("ds"));
            DS newDS = new DS((String) dsObj.get("dss_name"), (String) dsObj.get("seq_number"));
            DBObject SD = (DBObject) dsObj.get("sd");
            for (String sd_name : SD.keySet())
                newDS.SD.add(new String[]{sd_name, (String) SD.get(sd_name)});
            DBObject DI = (DBObject) dsObj.get("di");
            for (String di_name : DI.keySet())
                newDS.DI.add(new String[]{di_name, (String) DI.get(di_name)});
            results.add(newDS);
        }
        return results;
    }
}
