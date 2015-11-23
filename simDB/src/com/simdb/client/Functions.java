package com.simdb.client;

import com.simdb.DS;
import com.simdb.Errors;

import java.io.File;
import java.util.ArrayList;

public class Functions {
    public Conf conf;

    private Requester requester;

	public Functions(Conf conf) {
        this.conf = conf;
        requester = new Requester(conf.host, conf.port, conf.DEBUG);
        requester.run();
	}
	
	public void remove(String DSS_name, String seqNumber) throws Exception {
        // SSS invece di DSS come scritto nelle specifiche
        requester.readQueue.clear();
		String message = String.format("REMOVE %s\nSSS %s\n", seqNumber, DSS_name);

		requester.send(message);
		requester.readStatus();
	}
	
	public ArrayList<String> speclist() throws Exception {
        requester.readQueue.clear();
		String message = "SPECLIST\n";

		requester.send(message);


        requester.readStatus();
        String tmp[] = requester.readLine().split(" ", 2);
        if (!tmp[0].equals("FOUND"))
            throw new Errors.GenericError();
		Integer found = Integer.parseInt(tmp[1]);
        ArrayList<String> result = new ArrayList<String>(found);
		for (int i=0; i < found; i++)
			result.add(requester.readLine());
		return result;
	}

    public DS get(String DSS_name, String seqNumber) throws Exception {
        requester.readQueue.clear();
        // SSS invece di DSS come scritto nelle specifiche
        String message = String.format("GET %s\nSSS %s\n", seqNumber, DSS_name);

        requester.send(message);

        requester.readStatus();

        return DS.parseFromQueue(DSS_name, seqNumber, requester.readQueue, conf.file_dir, true);
    }

    public Integer insert(DS ds) throws Exception {
        requester.readQueue.clear();
		/*
		INSERT
        DSS name
        SD n
        F1.name F1.value
        F2.name F2.value
        ...
        Fn.name Fn.value
        DIFILES l
        DIf1.name filesize1
        DIf2.name filesize2
        ...
        DIfl.name filesizel
        
        (if OK has been received)
        DIf1.name nbytes1
        <data_1>
		 */
        // SSS invece di DSS come scritto nelle specifiche
        StringBuilder messageB = new StringBuilder(
                "INSERT\nSSS " + ds.name + "\nSD " + ds.SD.size() + "\n");
        for (String[] SD : ds.SD)
            messageB.append(SD[0]).append(' ').append(SD[1]).append('\n');
        messageB.append("DI " + ds.DI.size() + "\n"); // sulle specifiche dice DIFILES ma nel programma usa DI
        for (String[] di : ds.DI)
            messageB.append(di[0]).append(' ').append(new File(di[1]).getName()).append('\n');
            //messageB.append(di[0]).append(' ').append(new File(di[1]).length()).append('\n');

        requester.send(messageB.toString());

        requester.readStatus();
        Integer seq_number = null;

        // non richiesto nelle specifiche ma lo fa nel programma del docente
        requester.send("DIFILES " + ds.DI.size() + "\n");

        for (int i=0; i < ds.DI.size(); i++) {
            String[] di = ds.DI.get(i);
            String encodedData = ds.getEncodedDI(i);

            String message = String.format("%s %d\n%s\n", di[0], encodedData.length(), encodedData);
            requester.send(message);

            if (i+1 == ds.DI.size()) {
                String[] tmp = requester.readLine().split(" ", 3);
                String return_code_string = String.format("%s %s", tmp[0], tmp[1]);
                requester.readStatus(return_code_string);
                seq_number = Integer.parseInt(tmp[2]);
            } else
                requester.readStatus();
        }

        return seq_number;
    }

    public ArrayList<DS> search(DS ds) throws Exception {
        requester.readQueue.clear();
		/*
        SEARCH
        DSS name
        SD l
        F1.name F1.value
        ...
        Fl.name Fl.value
		 */
        // The DS 'ds' is a 'fake' DS (basically a DS without DI) created to make this search
        String SD_list = "";
        for (String[] SD : ds.SD)
            SD_list = SD_list.concat(String.format("%s %s\n", SD[0], SD[1]));
        // SSS invece di DSS come scritto nelle specifiche
        String message = String.format("SEARCH\nSSS %s\nSD %d\n%s", ds.name, ds.SD.size(), SD_list);

        requester.send(message);

        requester.readStatus();
        String tmp[] = requester.readLine().split(" ", 2);
        if (!tmp[0].equals("FOUND"))
            throw new Errors.GenericError();
        Integer found_DS = Integer.parseInt(tmp[1]);

        ArrayList<DS> results = new ArrayList<DS>(found_DS);
        for (int i=0; i< found_DS; i++)
            results.add(DS.parseFromQueue(ds.name, null, requester.readQueue, conf.file_dir, false));
        return results;
    }

    public void quit() {
        //requester.readQueue.clear();
        requester.quit();
    }
}
