import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Scanner;

/**
 * Created by Evgeniy Baranuk on 24.05.14.
 */
public class Main {
    public static void main(String[] a) {
        Scanner in = new Scanner(System.in);
        FileSystem fs = new FileSystem();

        while (true) {
            System.out.print("> ");
            String command = in.nextLine();
            String[] args = null;

            if (command.split(" ").length > 1)
                args = Arrays.copyOfRange(command.split(" "), 1, command.split(" ").length);

            switch (Commands.commandOf(command)) {
                case mount:
                    if (!checkArgs(args, 1)) break;

                    // TODO delete !!
                    if (fs.mount(args[0]))
                        System.out.println("Mounted : " + args[0]);
                    else
                        System.out.println("Error, disk not mounted");
                    break;

                case unmount:
                    if (!fs.isMounted()) System.out.println("Disk not mounted");
                    else {
                        fs.unmount();
                        System.out.println("Disk unmounted");
                    }
                    break;

                case filestat:
                    if (!checkArgs(args, 1)) break;

                    try {
                        System.out.println(fs.filestat(args[0]));
                    } catch (IOException e) {
                        System.out.println("IO error");
                    }
                    break;

                case ls:
                    try {
                        System.out.println(fs.ls());
                    } catch (IOException e) {
                        System.out.println("IO error");
                    }
                    break;

                case create:
                    if (!checkArgs(args, 1)) break;

                    try {
                        if (fs.create(args[0]))
                            System.out.println("File created");
                        else
                            System.out.println("Error");
                    } catch (IOException e) {
                        System.out.println("IO error");
                    }
                    break;

                case open:
                    if (!checkArgs(args, 1)) break;

                    try {
                        int id = fs.open(args[0]);
                        if (id == -1)
                            System.out.println("File does not exist");
                        else
                            System.out.println("Opened. File descriptor generated : " + id);
                    } catch (IOException e) {
                        System.out.println("IO error");
                    }
                    break;

                case close:
                    if (!checkArgs(args, 1)) break;

                    if (fs.close(Integer.parseInt(args[0])))
                        System.out.println("File closed");
                    else
                        System.out.println("Can not close file");
                    break;

                case read:
                    if (!checkArgs(args, 3)) break;
                    try {
                        System.out.println(
                                fs.read(Integer.parseInt(args[0]),
                                        Integer.parseInt(args[1]),
                                        Integer.parseInt(args[2]))
                        );
                    } catch (IOException e) {
                        System.out.println("Error");
                    }

                    break;

                case write:
                    if (!checkArgs(args, 3)) break;
                    try {
                        fs.write(Integer.parseInt(args[0]),
                                Integer.parseInt(args[1]),
                                Integer.parseInt(args[2]));
                    } catch (IOException e) {
                        System.out.println("IO error");
                    }

                    break;

                case link:
                    if (!checkArgs(args, 2)) break;

                    try {
                        fs.link(args[0], args[1]);
                    } catch (IOException e) {
                        System.out.println("IO error");
                    }

                    break;

                case unlink:
                    if (!checkArgs(args, 1)) break;

                    try {
                        if (!fs.unlink(args[0]))
                            System.out.println("Link not exist");;
                    } catch (IOException e) {
                        System.out.println("IO error");
                    }

                    break;

                case truncate:
                    if (!checkArgs(args, 2)) break;
                    try {
                        fs.truncate(args[0], Integer.parseInt(args[1]));
                    } catch (IOException e) {
                        System.out.println("IO error");
                    }
                    break;

                case commandNotFound:
                    System.out.println(command + ": Command not found");
                    break;

                case q:
                    System.exit(0);

            }

        }
    }

    public static boolean checkArgs(String[] args, int size) {
        if (args == null) {
            System.out.println("Enter arguments");
            return false;
        }
        if (args.length < size) {
            System.out.println("Enter more arguments");
            return false;
        }

        return true;
    }
}