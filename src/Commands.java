/**
 * Created by Evgeniy Baranuk on 24.05.14.
 */
public enum Commands {
    mount,
    unmount,
    filestat,
    ls,
    create,
    open,
    close,
    read,
    write,
    link,
    unlink,
    truncate,
    commandNotFound,
    q;


    public static Commands commandOf(String s) {
        String[] args = s.split(" ");

        for (Commands c : values()) {
            if (c.name().equals(args[0])) {
                return c;
            }
        }

        return commandNotFound;
    }
}