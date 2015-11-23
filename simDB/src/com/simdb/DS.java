package com.simdb;

import org.apache.commons.codec.binary.Base64;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.LinkedBlockingQueue;

import static org.apache.commons.io.FileSystemUtils.freeSpaceKb;


public class DS {
    public final static String EOL = System.getProperty("line.separator");

	public String name;
    public String seqNumber;
	public ArrayList<String[]> SD;
	public ArrayList<String[]> DI;
    public DS(String name, String seqNumber) {
        this.name = name;
        this.seqNumber = seqNumber;
        SD = new ArrayList<String[]>();
        DI = new ArrayList<String[]>();
    }

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder("**DSS: " + name);
        if (seqNumber != null)
            result.append(EOL + "**SeqNum: " + seqNumber);
        result.append(EOL + "--- SD List ---" + EOL);
        for (String[] sd : SD)
            result.append(sd[0]).append(": ").append(sd[1]).append(EOL);
        if (DI.size() > 0)
            result.append("--- DI List ---" + EOL);
        for (String[] di : DI)
            result.append(di[0]).append(": ").append(di[1]).append(EOL);
        return result.toString();
    }
    public String toInLineString() {
        StringBuilder result = new StringBuilder("**DSS: " + name);
        if (seqNumber != null)
            result.append(" **SeqNum: " + seqNumber);
        result.append(" --- SD List ---");
        for (String[] sd : SD)
            result.append(sd[0]).append(": ").append(sd[1]).append(" ; ");
        if (DI.size() > 0)
            result.append(" --- DI List ---");
        for (String[] di : DI)
            result.append(di[0]).append(": ").append(di[1]).append(" ; ");
        return result.toString();
    }

	public static DS parseFromQueue(String name, String seqNumber, LinkedBlockingQueue<String> queue, String file_dir, Boolean hasDI) throws Exception {
		/*
            SD n
            F1.name F1.value
            F2.name F2.value
            ...
            Fn.name Fn.value
            DI m
            DI1.name DI1.value
            DI2.name DI2.value
            ...
            DIm.name DIn.value
            DIFILES l
            DIf1.name nbytes1
            <data_1>
            DIf2.name nbytes2
            <data_2>
            ...
            DIf2.name nbytesl
            <data_l>
		 */
        // SD required
        // DI and DIFiles optionals
        // DI required to have DIFiles
        DS result = new DS(name, seqNumber);

        String[] tmp = queue.take().split(" ", 2);
        if (!tmp[0].equals("SD"))
            throw new Errors.GenericError();
        Integer SD_length = Integer.parseInt(tmp[1]);
        for (int i=0; i < SD_length; i++) {
            tmp = queue.take().split(" ", 2);
            if (result.seqNumber == null && tmp[0].equals("SeqNum"))
                result.seqNumber = tmp[1];
            else
                result.SD.add(tmp);
        }

        if (hasDI) {
            tmp = queue.take().split(" ", 2);
            if (!tmp[0].equals("DI"))
                throw new Errors.GenericError();
            Integer DI_length = Integer.parseInt(tmp[1]);
            HashMap<String, String> DINameToFile = new HashMap<String, String>();
            //system.err.println("LEN: " + DI_length);
            for (int i=0; i < DI_length; i++) {
                String[] di = queue.take().split(" ", 2);
                String fileName = result.getNewDIFilePath(file_dir, di).getAbsolutePath();
                result.DI.add(new String[]{di[0], fileName});
                //system.err.println(Arrays.toString(di) + " " + fileName);
                DINameToFile.put(di[1], fileName);
            }

            tmp = queue.take().split(" ", 2);
            if (!tmp[0].equals("DIFILES"))
                throw new Errors.GenericError();
            Integer DIFiles_length = Integer.parseInt(tmp[1]);
            for (int i=0; i < DIFiles_length; i++) {
                tmp = queue.take().split(" ", 2);
                String DIFile_name = DINameToFile.get(tmp[0]);
                Integer nbytes = Integer.parseInt(tmp[1]); // file length in base64

                //system.err.println("saving file: " + DIFile_name);
                String DIFile_base64 = queue.take();
                DS.saveEncodedFile(new File(DIFile_name), nbytes, DIFile_base64);
            }
        }

        return result;
    }

    public String getEncodedDI(Integer i) throws FileNotFoundException, IOException {
        String[] di = DI.get(i);
        File file = new File(di[1]);

        BufferedInputStream fin = new BufferedInputStream(new FileInputStream(file));
        byte buffer[] = new byte[32*1024];
        ByteArrayOutputStream bout = new ByteArrayOutputStream(buffer.length);
        int read = 0;
        while(read != -1) {
            read = fin.read(buffer);
            if (read > 0)
                bout.write(buffer, 0, read);
        }
        fin.close();
        byte[] encoded = Base64.encodeBase64(bout.toByteArray());
        String encodedData = new String(encoded);
        return encodedData;

       /* InputStream is = new FileInputStream(file);
        ByteArrayOutputStream base64OutputStream = new ByteArrayOutputStream();
        Base64OutputStream out = new Base64OutputStream(base64OutputStream);
        IOUtils.copy(is, out);
        is.close();
        out.close();
        return new String(base64OutputStream.toByteArray());      */
    }

    public static void saveEncodedFile(File file, Integer nbytes, String DIFile_base64)
            throws Errors.TooMuchData, Errors.GenericError, IOException {
        if (DIFile_base64.length() != nbytes)
            throw new Errors.GenericError();
        byte[] decodedBytes = Base64.decodeBase64(DIFile_base64);
        Long freeSpace = null;
        try {
            freeSpace = freeSpaceKb(file.getParentFile().getAbsolutePath()) * 1024;
        } catch (Exception e) {
            freeSpace = file.getParentFile().getUsableSpace();
        }
        //System.err.println(freeSpace + " " + decodedBytes.length);
        //if (freeSpace < decodedBytes.length)
        //    throw new Errors.TooMuchData();
        //String fileData = new String(decodedBytes);

        file.getParentFile().mkdirs();

        BufferedOutputStream output = new BufferedOutputStream(new FileOutputStream(file));
        output.write(decodedBytes);
        output.close();
        /*FileWriter fstream = new FileWriter(file);
        BufferedWriter out = new BufferedWriter(fstream);
        out.write(fileData);
        out.close();     */
    }

    public void saveEncodedDIFile(Integer i, Integer nbytes, String DIFile_base64)
            throws Errors.TooMuchData, Errors.GenericError, IOException {
        //File.createTempFile("simDB", tmpDS.seqNumber + "_" + DIFileName)
        saveEncodedFile(new File(DI.get(i)[1]), nbytes, DIFile_base64);
    }

    public File getNewDIFilePath(String dataDir, Integer i) {
        return getNewDIFilePath(dataDir, DI.get(i));
    }
    public File getNewDIFilePath(String dataDir, String di[]) {
        return new File(new File(dataDir, name.replace("_", "-").replace("/", "-")).getAbsolutePath(),
                String.format("%s_%s", seqNumber, di[0]));
    }
}
