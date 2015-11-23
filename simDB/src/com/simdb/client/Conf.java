package com.simdb.client;

public class Conf {
	public final String file_dir;
    public final String host;
    public final Integer port;

    public final Boolean DEBUG;
    public Conf() {
        this.file_dir = "data";
        this.host = "127.0.0.1";
        this.port = 4444;
        this.DEBUG = true;
    }
    public Conf(String file_dir) {
        this.file_dir = file_dir;
        this.host = "127.0.0.1";
        this.port = 4444;
        this.DEBUG = true;
    }
    public Conf(String file_dir, String host, Integer port) {
        this.file_dir = file_dir;
        this.host = host;
        this.port = port;
        this.DEBUG = true;
    }
    public Conf(String file_dir, String host, Integer port, Boolean DEBUG) {
        this.file_dir = file_dir;
        this.host = host;
        this.port = port;
        this.DEBUG = DEBUG;
    }
}
