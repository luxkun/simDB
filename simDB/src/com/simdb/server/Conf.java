package com.simdb.server;

public class Conf {
	public final String data_dir;
    public final String db_name;
    public final String db_host;
    public final Integer db_port;

    public Boolean DEBUG = true;
    public Conf() {
        this("data");
    }
    public Conf(String data_dir) {
        this(data_dir, true)
    }
    public Conf(String data_dir, Boolean DEBUG) {
        this(data_dir, DEBUG, "simDB", "127.0.0.1", null);
    }
    public Conf(String data_dir, Boolean DEBUG, String db_name, String db_host, Integer db_port) {
        this.DEBUG = DEBUG;
        this.data_dir = data_dir;
        this.db_name = db_name;
        this.db_host = db_host;
        this.db_port = db_port;
    }
}
