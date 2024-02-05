package FAT32;
import static FAT32.Shell.*;
import java.util.*;
import java.io.*;

public class Util {
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
            return (name + " is 0x" + printHex(val) + ", " + val);
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
            return (""
            +"Size is " + DIR_FileSize +"\n"
            +"Attributes " + attributes +"\n"
            +"Next cluster number is " + printHex(nextClusterNumber));
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
            if (root) names.add("."); //unique condition of root  
            if (root) names.add(".."); //unique condition of root 
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
                if (DirEntry.parsableEntryRaw(entryRaw)) {
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
        return printHex(val, 4);//Default: 4 byte value.
    }

    protected static String printHex (long val, int padding) {
        return String.format("0x%04X", val);
    }


}