import java.util.*;
import java.io.*;

public class fat32_reader {
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
        if (args.length != 1) {
            System.out.println("Error: Improper number of arguments. args=" + Arrays.toString(args));
            return;
        }
        
        String path = args[0];
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
        if (!currentDir.contains(fileName)) {
            System.out.println("Error: "+fileName+" is not a file");
            return;
        }
        DirEntry entry = currentDir.entryWithName(fileName);
        if (entry.isDirectory()) {
            System.out.println("Error: "+fileName+" is not a file");
            return;
        }

        long fileSize = currentDir.entryWithName(fileName).getFileSize();
        System.out.println("Size of "+fileName+" is "+fileSize+" bytes");
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
        } if (NUMBYTES <= 0) {
            System.out.println("Error: NUM_BYTES must be greater than zero");
            return; 
        } if (!currentDir.contains(FILE_NAME)) {
            System.out.println("Error: "+ FILE_NAME +" is not a file");
            return;
        } 
        
        DirEntry fileEntry = currentDir.entryWithName(FILE_NAME);
        if (fileEntry.isDirectory()) {
            System.out.println("Error: "+ FILE_NAME +" is not a file");
            return;
        }

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

    protected static class Field {
        private String name; //Microsoft official name, as defined by Ms spec.
        private int offset; //Defined by Ms spec.
        private int bytes; //Number of bytes in this field.
        private long val; //Value for field from the FAT.
        private long maxVal;//Calculated maximum value for this data type -- artificial unsigned type safety, becuase JAVA >:( 
        public Field (String name, int offset, int bytes) {
            this.name = name;
            this.offset = offset;
            this.bytes = bytes;
            this.maxVal = (long)(Math.pow(256,bytes) - 1); 
        }
        
        public void setVal(long val) {
            if (val > maxVal) throw new IllegalStateException("val=" + val + ". Field=" + this + ". val is too large for bytes=" + bytes);
            this.val = val;
        }

        public String getName() {return name;}
        public int getOffset() {return offset;}
        public int getBytes() {return bytes;}
        public long getVal() {return val;}

        @Override
        public String toString() {
            return (name + " is " + printHex(val) + ", " + val);
        }

        @Override
        public int hashCode() {
            return (int)(name.hashCode() + offset + bytes + val);
        }
    }

    protected static enum ATTR {
        ATTR_READ_ONLY(0x01),
        ATTR_HIDDEN(0x02),
        ATTR_SYSTEM(0x04),
        ATTR_VOLUME_ID(0x08),
        ATTR_DIRECTORY(0x10),
        ATTR_ARCHIVE(0x20),
        ATTR_LONG_NAME(0x0F);
        
        int bitmask;
        ATTR(int bitmask) {
            this.bitmask = bitmask;
        }
    }

    protected static class DirEntry {
        byte[] raw;
        byte[] DIR_Name8; //8 bytes, binary
        byte[] DIR_Name3; //3 bytes, binary
        byte DIR_Attr; //1 byte, binary
        
        long DIR_FstClusHI; //2 bytes, numeric
        long DIR_FstClusLO; //2 bytes, numeric
        long DIR_FileSize; //4 bytes, numeric
        
        boolean free;
        boolean endOfDir;
        boolean directory;

        String DIR_NameString;
        String DIR_Name8String;
        String DIR_Name3String;
        long nextClusterNumber;

        List<ATTR> DIR_AttrList;

        public DirEntry (byte[] raw) {
            if (raw.length != 32) throw new IllegalStateException("DirEntry constructor called with raw.length="+raw.length);
            this.raw = raw;
            DIR_Name8 = parseBytesToBytes(raw, 0, 8);
            DIR_Name3 = parseBytesToBytes(raw, 8, 3);
            DIR_Attr = parseBytesToBytes(raw, 11, 1)[0];
            
            DIR_FstClusHI = parseBytesToNumeric(raw, 20, 2);
            DIR_FstClusLO = parseBytesToNumeric(raw, 26, 2);
            DIR_FileSize = parseBytesToNumeric(raw, 28, 4);
            
            parseAttr();
            parseName();
            parseNextClusterNumber();

            if (raw[0] == 0xE5) free = true;
            if (raw[0] == 0x00) endOfDir = true;
            if (DIR_AttrList.contains(ATTR.ATTR_DIRECTORY)) directory = true;
        }
        
        public String getDIR_NameString() {return DIR_NameString;}
        public long getNextClusterNumber() {return nextClusterNumber;}
        public long getFileSize() {return DIR_FileSize;}
        public byte[] getRaw() {return this.raw;}

        private void parseAttr() {
            this.DIR_AttrList = new ArrayList<>();
            for (ATTR attr : ATTR.values()) {
                if ((this.DIR_Attr & attr.bitmask) != 0) this.DIR_AttrList.add(attr);
            }
        } 

        private void parseName() {
            DIR_NameString = DIR_Name8String = DIR_Name3String = "";        
            for (byte c : DIR_Name8) {
                if (!Character.isWhitespace(c)) DIR_Name8String += (char)c;
            }
            for (byte c : DIR_Name3) {
                if (!Character.isWhitespace(c)) DIR_Name3String += (char)c;
            }
            if (this.DIR_AttrList.contains(ATTR.ATTR_DIRECTORY)) DIR_NameString = DIR_Name8String;
            else DIR_NameString = DIR_Name8String + "." + DIR_Name3String;
        }

        private void parseNextClusterNumber() {
            nextClusterNumber = (DIR_FstClusHI << 16) + DIR_FstClusLO;
        }

        public boolean isFree() {return free;}
        public boolean isEndOfDir() {return endOfDir;}
        public boolean isDirectory() {return directory;}

        @Override    
        public String toString() {
            String attributes = "";
            for (ATTR attr : DIR_AttrList) attributes += attr + " ";
            if (attributes == "") attributes = "NONE";
            return (""
            +"Size is " + DIR_FileSize +"\n"
            +"Attributes " + attributes +"\n"
            +"Next cluster number is " + printHex(nextClusterNumber,4));
        }

    }
    
    protected static class Dir {
        private List<DirEntry> entries;
        private Set<String> names;
        private List<String> pathList;
        private boolean root;

        public Dir (List<DirEntry> entries, List<String> pathList) {
            this.entries = entries;
            this.pathList = pathList;
            this.names = new TreeSet<>();
            
            for (DirEntry entry : entries) names.add(entry.getDIR_NameString());
            if (pathList.equals(List.of(rootDirName))) root = true;
            if (root) {
                names.add("."); //unique condition of root  
                names.add(".."); //unique condition of root 
                entries.add(new DirEntry(rawRootDot()));
                entries.add(new DirEntry(rawRootDotDot()));
            }
        }
    
        public List<DirEntry> getEntries() {return entries;}
        public Set<String> getNames() {return names;}
        public String getNamesString() {return getNamesString(" ");}
        public String getNamesString(String delim) {return String.join(delim, names);}
        public List<String> getPathList() {return pathList;}
        public String getPathString() {
            List<String> pathTail = this.pathList.subList(1,pathList.size());
            return rootDirName + String.join(pathDelimiter, pathTail);
        }
        public boolean isRoot() {return root;}
        
        public boolean contains(DirEntry entry) {return entries.contains(entry);}
        public boolean contains(String name) {return names.contains(name);}
        
        public DirEntry entryWithName(String name) {
            if (!contains(name)) System.out.println("WARNING. entryWithName(String name) called for a name not in Dir. Usage: preface with call to contains(String name)");
            
            for (DirEntry entry : entries) {
                if (entry.getDIR_NameString().equals(name)) return entry;
            }
            return null;
        }

        private byte[] rawRootDot() {
            return new byte[]{46, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 16, 0, 100, -52, 54, -121, 68, -121, 68, 0, 0, -52, 54, -121, 68, 0, 0, 0, 0, 0, 0};
        }
        private byte[] rawRootDotDot() {
            return new byte[]{46, 46, 32, 32, 32, 32, 32, 32, 32, 32, 32, 16, 0, 100, -52, 54, -121, 68, -121, 68, 0, 0, -52, 54, -121, 68, 0, 0, 0, 0, 0, 0};
        }
        
    }    
    
    //Determines if an entry is parsable
    public static boolean parsableEntryRaw (byte[] raw) {
        byte[] DIR_Name = parseBytesToBytes(raw,0,11);
        byte DIR_Attr = parseBytesToBytes(raw, 11, 1)[0];
        if (DIR_Name[0] == 0xE5) return false; // directory entry is free
        if (DIR_Name[0] == 0x00) return false; //directory entry is free (same as for 0xE5), and there are no allocated directory entries after this one
        if ((DIR_Attr & ATTR.ATTR_LONG_NAME.bitmask) != 0) return false; //long-name entry
        return true;
    }

    protected static byte[] fileAsByteArray (DirEntry fileEntry, int offset, int numBytes) throws IOException {
        byte[] file = fileAsByteArray(fileEntry);
        if (offset + numBytes > file.length) return null;
        byte[] ret = new byte[numBytes]; 
        System.arraycopy(file, offset, ret, 0, numBytes);
        return ret;
    }

    protected static byte[] fileAsByteArray (DirEntry fileEntry) throws IOException {
        byte[] file = entryToFile(fileEntry);
        return file;
    }

    protected static Dir entryToDir(DirEntry entry, List<String> pathList) throws IOException {
        if (!entry.isDirectory()) return null;

        List<DirEntry> entries = new ArrayList<>();
        long clusterNumber = entry.getNextClusterNumber();
        do {
            entries.addAll(parseClusterAsDir(clusterNumber));
            clusterNumber = FAT[(int)clusterNumber];
        } while (continuesInFAT(clusterNumber));
        return new Dir(entries, pathList);
    }

    protected static byte[] entryToFile(DirEntry entry) throws IOException {
        if (entry.isDirectory()) return null;

        int fileSize = (int)entry.getFileSize();
        int currentSize = 0;
        byte[] file = new byte[fileSize];
        long clusterNumber = entry.getNextClusterNumber();
        while (currentSize < fileSize && continuesInFAT(clusterNumber)) {
            byte[] filePart = parseClusterAsFile(clusterNumber);
            int readableByes = Math.min(filePart.length, fileSize - currentSize);
            System.arraycopy(filePart, 0, file, currentSize, readableByes);
            clusterNumber = FAT[(int)clusterNumber];
            currentSize += bytesPerCluster;
        }
        return file;
    }

    protected static List<DirEntry> parseClusterAsDir(long clusterNumber) throws IOException {
        List<DirEntry> entries = new ArrayList<>(entriesPerCluster);
        byte[] cluster = readClusterBytes(clusterNumber);
        for (int entryNumber = 0; entryNumber < entriesPerCluster; entryNumber++) {
            byte[] entryRaw = parseBytesToBytes(cluster, entryNumber*bytesPerEntry, bytesPerEntry);
            if (parsableEntryRaw(entryRaw)) {
                entries.add(new DirEntry(entryRaw));
            }
        }
        return entries;
    }

    protected static byte[] parseClusterAsFile (long clusterNumber) throws IOException {
        byte[] cluster = readClusterBytes(clusterNumber);
        return cluster;
    }

    protected static byte[] readClusterBytes(long clusterNumber) throws IOException {
        seekToClusterNumber(clusterNumber);
        return readBytes(bytesPerCluster);
    }
    
    protected static void seekToClusterNumber(long clusterNumber) throws IOException {
        if (clusterNumber == 0) file.seek(cluster02Offset); //per MS spec pg. 25, references to root entry are uniquely 0
        else file.seek(cluster00Offset + bytesPerCluster*clusterNumber);
    }

    protected static Dir dirAt(String pathString) throws IOException {
        if (pathString == null || pathString == "") throw new IllegalStateException("changeDir(String pathString) called for empty pathString="+pathString);
        List<String> pathList = pathStringToList(pathString);
        return dirAt(pathList);
    }

    private static Dir dirAt(List<String> pathList) throws IOException {
        Dir dir = currentDir;
        if (pathList.get(0).equals(rootDirName)) {
            dir = rootDir;
            pathList.remove(0);
        }
        List<String> currentPathList = new ArrayList<>(dir.getPathList()); //Make a copy of the current dir's pathList, will be modified as the pathList is traversed

        for (String s : pathList) {
            if (s.equals(".")) continue; //No-opp for "."
            if (s.equals("..")) {
                if (dir.isRoot()) { continue; //No-opp for ".." at rootDir 
                } else {currentPathList.remove(currentPathList.size()-1);} //Moving up, remove last field.
            } else {currentPathList.add(s);} //Moving down, add one field.
            if (!dir.contains(s)) return null; 
            DirEntry nextDir = dir.entryWithName(s);
            dir = entryToDir(nextDir, currentPathList);
        }
        return dir;
    }

    //input: path, relative or absolute
    //output: array of strings enabling proper traversal
    protected static List<String> pathStringToList(String path) {
        List<String> pathList = new ArrayList<>();
        List<String> pathSplitList = new ArrayList<>(List.of(path.split(pathDelimiter)));
        if (path.startsWith(rootDirName)) {
            pathList.add(rootDirName);
            if (!pathSplitList.isEmpty()) pathSplitList.remove(0);
        } 
        pathList.addAll(pathSplitList);
        return pathList;
    }

    protected static boolean continuesInFAT(long clusterNumber) {
        return (clusterNumber < 0x0FFFFFF8 || clusterNumber > 0x0FFFFFFF);
    }

    //Parse byte array to numeric value in LITTLE ENDIAN format.
    protected static long parseBytesToNumeric(byte[] b, int offset, int len) {
        if (len > 7) System.out.println("WARNING. Attempted parseBytesToNumeric(byte[] b) for bytes=" + len + ". Returned signed long may overflow.");
        long ret = 0;
        for (int place = 0; place < len; place++)
        ret += (b[place + offset] & 0xFF)*Math.pow(256,place); //Properly read byte: unsigned_value * 256^place_in_endian_order. Use `& 0xFF` to force Java to treat as raw bits and then cast to larger data type
        return ret;
    }
    
    protected static long parseBytesToNumeric(byte[] b) {
        return parseBytesToNumeric(b, 0, b.length);
    }

    protected static byte[] parseBytesToBytes(byte[] b, int offset, int len) {
        byte[] ret = new byte[len];
        System.arraycopy(b, offset, ret, 0, len);
        return ret;
    }

    //Conveience method for parseBytesToNumeric(readBytes(bytes))
    protected static long readNumeric(int bytes) throws IOException {
        return parseBytesToNumeric(readBytes(bytes));
    }
    
    //Read array of size bytes.
    protected static byte[] readBytes (int bytes) throws IOException {
        byte[] b = new byte[bytes];
        file.read(b);
        return b;
    }

    protected static String printHex (long val) {
        return printHex(val,1);
    }

    protected static String printHex (long val, int padding) {
        return String.format("0x%0"+padding+"X", val);
    }

    
}
