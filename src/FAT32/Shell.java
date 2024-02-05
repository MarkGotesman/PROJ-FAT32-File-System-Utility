package FAT32;
import static FAT32.Util.*;
import static FAT32.Util.Dir.dirAt;
import static FAT32.Util.Dir.parseClusterAsDir;
import static FAT32.Util.Dir.fileAsByteArray;
import java.util.*;
import java.io.*;

public class Shell {
    protected static RandomAccessFile file;
    protected static Dir currentDir;
    protected static Dir rootDir;
    protected static Scanner in = new Scanner(System.in);
    protected static long[] FAT;
    
    // Microsoft Name  Field                       Offset  Size    Value
    // BPB_BytesPerSec Bytes Per Sector            0x0B    16 Bits Always 512 Bytes
    // BPB_SecPerClus  Sectors Per Cluster         0x0D    8 Bits  1,2,4,8,16,32,64,128
    // BPB_RsvdSecCnt  Number of Reserved Sectors  0x0E    16 Bits Usually 0x20
    // BPB_NumFATS     Number of FATs              0x10    8 Bits  Always 2
    // BPB_FATSz32     Sectors Per FAT             0x24    32 Bits Depends on disk size
    // BPB_RootClus     Root Dir First Cluster      0x2C    32 Bits Usually 0x2
    protected static Field BPB_BytesPerSec = new Field("BPB_BytesPerSec", 0x0B, 2);
    protected static Field BPB_SecPerClus = new Field("BPB_SecPerClus", 0x0D, 1);
    protected static Field BPB_RsvdSecCnt = new Field("BPB_RsvdSecCnt", 0x0E, 2);
    protected static Field BPB_NumFATS = new Field("BPB_NumFATS", 0x10, 1);
    protected static Field BPB_FATSz32 = new Field("BPB_FATSz32", 0x24, 4);
    protected static Field BPB_RootClus = new Field("BPB_RootClus", 0x2C, 4);
    protected static List<Field> infoFields = List.of(BPB_BytesPerSec,BPB_SecPerClus,BPB_RsvdSecCnt,BPB_NumFATS,BPB_FATSz32); //Printed.
    protected static List<Field> privateFields = List.of(BPB_RootClus); //Not printed.
    protected static List<Field> fields = new ArrayList<>();
    
    protected static final String rootDirName = "/";
    protected static final String pathDelimiter = "/";

    //Shorthand members to make code more readable.
    protected static int bytesPerEntry = 32;
    protected static int bytesPerCluster;
    protected static int bytesPerFAT;
    protected static int entriesPerCluster;
    protected static int FATOffset;
    protected static int cluster02Offset;
    protected static int cluster00Offset;
    
    public static void main (String[] args) throws IOException {
        String path = args[0].toUpperCase();
        init(path);

        while (true) {
            System.out.print(currentDir.getPathString() + "] ");
            parseInput(in.nextLine());
        }
    }
    
    private static void init(String path) throws IOException {
        file = new RandomAccessFile(path, "r");
        initFields();
        initShorthands();
        initFAT();
        initRootDir();
    }

        private static void initFields() throws IOException {
            fields.addAll(infoFields);
            fields.addAll(privateFields);
            for (Field field : fields) {
                file.seek(field.getOffset());
                field.setVal(readNumeric(field.getBytes()));
            }
        }

        private static void initShorthands() {
            bytesPerCluster = (int)(BPB_SecPerClus.getVal() * BPB_BytesPerSec.getVal());
            bytesPerFAT = (int)(BPB_FATSz32.getVal() * bytesPerCluster);
            entriesPerCluster = bytesPerCluster/bytesPerEntry;
            FATOffset = (int)(BPB_RsvdSecCnt.getVal() * BPB_BytesPerSec.getVal());
            cluster02Offset = FATOffset + 2*bytesPerFAT;
            cluster00Offset = cluster02Offset - 2*bytesPerCluster;
        }

        private static void initFAT() throws IOException {
            FAT = new long[bytesPerFAT/4]; //Each FAT entry is a 4 byte numeric value.
            file.seek(FATOffset); //Seek to start of FAT.
            byte[] raw = readBytes(bytesPerFAT);
            for (int FATIndex = 0; FATIndex < FAT.length; FATIndex++) {
                FAT[FATIndex] = parseBytesToNumeric(raw, FATIndex*4, 4);
            }
        }

        private static void initRootDir() throws IOException {
            List<DirEntry> entries = parseClusterAsDir(2);
            List<String> pathList = new ArrayList<>();
            pathList.add(rootDirName);
            rootDir = new Dir(entries, pathList);
            currentDir = rootDir;
        }

    private static void parseInput(String input) throws IOException {
        if (input == null || input.equals("")) throw new IllegalArgumentException("Empty input"); 

        String[] split = input.split(" ");
        String command = split[0];
        String[] args = Arrays.copyOfRange(split, 1, split.length); 
        
        switch (command) {
            case "stop": 
                stop();
                break;
            case "info": 
                info();
                break;
            case "ls": 
                ls();
                break;
            case "stat": 
                stat(args);
                break;
            case "size": 
                size(args);
                break;
            case "cd": 
                cd(args);
                break;
            case "read": 
                read(args);
                break;
            default:
                System.out.println("Invalid command: " + command); 
        }
    }

    private static void stop() {
        in.close();
        System.exit(0);
    }
    
    private static void info() {
        for (Field infoField : infoFields) {
            System.out.println(infoField);
        }
    }
    
    private static void ls() {
        System.out.println(currentDir.getNamesString());
    }
    
    private static void stat(String[] args) {
        if (args.length != 1) {
            System.out.println("Error: Improper number of arguments for command: stat. args=" + Arrays.toString(args));
            return;
        }

        String FILE_NAME = args[0].toUpperCase();
        
        if (!currentDir.contains(FILE_NAME)) {
            System.out.println("Error: file/directory does not exist");
            return;
        }

        System.out.println(currentDir.entryWithName(FILE_NAME));
    }
    
    private static void size(String[] args) {
        if (args.length != 1) {
            System.out.println("Error: Improper number of arguments for command: size. args=" + Arrays.toString(args));
            return;
        }

        String fileName = args[0].toUpperCase();
        if (currentDir.contains(fileName)) {
            long fileSize = currentDir.entryWithName(fileName).getFileSize();
            System.out.println("Size of "+fileName+" is "+fileSize+" bytes");
        } else {
            System.out.println("Error: file/directory does not exist");
        } 
    }
    
    private static void cd(String[] args) throws IOException {
        if (args.length != 1) {
            System.out.println("Error: Improper number of arguments for command: cd. args=" + Arrays.toString(args));
            return;
        }

        String pathString = args[0].toUpperCase();
        Dir newDir = dirAt(pathString);
        if (newDir == null) System.out.println("Error: "+ pathString +" is not a directory");
        else currentDir = newDir;
    }
    
    private static void read(String[] args) throws IOException {
        if (args.length != 3) {
            System.out.println("Error: Improper number of arguments for command: read. args=" + Arrays.toString(args));
            return;
        }        

        String FILE_NAME = args[0].toUpperCase();
        int OFFSET = Integer.parseInt(args[1]);
        int NUMBYTES = Integer.parseInt(args[2]);
        
        if (OFFSET < 0) {
            System.out.println("Error: OFFSET must be a positive value");
            return;
        } if (NUMBYTES < 0) {
            System.out.println("Error: NUM_BYTES must be a positive value");
            return; 
        } if (!currentDir.contains(FILE_NAME)) {
            System.out.println("Error: "+ FILE_NAME +" is not a file");
            return;
        } 
        
        DirEntry fileEntry = currentDir.entryWithName(FILE_NAME);
        byte[] bytes = fileAsByteArray(fileEntry, OFFSET, NUMBYTES);
        
        if (bytes == null) {
            System.out.println("Error: attempt to read data outside of file bounds");
            return;
        }

        for (byte b : bytes) {
            if (b < 127) System.out.print((char)b);
            else System.out.print(printHex(b,2));
        }
        System.out.println();
    }


    
}
