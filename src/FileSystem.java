import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by Evgeniy Baranuk on 24.05.14.
 */
public class FileSystem {
    /**
     * Disk capacity - 16 bytes
     * Bitmask - diskCapacity bytes
     * File Descriptors - FILES_MAX_COUNT * (FILE_NAME_MAX_LENGTH + 1) (1 - id)
     * IndexNodes -
     *
     */
    private final int BLOCK_SIZE = 512;
    private final int DISK_CAPACITY_MAX_LENGTH = 8;
    private final int FILE_NAME_MAX_LENGTH = 8;
    private final int FILES_MAX_COUNT = 9;

    private RandomAccessFile io;
    private long deviceCapacity = -1; // blocks count
    private boolean mounted = false;
    private boolean[] fileIndexes = new boolean[FILES_MAX_COUNT];

    // pointers
    private long bitmaskPointer;
    private long descriptorsPointer;
    private long firstIndodePointer;
    private long firstBlockPointer;

    // opened files
    Map<Integer, Integer> openedFiles = new HashMap<Integer, Integer>();
    int fdCounter = 0;

    public boolean mount(String path) {
        if (mounted) unmount();

        try {
            io = new RandomAccessFile(path, "rw");

            deviceCapacity = getCapacity();

            bitmaskPointer = (DISK_CAPACITY_MAX_LENGTH + 1) * 2;
            descriptorsPointer = bitmaskPointer + (deviceCapacity - 1) * 2;
            firstIndodePointer = (((descriptorsPointer + (1 + FILE_NAME_MAX_LENGTH) * 2)) / BLOCK_SIZE + 1) * BLOCK_SIZE ;
            firstBlockPointer = firstIndodePointer + FILES_MAX_COUNT * BLOCK_SIZE * 2;
            updateFileIndexes();

            mounted = true;
            return true;
        } catch (FileNotFoundException e) {
            return false;
        } catch (IOException e) {
            return false;
        }
    }

    public void unmount() {
        mounted = false;
        deviceCapacity = -1;
        io = null;
    }

    private int getCapacity() throws IOException {
        StringBuilder num = new StringBuilder();
        io.seek(0);

        for (long i = 0; i < DISK_CAPACITY_MAX_LENGTH; i++) {
            num.append(io.readChar());
        }

        return Integer.parseInt(num.toString());
    }

    private int findFreeBlock() throws IOException {
        io.seek(bitmaskPointer);

        for (int i = 0; i < deviceCapacity; i++) {
            char c = io.readChar();
            if (c == '0')
                return i;
        }

        return -1;
    }

    private int freeBlockCount() throws IOException {
        io.seek(bitmaskPointer);
        int count = 0;

        for (int i = 0; i < deviceCapacity; i++) {
            if (io.readChar() == '0')
                count++;
        }

        return count;
    }

    public String ls() throws IOException {
        if (!isMounted()) return "Disk not mounted";

        StringBuilder res = new StringBuilder();
        io.seek(descriptorsPointer);

        for (int i = 0; i < FILES_MAX_COUNT; i++) {
            char num = io.readChar();
            if (num == '0') {
                io.seek(io.getFilePointer() + FILE_NAME_MAX_LENGTH * 2);
                continue;
            }

            res.append(num + " : ");

            boolean nameFound = false;
            for (int k = 0; k < FILE_NAME_MAX_LENGTH; k++) {
                char c = io.readChar();
                if (c == '0' && !nameFound) continue;
                else nameFound = true;

                res.append(c);
            }
            res.append('\n');
        }

        return res.toString();
    }

    public String filestat(String id_arg) throws IOException {
        if (!isMounted()) return "Disk not mounted";

        int id = Integer.parseInt(id_arg);

        if (!isIdUsed(id)) return "Cant find file with id : " + id;

        StringBuilder res = new StringBuilder();

        res.append("ID : " + id);
        res.append('\n');
        res.append("Type : " + (isDirectory(id) ? "directory" : "file"));
        res.append('\n');
        res.append("Links count : " + getFileLinksCount(id));
        res.append('\n');
        res.append("Size : " + getFileSize(id) + "B");
        res.append('\n');

        return res.toString();
    }

    public boolean create(String name) throws IOException {
        if (isFileExist(name)) {
            System.out.println("File already exist");
            return false;
        }

        if (name.length() > FILE_NAME_MAX_LENGTH) {
            System.out.println("File name is too long");
            return false;
        }

        int id = findFreeFileIndex();
        if (id == -1) return false;

        long pointer = descriptorsPointer + (FILE_NAME_MAX_LENGTH + 1) * 2 * (id - 1);
        io.seek(pointer);
        io.writeChar(id + '0');
        pointer += 2 + (FILE_NAME_MAX_LENGTH - name.length()) * 2;
        io.seek(pointer);
        io.writeChars(name);
        fileIndexes[id - 1] = true;

        setFileSize(id, 0);
        setFileLinksCount(id, 1);

        return true;
    }

    public int open(String name) throws IOException {
        int id = getFileId(name);
        if (id == -1) return -1;

        fdCounter++;
        openedFiles.put(fdCounter, id);

        return fdCounter;
    }

    public boolean close(int fd) {
        if (!openedFiles.containsKey(fd))
            return false;

        openedFiles.remove(fd);
        return true;
    }

    public boolean write(int fd, int offset, int size) throws IOException {
        if (!openedFiles.containsKey(fd)) {
            System.out.println("File was not opened");
            return false;
        }

        int id = openedFiles.get(fd);

        int fileSize = getFileSize(id);
        if (offset + size > getFileSize(id)) {
            System.out.println("File is too small to write this information");
            return false;
        }

        int fileBlocksCount = getFileSize(id) / BLOCK_SIZE + 1;
        int startBlock = offset / BLOCK_SIZE;
        int endBlcok = (offset + size) / BLOCK_SIZE;

        for (int j = startBlock; j <= endBlcok; j++) {
            io.seek(getInodePointer(id) + 6 + j * 2); // read block id from file desc
            int block = io.readChar();
            int pointerInBlock = offset % BLOCK_SIZE;
            for (int i = pointerInBlock; i < BLOCK_SIZE && size > 0; i++) {
                io.seek(getBlockPointer(block) + i * 2);
                io.writeChar('1');
                offset++;
                size--;
            }
        }

        return true;
    }

    public String read(int fd, int offset, int size) throws IOException {
        if (!openedFiles.containsKey(fd)) {
            return "File was not opened";
        }

        int id = openedFiles.get(fd);

        int fileSize = getFileSize(id);
        if (offset + size > getFileSize(id)) {
            return "File have not this information";
        }


        StringBuilder res = new StringBuilder();
        int startBlock = offset / BLOCK_SIZE;
        int endBlcok = (offset + size) / BLOCK_SIZE;

        for (int j = startBlock; j <= endBlcok; j++) {
            io.seek(getInodePointer(id) + 6 + j * 2); // read block id from file desc
            int block = io.readChar();
            int pointerInBlock = offset % BLOCK_SIZE;
            for (int i = pointerInBlock; i < BLOCK_SIZE && size > 0; i++) {
                io.seek(getBlockPointer(block) + i * 2);
                res.append(io.readChar());
                offset++;
                size--;
            }
        }

        return res.toString();
    }

    public boolean link(String parent, String link) throws IOException {
        if (isFileExist(link)) {
            System.out.println("File already exist");
            return false;
        }

        if (link.length() > FILE_NAME_MAX_LENGTH) {
            System.out.println("File name is too long");
            return false;
        }

        int parentid = getFileId(parent);
        int id = findFreeFileIndex();
        if (parentid == -1 || id == -1) return false;

        long pointer = descriptorsPointer + (FILE_NAME_MAX_LENGTH + 1) * 2 * (id - 1);
        io.seek(pointer);
        io.writeChar(parentid + '0');
        pointer += 2 + (FILE_NAME_MAX_LENGTH - link.length()) * 2;
        io.seek(pointer);
        io.writeChars(link);
        fileIndexes[id - 1] = true;

        setFileLinksCount(parentid, getFileLinksCount(parentid) + 1);

        return true;
    }

    public boolean unlink(String name) throws IOException {
        int id = getFileId(name);
        if (id == -1) return false;

        long pointer = descriptorsPointer + (FILE_NAME_MAX_LENGTH + 1) * 2 * (id - 1);
        io.seek(pointer);
        io.writeChar('0'); // id
        io.writeChars("00000000"); // name
        fileIndexes[id - 1] = false;
        setFileLinksCount(id, getFileLinksCount(id) - 1);

        if (getFileLinksCount(id) == 0) {
            pointer = getInodePointer(id);
            for (int i = 0; i < BLOCK_SIZE; i++)
                io.writeChar('0');
        }

        return true;
    }

    public boolean truncate(String name, int size) throws IOException {
        int id = getFileId(name);
        int prevSize = getFileSize(id);
        if (size > freeBlockCount() * BLOCK_SIZE) return false;

        if (prevSize == 0) {
            setFileSize(id, size);
            int blockNeededCount = size / BLOCK_SIZE + 1;
            int i = 0;
            while (blockNeededCount > 0) {
                int block = findFreeBlock();
                markBlockUsed(block);
                io.seek(getInodePointer(id) + 6 + i * 2);
                io.writeChar(block);
                io.seek(getBlockPointer(block));
                fillValue('0', BLOCK_SIZE);
                size -= BLOCK_SIZE;
                blockNeededCount--;
                i++;
            }
        }
        else {
            if (prevSize < size) {
                setFileSize(id, size);
                if (blocksCount(prevSize) < blocksCount(size)) {
                    int blockNeededCount = (size - prevSize) / BLOCK_SIZE;
                    int i = 1;
                    while (blockNeededCount > 0) {
                        int block = findFreeBlock();
                        markBlockUsed(block);
                        io.seek(getInodePointer(id) + 6 + (prevSize / BLOCK_SIZE + i)  * 2);
                        io.writeChar(block);
                        io.seek(getBlockPointer(block));
                        fillValue('0', BLOCK_SIZE);
                        size -= BLOCK_SIZE;
                        blockNeededCount--;
                        i++;
                    }
                }
            } else { // prevSize > size
                setFileSize(id, size);
                int blockNeededToRemove = blocksCount(prevSize) - blocksCount(size);
                for (int i = 0; i < blockNeededToRemove; i++) {
                    long pointer = getInodePointer(id) + 6 + (size / BLOCK_SIZE + 1 + i) * 2;
                    io.seek(pointer);
                    makeBlockUnused(io.readChar());
                    io.seek(pointer);
                    io.writeChar('0');
                    pointer += 2;
                }
            }
        }


        return true;
    }

    private void makeBlockUnused(int num) throws IOException {
        long pointer = bitmaskPointer + num * 2;
        io.seek(pointer);
        io.writeChar('0');
    }

    private void markBlockUsed(int num) throws IOException {
        long pointer = bitmaskPointer + num * 2;
        io.seek(pointer);
        io.writeChar('1');
    }

    private int getFileId(String name) throws IOException {
        for (int i = 0; i < fileIndexes.length; i++) {
            if (fileIndexes[i] && getFileName(i + 1).equals(name))
                return i + 1;
        }

        return -1;
    }

    private String getFileName(int id) throws IOException {
        StringBuilder res = new StringBuilder();
        long pointer = descriptorsPointer;
        io.seek(pointer);

        for (int i = 0; i < FILES_MAX_COUNT; i++) {
            if (Character.getNumericValue(io.readChar()) == id)
                break;

            pointer += (1 + FILE_NAME_MAX_LENGTH) * 2;
            io.seek(pointer);
        }

        boolean nameFound = false;

        for (int i = 0; i < FILE_NAME_MAX_LENGTH; i++) {
            char c = io.readChar();

            if (c == '0' && !nameFound) continue;
            else nameFound = true;

            res.append(c);
        }

        return res.toString();
    }

    private int getFileLinksCount(int id) throws IOException {
        long pointer = getInodePointer(id) + 2;
        io.seek(pointer);
        return io.readChar();
    }

    private void setFileLinksCount(int id, int count) throws IOException {
        long pointer = getInodePointer(id) + 2;
        io.seek(pointer);
        io.writeChar(count);
    }

    private boolean isDirectory(int id) throws IOException {
        long pointer = getInodePointer(id);
        io.seek(pointer);
        return io.readChar() == '1'; // 0 - file; 1 - directory
    }

    private boolean isFileExist(String name) throws IOException {
        for (int i = 0; i < fileIndexes.length; i++) {
            if (fileIndexes[i])
                if (getFileName(i + 1).equals(name))
                    return true;
        }

        return false;
    }

    private int getFileSize(int id) throws IOException {
        long pointer = getInodePointer(id) + 4;
        io.seek(pointer);

        char c = io.readChar();
        return c;
    }

    private void setFileSize(int id, int size) throws IOException {
        long pointer = getInodePointer(id) + 4;
        io.seek(pointer);
        io.writeChar(size);
    }

    private long getInodePointer(int id) throws IOException {
        return firstIndodePointer + (BLOCK_SIZE * (id - 1)) * 2;
    }

    private void updateFileIndexes() throws IOException {
        Arrays.fill(fileIndexes, false);

        long pointer = descriptorsPointer;

        for (int i = 0; i < FILES_MAX_COUNT; i++) {
            io.seek(pointer);
            char num = io.readChar();
            if (num == '0') {
                pointer += (FILE_NAME_MAX_LENGTH + 1) * 2;
                continue;
            } else {
                int id = Character.getNumericValue(num);
                fileIndexes[id - 1] = true;
            }
            pointer += (FILE_NAME_MAX_LENGTH + 1) * 2;
        }
    }

    private boolean isIdUsed(int id) {
        if (id < 0 || id > FILES_MAX_COUNT) return false;
        return fileIndexes[id - 1];
    }

    private int findFreeFileIndex() {
        for (int i = 0; i < fileIndexes.length; i++) {
            if (!fileIndexes[i]) return i + 1;
        }

        return -1;
    }

    private long getBlockPointer(int i) {
        return firstBlockPointer + BLOCK_SIZE * 2 * i;
    }

    private void fillValue(int val, int count) throws IOException {
        for (int i = 0; i < count; i++)
            io.writeChar(val);
    }

    private int blocksCount(int size) {
        return size / BLOCK_SIZE + 1;
    }

    public boolean isMounted() {
        return mounted;
    }
}