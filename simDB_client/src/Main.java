public class Main {
    static final String version = "0.1";

    public static void main(String[] args) {
        boolean return_status;
        try {
            return_status = Worker.parseArgs(args);
        } catch (Exception e) {
            e.printStackTrace();
            return_status = false;
        }
        System.exit(return_status ? 0 : -1);
    }

}
