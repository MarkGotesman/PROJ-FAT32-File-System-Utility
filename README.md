# PROJ-FAT32-File-System-Utility
A simple, user-space, shell-like utility that is capable of interpreting a FAT32 file system image. The program understands basic commands to manipulate the given file system image and does not corrupt the file system image. Written in Java with good object-oriented programming (OOP) style.  
  
# Quickstart  
## 1. Compilation  
__Command:__ `javac fat32_reader.java`   
__Notes:__ In the current directory of the .java file, run the above command. A few class files will be created in the pwd.   
  
## 2. Execution  
__Command:__ `java fat32_reader <FAT32_PATH>`  
__Parameters:__  
`<FAT32_PATH>` Valid linux path to the FAT32 .img file.   
__Notes:__ Run the above command with a valid relative or absolute path.  
